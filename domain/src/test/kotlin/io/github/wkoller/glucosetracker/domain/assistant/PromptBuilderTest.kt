package io.github.wkoller.glucosetracker.domain.assistant

import io.github.wkoller.glucosetracker.domain.Fixtures
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.domain.model.SlotNaming
import io.github.wkoller.glucosetracker.domain.readout.ReadoutBuilder
import io.github.wkoller.glucosetracker.domain.readout.ReadoutPeriod
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {
    private val plan = Fixtures.plan()
    private val today = Fixtures.day1
    private val readings = listOf(Fixtures.reading(Slot.BREAKFAST, 137), Fixtures.reading(Slot.BEDTIME, 92, hour = 21))
    private val doses = listOf(Fixtures.correction(Slot.BREAKFAST, 4.0, Fixtures.at(today, 8)))

    private fun facts(naming: SlotNaming): String {
        val week = ReadoutBuilder.build(ReadoutPeriod.WEEK, plan, today, readings, doses, emptyList())
        return PromptBuilder.factsBlock(plan, week, today, readings, doses, naming)
    }

    @Test
    fun `meal names are used by default`() {
        val text = facts(SlotNaming.MEALS)
        assertTrue(text, "before breakfast" in text)
        assertTrue(text, "bedtime" in text)
        assertFalse(text, "morning" in text)
    }

    @Test
    fun `times of day replace the meal names when chosen`() {
        val text = facts(SlotNaming.TIMES_OF_DAY)
        assertTrue(text, "morning" in text)
        assertTrue(text, "night" in text)
        assertFalse(text, "breakfast" in text)
        assertFalse(text, "bedtime" in text)
    }
}
