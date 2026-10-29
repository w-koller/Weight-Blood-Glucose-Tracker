package io.github.wkoller.glucosetracker.domain.records

import io.github.wkoller.glucosetracker.domain.dosing.DoseAdvisor
import io.github.wkoller.glucosetracker.domain.dosing.GlucoseStatus
import io.github.wkoller.glucosetracker.domain.model.BodyMeasurement
import io.github.wkoller.glucosetracker.domain.model.DoseRecord
import io.github.wkoller.glucosetracker.domain.model.GlucoseReading
import io.github.wkoller.glucosetracker.domain.model.GlucoseTargets
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.domain.progress.TrendPoint
import java.time.Instant
import java.time.LocalDate

/** One line of the logbook list. [key] stays the same while the records do, so the list keeps its place as it grows. */
sealed interface LogbookEntry {
    val date: LocalDate
    val takenAt: Instant
    val key: String
}

/**
 * One check: a reading with the doses recorded with it. [reading] is null for doses recorded at a time of day
 * without a reading of their own, such as tablets recorded from the Medicines screen.
 */
data class CheckEntry(
    override val date: LocalDate,
    override val takenAt: Instant,
    val slot: Slot,
    val reading: GlucoseReading?,
    val status: GlucoseStatus?,
    val doses: List<DoseRecord>,
) : LogbookEntry {
    override val key: String get() = reading?.let { "reading-${it.id}" } ?: "doses-$date-${slot.name}"
    val notes: List<String> get() = listOfNotNull(reading?.note) + doses.mapNotNull { it.note }

    /** The reading as a point on the logbook chart. */
    val point: TrendPoint? get() = reading?.let { TrendPoint(it.takenAt, it.date, it.tenths, it.slot, it.meterFlag, status) }
}

data class MeasurementEntry(val measurement: BodyMeasurement) : LogbookEntry {
    override val date: LocalDate get() = measurement.date
    override val takenAt: Instant get() = measurement.takenAt
    override val key: String get() = "measurement-${measurement.id}"
}

/** A day of the logbook list, with its entries in the order they happened. */
data class LogbookDay(val date: LocalDate, val entries: List<LogbookEntry>) {
    val readings: List<GlucoseReading> get() = entries.mapNotNull { (it as? CheckEntry)?.reading }
}

object Logbook {

    /** The days of [log], newest first as given, each with its entries in time order. Readings are judged against [targets]. */
    fun days(log: List<DayLog>, targets: GlucoseTargets): List<LogbookDay> = log.map { day -> LogbookDay(day.date, entries(day, targets)) }

    /**
     * A day's records as entries in time order. Each reading carries the doses recorded with it. The doses of a time of
     * day that are not linked to one of its readings form an entry of their own, at the time of the first of them.
     */
    fun entries(day: DayLog, targets: GlucoseTargets): List<LogbookEntry> {
        val checks = day.slots.flatMap { slot ->
            val ids = slot.readings.map { it.id }.toSet()
            val withReadings = slot.readings.map { reading ->
                CheckEntry(
                    date = day.date,
                    takenAt = reading.takenAt,
                    slot = slot.slot,
                    reading = reading,
                    status = DoseAdvisor.statusOf(reading.tenths, reading.meterFlag, targets),
                    doses = slot.doses.filter { it.readingId == reading.id },
                )
            }
            val unlinked = slot.doses.filter { it.readingId !in ids }
            val dosesOnly = if (unlinked.isEmpty()) {
                emptyList()
            } else {
                listOf(CheckEntry(day.date, unlinked.minOf { it.takenAt }, slot.slot, reading = null, status = null, doses = unlinked))
            }
            withReadings + dosesOnly
        }
        return (checks + day.measurements.map(::MeasurementEntry)).sortedBy { it.takenAt }
    }

    /** Every reading in [days] as a chart point, oldest first. */
    fun points(days: List<LogbookDay>): List<TrendPoint> = days
        .flatMap { day -> day.entries.filterIsInstance<CheckEntry>() }
        .mapNotNull { it.point }
        .sortedBy { it.takenAt }
}
