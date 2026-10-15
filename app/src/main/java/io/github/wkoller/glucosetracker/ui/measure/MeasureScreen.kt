package io.github.wkoller.glucosetracker.ui.measure

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.domain.format.Measures
import io.github.wkoller.glucosetracker.domain.progress.Progress
import io.github.wkoller.glucosetracker.domain.schedule.NextStep
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.domain.validation.NumberInput
import io.github.wkoller.glucosetracker.ui.UiFormat
import io.github.wkoller.glucosetracker.ui.appViewModel
import io.github.wkoller.glucosetracker.ui.components.BigButton
import io.github.wkoller.glucosetracker.ui.components.ButtonStyle
import io.github.wkoller.glucosetracker.ui.components.ConfirmDialog
import io.github.wkoller.glucosetracker.ui.components.NumberEntryScaffold
import io.github.wkoller.glucosetracker.ui.components.SectionCard
import io.github.wkoller.glucosetracker.ui.components.SpeakButton
import io.github.wkoller.glucosetracker.ui.components.StepScaffold
import io.github.wkoller.glucosetracker.ui.components.phrase

@Composable
fun MeasureScreen(onClose: () -> Unit, onCheckIn: (Slot) -> Unit) {
    val vm = appViewModel { container, handle -> MeasureViewModel(container, handle) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    BackHandler { if (!vm.back()) onClose() }
    if (ui.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val onBack = { if (!vm.back()) onClose() }

    when (ui.step) {
        MeasureStep.WEIGHT -> NumberEntryScaffold(
            title = stringResource(R.string.measure_weight_title),
            onBack = onBack,
            value = ui.text,
            unit = stringResource(R.string.unit_kg),
            onValueChange = vm::setText,
            error = measureError(ui.error, R.string.measure_weight_range),
            maxDecimals = 2,
            maxIntegerDigits = 3,
            action = { BigButton(stringResource(R.string.save), onClick = vm::submit, enabled = !ui.busy) },
        ) {
            Text(stringResource(R.string.measure_weight_body), style = MaterialTheme.typography.bodyLarge)
            ui.lastWeight?.let { last ->
                Text(
                    stringResource(R.string.measure_weight_last, Measures.formatKg(last.weightGrams!!), UiFormat.dayLong(last.date)),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        MeasureStep.WAIST_OFFER -> StepScaffold(
            step = 2,
            stepCount = 0,
            title = stringResource(R.string.measure_waist_offer_title),
            onBack = null,
            actions = { BigButton(stringResource(R.string.measure_waist_yes), onClick = vm::measureWaist, icon = R.drawable.ic_straighten) },
            moreActions = { BigButton(stringResource(R.string.measure_waist_no), onClick = vm::skipWaist, style = ButtonStyle.SECONDARY) },
        ) {
            Text(stringResource(R.string.measure_waist_offer_body), style = MaterialTheme.typography.bodyLarge)
        }
        MeasureStep.WAIST -> NumberEntryScaffold(
            title = stringResource(R.string.measure_waist_title),
            onBack = onBack,
            value = ui.text,
            unit = stringResource(R.string.unit_cm),
            onValueChange = vm::setText,
            error = measureError(ui.error, R.string.measure_waist_range),
            maxDecimals = 1,
            maxIntegerDigits = 3,
            action = { BigButton(stringResource(R.string.save), onClick = vm::submit, enabled = !ui.busy) },
        ) {
            Text(stringResource(R.string.measure_waist_body), style = MaterialTheme.typography.bodyLarge)
            ui.lastWaist?.let { last ->
                Text(
                    stringResource(R.string.measure_waist_last, Measures.formatCm(last.waistMm!!), UiFormat.dayLong(last.date)),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        MeasureStep.DONE -> DoneStep(ui, onClose, onCheckIn)
    }

    ui.confirmLargeChange?.let { grams ->
        val last = ui.lastWeight?.weightGrams ?: return@let
        val diff = grams - last
        ConfirmDialog(
            title = stringResource(R.string.measure_large_title),
            messages = listOf(
                stringResource(
                    if (diff > 0) R.string.measure_large_more else R.string.measure_large_less,
                    Measures.formatKgChange(diff),
                    Measures.formatKg(last),
                    Measures.formatKg(grams),
                ),
            ),
            confirmText = stringResource(R.string.measure_large_yes, Measures.formatKg(grams)),
            dismissText = stringResource(R.string.measure_large_no),
            onConfirm = vm::confirmLargeChange,
            onDismiss = vm::cancelLargeChange,
        )
    }
}

@Composable
private fun measureError(error: NumberInput<Int>?, rangeRes: Int): String? = when (error) {
    null -> null
    NumberInput.Empty -> stringResource(R.string.measure_empty)
    NumberInput.NotANumber -> stringResource(R.string.measure_not_number)
    else -> stringResource(rangeRes)
}

@Composable
private fun DoneStep(ui: MeasureUi, onClose: () -> Unit, onCheckIn: (Slot) -> Unit) {
    val sentences = buildList {
        ui.weight?.takeIf { ui.saved?.weightGrams != null }?.let { addAll(weightSentences(it, ui)) }
        ui.waist?.takeIf { ui.saved?.waistMm != null }?.let { addAll(waistSentences(it)) }
    }
    StepScaffold(
        step = 3,
        stepCount = 0,
        title = stringResource(R.string.measure_done_title),
        onBack = null,
        actions = {
            val next = ui.nextStep
            if (next is NextStep.CheckIn) {
                BigButton(stringResource(R.string.checkin_done_next_check, next.slot.phrase()), onClick = { onCheckIn(next.slot) }, icon = R.drawable.ic_water_drop)
            } else {
                BigButton(stringResource(R.string.go_home), onClick = onClose, icon = R.drawable.ic_home)
            }
        },
        moreActions = if (ui.nextStep is NextStep.CheckIn) {
            { BigButton(stringResource(R.string.go_home), onClick = onClose, icon = R.drawable.ic_home, style = ButtonStyle.SECONDARY) }
        } else {
            null
        },
    ) {
        SectionCard(title = null) { sentences.forEach { Text(it, style = MaterialTheme.typography.titleMedium) } }
        if (sentences.isNotEmpty()) SpeakButton(sentences.joinToString(" "))
    }
}

@Composable
private fun weightSentences(progress: Progress, ui: MeasureUi): List<String> = buildList {
    val current = progress.current ?: return@buildList
    add(stringResource(R.string.measure_saved_weight, Measures.formatKg(current)))
    progress.changeSincePrevious?.let { change ->
        add(
            when {
                change > 0 -> stringResource(R.string.measure_since_last_up, Measures.formatKgChange(change))
                change < 0 -> stringResource(R.string.measure_since_last_down, Measures.formatKgChange(change))
                else -> stringResource(R.string.measure_since_last_same)
            },
        )
    }
    progress.changeSinceStart?.let { change ->
        add(
            when {
                change > 0 -> stringResource(R.string.measure_since_start_up, Measures.formatKgChange(change))
                change < 0 -> stringResource(R.string.measure_since_start_down, Measures.formatKgChange(change))
                else -> stringResource(R.string.measure_since_start_same)
            },
        )
    }
    val goal = progress.goal
    val remaining = progress.remaining
    if (goal != null && remaining != null) {
        if (progress.goalReached) {
            add(stringResource(R.string.measure_goal_reached, Measures.formatKg(goal)))
        } else {
            add(stringResource(R.string.measure_to_goal, Measures.formatKgChange(remaining), Measures.formatKg(goal)))
            val perWeek = progress.perWeekNeeded(ui.today, ui.plan?.goals?.targetDate)
            val target = ui.plan?.goals?.targetDate
            if (perWeek != null && target != null) {
                add(stringResource(R.string.measure_per_week, Measures.formatKgChange(perWeek), UiFormat.dayLongYear(target)))
            }
        }
    }
}

@Composable
private fun waistSentences(progress: Progress): List<String> = buildList {
    val current = progress.current ?: return@buildList
    add(stringResource(R.string.measure_saved_waist, Measures.formatCm(current)))
    progress.changeSinceStart?.let { change ->
        add(
            when {
                change > 0 -> stringResource(R.string.measure_waist_since_start_up, Measures.formatCmChange(change))
                change < 0 -> stringResource(R.string.measure_waist_since_start_down, Measures.formatCmChange(change))
                else -> stringResource(R.string.measure_since_start_same)
            },
        )
    }
    val goal = progress.goal
    val remaining = progress.remaining
    if (goal != null && remaining != null && !progress.goalReached) {
        add(stringResource(R.string.measure_waist_to_goal, Measures.formatCmChange(remaining), Measures.formatCm(goal)))
    }
}
