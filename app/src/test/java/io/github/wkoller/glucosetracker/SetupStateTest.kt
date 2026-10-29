package io.github.wkoller.glucosetracker

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.wkoller.glucosetracker.domain.model.Frequency
import io.github.wkoller.glucosetracker.domain.model.MedicineKind
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.ui.plan.MedicineDraft
import io.github.wkoller.glucosetracker.ui.plan.PlanDraft
import io.github.wkoller.glucosetracker.ui.plan.PlanSetupViewModel
import io.github.wkoller.glucosetracker.ui.plan.SavedSetup
import io.github.wkoller.glucosetracker.ui.plan.SetupMode
import io.github.wkoller.glucosetracker.ui.plan.SetupStateJson
import io.github.wkoller.glucosetracker.ui.plan.SetupStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate
import java.time.LocalTime

/** A plan setup in progress survives Android closing the app in the background. */
@RunWith(AndroidJUnit4::class)
class SetupStateTest {
    private val app: TrackerApp get() = ApplicationProvider.getApplicationContext()

    private val draft = PlanDraft(
        correctionInsulin = "NovoRapid",
        prescriber = "Dr Example",
        orderDate = LocalDate.of(2026, 10, 7),
        bands = PlanDraft.commonBands(),
        scaleChecked = true,
        slots = PlanDraft.DEFAULT_SLOTS.map { if (it.slot == Slot.BEDTIME) it.copy(time = LocalTime.of(22, 15), reminderOn = false) else it },
        medicines = listOf(
            MedicineDraft(key = "premix", name = "NovoMix 30", kind = MedicineKind.INSULIN, unit = "units", amounts = mapOf(Slot.BREAKFAST to "30", Slot.DINNER to "26")),
        ),
        startDate = LocalDate.of(2026, 10, 8),
        startWeight = "115",
        goalWeight = "100",
        targetDate = LocalDate.of(2027, 3, 1),
        weighEvery = Frequency.WEEKLY,
    )

    private fun saved(step: SetupStep = SetupStep.GOALS) =
        SavedSetup(step, false, draft, "Mary", "Smith", "Anne", "0400 000 000", true, false, null, false, "salt", "hash")

    private fun waitUntil(condition: () -> Boolean) {
        repeat(500) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        fail("Timed out waiting for the setup to load")
    }

    @Test
    fun `a setup in progress is kept in full`() {
        val saved = saved()
        assertEquals(saved, SetupStateJson.decode(SetupStateJson.encode(saved)))
    }

    @Test
    fun `text that cannot be read starts the setup afresh`() {
        assertNull(SetupStateJson.decode("not a saved setup"))
        assertNull(SetupStateJson.decode(SetupStateJson.encode(saved()).replace("\"GOALS\"", "\"SOMEWHERE\"")))
    }

    @Test
    fun `the setup carries on where it was`() {
        val handle = SavedStateHandle(mapOf("mode" to SetupMode.FIRST.name, "setup" to SetupStateJson.encode(saved())))
        val vm = PlanSetupViewModel(app.container, handle)
        waitUntil { !vm.ui.value.loading }
        val ui = vm.ui.value
        assertEquals(SetupStep.GOALS, ui.step)
        assertEquals(draft, ui.draft)
        assertEquals("Mary", ui.firstName)
        assertEquals("hash", ui.chosenPinHash)
    }
}
