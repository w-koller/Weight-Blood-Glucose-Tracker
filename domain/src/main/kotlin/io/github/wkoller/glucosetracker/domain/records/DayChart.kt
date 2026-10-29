package io.github.wkoller.glucosetracker.domain.records

import io.github.wkoller.glucosetracker.domain.dosing.DoseAdvisor
import io.github.wkoller.glucosetracker.domain.model.DoseRecord
import io.github.wkoller.glucosetracker.domain.model.GlucoseReading
import io.github.wkoller.glucosetracker.domain.model.GlucoseTargets
import io.github.wkoller.glucosetracker.domain.progress.TrendPoint
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A reading placed by its time of day: [minute] counts from the start of its own day, as the plan sets that start. */
data class DayPoint(val point: TrendPoint, val minute: Int)

/**
 * One day's readings by time of day, over the readings of the days before it, for the full-screen chart.
 * [entries] are the day's checks that have a reading, with their doses, in time order, and [points] are their
 * readings. [others] are the readings of the [DayCharts.HISTORY_DAYS] days before, so the day can be compared
 * with what is usual at each time of day. [dayStartMinute] is where the chart's day begins.
 */
data class DayChart(
    val date: LocalDate,
    val entries: List<CheckEntry>,
    val points: List<DayPoint>,
    val others: List<DayPoint>,
    val dayStartMinute: Int,
)

object DayCharts {
    const val HISTORY_DAYS = 30
    const val MINUTES_PER_DAY = 24 * 60

    /**
     * The chart of [date] from [readings] and [doses], which may include other days. Readings are judged against
     * [targets]. A reading after midnight that belongs to the day before, such as a late bedtime check, stays at
     * the end of its day rather than at the start of the next.
     */
    fun build(
        date: LocalDate,
        readings: List<GlucoseReading>,
        doses: List<DoseRecord>,
        targets: GlucoseTargets,
        dayStartMinute: Int,
        zone: ZoneId,
    ): DayChart {
        val day = DailyLog.build(readings.filter { it.date == date }, doses.filter { it.date == date }, emptyList()).firstOrNull()
        val entries = day?.let { Logbook.entries(it, targets) }.orEmpty().filterIsInstance<CheckEntry>().filter { it.reading != null }
        val from = date.minusDays(HISTORY_DAYS.toLong())
        val others = readings
            .filter { !it.date.isBefore(from) && it.date.isBefore(date) }
            .sortedBy { it.takenAt }
            .map { reading ->
                val point = TrendPoint(reading.takenAt, reading.date, reading.tenths, reading.slot, reading.meterFlag, DoseAdvisor.statusOf(reading.tenths, reading.meterFlag, targets))
                DayPoint(point, minuteOfDay(reading.date, reading.takenAt, dayStartMinute, zone))
            }
        return DayChart(
            date = date,
            entries = entries,
            points = entries.mapNotNull { entry -> entry.point?.let { DayPoint(it, minuteOfDay(date, it.takenAt, dayStartMinute, zone)) } },
            others = others,
            dayStartMinute = dayStartMinute,
        )
    }

    /** Minutes from the start of [date]'s day, [dayStartMinute] after midnight, to [at], kept within the day. */
    fun minuteOfDay(date: LocalDate, at: Instant, dayStartMinute: Int, zone: ZoneId): Int {
        val start = date.atStartOfDay(zone).plusMinutes(dayStartMinute.toLong()).toInstant()
        return Duration.between(start, at).toMinutes().coerceIn(0L, MINUTES_PER_DAY - 1L).toInt()
    }
}
