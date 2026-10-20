package io.github.wkoller.glucosetracker.ui.plan

import io.github.wkoller.glucosetracker.domain.model.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class DraftValidatorTest {

    @Test
    fun `the usual check times are accepted`() {
        assertEquals(emptyList<DraftError>(), DraftValidator.times(PlanDraft()))
    }

    @Test
    fun `a bedtime set to the morning by mistake is caught`() {
        val draft = PlanDraft(slots = PlanDraft.DEFAULT_SLOTS.map { if (it.slot == Slot.BEDTIME) it.copy(time = LocalTime.of(9, 0)) else it })
        assertEquals(listOf(DraftError.TimesOutOfOrder), DraftValidator.times(draft))
    }

    @Test
    fun `a time with only a medicine still has to be in order`() {
        // Bedtime has no BGL check here, but a tablet is taken then, so its time still counts.
        val slots = PlanDraft.DEFAULT_SLOTS.map { if (it.slot == Slot.BEDTIME) it.copy(checkBgl = false, time = LocalTime.of(9, 0)) else it }
        val tablet = MedicineDraft(name = "Tablet", amounts = mapOf(Slot.BEDTIME to "1"))
        assertEquals(listOf(DraftError.TimesOutOfOrder), DraftValidator.times(PlanDraft(slots = slots, medicines = listOf(tablet))))
        assertEquals(emptyList<DraftError>(), DraftValidator.times(PlanDraft(slots = slots)))
    }

    @Test
    fun `a low level above the target range is caught`() {
        assertTrue(DraftError.HypoAboveRange in DraftValidator.targets(PlanDraft(hypo = "14")))
        assertEquals(emptyList<DraftError>(), DraftValidator.targets(PlanDraft()))
    }
}
