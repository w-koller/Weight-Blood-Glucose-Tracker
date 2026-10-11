package io.github.wkoller.glucosetracker.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CompletableDeferred
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
    private var pending: (() -> Unit)? = null

    @Volatile
    var choice: VoiceChoice = VoiceChoice.PhoneDefault

    /** Set once a natural voice has spoken, so stopping does not load the natural voices for nothing. */
    @Volatile
    private var naturalUsed = false

    fun speak(text: String, rate: Float) = speak(text, rate, choice)

    /** Speaks with a particular voice, for example to preview it before it is chosen. */
    fun speak(text: String, rate: Float, voice: VoiceChoice) {
        stop()
        when (voice) {
            is VoiceChoice.Natural -> {
                val voices = natural()
                if (voices.isReady) {
                    naturalUsed = true
                    voices.speak(text, voice.speakerId, naturalSpeed(rate))
                } else {
                    speakOnPhone(text, rate, null)
                }
            }
            is VoiceChoice.Phone -> speakOnPhone(text, rate, voice.name)
            VoiceChoice.PhoneDefault -> speakOnPhone(text, rate, null)
        }
    }

    fun stop() {
        tts?.stop()
        if (naturalUsed) natural().stop()
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

    private fun speakOnPhone(text: String, rate: Float, voiceName: String?) {
        val say: () -> Unit = {
            tts?.let { engine ->
                val chosen = voiceName?.let { name -> runCatching { engine.voices }.getOrNull()?.firstOrNull { it.name == name } }
                // With no particular voice chosen, the phone's own default voice is used, whatever its language.
                engine.voice = chosen ?: engine.defaultVoice
                engine.setSpeechRate(rate)
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
            }
            Unit
        }
        val waiting = engine()
        if (waiting.isCompleted) {
            if (waiting.getCompleted()) say()
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
            if (ok) pending?.invoke()
            pending = null
        }
        return deferred
    }

    private companion object {
        const val UTTERANCE_ID = "tracker"

        /** The phone voice rates are 0.75 (slow) and 0.95 (normal); natural voices use 1.0 for normal. */
        fun naturalSpeed(rate: Float): Float = (rate / 0.95f).coerceIn(0.6f, 1.2f)
    }
}

val LocalSpeaker = staticCompositionLocalOf<Speaker?> { null }
