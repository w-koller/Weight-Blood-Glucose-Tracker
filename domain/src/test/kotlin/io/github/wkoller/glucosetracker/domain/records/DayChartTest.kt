package io.github.wkoller.glucosetracker.domain.records

import io.github.wkoller.glucosetracker.domain.Fixtures
import io.github.wkoller.glucosetracker.domain.Fixtures.day1
import io.github.wkoller.glucosetracker.domain.Fixtures.zone
import io.github.wkoller.glucosetracker.domain.dosing.GlucoseStatus
import io.github.wkoller.glucosetracker.domain.model.GlucoseTargets
import io.github.wkoller.glucosetracker.domain.model.MeterFlag
import io.github.wkoller.glucosetracker.domain.model.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayChartTest {
    private val targets = GlucoseTargets.DEFAULT

    /** The example plan's day starts at 3 am. */
    private val dayStart = 3 * 60

    @Test
    fun `the day's readings are placed by their time from the start of the day`() {
        val readings = listOf(
            Fixtures.reading(Slot.LUNCH, 62, hour = 12, id = 2),
            Fixtures.reading(Slot.BREAKFAST, 137, hour = 7, id = 1),
        )
        val chart = DayCharts.build(day1, readings, emptyList(), targets, dayStart, zone)

        assertEquals(listOf(1L, 2L), chart.entries.map { it.reading!!.id })
        assertEquals(listOf(4 * 60, 9 * 60), chart.points.map { it.minute })
        assertEquals(listOf(GlucoseStatus.ABOVE_TARGET, GlucoseStatus.IN_TARGET), chart.points.map { it.point.status })
    }

    @Test
    fun `a bedtime check after midnight stays at the end of its day`() {
        val late = Fixtures.reading(Slot.BEDTIME, 90, id = 3).copy(takenAt = Fixtures.at(day1.plusDays(1), 0, 30))
        val chart = DayCharts.build(day1, listOf(late), emptyList(), targets, dayStart, zone)

        assertEquals(listOf(21 * 60 + 30), chart.points.map { it.minute })
    }

    @Test
    fun `the readings of the 30 days before are shown behind the day, and later days are not`() {
        val readings = listOf(
            Fixtures.reading(Slot.BREAKFAST, 80, day1, hour = 7, id = 1),
            Fixtures.reading(Slot.BREAKFAST, 95, day1.minusDays(1), hour = 8, id = 2),
            Fixtures.reading(Slot.DINNER, 150, day1.minusDays(30), hour = 18, id = 3),
            Fixtures.reading(Slot.DINNER, 160, day1.minusDays(31), hour = 18, id = 4),
            Fixtures.reading(Slot.LUNCH, 70, day1.plusDays(1), hour = 12, id = 5),
        )
        val chart = DayCharts.build(day1, readings, emptyList(), targets, dayStart, zone)

        assertEquals(listOf(80), chart.points.map { it.point.value })
        assertEquals(listOf(150, 95), chart.others.map { it.point.value })
        assertEquals(listOf(15 * 60, 5 * 60), chart.others.map { it.minute })
    }

    @Test
    fun `each check keeps its doses and a meter's HI is above the range`() {
        val reading = Fixtures.reading(Slot.DINNER, 333, hour = 17, id = 7).copy(meterFlag = MeterFlag.HI)
        val dose = Fixtures.correction(Slot.DINNER, 10.0, Fixtures.at(day1, 17, 5)).copy(id = 9, readingId = 7)
        val chart = DayCharts.build(day1, listOf(reading), listOf(dose), targets, dayStart, zone)

        assertEquals(listOf(9L), chart.entries.single().doses.map { it.id })
        assertEquals(GlucoseStatus.ABOVE_TARGET, chart.points.single().point.status)
    }

    @Test
    fun `a day without readings has an empty chart over the days before`() {
        val chart = DayCharts.build(day1, listOf(Fixtures.reading(Slot.LUNCH, 70, day1.minusDays(2), hour = 12)), emptyList(), targets, dayStart, zone)

        assertTrue(chart.entries.isEmpty())
        assertTrue(chart.points.isEmpty())
        assertEquals(1, chart.others.size)
    }
}
