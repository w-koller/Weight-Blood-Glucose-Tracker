package io.github.wkoller.glucosetracker.domain.records

import io.github.wkoller.glucosetracker.domain.model.BodyMeasurement
import io.github.wkoller.glucosetracker.domain.model.DoseKind
import io.github.wkoller.glucosetracker.domain.model.DoseRecord
import io.github.wkoller.glucosetracker.domain.model.DoseStatus
import io.github.wkoller.glucosetracker.domain.model.GlucoseReading
import io.github.wkoller.glucosetracker.domain.model.Plan
import io.github.wkoller.glucosetracker.domain.model.Slot
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Everything recorded for one time of day on one day. */
data class SlotEntry(val slot: Slot, val readings: List<GlucoseReading>, val doses: List<DoseRecord>) {
    val correctionDoses: List<DoseRecord> get() = doses.filter { it.kind == DoseKind.CORRECTION }
    val fixedDoses: List<DoseRecord> get() = doses.filter { it.kind == DoseKind.FIXED }
}

/** One day of records, laid out like a row of the family's spreadsheet. */
data class DayLog(val date: LocalDate, val slots: List<SlotEntry>, val measurements: List<BodyMeasurement>) {
    fun slot(slot: Slot): SlotEntry? = slots.firstOrNull { it.slot == slot }
    val weightGrams: Int? get() = measurements.lastOrNull { it.weightGrams != null }?.weightGrams
    val waistMm: Int? get() = measurements.lastOrNull { it.waistMm != null }?.waistMm
    val notes: List<String> get() = slots.flatMap { s -> s.readings.mapNotNull { it.note } + s.doses.mapNotNull { it.note } } + measurements.mapNotNull { it.note }
}

object DailyLog {

    /** Groups records by day, newest day first, with slots in the order of the day. */
    fun build(readings: List<GlucoseReading>, doses: List<DoseRecord>, measurements: List<BodyMeasurement>): List<DayLog> {
        val dates = (readings.map { it.date } + doses.map { it.date } + measurements.map { it.date }).toSortedSet().reversed()
        return dates.map { date ->
            val dayReadings = readings.filter { it.date == date }.sortedBy { it.takenAt }
            val dayDoses = doses.filter { it.date == date }.sortedBy { it.takenAt }
            DayLog(
                date = date,
                slots = Slot.entries.mapNotNull { slot ->
                    val r = dayReadings.filter { it.slot == slot }
                    val d = dayDoses.filter { it.slot == slot }
                    if (r.isEmpty() && d.isEmpty()) null else SlotEntry(slot, r, d)
                },
                measurements = measurements.filter { it.date == date }.sortedBy { it.takenAt },
            )
        }
    }
}

/** How often a fixed medicine was recorded as taken, compared with how often the plan scheduled it. */
data class Adherence(val medicineKey: String, val medicineName: String, val scheduled: Int, val given: Int, val skipped: Int)

object AdherenceCalculator {

    /**
     * Counts the scheduled doses of each of [plan]'s medicines from [from] to [today], leaving out doses later today
     * that are not yet due. Each day is judged against the version of the plan in force that day, taken from
     * [history], so days before the plan started, or before a medicine was added, are not counted. A dose counts as
     * due from its slot's planned time.
     */
    fun calculate(
        plan: Plan,
        history: List<Plan>,
        zone: ZoneId,
        from: LocalDate,
        now: LocalDateTime,
        today: LocalDate,
        doses: List<DoseRecord>,
    ): List<Adherence> =
        plan.medicines.map { medicine ->
            var scheduled = 0
            var date = from
            while (!date.isAfter(today)) {
                val dayPlan = PlanTimeline.planOn(date, history, zone)
                dayPlan?.medicines?.firstOrNull { it.key == medicine.key }?.doses?.keys?.forEach { slot ->
                    val time = dayPlan.schedule(slot)?.time
                    val due = date.isBefore(today) || time == null || !now.toLocalTime().isBefore(time)
                    if (due) scheduled++
                }
                date = date.plusDays(1)
            }
            val records = doses.filter { it.kind == DoseKind.FIXED && it.medicineKey == medicine.key && !it.date.isBefore(from) && !it.date.isAfter(today) }
            Adherence(
                medicineKey = medicine.key,
                medicineName = medicine.displayName,
                scheduled = scheduled,
                given = records.count { it.status == DoseStatus.GIVEN },
                skipped = records.count { it.status == DoseStatus.SKIPPED },
            )
        }
}
