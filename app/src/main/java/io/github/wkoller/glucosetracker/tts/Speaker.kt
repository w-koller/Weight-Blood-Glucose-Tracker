package io.github.wkoller.glucosetracker.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** A voice installed on the phone, as offered in Choose a voice. */
data class PhoneVoice(val name: String, val locale: Locale)

/**
 * Reads text aloud with the voice the user chose: the phone's default voice, another voice installed
 * on the phone, or one of the optional natural voices. A natural voice that is not installed falls back
 * to the phone's default.
 */
class Speaker(private val context: Context, private val natural: () -> NaturalVoices) {
    private var tts: TextToSpeech? = null
    private var ready: CompletableDeferred<Boolean>? = null

    /** Speech waiting for the phone's voice to start up. It is told whether the voice started. */
    private var pending: ((Boolean) -> Unit)? = null
    private val requests = SpeechRequests()

    /** The phone's voice reports here, on its own thread, when each request has been read or stopped. */
    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) = ended(utteranceId)
        override fun onStop(utteranceId: String?, interrupted: Boolean) = ended(utteranceId)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = ended(utteranceId)

        private fun ended(utteranceId: String?) {
            utteranceId?.toLongOrNull()?.let(requests::end)
        }
    }

    @Volatile
    var choice: VoiceChoice = VoiceChoice.PhoneDefault

    /** Set once a natural voice has spoken, so stopping does not load the natural voices for nothing. */
    @Volatile
    private var naturalUsed = false

    /** The text being read aloud, or null when nothing is. It is set as soon as the text is asked for. */
    val speaking: StateFlow<String?> get() = requests.speaking

    fun speak(text: String, rate: Float) = speak(text, rate, choice)

    /** Speaks with a particular voice, for example to preview it before it is chosen. */
    fun speak(text: String, rate: Float, voice: VoiceChoice) {
        stop()
        val request = requests.begin(text)
        when (voice) {
            is VoiceChoice.Natural -> {
                val voices = natural()
                if (voices.isReady) {
                    naturalUsed = true
                    voices.speak(
                        text,
                        voice.speakerId,
                        naturalSpeed(rate),
                        onFinished = { requests.end(request) },
                        // If the natural voice fails, the phone's default voice reads the text instead, unless it has been stopped.
                        onFailure = { context.mainExecutor.execute { if (requests.isLatest(request)) speakOnPhone(text, rate, null, request) } },
                    )
                } else {
                    speakOnPhone(text, rate, null, request)
                }
            }
            is VoiceChoice.Phone -> speakOnPhone(text, rate, voice.name, request)
            VoiceChoice.PhoneDefault -> speakOnPhone(text, rate, null, request)
        }
    }

    /** Stops speaking straight away, including speech that is still waiting for a voice to start. */
    fun stop() {
        requests.cancel()
        pending = null
        tts?.stop()
        if (naturalUsed) natural().stop()
    }

    /** Stops reading [text], if that is what is being read. Anything else being read carries on. */
    fun stop(text: String) {
        if (speaking.value == text) stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = null
    }

    /** The phone's offline voices for the phone's language, with the phone's own country first. */
    suspend fun phoneVoices(): List<PhoneVoice> {
        if (!engine().await()) return emptyList()
        val device = Locale.getDefault()
        val voices: Set<Voice> = runCatching { tts?.voices }.getOrNull().orEmpty()
        return voices
            .filter { !it.isNetworkConnectionRequired && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() }
            .filter { it.locale.language == device.language }
            .sortedWith(compareBy<Voice>({ it.locale.country != device.country }, { it.locale.displayName }, { -it.quality }, { it.name }))
            .map { PhoneVoice(it.name, it.locale) }
    }

    private fun speakOnPhone(text: String, rate: Float, voiceName: String?, request: Long) {
        val say: (Boolean) -> Unit = { started ->
            val engine = tts
            if (started && engine != null) {
                val chosen = voiceName?.let { name -> runCatching { engine.voices }.getOrNull()?.firstOrNull { it.name == name } }
                // With no particular voice chosen, the phone's own default voice is used, whatever its language.
                engine.voice = chosen ?: engine.defaultVoice
                engine.setSpeechRate(rate)
                // The request number is the utterance ID, so the progress reports can say which request ended.
                if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, request.toString()) != TextToSpeech.SUCCESS) requests.end(request)
            } else {
                requests.end(request)
            }
        }
        val waiting = engine()
        if (waiting.isCompleted) {
            say(waiting.getCompleted())
        } else {
            pending = say
        }
    }

    private fun engine(): CompletableDeferred<Boolean> {
        ready?.let { return it }
        val deferred = CompletableDeferred<Boolean>()
        ready = deferred
        tts = TextToSpeech(context.applicationContext) { status ->
            val ok = status == TextToSpeech.SUCCESS
            deferred.complete(ok)
            val say = pending
            pending = null
            say?.invoke(ok)
        }
        tts?.setOnUtteranceProgressListener(progress)
        return deferred
    }

    private companion object {
        /** The phone voice rates are 0.75 (slow) and 0.95 (normal); natural voices use 1.0 for normal. */
        fun naturalSpeed(rate: Float): Float = (rate / 0.95f).coerceIn(0.6f, 1.2f)
    }
}

/**
 * Keeps track of what is being read aloud. Each request to speak gets a new number, so a late report
 * that an earlier request has ended does not clear a later one, even when both read the same text.
 */
internal class SpeechRequests {
    private var latest = 0L
    private val _speaking = MutableStateFlow<String?>(null)

    /** The text being read aloud, or null when nothing is. */
    val speaking: StateFlow<String?> = _speaking.asStateFlow()

    /** Records that [text] is being read, and returns the number of this request. */
    @Synchronized
    fun begin(text: String): Long {
        _speaking.value = text
        return ++latest
    }

    /** Records that [request] has ended. Nothing changes if a later request has begun since. */
    @Synchronized
    fun end(request: Long) {
        if (request == latest) _speaking.value = null
    }

    /** Ends every request, including one still waiting for a voice to start. */
    @Synchronized
    fun cancel() {
        latest++
        _speaking.value = null
    }

    @Synchronized
    fun isLatest(request: Long): Boolean = request == latest
}

val LocalSpeaker = staticCompositionLocalOf<Speaker?> { null }
