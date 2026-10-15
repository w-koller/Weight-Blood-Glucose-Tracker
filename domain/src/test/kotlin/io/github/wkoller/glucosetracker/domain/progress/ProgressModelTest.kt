package io.github.wkoller.glucosetracker.domain.progress

import io.github.wkoller.glucosetracker.domain.Fixtures
import io.github.wkoller.glucosetracker.domain.Fixtures.day1
import io.github.wkoller.glucosetracker.domain.dosing.GlucoseStatus
import io.github.wkoller.glucosetracker.domain.model.MeterFlag
import io.github.wkoller.glucosetracker.domain.model.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressModelTest {
    private val plan = Fixtures.plan()
    private val to = day1
    private val from = day1.minusDays(6)

    private fun build(
        readings: List<io.github.wkoller.glucosetracker.domain.model.GlucoseReading> = emptyList(),
        measurements: List<io.github.wkoller.glucosetracker.domain.model.BodyMeasurement> = emptyList(),
        start: java.time.LocalDate = from,
    ) = ProgressModelBuilder.build(plan, start, to, readings, emptyList(), measurements)

    @Test
    fun `the first and last days are both in the period`() {
        val model = build(
            listOf(
                Fixtures.reading(Slot.BREAKFAST, 70, date = from.minusDays(1)),
                Fixtures.reading(Slot.BREAKFAST, 71, date = from),
                Fixtures.reading(Slot.BREAKFAST, 72, date = to),
                Fixtures.reading(Slot.BREAKFAST, 73, date = to.plusDays(1)),
            ),
        )
        assertEquals(7, model.days)
        assertEquals(listOf(71, 72), model.glucoseSeries.points.map { it.value })
        assertEquals(2, model.glucose.count)
    }

    @Test
    fun `points are in time order and grouped by day`() {
        val model = build(
            listOf(
                Fixtures.reading(Slot.DINNER, 90, date = to, hour = 17),
                Fixtures.reading(Slot.BREAKFAST, 80, date = to, hour = 7),
                Fixtures.reading(Slot.LUNCH, 85, date = from, hour = 12),
            ),
        )
        assertEquals(listOf(85, 80, 90), model.glucoseSeries.points.map { it.value })
        assertEquals(listOf(from, to), model.glucoseSeries.days.map { it.date })
        assertEquals(listOf(Slot.BREAKFAST, Slot.DINNER), model.glucoseSeries.days.last().points.map { it.slot })
    }

    @Test
    fun `each reading has its status, and meter flags count as low or above`() {
        val readings = listOf(
            Fixtures.reading(Slot.BREAKFAST, 70),
            Fixtures.reading(Slot.LUNCH, 35, hour = 12),
            Fixtures.reading(Slot.DINNER, 140, hour = 17),
            Fixtures.reading(Slot.BEDTIME, 11, hour = 21).copy(meterFlag = MeterFlag.LO),
            Fixtures.reading(Slot.OTHER, 333, hour = 23).copy(meterFlag = MeterFlag.HI),
        )
        val points = build(readings).glucoseSeries.points
        assertEquals(
            listOf(GlucoseStatus.IN_TARGET, GlucoseStatus.LOW, GlucoseStatus.ABOVE_TARGET, GlucoseStatus.LOW, GlucoseStatus.ABOVE_TARGET),
            points.map { it.status },
        )
        assertEquals(MeterFlag.LO, points[3].meterFlag)
        assertEquals(listOf(35, 11), build(readings).glucoseSeries.lows.map { it.value })
    }

    @Test
    fun `the series carry the plan's targets and weight goal`() {
        val model = build()
        assertEquals(plan.targets.lowTenths, model.glucoseSeries.targetLow)
        assertEquals(plan.targets.highTenths, model.glucoseSeries.targetHigh)
        assertEquals(plan.targets.hypoTenths, model.glucoseSeries.lowLine)
        assertEquals(plan.goals.goalWeightGrams, model.weightSeries.goal)
    }

    @Test
    fun `weeks are counted back from the last day, and the oldest may be short`() {
        val start = to.minusDays(15)
        val readings = (0..15).map { Fixtures.reading(Slot.BREAKFAST, 60 + it, date = start.plusDays(it.toLong())) }
        val model = build(readings, start = start)
        assertEquals(16, model.days)
        assertEquals(3, model.weeks.size)
        assertEquals(start, model.weeks[0].from)
        assertEquals(start.plusDays(1), model.weeks[0].to)
        assertEquals(to.minusDays(6), model.weeks[2].from)
        assertEquals(to, model.weeks[2].to)
        assertEquals(listOf(2, 7, 7), model.weeks.map { it.glucose.count })
        assertEquals(75, model.weeks[2].glucose.highest!!.tenths)
    }

    @Test
    fun `weight in the period and the change from before it`() {
        val measurements = listOf(
            Fixtures.weight(from.minusDays(3), 110_000),
            Fixtures.weight(from.plusDays(1), 109_500),
            Fixtures.weight(to, 109_000),
            Fixtures.weight(to.plusDays(1), 108_000),
        )
        val model = build(measurements = measurements)
        assertEquals(listOf(109_500, 109_000), model.weightSeries.points.map { it.value })
        assertEquals(-1_000, model.weightChangeInPeriod)
        assertEquals(109_000, model.weight.current)
        assertEquals(109_000, model.weeks.last().lastWeight!!.value)
    }

    @Test
    fun `an empty period has empty series`() {
        val model = build()
        assertTrue(model.glucoseSeries.points.isEmpty())
        assertTrue(model.glucoseSeries.days.isEmpty())
        assertNull(model.glucoseSeries.highest)
        assertEquals(0, model.glucose.count)
        assertNull(model.weightChangeInPeriod)
        assertEquals(1, model.weeks.size)
    }
}
