package io.github.wkoller.glucosetracker.domain.progress

import io.github.wkoller.glucosetracker.domain.Fixtures
import io.github.wkoller.glucosetracker.domain.Fixtures.day1
import io.github.wkoller.glucosetracker.domain.format.Measures
import io.github.wkoller.glucosetracker.domain.model.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressTest {
    private val goals = Fixtures.plan().goals

    @Test
    fun `figures from the family spreadsheet`() {
        val measurements = listOf(Fixtures.weight(day1, 116_300), Fixtures.weight(day1.plusDays(2), 116_510))
        val progress = ProgressCalculator.weight(goals, measurements)
        assertEquals(1_510, progress.changeSinceStart)
        assertEquals("1.5", Measures.formatKgChange(progress.changeSinceStart!!))
        assertEquals(210, progress.changeSincePrevious)
        assertEquals(16_510, progress.remaining)
        assertEquals("16.5", Measures.formatKgChange(progress.remaining!!))
        assertFalse(progress.goalReached)
        assertEquals(0.0, progress.fraction!!, 0.0)
    }

    @Test
    fun `reaching the goal`() {
        val progress = ProgressCalculator.weight(goals, listOf(Fixtures.weight(day1, 100_000)))
        assertTrue(progress.goalReached)
        assertEquals(1.0, progress.fraction!!, 0.0)
    }

    @Test
    fun `weekly rate needed to reach a target date`() {
        val progress = ProgressCalculator.weight(goals, listOf(Fixtures.weight(day1, 114_000)))
        val today = LocalDate.of(2026, 10, 9)
        assertEquals(1_000, progress.perWeekNeeded(today, today.plusWeeks(14)))
        assertNull(progress.perWeekNeeded(today, null))
        assertNull(progress.perWeekNeeded(today, today.minusDays(1)))
    }

    @Test
    fun `glucose summary counts readings against the target range`() {
        val readings = listOf(
            Fixtures.reading(Slot.BREAKFAST, 137),
            Fixtures.reading(Slot.LUNCH, 185),
            Fixtures.reading(Slot.DINNER, 78),
            Fixtures.reading(Slot.BEDTIME, 35),
        )
        val summary = GlucoseStats.summarise(readings, Fixtures.plan().targets)
        assertEquals(4, summary.count)
        assertEquals(1, summary.lowCount)
        assertEquals(1, summary.inTargetCount)
        assertEquals(2, summary.aboveTargetCount)
        assertEquals(25, summary.inTargetPercent)
        assertEquals(35, summary.lowest!!.tenths)
        assertEquals(109, summary.averageTenths)
    }
}
