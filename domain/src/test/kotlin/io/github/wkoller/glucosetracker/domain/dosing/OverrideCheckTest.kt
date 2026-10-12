package io.github.wkoller.glucosetracker.domain.dosing

import io.github.wkoller.glucosetracker.domain.Fixtures
import io.github.wkoller.glucosetracker.domain.Fixtures.day1
import io.github.wkoller.glucosetracker.domain.model.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverrideCheckTest {
    private val plan = Fixtures.plan()
    private val now = Fixtures.at(day1, 12, 5)

    @Test
    fun `giving the prescribed dose at a prescribed time needs no confirmation`() {
        val advice = DoseAdvisor.advise(plan, Slot.LUNCH, 137, null, now, day1, emptyList())
        assertTrue(OverrideCheck.forCorrection(advice, Slot.LUNCH, 4.0).isEmpty())
    }

    @Test
    fun `a different amount asks for confirmation`() {
        val advice = DoseAdvisor.advise(plan, Slot.LUNCH, 137, null, now, day1, emptyList())
        assertEquals(listOf(DosePrompt.DifferentAmount(4.0, 6.0)), OverrideCheck.forCorrection(advice, Slot.LUNCH, 6.0))
    }

    @Test
    fun `a bedtime dose asks for confirmation`() {
        val advice = DoseAdvisor.advise(plan, Slot.BEDTIME, 144, null, now, day1, emptyList())
        assertEquals(listOf(DosePrompt.NotPrescribedTime(Slot.BEDTIME)), OverrideCheck.forCorrection(advice, Slot.BEDTIME, 6.0))
    }

    @Test
    fun `a second dose lists the earlier one first`() {
        val earlier = listOf(Fixtures.correction(Slot.LUNCH, 4.0, Fixtures.at(day1, 11, 50)))
        val advice = DoseAdvisor.advise(plan, Slot.LUNCH, 137, null, now, day1, earlier)
        val prompts = OverrideCheck.forCorrection(advice, Slot.LUNCH, 4.0)
        assertTrue(prompts.first() is DosePrompt.AlreadyDosed)
    }

    @Test
    fun `fixed medicines compare against the plan amount`() {
        assertTrue(OverrideCheck.forFixed(30.0, 30.0).isEmpty())
        assertEquals(listOf(DosePrompt.DifferentAmount(30.0, 28.0)), OverrideCheck.forFixed(30.0, 28.0))
    }
}
