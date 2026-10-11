package io.github.wkoller.glucosetracker.domain.schedule

import io.github.wkoller.glucosetracker.domain.model.Plan
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.domain.model.SlotSchedule
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private const val MINUTES_PER_DAY = 24 * 60

/**
 * Each active slot owns the stretch of the day from halfway after the previous slot to halfway
 * before the next one. A late-night bedtime check therefore still counts as bedtime.
 */
data class SlotWindow(val schedule: SlotSchedule, val startMinute: Int, val endMinute: Int) {
    val slot: Slot get() = schedule.slot
    val slotMinute: Int get() = schedule.time.minuteOfDay()

    fun contains(minute: Int): Boolean = circularOffset(startMinute, minute) < circularOffset(startMinute, endMinute)
}

object SlotResolver {

    fun windows(plan: Plan): List<SlotWindow> {
        val active = plan.activeSlots
        if (active.isEmpty()) return emptyList()
        if (active.size == 1) return listOf(SlotWindow(active[0], 0, MINUTES_PER_DAY))
        val minutes = active.map { it.time.minuteOfDay() }
        val starts = active.indices.map { i ->
            val previous = minutes[(i - 1 + active.size) % active.size]
            val gap = circularOffset(previous, minutes[i]).let { if (it == 0) MINUTES_PER_DAY else it }
            (previous + gap / 2) % MINUTES_PER_DAY
        }
        return active.indices.map { i -> SlotWindow(active[i], starts[i], starts[(i + 1) % active.size]) }
    }

    fun resolve(plan: Plan, time: LocalTime): Slot =
        windows(plan).firstOrNull { it.contains(time.minuteOfDay()) }?.slot ?: Slot.OTHER

    /**
     * The minute the user's day starts: the start of the earliest slot's window. A check at 00:30
     * belongs to the previous evening's bedtime, not the new day. Falls back to midnight when the
     * first window would start in the afternoon or evening.
     */
    fun dayStartMinute(plan: Plan): Int {
        val first = windows(plan).minByOrNull { it.slotMinute } ?: return 0
        return if (first.startMinute <= first.slotMinute && first.startMinute < 12 * 60) first.startMinute else 0
    }

    /** The day a moment belongs to, using [dayStartMinute]. */
    fun logicalDate(plan: Plan, at: LocalDateTime): LocalDate =
        if (at.toLocalTime().minuteOfDay() < dayStartMinute(plan)) at.toLocalDate().minusDays(1) else at.toLocalDate()

    /** Windows ordered from the start of the logical day. */
    fun windowsInDayOrder(plan: Plan): List<SlotWindow> {
        val dayStart = dayStartMinute(plan)
        return windows(plan).sortedBy { circularOffset(dayStart, it.slotMinute) }
    }
}

internal fun LocalTime.minuteOfDay(): Int = hour * 60 + minute

internal fun circularOffset(from: Int, to: Int): Int = ((to - from) % MINUTES_PER_DAY + MINUTES_PER_DAY) % MINUTES_PER_DAY
