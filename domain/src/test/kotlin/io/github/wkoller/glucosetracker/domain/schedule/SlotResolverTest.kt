package io.github.wkoller.glucosetracker.domain.schedule

import io.github.wkoller.glucosetracker.domain.Fixtures
import io.github.wkoller.glucosetracker.domain.model.Slot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class SlotResolverTest {
    private val plan = Fixtures.plan()

    @Test
    fun `times resolve to the nearest planned slot`() {
        val cases = mapOf(
            LocalTime.of(6, 0) to Slot.BREAKFAST,
            LocalTime.of(9, 44) to Slot.BREAKFAST,
            LocalTime.of(9, 45) to Slot.LUNCH,
            LocalTime.of(14, 0) to Slot.LUNCH,
            LocalTime.of(14, 45) to Slot.DINNER,
            LocalTime.of(19, 15) to Slot.BEDTIME,
            LocalTime.of(23, 30) to Slot.BEDTIME,
            LocalTime.of(1, 0) to Slot.BEDTIME,
            LocalTime.of(2, 15) to Slot.BREAKFAST,
        )
        cases.forEach { (time, slot) -> assertEquals("at $time", slot, SlotResolver.resolve(plan, time)) }
    }

    @Test
    fun `the day starts halfway between bedtime and breakfast`() {
        assertEquals(2 * 60 + 15, SlotResolver.dayStartMinute(plan))
    }

    @Test
    fun `a check just after midnight belongs to the previous day`() {
        val date = LocalDate.of(2026, 10, 10)
        assertEquals(date.minusDays(1), SlotResolver.logicalDate(plan, LocalDateTime.of(date, LocalTime.of(0, 30))))
        assertEquals(date, SlotResolver.logicalDate(plan, LocalDateTime.of(date, LocalTime.of(6, 0))))
    }
}
