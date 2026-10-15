package io.github.wkoller.glucosetracker.domain.progress

import io.github.wkoller.glucosetracker.domain.dosing.DoseAdvisor
import io.github.wkoller.glucosetracker.domain.dosing.GlucoseStatus
import io.github.wkoller.glucosetracker.domain.model.BodyMeasurement
import io.github.wkoller.glucosetracker.domain.model.DoseRecord
import io.github.wkoller.glucosetracker.domain.model.GlucoseReading
import io.github.wkoller.glucosetracker.domain.model.MeterFlag
import io.github.wkoller.glucosetracker.domain.model.Plan
import io.github.wkoller.glucosetracker.domain.model.Slot
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** What a series measures, and so the unit of its values: tenths of mmol/L or grams. */
enum class Measure { GLUCOSE, WEIGHT }

/** One recorded value. A glucose point also carries its slot, meter flag and status against the plan's targets. */
data class TrendPoint(
    val takenAt: Instant,
    val date: LocalDate,
    val value: Int,
    val slot: Slot? = null,
    val meterFlag: MeterFlag? = null,
    val status: GlucoseStatus? = null,
)

data class TrendDay(val date: LocalDate, val points: List<TrendPoint>)

/** The points of one measure in time order, with the lines drawn behind them. */
data class TrendSeries(
    val measure: Measure,
    val points: List<TrendPoint>,
    val goal: Int? = null,
    val targetLow: Int? = null,
    val targetHigh: Int? = null,
    val lowLine: Int? = null,
) {
    val days: List<TrendDay> by lazy { points.groupBy { it.date }.map { (date, points) -> TrendDay(date, points) }.sortedBy { it.date } }
    val lowest: TrendPoint? get() = points.minByOrNull { it.value }
    val highest: TrendPoint? get() = points.maxByOrNull { it.value }
    val lows: List<TrendPoint> get() = points.filter { it.status == GlucoseStatus.LOW }
}

data class WeekSummary(val from: LocalDate, val to: LocalDate, val glucose: GlucoseSummary, val lastWeight: TrendPoint?)

/**
 * Everything the Progress screen shows for a period, worked out once. The charts, the words read by
 * TalkBack, the PDF report, the readout and the facts given to the language model all come from this,
 * so they always agree.
 */
data class ProgressModel(
    val from: LocalDate,
    val to: LocalDate,
    val plan: Plan,
    val glucoseSeries: TrendSeries,
    val weightSeries: TrendSeries,
    val glucose: GlucoseSummary,
    val weeks: List<WeekSummary>,
    val doses: List<DoseSummary>,
    val weight: Progress,
    val waist: Progress,
    val weightChangeInPeriod: Int?,
) {
    val days: Int get() = ChronoUnit.DAYS.between(from, to).toInt() + 1
}

object ProgressModelBuilder {

    /** Builds the model for [from] to [to], both included. Weight and waist progress use every measurement up to [to]. */
    fun build(
        plan: Plan,
        from: LocalDate,
        to: LocalDate,
        readings: List<GlucoseReading>,
        doses: List<DoseRecord>,
        measurements: List<BodyMeasurement>,
    ): ProgressModel {
        val inPeriod = { date: LocalDate -> !date.isBefore(from) && !date.isAfter(to) }
        val periodReadings = readings.filter { inPeriod(it.date) }.sortedBy { it.takenAt }
        val upToEnd = measurements.filter { !it.date.isAfter(to) }
        val targets = plan.targets
        val glucoseSeries = TrendSeries(
            measure = Measure.GLUCOSE,
            points = periodReadings.map { reading ->
                TrendPoint(reading.takenAt, reading.date, reading.tenths, reading.slot, reading.meterFlag, DoseAdvisor.statusOf(reading.tenths, reading.meterFlag, targets))
            },
            targetLow = targets.lowTenths,
            targetHigh = targets.highTenths,
            lowLine = targets.hypoTenths,
        )
        val weightSeries = TrendSeries(
            measure = Measure.WEIGHT,
            points = upToEnd.filter { it.weightGrams != null && inPeriod(it.date) }.sortedBy { it.takenAt }.map { TrendPoint(it.takenAt, it.date, it.weightGrams!!) },
            goal = plan.goals.goalWeightGrams,
        )
        val weeks = generateSequence(to) { it.minusDays(7) }
            .takeWhile { !it.isBefore(from) }
            .map { end ->
                val start = maxOf(end.minusDays(6), from)
                WeekSummary(
                    from = start,
                    to = end,
                    glucose = GlucoseStats.summarise(periodReadings.filter { it.date in start..end }, targets),
                    lastWeight = weightSeries.points.lastOrNull { it.date in start..end },
                )
            }
            .toList()
            .reversed()
        return ProgressModel(
            from = from,
            to = to,
            plan = plan,
            glucoseSeries = glucoseSeries,
            weightSeries = weightSeries,
            glucose = GlucoseStats.summarise(periodReadings, targets),
            weeks = weeks,
            doses = GlucoseStats.summariseDoses(doses.filter { inPeriod(it.date) }),
            weight = ProgressCalculator.weight(plan.goals, upToEnd),
            waist = ProgressCalculator.waist(plan.goals, upToEnd),
            weightChangeInPeriod = weightChange(upToEnd, from, to),
        )
    }

    /** The change from the last weight before the period, or the first in it, to the last weight in it. */
    private fun weightChange(measurements: List<BodyMeasurement>, from: LocalDate, to: LocalDate): Int? {
        val weights = measurements.filter { it.weightGrams != null }.sortedBy { it.takenAt }
        val last = weights.lastOrNull { !it.date.isAfter(to) && !it.date.isBefore(from) } ?: return null
        val before = weights.lastOrNull { it.date.isBefore(from) }
            ?: weights.firstOrNull { !it.date.isBefore(from) }
            ?: return null
        if (before === last) return null
        return last.weightGrams!! - before.weightGrams!!
    }
}
