package io.github.wkoller.glucosetracker.domain.readout

import io.github.wkoller.glucosetracker.domain.model.BodyMeasurement
import io.github.wkoller.glucosetracker.domain.model.DoseRecord
import io.github.wkoller.glucosetracker.domain.model.GlucoseReading
import io.github.wkoller.glucosetracker.domain.model.Plan
import io.github.wkoller.glucosetracker.domain.progress.DoseSummary
import io.github.wkoller.glucosetracker.domain.progress.GlucoseStats
import io.github.wkoller.glucosetracker.domain.progress.GlucoseSummary
import io.github.wkoller.glucosetracker.domain.progress.Progress
import io.github.wkoller.glucosetracker.domain.progress.ProgressCalculator
import java.time.LocalDate

enum class ReadoutPeriod(val days: Long) { TODAY(1), WEEK(7) }

/** The figures behind a readout. Every sentence shown to the user, or given to the model, comes from these. */
data class ReadoutFacts(
    val period: ReadoutPeriod,
    val from: LocalDate,
    val to: LocalDate,
    val plan: Plan,
    val glucose: GlucoseSummary,
    val doses: List<DoseSummary>,
    val weight: Progress,
    val waist: Progress,
    val weightChangeInPeriod: Int?,
)

object ReadoutBuilder {

    fun build(
        period: ReadoutPeriod,
        plan: Plan,
        today: LocalDate,
        readings: List<GlucoseReading>,
        doses: List<DoseRecord>,
        measurements: List<BodyMeasurement>,
    ): ReadoutFacts {
        val from = today.minusDays(period.days - 1)
        val inPeriod = { date: LocalDate -> !date.isBefore(from) && !date.isAfter(today) }
        val periodReadings = readings.filter { inPeriod(it.date) }
        val periodDoses = doses.filter { inPeriod(it.date) }
        return ReadoutFacts(
            period = period,
            from = from,
            to = today,
            plan = plan,
            glucose = GlucoseStats.summarise(periodReadings, plan.targets),
            doses = GlucoseStats.summariseDoses(periodDoses),
            weight = ProgressCalculator.weight(plan.goals, measurements.filter { !it.date.isAfter(today) }),
            waist = ProgressCalculator.waist(plan.goals, measurements.filter { !it.date.isAfter(today) }),
            weightChangeInPeriod = weightChange(measurements, from, today),
        )
    }

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
