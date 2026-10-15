package io.github.wkoller.glucosetracker.domain.readout

import io.github.wkoller.glucosetracker.domain.model.BodyMeasurement
import io.github.wkoller.glucosetracker.domain.model.DoseRecord
import io.github.wkoller.glucosetracker.domain.model.GlucoseReading
import io.github.wkoller.glucosetracker.domain.model.Plan
import io.github.wkoller.glucosetracker.domain.progress.DoseSummary
import io.github.wkoller.glucosetracker.domain.progress.GlucoseSummary
import io.github.wkoller.glucosetracker.domain.progress.Progress
import io.github.wkoller.glucosetracker.domain.progress.ProgressModelBuilder
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
        val model = ProgressModelBuilder.build(plan, today.minusDays(period.days - 1), today, readings, doses, measurements)
        return ReadoutFacts(
            period = period,
            from = model.from,
            to = model.to,
            plan = plan,
            glucose = model.glucose,
            doses = model.doses,
            weight = model.weight,
            waist = model.waist,
            weightChangeInPeriod = model.weightChangeInPeriod,
        )
    }
}
