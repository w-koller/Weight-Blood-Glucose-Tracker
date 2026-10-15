package io.github.wkoller.glucosetracker.tts

import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowTextToSpeech

/** Reading aloud with the phone's voice, which Robolectric stands in for. */
@RunWith(AndroidJUnit4::class)
class SpeakerTest {
    private val speaker = Speaker(ApplicationProvider.getApplicationContext()) { error("Only the phone's voice is used here") }
    private val engine get() = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
    private val text = "Your reading is 13.7 mmol/L."

    @Test
    fun `text is being read from the moment it is asked for until the voice has read it`() {
        speaker.speak(text, 0.95f)
        // The phone's voice is still starting up, and the text waits for it.
        assertEquals(text, speaker.speaking.value)
        engine.onInitListener.onInit(TextToSpeech.SUCCESS)
        assertEquals(text, engine.lastSpokenText)
        assertEquals(text, speaker.speaking.value)
        // Robolectric reports that the text has been read when the main thread runs.
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(speaker.speaking.value)
    }

    @Test
    fun `stop silences the voice straight away`() {
        speaker.speak(text, 0.95f)
        engine.onInitListener.onInit(TextToSpeech.SUCCESS)
        speaker.stop()
        assertTrue(engine.isStopped)
        assertNull(speaker.speaking.value)
    }

    @Test
    fun `text stopped before the voice has started is never read`() {
        speaker.speak(text, 0.95f)
        speaker.stop()
        engine.onInitListener.onInit(TextToSpeech.SUCCESS)
        assertNull(engine.lastSpokenText)
        assertNull(speaker.speaking.value)
    }

    @Test
    fun `a voice that cannot start ends the reading`() {
        speaker.speak(text, 0.95f)
        engine.onInitListener.onInit(TextToSpeech.ERROR)
        assertNull(speaker.speaking.value)
    }

    @Test
    fun `stopping other text leaves the current reading alone`() {
        speaker.speak(text, 0.95f)
        speaker.stop("Your weight is 115.2 kg.")
        assertEquals(text, speaker.speaking.value)
        speaker.stop(text)
        assertNull(speaker.speaking.value)
    }
}
