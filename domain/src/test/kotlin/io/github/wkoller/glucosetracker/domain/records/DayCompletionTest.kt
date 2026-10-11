package io.github.wkoller.glucosetracker.domain.records

import io.github.wkoller.glucosetracker.domain.Fixtures
import io.github.wkoller.glucosetracker.domain.Fixtures.day1
import io.github.wkoller.glucosetracker.domain.model.DoseStatus
import io.github.wkoller.glucosetracker.domain.model.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Instant
import java.time.LocalTime

class DayCompletionTest {
    private val plan = Fixtures.plan()
    private val today = day1.plusDays(3)
    private val noon = LocalTime.NOON

    private fun allReadings(date: java.time.LocalDate = day1) = Slot.scheduled.map { Fixtures.reading(it, 80, date) }
    private fun allMedicines(date: java.time.LocalDate = day1, status: DoseStatus = DoseStatus.GIVEN) = listOf(
        Fixtures.fixed(Fixtures.premix, Slot.BREAKFAST, status, date),
        Fixtures.fixed(Fixtures.premix, Slot.DINNER, status, date),
        Fixtures.fixed(Fixtures.tablet, Slot.BREAKFAST, status, date),
    )

    private fun status(readings: List<io.github.wkoller.glucosetracker.domain.model.GlucoseReading>, doses: List<io.github.wkoller.glucosetracker.domain.model.DoseRecord>, date: java.time.LocalDate = day1) =
        DayCompletion.status(plan, date, readings, doses, today, noon)

    @Test
    fun `every check and medicine recorded is complete`() {
        assertEquals(DayStatus.COMPLETE, status(allReadings(), allMedicines()))
    }

    @Test
    fun `a medicine recorded as not given still counts as recorded`() {
        assertEquals(DayStatus.COMPLETE, status(allReadings(), allMedicines(status = DoseStatus.SKIPPED)))
    }

    @Test
    fun `a missing check or medicine is partial`() {
        assertEquals(DayStatus.PARTIAL, status(allReadings().drop(1), allMedicines()))
        assertEquals(DayStatus.PARTIAL, status(allReadings(), allMedicines().drop(1)))
    }

    @Test
    fun `nothing recorded is none`() {
        assertEquals(DayStatus.NONE, status(emptyList(), emptyList()))
    }

    @Test
    fun `today is in progress until everything is recorded`() {
        assertEquals(DayStatus.TODAY_IN_PROGRESS, status(emptyList(), emptyList(), today))
        assertEquals(DayStatus.TODAY_IN_PROGRESS, status(allReadings(today).take(2), emptyList(), today))
        assertEquals(DayStatus.COMPLETE, status(allReadings(today), allMedicines(today), today))
    }

    @Test
    fun `future days and days without a plan are not judged`() {
        assertEquals(DayStatus.FUTURE, status(emptyList(), emptyList(), today.plusDays(1)))
        assertEquals(DayStatus.NOT_TRACKED, DayCompletion.status(null, day1, emptyList(), emptyList(), today, noon))
    }

    @Test
    fun `each day uses the plan version in force that day`() {
        val first = plan.copy(id = 1, effectiveFrom = Instant.parse("2026-10-07T00:00:00Z"))
        val second = plan.copy(id = 2, version = 2, effectiveFrom = Instant.parse("2026-10-10T03:00:00Z"))
        val plans = listOf(second, first)
        assertNull(PlanTimeline.planOn(day1.minusDays(5), plans, Fixtures.zone))
        assertSame(first, PlanTimeline.planOn(day1, plans, Fixtures.zone))
        assertSame(second, PlanTimeline.planOn(day1.plusDays(1), plans, Fixtures.zone))
        assertSame(second, PlanTimeline.planOn(day1.plusDays(5), plans, Fixtures.zone))
    }
}
