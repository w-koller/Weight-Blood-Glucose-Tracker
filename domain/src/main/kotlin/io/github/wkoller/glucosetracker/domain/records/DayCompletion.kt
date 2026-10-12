package io.github.wkoller.glucosetracker.domain.records

import io.github.wkoller.glucosetracker.domain.model.DoseRecord
import io.github.wkoller.glucosetracker.domain.model.GlucoseReading
import io.github.wkoller.glucosetracker.domain.model.Plan
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** How much of a day's plan was recorded, for the logbook calendar. */
enum class DayStatus {
    /** Every BGL check and every medicine in the plan has an entry. */
    COMPLETE,

    /** Some entries, but not all. */
    PARTIAL,

    /** Nothing was recorded. */
    NONE,

    /** Today, while checks are still to come or still to be recorded. */
    TODAY_IN_PROGRESS,

    FUTURE,

    /** Before the first plan started, so there was nothing to record. */
    NOT_TRACKED,
}

object DayCompletion {

    /**
     * A slot counts as recorded when it has a reading (if the plan checks BGL then) and an entry for every
     * medicine due then. An entry saying a medicine was not given still counts: the day was recorded.
     * Weight and waist are not counted.
     */
    fun status(
        plan: Plan?,
        date: LocalDate,
        readings: List<GlucoseReading>,
        doses: List<DoseRecord>,
        today: LocalDate,
        now: LocalTime,
    ): DayStatus {
        if (date.isAfter(today)) return DayStatus.FUTURE
        if (plan == null) return DayStatus.NOT_TRACKED
        val dayReadings = readings.filter { it.date == date }
        val dayDoses = doses.filter { it.date == date }
        val slots = plan.activeSlots
        fun recorded(slot: io.github.wkoller.glucosetracker.domain.model.SlotSchedule): Boolean {
            val readingOk = !slot.checkBgl || dayReadings.any { it.slot == slot.slot }
            val medicinesOk = plan.medicinesAt(slot.slot).all { medicine ->
                dayDoses.any { it.slot == slot.slot && it.medicineKey == medicine.key }
            }
            return readingOk && medicinesOk
        }
        if (date == today) {
            return if (slots.isNotEmpty() && slots.all(::recorded)) DayStatus.COMPLETE else DayStatus.TODAY_IN_PROGRESS
        }
        return when {
            slots.isNotEmpty() && slots.all(::recorded) -> DayStatus.COMPLETE
            dayReadings.isEmpty() && dayDoses.isEmpty() -> DayStatus.NONE
            else -> DayStatus.PARTIAL
        }
    }
}

object PlanTimeline {
    /**
     * The plan version in force at the end of [date], so a day is judged against the plan of the time.
     * Returns null for days before the first plan, or before the start date of the first plan.
     */
    fun planOn(date: LocalDate, plans: List<Plan>, zone: ZoneId): Plan? {
        val endOfDay = date.plusDays(1).atStartOfDay(zone).toInstant()
        val inForce = plans.filter { it.effectiveFrom.isBefore(endOfDay) }.maxByOrNull { it.effectiveFrom } ?: return null
        val firstStart = plans.minOfOrNull { minOf(it.goals.startDate, it.effectiveFrom.atZone(zone).toLocalDate()) }
        return if (firstStart != null && date.isBefore(firstStart)) null else inForce
    }
}
