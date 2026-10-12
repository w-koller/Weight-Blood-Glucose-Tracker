package io.github.wkoller.glucosetracker.domain.schedule

import io.github.wkoller.glucosetracker.domain.model.DoseRecord
import io.github.wkoller.glucosetracker.domain.model.Frequency
import io.github.wkoller.glucosetracker.domain.model.GlucoseReading
import io.github.wkoller.glucosetracker.domain.model.Plan
import io.github.wkoller.glucosetracker.domain.model.Slot
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

sealed interface NextStep {
    data class CheckIn(val slot: Slot, val time: LocalTime) : NextStep
    data class Medicines(val slot: Slot, val time: LocalTime) : NextStep
    data object Weigh : NextStep
    data object MeasureWaist : NextStep
    data class AllDone(val nextSlot: Slot?, val nextTime: LocalTime?, val isTomorrow: Boolean) : NextStep
}

enum class SlotState { DONE, PARTLY_DONE, DUE, UPCOMING, MISSED }

data class SlotProgress(val slot: Slot, val time: LocalTime, val state: SlotState)

/** Records for one logical day, plus the dates of the most recent measurements. */
data class DayRecords(
    val date: LocalDate,
    val readings: List<GlucoseReading>,
    val doses: List<DoseRecord>,
    val lastWeightDate: LocalDate?,
    val lastWaistDate: LocalDate?,
)

object NextStepEngine {
    /** A check becomes due this many minutes before its planned time. */
    const val LEAD_MINUTES = 60

    fun next(plan: Plan, now: LocalDateTime, day: DayRecords): NextStep {
        val windows = SlotResolver.windowsInDayOrder(plan)
        if (windows.isEmpty()) return measurementStep(plan, day) ?: NextStep.AllDone(null, null, isTomorrow = false)

        val minute = now.toLocalTime().minuteOfDay()
        val dayStart = SlotResolver.dayStartMinute(plan)
        val currentIndex = windows.indexOfFirst { it.contains(minute) }.coerceAtLeast(0)
        val current = windows[currentIndex]

        if (isDue(current, minute, dayStart)) {
            stepFor(plan, current, day)?.let { return it }
        } else if (currentIndex > 0) {
            // Before the current slot is due, a forgotten earlier check is still worth doing.
            val previous = windows[currentIndex - 1]
            stepFor(plan, previous, day)?.let { return it }
        }

        measurementStep(plan, day)?.let { return it }

        val upcoming = windows.drop(currentIndex).firstOrNull { !isDue(it, minute, dayStart) && stepFor(plan, it, day) != null }
        return if (upcoming != null) {
            NextStep.AllDone(upcoming.slot, upcoming.schedule.time, isTomorrow = false)
        } else {
            NextStep.AllDone(windows.first().slot, windows.first().schedule.time, isTomorrow = true)
        }
    }

    fun progress(plan: Plan, now: LocalDateTime, day: DayRecords): List<SlotProgress> {
        val windows = SlotResolver.windowsInDayOrder(plan)
        val minute = now.toLocalTime().minuteOfDay()
        val dayStart = SlotResolver.dayStartMinute(plan)
        val currentIndex = windows.indexOfFirst { it.contains(minute) }.coerceAtLeast(0)
        return windows.mapIndexed { index, window ->
            val state = when {
                stepFor(plan, window, day) == null -> SlotState.DONE
                partlyDone(plan, window.slot, day) -> SlotState.PARTLY_DONE
                index < currentIndex -> SlotState.MISSED
                index == currentIndex && isDue(window, minute, dayStart) -> SlotState.DUE
                else -> SlotState.UPCOMING
            }
            SlotProgress(window.slot, window.schedule.time, state)
        }
    }

    private fun isDue(window: SlotWindow, minute: Int, dayStart: Int): Boolean {
        val dueFrom = window.slotMinute - LEAD_MINUTES
        val offsetNow = circularOffset(dayStart, minute)
        val offsetDue = circularOffset(dayStart, (dueFrom + 24 * 60) % (24 * 60))
        val offsetSlot = circularOffset(dayStart, window.slotMinute)
        // If the lead time reaches back past the start of the day, the slot is due from the start of the day.
        val effectiveDue = if (offsetDue > offsetSlot) 0 else offsetDue
        return offsetNow >= effectiveDue
    }

    private fun stepFor(plan: Plan, window: SlotWindow, day: DayRecords): NextStep? {
        val slot = window.slot
        val schedule = window.schedule
        if (schedule.checkBgl && day.readings.none { it.slot == slot }) return NextStep.CheckIn(slot, schedule.time)
        if (!medicinesDone(plan, slot, day)) {
            return if (schedule.checkBgl) NextStep.CheckIn(slot, schedule.time) else NextStep.Medicines(slot, schedule.time)
        }
        return null
    }

    private fun medicinesDone(plan: Plan, slot: Slot, day: DayRecords): Boolean =
        plan.medicinesAt(slot).all { medicine -> day.doses.any { it.slot == slot && it.medicineKey == medicine.key } }

    private fun partlyDone(plan: Plan, slot: Slot, day: DayRecords): Boolean {
        val hasReading = day.readings.any { it.slot == slot }
        val someMedicines = plan.medicinesAt(slot).any { medicine -> day.doses.any { it.slot == slot && it.medicineKey == medicine.key } }
        return hasReading || someMedicines
    }

    private fun measurementStep(plan: Plan, day: DayRecords): NextStep? = when {
        isDue(plan.goals.weighEvery, day.lastWeightDate, day.date) -> NextStep.Weigh
        isDue(plan.goals.waistEvery, day.lastWaistDate, day.date) -> NextStep.MeasureWaist
        else -> null
    }

    private fun isDue(frequency: Frequency, last: LocalDate?, today: LocalDate): Boolean = when (frequency) {
        Frequency.OFF -> false
        Frequency.DAILY -> last == null || last.isBefore(today)
        Frequency.WEEKLY -> last == null || ChronoUnit.DAYS.between(last, today) >= 7
    }
}
