package io.github.wkoller.glucosetracker.ui.assistant

import android.content.Context
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.domain.format.Measures
import io.github.wkoller.glucosetracker.domain.model.DoseKind
import io.github.wkoller.glucosetracker.domain.model.MedicineKind
import io.github.wkoller.glucosetracker.domain.readout.ReadoutFacts
import io.github.wkoller.glucosetracker.domain.readout.ReadoutPeriod
import io.github.wkoller.glucosetracker.ui.UiFormat

/** The readout in plain sentences, worked out from the figures alone. It does not need the model. */
object ReadoutText {

    fun sentences(context: Context, facts: ReadoutFacts): List<String> = buildList {
        val today = facts.period == ReadoutPeriod.TODAY
        val glucose = facts.glucose
        if (glucose.count == 0) {
            add(context.getString(if (today) R.string.readout_no_readings_today else R.string.readout_no_readings_week))
        } else {
            add(context.getString(if (today) R.string.readout_count_today else R.string.readout_count_week, UiFormat.readings(context, glucose.count)))
            glucose.averageTenths?.let {
                add(context.getString(R.string.readout_average, Measures.formatTenths(it), glucose.inTargetCount, glucose.count))
            }
            add(
                when (glucose.lowCount) {
                    0 -> context.getString(R.string.readout_no_lows)
                    else -> context.resources.getQuantityString(R.plurals.readout_lows, glucose.lowCount, glucose.lowCount)
                },
            )
            glucose.highest?.let {
                add(context.getString(R.string.readout_highest, UiFormat.reading(context, it.tenths, it.meterFlag), UiFormat.dayLong(it.date)))
            }
        }
        facts.doses.forEach { dose ->
            if (dose.kind == DoseKind.CORRECTION) {
                if (dose.givenCount > 0) {
                    add(context.getString(R.string.readout_correction, dose.medicineName, UiFormat.times(context, dose.givenCount), UiFormat.dose(context, dose.totalGiven, MedicineKind.INSULIN, dose.unit)))
                }
            } else {
                val given = UiFormat.times(context, dose.givenCount)
                val skipped = UiFormat.times(context, dose.skippedCount)
                add(
                    when {
                        dose.skippedCount == 0 -> context.getString(R.string.readout_fixed_given_only, dose.medicineName, given)
                        dose.givenCount == 0 -> context.getString(R.string.readout_fixed_not_given_only, dose.medicineName, skipped)
                        else -> context.getString(R.string.readout_fixed, dose.medicineName, given, skipped)
                    },
                )
            }
        }
        val weight = facts.weight
        weight.current?.let { current ->
            val change = weight.changeSinceStart
            add(
                when {
                    change == null || change == 0 -> context.getString(R.string.readout_weight_same, Measures.formatKg(current))
                    change > 0 -> context.getString(R.string.readout_weight_up, Measures.formatKg(current), Measures.formatKgChange(change))
                    else -> context.getString(R.string.readout_weight_down, Measures.formatKg(current), Measures.formatKgChange(change))
                },
            )
            val left = weight.remaining
            val goal = weight.goal
            if (left != null && goal != null) {
                add(
                    if (left <= 0) context.getString(R.string.measure_goal_reached, Measures.formatKg(goal))
                    else context.getString(R.string.measure_to_goal, Measures.formatKgChange(left), Measures.formatKg(goal)),
                )
            }
        }
    }
}
