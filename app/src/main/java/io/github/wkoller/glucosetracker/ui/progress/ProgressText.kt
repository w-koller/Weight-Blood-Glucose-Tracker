package io.github.wkoller.glucosetracker.ui.progress

import android.content.Context
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.domain.dosing.GlucoseStatus
import io.github.wkoller.glucosetracker.domain.format.Measures
import io.github.wkoller.glucosetracker.domain.model.MeterFlag
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.domain.model.SlotNaming
import io.github.wkoller.glucosetracker.domain.progress.TrendDay
import io.github.wkoller.glucosetracker.domain.progress.TrendPoint
import io.github.wkoller.glucosetracker.domain.progress.TrendSeries
import io.github.wkoller.glucosetracker.ui.UiFormat
import io.github.wkoller.glucosetracker.ui.components.phraseRes

/** The charts in words, for TalkBack. Worked out from the progress model alone. */
object ProgressText {

    fun glucoseSummary(context: Context, series: TrendSeries): String {
        val lowest = series.lowest ?: return ""
        val highest = series.highest ?: return ""
        return context.getString(R.string.progress_glucose_chart, series.points.size, Measures.formatTenths(lowest.value), Measures.formatTenths(highest.value))
    }

    fun weightSummary(context: Context, series: TrendSeries): String {
        val lowest = series.lowest ?: return ""
        val highest = series.highest ?: return ""
        return context.getString(R.string.progress_weight_chart, series.points.size, Measures.formatKg(lowest.value), Measures.formatKg(highest.value))
    }

    /** "Monday 3 March. 2 readings. 6.2 mmol/L before breakfast, in your target range. 3.6 mmol/L before lunch, low." */
    fun glucoseDay(context: Context, naming: SlotNaming, day: TrendDay): String = context.getString(
        R.string.progress_chart_day,
        UiFormat.dayLong(day.date),
        UiFormat.readings(context, day.points.size),
        day.points.joinToString(" ") { reading(context, naming, it) },
    )

    /** "Monday 3 March. 104.2 kg." */
    fun weightDay(context: Context, day: TrendDay): String = context.getString(
        R.string.progress_weight_day,
        UiFormat.dayLong(day.date),
        day.points.joinToString(", ") { context.getString(R.string.progress_weight_value, Measures.formatKg(it.value)) },
    )

    private fun reading(context: Context, naming: SlotNaming, point: TrendPoint): String {
        val value = when (point.meterFlag) {
            MeterFlag.LO -> context.getString(R.string.checkin_reading_lo)
            MeterFlag.HI -> context.getString(R.string.checkin_reading_hi)
            null -> context.getString(R.string.bgl_amount, Measures.formatTenths(point.value))
        }
        val status = context.getString(
            when (point.status) {
                GlucoseStatus.LOW -> R.string.progress_point_low
                GlucoseStatus.BELOW_TARGET -> R.string.progress_point_below
                GlucoseStatus.ABOVE_TARGET -> R.string.progress_point_above
                GlucoseStatus.IN_TARGET, null -> R.string.progress_point_in
            },
        )
        val slot = point.slot
        return if (slot == null || slot == Slot.OTHER) {
            context.getString(R.string.progress_point_at, value, UiFormat.time(context, point.takenAt), status)
        } else {
            context.getString(R.string.progress_point, value, context.getString(slot.phraseRes(naming)), status)
        }
    }
}
