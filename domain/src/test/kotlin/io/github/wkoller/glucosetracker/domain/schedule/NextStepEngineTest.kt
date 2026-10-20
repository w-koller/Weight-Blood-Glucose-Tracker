package io.github.wkoller.glucosetracker.domain.schedule

import io.github.wkoller.glucosetracker.domain.Fixtures
import io.github.wkoller.glucosetracker.domain.Fixtures.day1
import io.github.wkoller.glucosetracker.domain.model.DoseRecord
import io.github.wkoller.glucosetracker.domain.model.Frequency
import io.github.wkoller.glucosetracker.domain.model.GlucoseReading
import io.github.wkoller.glucosetracker.domain.model.Slot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class NextStepEngineTest {

    private fun day(readings: List<GlucoseReading> = emptyList(), doses: List<DoseRecord> = emptyList(), lastWeight: java.time.LocalDate? = day1) =
        DayRecords(day1, readings, doses, lastWeightDate = lastWeight, lastWaistDate = day1)

    private fun at(hour: Int, minute: Int = 0) = LocalDateTime.of(day1, LocalTime.of(hour, minute))

    @Test
    fun `early morning says the next check is breakfast`() {
        val step = NextStepEngine.next(Fixtures.plan(), at(5, 0), day())
        assertEquals(NextStep.AllDone(Slot.BREAKFAST, LocalTime.of(7, 30), isTomorrow = false), step)
    }

    @Test
    fun `breakfast check is due an hour before`() {
        assertEquals(NextStep.CheckIn(Slot.BREAKFAST, LocalTime.of(7, 30)), NextStepEngine.next(Fixtures.plan(), at(6, 30), day()))
    }

    @Test
    fun `once the breakfast reading is in, its medicines come next`() {
        val readings = listOf(Fixtures.reading(Slot.BREAKFAST, 137))
        // Not a second check: the reading is already recorded.
        assertEquals(NextStep.Medicines(Slot.BREAKFAST, LocalTime.of(7, 30)), NextStepEngine.next(Fixtures.plan(), at(8), day(readings)))
    }

    @Test
    fun `after breakfast is complete the weigh-in comes next when it is due`() {
        val plan = Fixtures.plan(weighEvery = Frequency.DAILY)
        val readings = listOf(Fixtures.reading(Slot.BREAKFAST, 137))
        val doses = listOf(Fixtures.fixed(Fixtures.premix, Slot.BREAKFAST), Fixtures.fixed(Fixtures.tablet, Slot.BREAKFAST))
        assertEquals(NextStep.Weigh, NextStepEngine.next(plan, at(8), day(readings, doses, lastWeight = day1.minusDays(1))))
        assertEquals(
            NextStep.AllDone(Slot.LUNCH, LocalTime.of(12, 0), isTomorrow = false),
            NextStepEngine.next(plan, at(8), day(readings, doses, lastWeight = day1)),
        )
    }

    @Test
    fun `a forgotten breakfast check is suggested until lunch is due`() {
        assertEquals(NextStep.CheckIn(Slot.BREAKFAST, LocalTime.of(7, 30)), NextStepEngine.next(Fixtures.plan(), at(10, 0), day()))
        assertEquals(NextStep.CheckIn(Slot.LUNCH, LocalTime.of(12, 0)), NextStepEngine.next(Fixtures.plan(), at(11, 0), day()))
    }

    @Test
    fun `after bedtime the next check is tomorrow's breakfast`() {
        val plan = Fixtures.plan(medicines = emptyList())
        val readings = Slot.scheduled.map { Fixtures.reading(it, 80) }
        assertEquals(NextStep.AllDone(Slot.BREAKFAST, LocalTime.of(7, 30), isTomorrow = true), NextStepEngine.next(plan, at(21, 30), day(readings)))
    }

    @Test
    fun `slot progress marks done, missed, due and upcoming`() {
        val plan = Fixtures.plan(medicines = emptyList())
        val readings = listOf(Fixtures.reading(Slot.BREAKFAST, 80))
        val progress = NextStepEngine.progress(plan, at(17, 0), day(readings)).map { it.state }
        assertEquals(listOf(SlotState.DONE, SlotState.MISSED, SlotState.DUE, SlotState.UPCOMING), progress)
    }
}
