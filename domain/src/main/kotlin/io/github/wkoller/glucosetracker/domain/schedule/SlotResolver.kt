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

    /**
     * The moment at [time] that belongs to the day [date], for a record added or changed later. A time
     * before the day starts, such as 00:30 for a late bedtime check, falls just after that night's midnight.
     */
    fun momentOn(plan: Plan, date: LocalDate, time: LocalTime): LocalDateTime =
        if (time.minuteOfDay() < dayStartMinute(plan)) LocalDateTime.of(date.plusDays(1), time) else LocalDateTime.of(date, time)

    /** Windows ordered from the start of the logical day. */
    fun windowsInDayOrder(plan: Plan): List<SlotWindow> {
        val dayStart = dayStartMinute(plan)
        return windows(plan).sortedBy { circularOffset(dayStart, it.slotMinute) }
    }
}

/** A rule for the check times a person enters for their plan. */
object CheckTimes {
    /**
     * True when [times], given in the order of the checks (breakfast, lunch, dinner, bedtime), are all different and
     * each comes later in the day than the one before. The last may fall after midnight, as long as it comes before
     * the first one again. Two checks at the same time, or a bedtime set to the morning by mistake, would file
     * readings under the wrong check and miss the warning about a dose at a time the prescription does not list.
     */
    fun inOrder(times: List<LocalTime>): Boolean {
        val start = times.firstOrNull()?.minuteOfDay() ?: return true
        return times.map { circularOffset(start, it.minuteOfDay()) }.zipWithNext().all { (earlier, later) -> later > earlier }
    }
}

internal fun LocalTime.minuteOfDay(): Int = hour * 60 + minute

internal fun circularOffset(from: Int, to: Int): Int = ((to - from) % MINUTES_PER_DAY + MINUTES_PER_DAY) % MINUTES_PER_DAY
