package io.github.wkoller.glucosetracker.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.domain.format.Measures
import io.github.wkoller.glucosetracker.domain.model.DoseKind
import io.github.wkoller.glucosetracker.domain.progress.Progress
import io.github.wkoller.glucosetracker.ui.UiFormat
import io.github.wkoller.glucosetracker.ui.appViewModel
import io.github.wkoller.glucosetracker.ui.components.BigButton
import io.github.wkoller.glucosetracker.ui.components.FactRow
import io.github.wkoller.glucosetracker.ui.components.OptionRow
import io.github.wkoller.glucosetracker.ui.components.ScreenScaffold
import io.github.wkoller.glucosetracker.ui.components.SectionCard
import io.github.wkoller.glucosetracker.ui.components.TrendChart
import io.github.wkoller.glucosetracker.ui.components.label
import io.github.wkoller.glucosetracker.ui.theme.LocalUiPrefs
import java.time.Instant
import java.time.ZoneId

/** [onAsk] opens the assistant for the number of days shown. */
@Composable
fun ProgressScreen(onBack: () -> Unit, onAsk: (Int) -> Unit) {
    val vm = appViewModel { container, _ -> ProgressViewModel(container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    ProgressContent(ui, onBack = onBack, onAsk = onAsk, onDays = vm::setDays)
}

@Composable
fun ProgressContent(ui: ProgressUi, onBack: () -> Unit, onAsk: (Int) -> Unit, onDays: (Int) -> Unit) {
    val model = ui.model
    val context = LocalContext.current
    val prefs = LocalUiPrefs.current
    val dayFormat = { millis: Long -> UiFormat.dayShort(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()) }
    val askLabel = stringResource(R.string.progress_ask)
    val askBar: (@Composable () -> Unit)? = if (ui.canAsk && model != null && !prefs.simpleMode) {
        { BigButton(askLabel, onClick = { onAsk(ui.days) }, icon = R.drawable.ic_forum) }
    } else {
        null
    }

    ScreenScaffold(title = stringResource(R.string.progress_title), onBack = onBack, bottomBar = askBar) {

        model?.weight?.takeIf { it.current != null }?.let { weight ->
            SectionCard(stringResource(R.string.progress_weight_title), icon = R.drawable.ic_monitor_weight) {
                GoalProgress(weight, kg = true)
                val series = model.weightSeries
                if (series.points.size >= 2) {
                    TrendChart(
                        series = series,
                        summary = ProgressText.weightSummary(context, series),
                        describeDay = { ProgressText.weightDay(context, it) },
                        yFormat = { "%.0f".format(it) },
                        xFormat = dayFormat,
                        goalLabel = stringResource(R.string.progress_goal_label),
                    )
                }
            }
        }
        model?.waist?.takeIf { it.current != null }?.let { waist ->
            SectionCard(stringResource(R.string.progress_waist_title), icon = R.drawable.ic_straighten) {
                GoalProgress(waist, kg = false)
            }
        }

        SectionCard(stringResource(R.string.progress_glucose_title), icon = R.drawable.ic_water_drop) {
            Text(stringResource(R.string.progress_period), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(7, 14, 30, 90).chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        pair.forEach { span ->
                            OptionRow(stringResource(R.string.progress_days, span), null, ui.days == span, { onDays(span) }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            val summary = model?.glucose
            if (model == null || summary == null || summary.count == 0) {
                Text(stringResource(R.string.progress_no_readings), style = MaterialTheme.typography.bodyLarge)
            } else {
                val series = model.glucoseSeries
                TrendChart(
                    series = series,
                    summary = ProgressText.glucoseSummary(context, series),
                    describeDay = { ProgressText.glucoseDay(context, prefs.slotNaming, it) },
                    yFormat = { "%.0f".format(it) },
                    xFormat = dayFormat,
                )
                Text(stringResource(R.string.progress_chart_key), style = MaterialTheme.typography.bodyMedium)
                FactRow(stringResource(R.string.progress_readings), summary.count.toString())
                summary.averageTenths?.let { FactRow(stringResource(R.string.progress_average), stringResource(R.string.bgl_amount, Measures.formatTenths(it))) }
                summary.inTargetPercent?.let { FactRow(stringResource(R.string.progress_in_target), stringResource(R.string.progress_in_target_value, it, summary.inTargetCount, summary.count)) }
                FactRow(stringResource(R.string.progress_lows), summary.lowCount.toString())
                FactRow(stringResource(R.string.progress_above), summary.aboveTargetCount.toString())
                summary.lowest?.let { FactRow(stringResource(R.string.progress_lowest), stringResource(R.string.progress_reading_on, Measures.formatTenths(it.tenths), UiFormat.dayLong(it.date))) }
                summary.highest?.let { FactRow(stringResource(R.string.progress_highest), stringResource(R.string.progress_reading_on, Measures.formatTenths(it.tenths), UiFormat.dayLong(it.date))) }
                Text(stringResource(R.string.progress_by_time), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                summary.perSlot.forEach { slot ->
                    FactRow(
                        slot.slot.label(),
                        stringResource(R.string.progress_slot_average, Measures.formatTenths(slot.averageTenths ?: 0), UiFormat.readings(context, slot.count)),
                    )
                }
            }
        }

        if (model != null && model.doses.isNotEmpty()) {
            SectionCard(stringResource(R.string.progress_doses_title), icon = R.drawable.ic_medication) {
                model.doses.forEach { dose ->
                    val total = UiFormat.dose(context, dose.totalGiven, dose.medicineKind, dose.unit)
                    val text = if (dose.kind == DoseKind.CORRECTION) {
                        stringResource(R.string.progress_correction_summary, UiFormat.times(context, dose.givenCount), total, UiFormat.times(context, dose.skippedCount))
                    } else {
                        stringResource(R.string.progress_fixed_summary, UiFormat.times(context, dose.givenCount), UiFormat.times(context, dose.skippedCount))
                    }
                    FactRow(dose.medicineName, text)
                }
            }
        }
    }
}

@Composable
private fun GoalProgress(progress: Progress, kg: Boolean) {
    val format = { value: Int -> if (kg) Measures.formatKg(value) else Measures.formatCm(value) }
    val change = { value: Int -> if (kg) Measures.formatKgChange(value) else Measures.formatCmChange(value) }
    val unit = stringResource(if (kg) R.string.unit_kg else R.string.unit_cm)
    val current = progress.current ?: return
    FactRow(stringResource(R.string.progress_now), "${format(current)} $unit")
    progress.start?.let { FactRow(stringResource(R.string.progress_start), "${format(it)} $unit") }
    progress.goal?.let { FactRow(stringResource(R.string.progress_goal), "${format(it)} $unit") }
    progress.changeSinceStart?.let { delta ->
        FactRow(
            stringResource(R.string.progress_change),
            when {
                delta > 0 -> stringResource(R.string.progress_change_up, change(delta), unit)
                delta < 0 -> stringResource(R.string.progress_change_down, change(delta), unit)
                else -> stringResource(R.string.progress_change_none)
            },
        )
    }
    progress.fraction?.let { fraction ->
        val barLabel = stringResource(if (kg) R.string.progress_weight_bar else R.string.progress_waist_bar)
        LinearProgressIndicator(
            progress = { fraction.toFloat() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 12.dp).semantics { contentDescription = barLabel },
        )
        val remaining = progress.remaining ?: 0
        Text(
            if (progress.goalReached) stringResource(R.string.progress_goal_reached)
            else stringResource(R.string.progress_to_go, change(remaining), unit),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
