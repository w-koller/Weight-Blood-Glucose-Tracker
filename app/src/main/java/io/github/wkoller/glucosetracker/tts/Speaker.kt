package io.github.wkoller.glucosetracker.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

/** Reads text aloud in Australian English, falling back to the phone's default voice. */
class Speaker(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: Pair<String, Float>? = null

    fun speak(text: String, rate: Float) {
        val engine = tts
        if (engine == null) {
            pending = text to rate
            tts = TextToSpeech(context.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    val australian = Locale.forLanguageTag("en-AU")
                    if (tts?.isLanguageAvailable(australian)?.let { it >= TextToSpeech.LANG_AVAILABLE } == true) {
                        tts?.language = australian
                    }
                    pending?.let { (t, r) -> say(t, r) }
                    pending = null
                }
            }
        } else if (ready) {
            say(text, rate)
        } else {
            pending = text to rate
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
    }

    private fun say(text: String, rate: Float) {
        tts?.setSpeechRate(rate)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tracker")
    }
}

val LocalSpeaker = staticCompositionLocalOf<Speaker?> { null }
