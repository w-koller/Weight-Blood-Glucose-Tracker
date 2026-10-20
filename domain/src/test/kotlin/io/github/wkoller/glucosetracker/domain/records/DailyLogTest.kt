package io.github.wkoller.glucosetracker.domain.records

import io.github.wkoller.glucosetracker.domain.Fixtures
import io.github.wkoller.glucosetracker.domain.Fixtures.day1
import io.github.wkoller.glucosetracker.domain.model.DoseStatus
import io.github.wkoller.glucosetracker.domain.model.Slot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class DailyLogTest {

    @Test
    fun `records are grouped by day and slot, newest day first`() {
        val day2 = day1.plusDays(1)
        val readings = listOf(
            Fixtures.reading(Slot.BREAKFAST, 137, day1),
            Fixtures.reading(Slot.LUNCH, 185, day1, hour = 12),
            Fixtures.reading(Slot.BREAKFAST, 155, day2),
        )
        val doses = listOf(Fixtures.correction(Slot.BREAKFAST, 4.0, Fixtures.at(day1, 8), day1))
        val log = DailyLog.build(readings, doses, listOf(Fixtures.weight(day2, 116_300)))
        assertEquals(listOf(day2, day1), log.map { it.date })
        assertEquals(116_300, log[0].weightGrams)
        assertEquals(listOf(Slot.BREAKFAST, Slot.LUNCH), log[1].slots.map { it.slot })
        assertEquals(1, log[1].slot(Slot.BREAKFAST)!!.correctionDoses.size)
    }

    @Test
    fun `adherence counts only doses that are already due`() {
        val plan = Fixtures.plan()
        val doses = listOf(
            Fixtures.fixed(Fixtures.premix, Slot.BREAKFAST, date = day1),
            Fixtures.fixed(Fixtures.premix, Slot.DINNER, DoseStatus.SKIPPED, date = day1),
            Fixtures.fixed(Fixtures.premix, Slot.BREAKFAST, date = day1.plusDays(1)),
        )
        val now = LocalDateTime.of(day1.plusDays(1), LocalTime.of(12, 0))
        val premix = AdherenceCalculator.calculate(plan, listOf(plan), Fixtures.zone, day1, now, day1.plusDays(1), doses).first { it.medicineKey == "premix" }
        assertEquals(3, premix.scheduled)
        assertEquals(2, premix.given)
        assertEquals(1, premix.skipped)
    }

    @Test
    fun `adherence starts on the day the plan started`() {
        val plan = Fixtures.plan().let { it.copy(effectiveFrom = Fixtures.at(day1, 9), goals = it.goals.copy(startDate = day1)) }
        val today = day1.plusDays(1)
        val now = LocalDateTime.of(today, LocalTime.of(12, 0))
        // A week is asked for, but only day 1 and this morning had the plan.
        val premix = AdherenceCalculator.calculate(plan, listOf(plan), Fixtures.zone, today.minusDays(6), now, today, emptyList())
            .first { it.medicineKey == "premix" }
        assertEquals(3, premix.scheduled)
    }

    @Test
    fun `a medicine added to the plan is counted from the day it was added`() {
        val first = Fixtures.plan(medicines = listOf(Fixtures.premix))
        val second = Fixtures.plan().copy(id = 2, version = 2, effectiveFrom = Fixtures.at(day1, 9))
        val today = day1.plusDays(1)
        val now = LocalDateTime.of(today, LocalTime.of(12, 0))
        val result = AdherenceCalculator.calculate(second, listOf(second, first), Fixtures.zone, day1.minusDays(1), now, today, emptyList())
        assertEquals(1 + 1, result.first { it.medicineKey == "tablet" }.scheduled)
        assertEquals(2 + 2 + 1, result.first { it.medicineKey == "premix" }.scheduled)
    }
}
