package io.github.wkoller.glucosetracker

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.wkoller.glucosetracker.data.prefs.Settings
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.ui.checkin.CheckInStep
import io.github.wkoller.glucosetracker.ui.checkin.CheckInViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * A check-in that Android closes in the background carries on where it was when the user comes back, for
 * example after a phone call from the low-reading screen. Nothing is recorded twice.
 */
@RunWith(AndroidJUnit4::class)
class CheckInStateTest {
    private val app: TrackerApp get() = ApplicationProvider.getApplicationContext()

    @Before
    fun seed() {
        runBlocking {
            app.container.settingsStore.update { Settings(setupDone = true) }
            app.container.repository.savePlanVersion(TestPlans.example())
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        repeat(500) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        fail("Timed out waiting for the check-in")
    }

    /** A check-in for breakfast, and the saved state Android would give back to a new one. */
    private fun breakfast(): Pair<CheckInViewModel, SavedStateHandle> {
        val handle = SavedStateHandle(mapOf("slot" to Slot.BREAKFAST.name))
        val vm = CheckInViewModel(app.container, handle)
        waitUntil { !vm.ui.value.loading }
        return vm to handle
    }

    private fun enterReading(vm: CheckInViewModel, reading: String, step: CheckInStep) {
        vm.setBglText(reading)
        vm.submitBgl()
        waitUntil { vm.ui.value.step == step && !vm.ui.value.busy }
    }

    @Test
    fun `a check-in carries on at the medicines`() {
        val (first, handle) = breakfast()
        enterReading(first, "13.7", CheckInStep.RESULT)
        first.giveSuggested()
        waitUntil { first.ui.value.step == CheckInStep.MEDICINE && !first.ui.value.busy }

        val second = CheckInViewModel(app.container, handle)
        waitUntil { second.ui.value.step == CheckInStep.MEDICINE && !second.ui.value.loading }
        val ui = second.ui.value
        assertEquals(137, ui.reading?.tenths)
        assertEquals(listOf(4.0), ui.recorded.map { it.givenAmount })
        assertEquals("NovoMix 30", ui.currentInsulin?.medicine?.displayName)
        assertEquals(1, runBlocking { app.container.repository.readingsBetween(ui.date, ui.date) }.size)
        assertEquals(1, runBlocking { app.container.repository.dosesBetween(ui.date, ui.date) }.size)
    }

    @Test
    fun `a low reading carries on at the low screen with its timer`() {
        val (first, handle) = breakfast()
        enterReading(first, "3.2", CheckInStep.LOW)
        first.startRecheckTimer()
        waitUntil { first.ui.value.recheckAt != null }

        val second = CheckInViewModel(app.container, handle)
        waitUntil { second.ui.value.step == CheckInStep.LOW && !second.ui.value.loading }
        assertEquals(first.ui.value.recheckAt?.toEpochMilli(), second.ui.value.recheckAt?.toEpochMilli())
        assertEquals(true, second.ui.value.advice?.isHypo)
    }
}
