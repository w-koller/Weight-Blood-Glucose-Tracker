package io.github.wkoller.glucosetracker.ui.checkin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.domain.dosing.DoseNotice
import io.github.wkoller.glucosetracker.domain.dosing.DosePrompt
import io.github.wkoller.glucosetracker.domain.format.Measures
import io.github.wkoller.glucosetracker.domain.model.DoseStatus
import io.github.wkoller.glucosetracker.domain.model.MedicineKind
import io.github.wkoller.glucosetracker.domain.model.MeterFlag
import io.github.wkoller.glucosetracker.domain.model.SkipReason
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.domain.schedule.NextStep
import io.github.wkoller.glucosetracker.domain.validation.NumberInput
import io.github.wkoller.glucosetracker.tts.LocalSpeaker
import io.github.wkoller.glucosetracker.ui.UiFormat
import io.github.wkoller.glucosetracker.ui.appViewModel
import io.github.wkoller.glucosetracker.ui.components.BglValue
import io.github.wkoller.glucosetracker.ui.components.BigButton
import io.github.wkoller.glucosetracker.ui.components.ButtonStyle
import io.github.wkoller.glucosetracker.ui.components.ConfirmDialog
import io.github.wkoller.glucosetracker.ui.components.FactRow
import io.github.wkoller.glucosetracker.ui.components.NoticeCard
import io.github.wkoller.glucosetracker.ui.components.NoticeKind
import io.github.wkoller.glucosetracker.ui.components.NumberPad
import io.github.wkoller.glucosetracker.ui.components.OptionRow
import io.github.wkoller.glucosetracker.ui.components.ScaleTable
import io.github.wkoller.glucosetracker.ui.components.SectionCard
import io.github.wkoller.glucosetracker.ui.components.SpeakButton
import io.github.wkoller.glucosetracker.ui.components.StatusChip
import io.github.wkoller.glucosetracker.ui.components.StepScaffold
import io.github.wkoller.glucosetracker.ui.components.ValueDisplay
import io.github.wkoller.glucosetracker.ui.components.labelRes
import io.github.wkoller.glucosetracker.ui.components.nameRes
import io.github.wkoller.glucosetracker.ui.components.phraseRes
import io.github.wkoller.glucosetracker.ui.theme.LocalUiPrefs
import kotlinx.coroutines.delay
import java.time.Instant

@Composable
fun CheckInScreen(onClose: () -> Unit, onMeasure: (String) -> Unit, onCheckIn: (Slot) -> Unit) {
    val vm = appViewModel { container, handle -> CheckInViewModel(container, handle) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    BackHandler { if (!vm.back()) onClose() }

    val plan = ui.plan
    if (ui.loading || plan == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val onBack = { if (!vm.back()) onClose() }

    when (ui.step) {
        CheckInStep.BGL -> BglStep(ui, vm, onBack)
        CheckInStep.LOW -> LowStep(ui, vm, onBack)
        CheckInStep.RESULT -> ResultStep(ui, vm, onBack)
        CheckInStep.CORRECTION_AMOUNT, CheckInStep.MEDICINE_AMOUNT -> AmountStep(ui, vm, onBack)
        CheckInStep.SKIP_REASON -> SkipReasonStep(ui, vm, onBack)
        CheckInStep.MEDICINE -> MedicineStep(ui, vm, onBack)
        CheckInStep.TABLETS -> TabletsStep(ui, vm, onBack)
        CheckInStep.DONE -> DoneStep(ui, onClose, onMeasure, onCheckIn)
    }

    ui.confirm?.let { pending -> DoseConfirmDialog(ui, pending, vm) }
}

@Composable
private fun BglStep(ui: CheckInUi, vm: CheckInViewModel, onBack: () -> Unit) {
    var choosingSlot by remember { mutableStateOf(false) }
    StepScaffold(
        step = 1,
        stepCount = 0,
        title = stringResource(R.string.checkin_bgl_title, stringResource(ui.slot.phraseRes())),
        onBack = onBack,
        actions = { BigButton(stringResource(R.string.next), onClick = vm::submitBgl, enabled = !ui.busy) },
    ) {
        Text(stringResource(R.string.checkin_bgl_body), style = MaterialTheme.typography.bodyLarge)
        BigButton(
            stringResource(R.string.checkin_change_time_of_day),
            onClick = { choosingSlot = true },
            icon = R.drawable.ic_schedule,
            style = ButtonStyle.SECONDARY,
        )
        ui.bglError?.let { error ->
            val message = when (error) {
                NumberInput.Empty -> R.string.checkin_bgl_empty
                NumberInput.NotANumber -> R.string.checkin_bgl_not_number
                NumberInput.TooLow -> R.string.checkin_bgl_too_low
                NumberInput.TooHigh -> R.string.checkin_bgl_too_high
                is NumberInput.Valid -> null
            }
            message?.let { NoticeCard(NoticeKind.DANGER, stringResource(R.string.checkin_check_number), stringResource(it)) }
        }
        ValueDisplay(ui.bglText, stringResource(R.string.unit_mmol))
        NumberPad(ui.bglText, vm::setBglText, maxDecimals = 1, maxIntegerDigits = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            BigButton(stringResource(R.string.checkin_meter_lo), onClick = { vm.meterShows(MeterFlag.LO) }, style = ButtonStyle.SECONDARY, modifier = Modifier.weight(1f))
            BigButton(stringResource(R.string.checkin_meter_hi), onClick = { vm.meterShows(MeterFlag.HI) }, style = ButtonStyle.SECONDARY, modifier = Modifier.weight(1f))
        }
    }
    if (choosingSlot) {
        AlertDialog(
            onDismissRequest = { choosingSlot = false },
            title = { Text(stringResource(R.string.checkin_which_time), style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Slot.entries.forEach { slot ->
                        OptionRow(stringResource(slot.nameRes()), null, ui.slot == slot, { vm.setSlot(slot); choosingSlot = false })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { choosingSlot = false }) { Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge) }
            },
        )
    }
}

@Composable
private fun LowStep(ui: CheckInUi, vm: CheckInViewModel, onBack: () -> Unit) {
    val plan = ui.plan ?: return
    val reading = ui.reading ?: return
    val readingText = if (reading.meterFlag == MeterFlag.LO) stringResource(R.string.checkin_reading_lo) else stringResource(R.string.bgl_amount, Measures.formatTenths(reading.tenths))
    val message = stringResource(R.string.checkin_low_body, readingText, plan.correctionInsulin, Measures.formatTenths(plan.targets.hypoTenths))
    AutoSpeak(message)
    StepScaffold(
        step = 2,
        stepCount = 0,
        title = stringResource(R.string.checkin_low_title),
        onBack = onBack,
        actions = {
            if (ui.recheckAt == null) {
                BigButton(stringResource(R.string.checkin_low_start_timer), onClick = vm::startRecheckTimer, icon = R.drawable.ic_timer)
            }
            BigButton(stringResource(R.string.checkin_low_new_reading), onClick = vm::enterNewReading, icon = R.drawable.ic_water_drop, style = if (ui.recheckAt == null) ButtonStyle.SECONDARY else ButtonStyle.PRIMARY)
            BigButton(stringResource(R.string.checkin_low_continue), onClick = vm::continueAfterLow, style = ButtonStyle.SECONDARY)
        },
    ) {
        StatusChip(ui.advice!!.status)
        NoticeCard(NoticeKind.DANGER, stringResource(R.string.checkin_low_notice_title), message)
        ui.recheckAt?.let { RecheckCountdown(it) }
        NoticeCard(NoticeKind.WARNING, stringResource(R.string.checkin_low_help_title), stringResource(R.string.checkin_low_help))
        SpeakButton(message)
    }
}

@Composable
private fun RecheckCountdown(at: Instant) {
    val context = LocalContext.current
    var remaining by remember { mutableStateOf(0L) }
    LaunchedEffect(at) {
        while (true) {
            remaining = ((at.toEpochMilli() - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            if (remaining == 0L) break
            delay(1000)
        }
    }
    val text = if (remaining > 0) {
        stringResource(R.string.checkin_low_timer_running, UiFormat.time(context, at), remaining / 60, remaining % 60)
    } else {
        stringResource(R.string.checkin_low_timer_done)
    }
    SectionCard(stringResource(R.string.checkin_low_timer_title), icon = R.drawable.ic_timer) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
private fun ResultStep(ui: CheckInUi, vm: CheckInViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val plan = ui.plan ?: return
    val advice = ui.advice ?: return
    val insulin = plan.correctionInsulin
    val readingText = if (advice.meterFlag == MeterFlag.HI) stringResource(R.string.checkin_reading_hi) else stringResource(R.string.bgl_amount, Measures.formatTenths(advice.tenths))
    val unitsText = UiFormat.units(context, advice.units.toDouble())
    val headline = stringResource(R.string.checkin_result_reading, readingText)
    val doseSentence = if (advice.units > 0) {
        stringResource(R.string.checkin_result_dose, unitsText, insulin)
    } else {
        stringResource(R.string.checkin_result_no_dose, insulin)
    }
    val noticeTexts = advice.notices.mapNotNull { notice -> noticeText(notice, ui, unitsText) }
    val spoken = (listOf(headline, doseSentence) + noticeTexts.map { it.second }).joinToString(" ")
    AutoSpeak(spoken)

    StepScaffold(
        step = 2,
        stepCount = 0,
        title = stringResource(R.string.checkin_result_title, stringResource(ui.slot.phraseRes())),
        onBack = onBack,
        actions = {
            if (advice.units > 0) {
                BigButton(stringResource(R.string.checkin_gave_amount, unitsText), onClick = vm::giveSuggested, icon = R.drawable.ic_check, enabled = !ui.busy)
                BigButton(stringResource(R.string.checkin_gave_other), onClick = vm::enterOtherAmount, style = ButtonStyle.SECONDARY)
                BigButton(stringResource(R.string.checkin_not_given), onClick = vm::notGivenCorrection, style = ButtonStyle.SECONDARY)
            } else {
                BigButton(stringResource(R.string.continue_label), onClick = vm::continueWithoutDose, enabled = !ui.busy)
            }
        },
    ) {
        Text(headline, style = MaterialTheme.typography.titleLarge)
        if (advice.meterFlag == null) BglValue(advice.tenths)
        StatusChip(advice.status)
        SectionCard(title = null) {
            Text(doseSentence, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (advice.units > 0) Text(unitsText, style = MaterialTheme.typography.displayLarge)
        }
        noticeTexts.forEach { (kind, text, title) -> NoticeCard(kind, title, text) }
        SpeakButton(spoken)
        ScaleTable(plan.scale, insulin, advice.bandIndex)
    }
}

private data class NoticeText(val kind: NoticeKind, val second: String, val title: String)

@Composable
private fun noticeText(notice: DoseNotice, ui: CheckInUi, unitsText: String): NoticeText? {
    val context = LocalContext.current
    val plan = ui.plan ?: return null
    val insulin = plan.correctionInsulin
    return when (notice) {
        DoseNotice.Hypo -> null
        DoseNotice.NotPrescribedTime -> {
            val listed = plan.slots.filter { it.correctionPrescribed }.map { stringResource(it.slot.phraseRes()) }
            val body = if (listed.isEmpty()) {
                stringResource(R.string.checkin_notice_not_prescribed_none, insulin, unitsText, stringResource(ui.slot.phraseRes()))
            } else {
                stringResource(R.string.checkin_notice_not_prescribed, insulin, joinWithAnd(listed), unitsText, stringResource(ui.slot.phraseRes()))
            }
            NoticeText(NoticeKind.WARNING, body, stringResource(R.string.checkin_notice_not_prescribed_title))
        }
        DoseNotice.TopOfScale -> NoticeText(NoticeKind.WARNING, stringResource(R.string.checkin_notice_top), stringResource(R.string.checkin_notice_top_title))
        DoseNotice.MeterHigh -> NoticeText(NoticeKind.DANGER, stringResource(R.string.checkin_notice_meter_hi), stringResource(R.string.checkin_notice_meter_hi_title))
        is DoseNotice.AlreadyDosed -> NoticeText(
            NoticeKind.DANGER,
            stringResource(R.string.checkin_notice_already, UiFormat.units(context, notice.amount), insulin, UiFormat.time(context, notice.at)),
            stringResource(R.string.checkin_notice_already_title),
        )
    }
}

@Composable
private fun AmountStep(ui: CheckInUi, vm: CheckInViewModel, onBack: () -> Unit) {
    val plan = ui.plan ?: return
    val name = if (ui.step == CheckInStep.MEDICINE_AMOUNT) ui.currentInsulin?.medicine?.displayName.orEmpty() else plan.correctionInsulin
    StepScaffold(
        step = 3,
        stepCount = 0,
        title = stringResource(R.string.checkin_amount_title, name),
        onBack = onBack,
        actions = { BigButton(stringResource(R.string.next), onClick = vm::submitAmount, enabled = !ui.busy) },
    ) {
        Text(stringResource(R.string.checkin_amount_body), style = MaterialTheme.typography.bodyLarge)
        ui.amountError?.let { error ->
            val message = when (error) {
                NumberInput.TooHigh -> R.string.checkin_amount_too_high
                else -> R.string.checkin_amount_invalid
            }
            NoticeCard(NoticeKind.DANGER, stringResource(R.string.checkin_check_number), stringResource(message))
        }
        ValueDisplay(ui.amountText, stringResource(R.string.units_word))
        NumberPad(ui.amountText, vm::setAmountText, maxDecimals = 1, maxIntegerDigits = 2)
    }
}

@Composable
private fun SkipReasonStep(ui: CheckInUi, vm: CheckInViewModel, onBack: () -> Unit) {
    val name = when (val target = ui.skipTarget) {
        is DoseTarget.Fixed -> target.due.medicine.displayName
        else -> ui.plan?.correctionInsulin.orEmpty()
    }
    StepScaffold(
        step = 3,
        stepCount = 0,
        title = stringResource(R.string.checkin_reason_title, name),
        onBack = onBack,
        actions = {},
    ) {
        Text(stringResource(R.string.checkin_reason_body), style = MaterialTheme.typography.bodyLarge)
        listOf(SkipReason.NOT_EATING, SkipReason.DOCTOR_ADVICE, SkipReason.OTHER).forEach { reason ->
            BigButton(stringResource(reason.labelRes()), onClick = { vm.chooseReason(reason) }, style = ButtonStyle.SECONDARY, enabled = !ui.busy)
        }
    }
}

@Composable
private fun MedicineStep(ui: CheckInUi, vm: CheckInViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val due = ui.currentInsulin ?: return
    val amountText = UiFormat.dose(context, due.amount, due.medicine.kind, due.medicine.unit)
    val sentence = stringResource(R.string.checkin_medicine_due, due.medicine.displayName, amountText, stringResource(ui.slot.phraseRes()))
    StepScaffold(
        step = 4,
        stepCount = 0,
        title = due.medicine.displayName,
        onBack = onBack,
        actions = {
            BigButton(stringResource(R.string.checkin_gave_amount, amountText), onClick = vm::giveMedicine, icon = R.drawable.ic_check, enabled = !ui.busy)
            BigButton(stringResource(R.string.checkin_gave_other), onClick = vm::enterOtherAmount, style = ButtonStyle.SECONDARY)
            BigButton(stringResource(R.string.checkin_not_given), onClick = vm::notGivenMedicine, style = ButtonStyle.SECONDARY)
        },
    ) {
        Text(sentence, style = MaterialTheme.typography.titleLarge)
        Text(amountText, style = MaterialTheme.typography.displayLarge)
        if (ui.lowThisCheck) {
            NoticeCard(NoticeKind.WARNING, stringResource(R.string.checkin_medicine_after_low_title), stringResource(R.string.checkin_medicine_after_low, due.medicine.displayName))
        }
        SpeakButton(sentence)
    }
}

@Composable
private fun TabletsStep(ui: CheckInUi, vm: CheckInViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    StepScaffold(
        step = 5,
        stepCount = 0,
        title = stringResource(R.string.checkin_tablets_title, stringResource(ui.slot.phraseRes())),
        onBack = onBack,
        actions = {
            if (ui.tabletsSeparately) {
                val allAnswered = ui.dueOthers.all { it.medicine.key in ui.tabletAnswers }
                BigButton(stringResource(R.string.save), onClick = vm::saveTablets, enabled = allAnswered && !ui.busy)
            } else {
                BigButton(stringResource(R.string.checkin_tablets_all), onClick = vm::tookAllOthers, icon = R.drawable.ic_check, enabled = !ui.busy)
                BigButton(stringResource(R.string.checkin_tablets_some), onClick = vm::answerSeparately, style = ButtonStyle.SECONDARY)
            }
        },
    ) {
        Text(stringResource(R.string.checkin_tablets_body), style = MaterialTheme.typography.bodyLarge)
        ui.dueOthers.forEach { due ->
            SectionCard(due.medicine.displayName, icon = R.drawable.ic_medication) {
                Text(UiFormat.dose(context, due.amount, due.medicine.kind, due.medicine.unit), style = MaterialTheme.typography.titleLarge)
                if (ui.tabletsSeparately) {
                    val answer = ui.tabletAnswers[due.medicine.key]
                    OptionRow(stringResource(R.string.checkin_tablet_taken), null, answer == true, { vm.setTablet(due.medicine.key, true) })
                    OptionRow(stringResource(R.string.checkin_tablet_not_taken), null, answer == false, { vm.setTablet(due.medicine.key, false) })
                }
            }
        }
    }
}

@Composable
private fun DoneStep(ui: CheckInUi, onClose: () -> Unit, onMeasure: (String) -> Unit, onCheckIn: (Slot) -> Unit) {
    val context = LocalContext.current
    val next = ui.nextStep
    StepScaffold(
        step = 6,
        stepCount = 0,
        title = stringResource(R.string.checkin_done_title),
        onBack = null,
        actions = {
            when (next) {
                NextStep.Weigh -> BigButton(stringResource(R.string.home_record_weight), onClick = { onMeasure("weight") }, icon = R.drawable.ic_monitor_weight)
                NextStep.MeasureWaist -> BigButton(stringResource(R.string.home_record_waist), onClick = { onMeasure("waist") }, icon = R.drawable.ic_straighten)
                is NextStep.CheckIn -> if (next.slot != ui.slot) {
                    BigButton(stringResource(R.string.checkin_done_next_check, stringResource(next.slot.phraseRes())), onClick = { onCheckIn(next.slot) }, icon = R.drawable.ic_water_drop)
                }
                else -> Unit
            }
            BigButton(stringResource(R.string.go_home), onClick = onClose, icon = R.drawable.ic_home, style = if (next is NextStep.AllDone) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY)
        },
    ) {
        Text(stringResource(R.string.checkin_done_body, stringResource(ui.slot.phraseRes())), style = MaterialTheme.typography.bodyLarge)
        SectionCard(stringResource(R.string.checkin_done_summary)) {
            ui.readings.forEach { reading ->
                val value = when (reading.meterFlag) {
                    MeterFlag.LO -> stringResource(R.string.checkin_reading_lo)
                    MeterFlag.HI -> stringResource(R.string.checkin_reading_hi)
                    null -> stringResource(R.string.bgl_amount, Measures.formatTenths(reading.tenths))
                }
                FactRow(stringResource(R.string.bgl_short_label), value)
            }
            ui.recorded.forEach { dose ->
                val kind = dose.medicineKind
                val value = when (dose.status) {
                    DoseStatus.GIVEN -> stringResource(R.string.dose_given, UiFormat.dose(context, dose.givenAmount ?: 0.0, kind, dose.unit))
                    DoseStatus.SKIPPED -> stringResource(R.string.dose_not_given)
                    DoseStatus.NOT_NEEDED -> stringResource(R.string.dose_not_needed)
                }
                FactRow(dose.medicineName, value)
            }
        }
        when (next) {
            is NextStep.AllDone -> {
                val nextSlot = next.nextSlot
                val nextTime = next.nextTime
                if (nextSlot != null && nextTime != null) Text(
                    stringResource(
                        if (next.isTomorrow) R.string.home_next_later_tomorrow else R.string.home_next_later,
                        stringResource(nextSlot.phraseRes()),
                        UiFormat.time(context, nextTime),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            NextStep.Weigh -> Text(stringResource(R.string.home_next_weigh), style = MaterialTheme.typography.titleMedium)
            NextStep.MeasureWaist -> Text(stringResource(R.string.home_next_waist), style = MaterialTheme.typography.titleMedium)
            else -> Unit
        }
    }
}

@Composable
private fun DoseConfirmDialog(ui: CheckInUi, pending: PendingConfirm, vm: CheckInViewModel) {
    val context = LocalContext.current
    val plan = ui.plan ?: return
    val name = when (val target = pending.target) {
        is DoseTarget.Fixed -> target.due.medicine.displayName
        DoseTarget.Correction -> plan.correctionInsulin
    }
    val kind = (pending.target as? DoseTarget.Fixed)?.due?.medicine?.kind ?: MedicineKind.INSULIN
    val unit = (pending.target as? DoseTarget.Fixed)?.due?.medicine?.unit ?: CheckInViewModel.UNITS
    val amount = UiFormat.dose(context, pending.amount, kind, unit)
    val messages = pending.prompts.map { prompt ->
        when (prompt) {
            is DosePrompt.AlreadyDosed -> stringResource(R.string.confirm_already, UiFormat.units(context, prompt.amount), name, UiFormat.time(context, prompt.at))
            is DosePrompt.NotPrescribedTime -> stringResource(R.string.confirm_not_prescribed, name, stringResource(prompt.slot.phraseRes()))
            is DosePrompt.DifferentAmount -> {
                val prescribed = UiFormat.dose(context, prompt.prescribed, kind, unit)
                if (pending.target == DoseTarget.Correction && ui.advice != null) {
                    stringResource(R.string.confirm_different_correction, prescribed, Measures.formatTenths(ui.advice.tenths), amount)
                } else {
                    stringResource(R.string.confirm_different_fixed, prescribed, name, amount)
                }
            }
        }
    }
    ConfirmDialog(
        title = stringResource(R.string.confirm_title),
        messages = messages + stringResource(R.string.confirm_question, amount, name),
        confirmText = stringResource(R.string.confirm_record, amount),
        dismissText = stringResource(R.string.go_back),
        onConfirm = vm::confirmDose,
        onDismiss = vm::cancelConfirm,
    )
}

/** Reads a result aloud once when it first appears, if the user has turned that on. */
@Composable
private fun AutoSpeak(text: String) {
    val prefs = LocalUiPrefs.current
    val speaker = LocalSpeaker.current
    LaunchedEffect(text) { if (prefs.readAloud) speaker?.speak(text, prefs.speechRate) }
}

/** "a", "a and b", "a, b and c". */
@Composable
private fun joinWithAnd(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items[0]
    else -> stringResource(R.string.list_and, items.dropLast(1).joinToString(", "), items.last())
}
