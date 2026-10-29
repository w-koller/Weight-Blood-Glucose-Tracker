package io.github.wkoller.glucosetracker.ui.logbook

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.domain.format.Measures
import io.github.wkoller.glucosetracker.domain.model.MedicineKind
import io.github.wkoller.glucosetracker.domain.model.MeterFlag
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.domain.validation.InputValidator
import io.github.wkoller.glucosetracker.domain.validation.NumberInput
import io.github.wkoller.glucosetracker.ui.UiFormat
import io.github.wkoller.glucosetracker.ui.appViewModel
import io.github.wkoller.glucosetracker.ui.components.BigButton
import io.github.wkoller.glucosetracker.ui.components.ButtonStyle
import io.github.wkoller.glucosetracker.ui.components.ConfirmDialog
import io.github.wkoller.glucosetracker.ui.components.FactRow
import io.github.wkoller.glucosetracker.ui.components.LabeledField
import io.github.wkoller.glucosetracker.ui.components.NoticeCard
import io.github.wkoller.glucosetracker.ui.components.NoticeKind
import io.github.wkoller.glucosetracker.ui.components.NumberEntryScaffold
import io.github.wkoller.glucosetracker.ui.components.OptionRow
import io.github.wkoller.glucosetracker.ui.components.SectionCard
import io.github.wkoller.glucosetracker.ui.components.StepScaffold
import io.github.wkoller.glucosetracker.ui.components.TimeStepper
import io.github.wkoller.glucosetracker.ui.components.label
import io.github.wkoller.glucosetracker.ui.components.meterKeys
import io.github.wkoller.glucosetracker.ui.components.meterShowsRes
import io.github.wkoller.glucosetracker.ui.components.phrase
import io.github.wkoller.glucosetracker.ui.components.wordRes

/** Changes a logbook record, or adds one the user forgot, one question per screen. */
@Composable
fun RecordEditorScreen(onClose: () -> Unit) {
    val vm = appViewModel { container, handle -> RecordEditorViewModel(container, handle) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    BackHandler { if (!vm.back()) onClose() }
    LaunchedEffect(ui.finished) { if (ui.finished) onClose() }
    if (ui.loading || ui.finished) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val onBack = { if (!vm.back()) onClose() }
    val next: @Composable () -> Unit = { BigButton(stringResource(if (ui.queue.isEmpty()) R.string.done else R.string.next), onClick = vm::next) }

    when (ui.step) {
        EditorStep.CHOOSE_TYPE -> ChooseTypeStep(ui, vm, onClose)
        EditorStep.SUMMARY -> SummaryStep(ui, vm, onClose)
        EditorStep.MEDICINE -> ChoiceStep(stringResource(R.string.editor_medicine_title), ui, onBack, next) {
            ui.medicineChoices.forEach { choice ->
                OptionRow(choice.name, null, ui.medicine?.key == choice.key && ui.medicine?.name == choice.name, { vm.setMedicine(choice) })
            }
        }
        EditorStep.SLOT -> ChoiceStep(stringResource(R.string.editor_slot_title), ui, onBack, next) {
            Slot.entries.forEach { slot -> OptionRow(slot.label(), null, ui.slot == slot, { vm.setSlot(slot) }) }
        }
        EditorStep.DOSE_STATUS -> ChoiceStep(stringResource(R.string.editor_status_title, ui.medicine?.name.orEmpty()), ui, onBack, next) {
            OptionRow(stringResource(R.string.editor_given_yes), null, ui.given, { vm.setGiven(true) })
            OptionRow(stringResource(R.string.editor_given_no), null, !ui.given, { vm.setGiven(false) })
        }
        EditorStep.BGL -> NumberEntryScaffold(
            title = stringResource(R.string.checkin_bgl_title, ui.slot.phrase()),
            onBack = onBack,
            value = ui.bglText,
            display = ui.meterFlag?.let { stringResource(it.wordRes()) } ?: ui.bglText,
            displayDescription = ui.meterFlag?.let { stringResource(it.meterShowsRes()) },
            unit = if (ui.meterFlag == null) stringResource(R.string.unit_mmol) else "",
            onValueChange = vm::setBglText,
            error = ui.error?.let { stringResource(it) },
            maxDecimals = 1,
            maxIntegerDigits = 2,
            wordKeys = meterKeys(ui.meterFlag, vm::meterShows),
            action = next,
        ) {
            Text(stringResource(R.string.checkin_bgl_body), style = MaterialTheme.typography.bodyLarge)
        }
        EditorStep.AMOUNT -> NumberEntryScaffold(
            title = stringResource(R.string.checkin_amount_title, ui.medicine?.name.orEmpty()),
            onBack = onBack,
            value = ui.amountText,
            unit = ui.medicine?.let { unitWord(it.kind, it.unit) }.orEmpty(),
            onValueChange = vm::setAmountText,
            error = ui.error?.let { stringResource(it) },
            maxDecimals = 2,
            maxIntegerDigits = 2,
            action = next,
        )
        EditorStep.WEIGHT -> NumberEntryScaffold(
            title = stringResource(R.string.measure_weight_title),
            onBack = onBack,
            value = ui.weightText,
            unit = stringResource(R.string.unit_kg),
            onValueChange = vm::setWeightText,
            error = ui.error?.let { stringResource(it) },
            maxDecimals = 2,
            maxIntegerDigits = 3,
            action = next,
        )
        EditorStep.WAIST -> NumberEntryScaffold(
            title = stringResource(R.string.measure_waist_title),
            onBack = onBack,
            value = ui.waistText,
            unit = stringResource(R.string.unit_cm),
            onValueChange = vm::setWaistText,
            error = ui.error?.let { stringResource(it) },
            maxDecimals = 1,
            maxIntegerDigits = 3,
            action = next,
        )
        EditorStep.TIME -> ChoiceStep(stringResource(R.string.editor_time_title), ui, onBack, next) {
            Text(stringResource(R.string.editor_time_body), style = MaterialTheme.typography.bodyLarge)
            TimeStepper(ui.time, vm::setTime)
        }
        EditorStep.NOTE -> ChoiceStep(stringResource(R.string.editor_note_title), ui, onBack, next) {
            Text(stringResource(R.string.editor_note_body), style = MaterialTheme.typography.bodyLarge)
            LabeledField(stringResource(R.string.edit_note), ui.note, vm::setNote, hint = stringResource(R.string.edit_note_hint), singleLine = false)
        }
    }

    ui.amountCheck?.let { check -> AmountCheckDialog(ui, check, vm) }
}

/** A question answered by choosing or setting something, with Next pinned at the bottom. */
@Composable
private fun ChoiceStep(
    title: String,
    ui: EditorUi,
    onBack: () -> Unit,
    next: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    StepScaffold(step = 0, stepCount = 0, title = title, onBack = onBack, actions = { next() }) {
        ui.error?.let { NoticeCard(NoticeKind.DANGER, stringResource(R.string.editor_check_answer), stringResource(it)) }
        content()
    }
}

@Composable
private fun ChooseTypeStep(ui: EditorUi, vm: RecordEditorViewModel, onClose: () -> Unit) {
    StepScaffold(
        step = 0,
        stepCount = 0,
        title = stringResource(R.string.editor_add_title),
        onBack = onClose,
        actions = { BigButton(stringResource(R.string.cancel), onClick = onClose, style = ButtonStyle.SECONDARY) },
    ) {
        Text(stringResource(R.string.editor_add_body, UiFormat.dayLong(ui.date)), style = MaterialTheme.typography.bodyLarge)
        BigButton(stringResource(R.string.editor_add_reading), onClick = { vm.chooseType(RecordType.READING) }, icon = R.drawable.ic_water_drop, style = ButtonStyle.SECONDARY)
        BigButton(stringResource(R.string.editor_add_dose), onClick = { vm.chooseType(RecordType.DOSE) }, icon = R.drawable.ic_medication, style = ButtonStyle.SECONDARY)
        BigButton(stringResource(R.string.editor_add_weight), onClick = { vm.chooseType(RecordType.MEASUREMENT, EditorStep.WEIGHT) }, icon = R.drawable.ic_monitor_weight, style = ButtonStyle.SECONDARY)
        BigButton(stringResource(R.string.editor_add_waist), onClick = { vm.chooseType(RecordType.MEASUREMENT, EditorStep.WAIST) }, icon = R.drawable.ic_straighten, style = ButtonStyle.SECONDARY)
    }
}

/** Every detail of the record, each with its own Change button, and Save at the bottom. */
@Composable
private fun SummaryStep(ui: EditorUi, vm: RecordEditorViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    StepScaffold(
        step = 0,
        stepCount = 0,
        title = stringResource(if (ui.adding) R.string.editor_summary_add_title else R.string.editor_summary_edit_title),
        onBack = onClose,
        actions = { BigButton(stringResource(R.string.save), onClick = vm::save, icon = R.drawable.ic_check, enabled = !ui.busy) },
        moreActions = { BigButton(stringResource(R.string.cancel), onClick = onClose, style = ButtonStyle.SECONDARY) },
    ) {
        Text(stringResource(R.string.editor_summary_body, UiFormat.dayLong(ui.date)), style = MaterialTheme.typography.bodyLarge)
        SectionCard(title = null) {
            when (ui.type) {
                RecordType.READING -> {
                    val value = when (ui.meterFlag) {
                        MeterFlag.LO -> stringResource(R.string.checkin_reading_lo)
                        MeterFlag.HI -> stringResource(R.string.checkin_reading_hi)
                        null -> stringResource(R.string.bgl_amount, ui.bglText)
                    }
                    SummaryRow(stringResource(R.string.bgl_short_label), value) { vm.change(EditorStep.BGL) }
                    SummaryRow(stringResource(R.string.editor_row_slot), ui.slot.label()) { vm.change(EditorStep.SLOT) }
                }
                RecordType.DOSE -> {
                    val medicine = ui.medicine
                    // The medicine and time of day of a saved dose stay as they are; a wrong one can be deleted and added again.
                    SummaryRow(stringResource(R.string.editor_row_medicine), medicine?.name.orEmpty(), onChange = if (ui.adding) ({ vm.change(EditorStep.MEDICINE) }) else null)
                    SummaryRow(stringResource(R.string.editor_row_slot), ui.slot.label(), onChange = if (ui.adding) ({ vm.change(EditorStep.SLOT) }) else null)
                    val amount = (InputValidator.amount(ui.amountText) as? NumberInput.Valid)?.value
                    val given = if (ui.given && amount != null && medicine != null) {
                        stringResource(R.string.dose_given, UiFormat.dose(context, amount, medicine.kind, medicine.unit))
                    } else {
                        stringResource(R.string.dose_not_given)
                    }
                    SummaryRow(stringResource(R.string.editor_row_given), given) { vm.change(EditorStep.DOSE_STATUS) }
                }
                RecordType.MEASUREMENT -> {
                    val notRecorded = stringResource(R.string.editor_not_recorded)
                    val weight = (InputValidator.weight(ui.weightText) as? NumberInput.Valid)?.value
                    val waist = (InputValidator.waist(ui.waistText) as? NumberInput.Valid)?.value
                    SummaryRow(stringResource(R.string.editor_row_weight), weight?.let { "${Measures.formatKg(it)} ${stringResource(R.string.unit_kg)}" } ?: notRecorded) { vm.change(EditorStep.WEIGHT) }
                    SummaryRow(stringResource(R.string.editor_row_waist), waist?.let { "${Measures.formatCm(it)} ${stringResource(R.string.unit_cm)}" } ?: notRecorded) { vm.change(EditorStep.WAIST) }
                }
                null -> Unit
            }
            SummaryRow(stringResource(R.string.editor_row_time), UiFormat.time(context, ui.time)) { vm.change(EditorStep.TIME) }
            SummaryRow(
                stringResource(R.string.edit_note),
                ui.note.trim().ifBlank { stringResource(R.string.editor_no_note) },
                changeText = stringResource(if (ui.note.isBlank()) R.string.editor_add_note else R.string.change),
                last = true,
            ) { vm.change(EditorStep.NOTE) }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, changeText: String? = null, last: Boolean = false, onChange: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FactRow(label, value)
        if (onChange != null) {
            BigButton(changeText ?: stringResource(R.string.change), onClick = onChange, icon = R.drawable.ic_edit, style = ButtonStyle.SECONDARY)
        }
        if (!last) HorizontalDivider()
    }
}

@Composable
private fun AmountCheckDialog(ui: EditorUi, check: AmountCheck, vm: RecordEditorViewModel) {
    val context = LocalContext.current
    val medicine = ui.medicine ?: return
    val prescribed = UiFormat.dose(context, check.prescribed, medicine.kind, medicine.unit)
    val amount = UiFormat.dose(context, check.amount, medicine.kind, medicine.unit)
    val difference = if (check.readingTenths != null) {
        stringResource(R.string.confirm_different_correction, prescribed, Measures.formatTenths(check.readingTenths), amount)
    } else {
        stringResource(R.string.confirm_different_fixed, prescribed, medicine.name, amount)
    }
    ConfirmDialog(
        title = stringResource(R.string.confirm_title),
        messages = listOf(difference, stringResource(R.string.confirm_question, amount, medicine.name)),
        confirmText = stringResource(R.string.confirm_record, amount),
        dismissText = stringResource(R.string.go_back),
        onConfirm = vm::confirmAmount,
        onDismiss = vm::cancelAmount,
    )
}

@Composable
private fun unitWord(kind: MedicineKind, unit: String): String =
    if (kind == MedicineKind.INSULIN) stringResource(R.string.units_word) else unit
