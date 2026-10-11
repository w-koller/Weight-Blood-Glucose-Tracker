package io.github.wkoller.glucosetracker.ui.plan

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.data.prefs.Settings as AppSettings
import io.github.wkoller.glucosetracker.data.prefs.TextSize
import io.github.wkoller.glucosetracker.domain.format.Measures
import io.github.wkoller.glucosetracker.domain.model.Frequency
import io.github.wkoller.glucosetracker.domain.model.MedicineKind
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.ui.UiFormat
import io.github.wkoller.glucosetracker.ui.appContainer
import io.github.wkoller.glucosetracker.ui.components.BigButton
import io.github.wkoller.glucosetracker.ui.components.ButtonStyle
import io.github.wkoller.glucosetracker.ui.components.CheckRow
import io.github.wkoller.glucosetracker.ui.components.DateField
import io.github.wkoller.glucosetracker.ui.components.FactRow
import io.github.wkoller.glucosetracker.ui.components.LabeledField
import io.github.wkoller.glucosetracker.ui.components.NoticeCard
import io.github.wkoller.glucosetracker.ui.components.NoticeKind
import io.github.wkoller.glucosetracker.ui.components.OptionRow
import io.github.wkoller.glucosetracker.ui.components.ScaleTable
import io.github.wkoller.glucosetracker.ui.components.SectionCard
import io.github.wkoller.glucosetracker.ui.components.StepScaffold
import io.github.wkoller.glucosetracker.ui.components.TimeField
import io.github.wkoller.glucosetracker.ui.components.ToggleRow
import io.github.wkoller.glucosetracker.ui.components.labelRes
import io.github.wkoller.glucosetracker.ui.components.nameRes
import io.github.wkoller.glucosetracker.ui.components.phraseRes
import kotlinx.coroutines.launch

@Composable
private fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge)

/** Shows the problems that stop the user moving on, as full sentences. */
@Composable
fun DraftErrors(errors: List<DraftError>) {
    if (errors.isEmpty()) return
    NoticeCard(
        kind = NoticeKind.DANGER,
        title = stringResource(R.string.setup_errors_title),
        body = errors.map { draftErrorText(it) }.joinToString("\n"),
    )
}

@Composable
fun draftErrorText(error: DraftError): String = when (error) {
    DraftError.InsulinNameMissing -> stringResource(R.string.error_insulin_name)
    is DraftError.BandUpperInvalid -> stringResource(R.string.error_band_upper, error.row)
    is DraftError.BandUnitsInvalid -> stringResource(R.string.error_band_units, error.row)
    is DraftError.BandNotAscending -> stringResource(R.string.error_band_order, error.row)
    is DraftError.BandUpperOutOfRange -> stringResource(R.string.error_band_upper_range, error.row)
    is DraftError.BandUnitsOutOfRange -> stringResource(R.string.error_band_units_range, error.row)
    DraftError.ScaleNotChecked -> stringResource(R.string.error_scale_not_checked)
    DraftError.NoCheckTimes -> stringResource(R.string.error_no_check_times)
    DraftError.MedicineNameMissing -> stringResource(R.string.error_medicine_name)
    DraftError.MedicineNoTimes -> stringResource(R.string.error_medicine_times)
    DraftError.MedicineUnitMissing -> stringResource(R.string.error_medicine_unit)
    is DraftError.MedicineAmountInvalid -> stringResource(R.string.error_medicine_amount, stringResource(error.slot.phraseRes()))
    DraftError.StartWeightInvalid -> stringResource(R.string.error_start_weight)
    DraftError.GoalWeightInvalid -> stringResource(R.string.error_goal_weight)
    DraftError.StartWaistInvalid -> stringResource(R.string.error_start_waist)
    DraftError.GoalWaistInvalid -> stringResource(R.string.error_goal_waist)
    DraftError.TargetDateBeforeStart -> stringResource(R.string.error_target_date)
    DraftError.TargetLowInvalid -> stringResource(R.string.error_target_low)
    DraftError.TargetHighInvalid -> stringResource(R.string.error_target_high)
    DraftError.HypoInvalid -> stringResource(R.string.error_hypo)
    DraftError.TargetRangeOrder -> stringResource(R.string.error_target_order)
}

@Composable
fun ColumnScope.WelcomeStep() {
    val container = appContainer()
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle(AppSettings())
    val scope = rememberCoroutineScope()
    Body(stringResource(R.string.setup_welcome_body))
    Body(stringResource(R.string.setup_welcome_text_size))
    TextSize.entries.forEach { size ->
        OptionRow(
            title = stringResource(
                when (size) {
                    TextSize.LARGE -> R.string.text_size_large
                    TextSize.EXTRA_LARGE -> R.string.text_size_extra_large
                    TextSize.LARGEST -> R.string.text_size_largest
                },
            ),
            description = null,
            selected = settings.textSize == size,
            onSelect = { scope.launch { container.settingsStore.update { it.copy(textSize = size) } } },
        )
    }
    NoticeCard(NoticeKind.INFO, stringResource(R.string.about_disclaimer_title), stringResource(R.string.about_disclaimer))
}

@Composable
fun ColumnScope.InsulinStep(ui: SetupUi, vm: PlanSetupViewModel) {
    val draft = ui.draft
    Body(stringResource(R.string.setup_insulin_body))
    DraftErrors(ui.errors)
    LabeledField(
        label = stringResource(R.string.setup_insulin_name),
        value = draft.correctionInsulin,
        onValueChange = { text -> vm.edit { it.copy(correctionInsulin = text) } },
        hint = stringResource(R.string.setup_insulin_name_hint),
    )
    LabeledField(
        label = stringResource(R.string.setup_prescriber),
        value = draft.prescriber,
        onValueChange = { text -> vm.edit { it.copy(prescriber = text) } },
    )
    DateField(
        label = stringResource(R.string.setup_order_date),
        date = draft.orderDate,
        onDateChange = { date -> vm.edit { it.copy(orderDate = date) } },
        allowClear = true,
    )
}

@Composable
fun ColumnScope.ScaleStep(ui: SetupUi, vm: PlanSetupViewModel) {
    val draft = ui.draft
    Body(stringResource(R.string.setup_scale_body))
    DraftErrors(ui.errors)
    draft.bands.forEachIndexed { index, band ->
        val isLast = index == draft.bands.lastIndex
        SectionCard(title = stringResource(R.string.setup_scale_row, index + 1)) {
            if (isLast) {
                val above = draft.bands.getOrNull(index - 1)?.upper?.ifBlank { null }
                Text(
                    if (above != null) stringResource(R.string.setup_scale_last_row, above) else stringResource(R.string.setup_scale_last_row_unknown),
                    style = MaterialTheme.typography.titleMedium,
                )
            } else {
                Text(
                    stringResource(R.string.setup_scale_from, draft.lowerBoundText(index) ?: "?"),
                    style = MaterialTheme.typography.titleMedium,
                )
                LabeledField(
                    label = stringResource(R.string.setup_scale_to),
                    value = band.upper,
                    onValueChange = { vm.setBand(index, band.copy(upper = it)) },
                    suffix = stringResource(R.string.unit_mmol),
                    numeric = true,
                )
            }
            LabeledField(
                label = stringResource(R.string.setup_scale_units),
                value = band.units,
                onValueChange = { vm.setBand(index, band.copy(units = it.filter(Char::isDigit))) },
                suffix = stringResource(R.string.units_word),
                numeric = true,
            )
        }
    }
    BigButton(stringResource(R.string.setup_scale_add_row), onClick = vm::addBand, icon = R.drawable.ic_add, style = ButtonStyle.SECONDARY)
    if (draft.bands.size > 1) {
        BigButton(stringResource(R.string.setup_scale_remove_row), onClick = vm::removeBand, icon = R.drawable.ic_remove, style = ButtonStyle.SECONDARY)
    }
    BigButton(
        stringResource(R.string.setup_scale_common),
        onClick = vm::useCommonScale,
        helper = stringResource(R.string.setup_scale_common_helper),
        style = ButtonStyle.SECONDARY,
    )
}

@Composable
fun ColumnScope.ScaleCheckStep(ui: SetupUi, vm: PlanSetupViewModel) {
    val draft = ui.draft
    Body(stringResource(R.string.setup_scale_check_body))
    DraftErrors(ui.errors)
    draft.parsedScale()?.let { ScaleTable(it, draft.correctionInsulin, highlightIndex = null) }
    CheckRow(
        text = stringResource(R.string.setup_scale_checked),
        checked = draft.scaleChecked,
        onCheckedChange = { checked -> vm.edit { it.copy(scaleChecked = checked) } },
    )
}

@Composable
fun ColumnScope.TimesStep(ui: SetupUi, vm: PlanSetupViewModel) {
    val draft = ui.draft
    Body(stringResource(R.string.setup_times_body))
    DraftErrors(ui.errors)
    draft.slots.forEach { slot ->
        SectionCard(title = stringResource(slot.slot.nameRes())) {
            ToggleRow(
                title = stringResource(R.string.setup_times_check),
                description = null,
                checked = slot.checkBgl,
                onCheckedChange = { checked -> vm.setSlot(slot.slot) { it.copy(checkBgl = checked) } },
            )
            ToggleRow(
                title = stringResource(R.string.setup_times_prescribed, draft.correctionInsulin.ifBlank { stringResource(R.string.insulin_fallback) }),
                description = stringResource(R.string.setup_times_prescribed_helper),
                checked = slot.correctionPrescribed,
                onCheckedChange = { checked -> vm.setSlot(slot.slot) { it.copy(correctionPrescribed = checked) } },
            )
            TimeField(
                label = stringResource(R.string.setup_times_usual),
                time = slot.time,
                onTimeChange = { vm.setSlotTime(slot.slot, it) },
            )
            ToggleRow(
                title = stringResource(R.string.setup_times_reminder),
                description = null,
                checked = slot.reminderOn,
                onCheckedChange = { checked -> vm.setSlot(slot.slot) { it.copy(reminderOn = checked) } },
            )
        }
    }
}

@Composable
fun ColumnScope.MedicinesStep(ui: SetupUi, vm: PlanSetupViewModel) {
    val context = LocalContext.current
    Body(stringResource(R.string.setup_medicines_body))
    if (ui.draft.medicines.isEmpty()) {
        Body(stringResource(R.string.setup_medicines_empty))
    }
    ui.draft.medicines.forEach { draft ->
        val medicine = ui.draft.toMedicine(draft)
        SectionCard(title = medicine.displayName) {
            Text(stringResource(medicine.kind.labelRes()), style = MaterialTheme.typography.bodyLarge)
            medicine.doses.forEach { (slot, amount) ->
                FactRow(stringResource(slot.nameRes()), UiFormat.dose(context, amount, medicine.kind, medicine.unit))
            }
            BigButton(stringResource(R.string.change), onClick = { vm.startMedicine(draft) }, icon = R.drawable.ic_edit, style = ButtonStyle.SECONDARY)
            BigButton(stringResource(R.string.remove), onClick = { vm.removeMedicine(draft.key) }, icon = R.drawable.ic_delete, style = ButtonStyle.SECONDARY)
        }
    }
}

@Composable
fun MedicineEditor(ui: SetupUi, medicine: MedicineDraft, vm: PlanSetupViewModel) {
    val isNew = ui.draft.medicines.none { it.key == medicine.key }
    StepScaffold(
        step = 0,
        stepCount = 0,
        title = stringResource(if (isNew) R.string.setup_medicine_add else R.string.setup_medicine_change),
        onBack = vm::cancelMedicine,
        actions = {
            BigButton(stringResource(R.string.setup_medicine_save), onClick = vm::saveMedicine)
            BigButton(stringResource(R.string.cancel), onClick = vm::cancelMedicine, style = ButtonStyle.SECONDARY)
        },
    ) {
        DraftErrors(ui.medicineErrors)
        LabeledField(
            label = stringResource(R.string.setup_medicine_name),
            value = medicine.name,
            onValueChange = { text -> vm.editMedicine { it.copy(name = text) } },
            hint = stringResource(R.string.setup_medicine_name_hint),
        )
        LabeledField(
            label = stringResource(R.string.setup_medicine_strength),
            value = medicine.strength,
            onValueChange = { text -> vm.editMedicine { it.copy(strength = text) } },
            hint = stringResource(R.string.setup_medicine_strength_hint),
        )
        Text(stringResource(R.string.setup_medicine_kind), style = MaterialTheme.typography.titleMedium)
        MedicineKind.entries.forEach { kind ->
            OptionRow(
                title = stringResource(kind.labelRes()),
                description = null,
                selected = medicine.kind == kind,
                onSelect = { vm.editMedicine { it.copy(kind = kind) } },
            )
        }
        if (medicine.kind == MedicineKind.OTHER) {
            LabeledField(
                label = stringResource(R.string.setup_medicine_unit),
                value = medicine.unit,
                onValueChange = { text -> vm.editMedicine { it.copy(unit = text) } },
                hint = stringResource(R.string.setup_medicine_unit_hint),
            )
        }
        Text(stringResource(R.string.setup_medicine_when), style = MaterialTheme.typography.titleMedium)
        Body(stringResource(R.string.setup_medicine_when_helper))
        val suffix = when (medicine.kind) {
            MedicineKind.INSULIN -> stringResource(R.string.units_word)
            MedicineKind.TABLET -> stringResource(R.string.tablets_word)
            MedicineKind.OTHER -> medicine.unit
        }
        Slot.scheduled.forEach { slot ->
            LabeledField(
                label = stringResource(slot.nameRes()),
                value = medicine.amounts[slot] ?: "",
                onValueChange = { text -> vm.editMedicine { it.copy(amounts = it.amounts + (slot to text)) } },
                suffix = suffix,
                numeric = true,
            )
        }
    }
}

@Composable
fun ColumnScope.GoalsStep(ui: SetupUi, vm: PlanSetupViewModel) {
    val draft = ui.draft
    Body(stringResource(R.string.setup_goals_body))
    DraftErrors(ui.errors)
    DateField(stringResource(R.string.setup_start_date), draft.startDate, { date -> date?.let { d -> vm.edit { it.copy(startDate = d) } } })
    LabeledField(stringResource(R.string.setup_start_weight), draft.startWeight, { t -> vm.edit { it.copy(startWeight = t) } }, suffix = stringResource(R.string.unit_kg), numeric = true)
    LabeledField(stringResource(R.string.setup_goal_weight), draft.goalWeight, { t -> vm.edit { it.copy(goalWeight = t) } }, suffix = stringResource(R.string.unit_kg), numeric = true)
    LabeledField(stringResource(R.string.setup_start_waist), draft.startWaist, { t -> vm.edit { it.copy(startWaist = t) } }, suffix = stringResource(R.string.unit_cm), numeric = true)
    LabeledField(stringResource(R.string.setup_goal_waist), draft.goalWaist, { t -> vm.edit { it.copy(goalWaist = t) } }, suffix = stringResource(R.string.unit_cm), numeric = true)
    DateField(stringResource(R.string.setup_target_date), draft.targetDate, { date -> vm.edit { it.copy(targetDate = date) } }, allowClear = true)
    Text(stringResource(R.string.setup_weigh_every), style = MaterialTheme.typography.titleMedium)
    Frequency.entries.forEach { frequency ->
        OptionRow(stringResource(frequency.labelRes()), null, draft.weighEvery == frequency, { vm.edit { it.copy(weighEvery = frequency) } })
    }
    Text(stringResource(R.string.setup_waist_every), style = MaterialTheme.typography.titleMedium)
    Frequency.entries.forEach { frequency ->
        OptionRow(stringResource(frequency.labelRes()), null, draft.waistEvery == frequency, { vm.edit { it.copy(waistEvery = frequency) } })
    }
}

@Composable
fun ColumnScope.TargetsStep(ui: SetupUi, vm: PlanSetupViewModel) {
    val draft = ui.draft
    NoticeCard(NoticeKind.INFO, stringResource(R.string.setup_targets_notice_title), stringResource(R.string.setup_targets_notice))
    DraftErrors(ui.errors)
    LabeledField(stringResource(R.string.setup_target_low), draft.targetLow, { t -> vm.edit { it.copy(targetLow = t) } }, suffix = stringResource(R.string.unit_mmol), numeric = true)
    LabeledField(stringResource(R.string.setup_target_high), draft.targetHigh, { t -> vm.edit { it.copy(targetHigh = t) } }, suffix = stringResource(R.string.unit_mmol), numeric = true)
    LabeledField(stringResource(R.string.setup_hypo), draft.hypo, { t -> vm.edit { it.copy(hypo = t) } }, suffix = stringResource(R.string.unit_mmol), numeric = true)
    Body(stringResource(R.string.setup_hypo_helper, draft.correctionInsulin.ifBlank { stringResource(R.string.insulin_fallback) }))
}

@Composable
fun ColumnScope.PinStep(ui: SetupUi, vm: PlanSetupViewModel) {
    Body(stringResource(R.string.setup_pin_body))
    ui.pinError?.let {
        NoticeCard(
            NoticeKind.DANGER,
            stringResource(R.string.setup_errors_title),
            stringResource(if (it == PinError.INVALID) R.string.pin_error_invalid else R.string.pin_error_mismatch),
        )
    }
    PinField(stringResource(R.string.setup_pin_choose), ui.pin, vm::setPin)
    PinField(stringResource(R.string.setup_pin_again), ui.pinConfirm, vm::setPinConfirm)
}

@Composable
fun PinField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineMedium,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun ColumnScope.PermissionsStep() {
    val context = LocalContext.current
    var notificationsAllowed by remember { mutableStateOf(false) }
    var alarmsAllowed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        alarmsAllowed = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsAllowed = it }

    Body(stringResource(R.string.setup_permissions_body))
    if (notificationsAllowed) {
        NoticeCard(NoticeKind.INFO, stringResource(R.string.setup_notifications_allowed), null)
    } else {
        BigButton(
            stringResource(R.string.setup_allow_notifications),
            onClick = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            icon = R.drawable.ic_notifications,
        )
    }
    Body(stringResource(R.string.setup_alarms_body))
    if (alarmsAllowed) {
        NoticeCard(NoticeKind.INFO, stringResource(R.string.setup_alarms_allowed), null)
    } else {
        BigButton(
            stringResource(R.string.setup_allow_alarms),
            onClick = {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
            },
            icon = R.drawable.ic_schedule,
            style = ButtonStyle.SECONDARY,
        )
    }
    Body(stringResource(R.string.setup_permissions_later))
}

@Composable
fun ColumnScope.ReviewStep(ui: SetupUi, vm: PlanSetupViewModel) {
    val context = LocalContext.current
    val draft = ui.draft
    val insulin = draft.correctionInsulin
    if (ui.mode == SetupMode.EDIT) Body(stringResource(R.string.setup_review_edit_body)) else Body(stringResource(R.string.setup_review_body))

    SectionCard(stringResource(R.string.setup_insulin_title)) {
        FactRow(stringResource(R.string.setup_insulin_name), insulin)
        FactRow(stringResource(R.string.setup_prescriber), draft.prescriber.ifBlank { stringResource(R.string.not_set) })
        FactRow(stringResource(R.string.setup_order_date), draft.orderDate?.let(UiFormat::dayLongYear) ?: stringResource(R.string.not_set))
        BigButton(stringResource(R.string.change), onClick = { vm.goTo(SetupStep.INSULIN) }, icon = R.drawable.ic_edit, style = ButtonStyle.SECONDARY)
    }
    SectionCard(stringResource(R.string.setup_scale_title)) {
        draft.parsedScale()?.let { ScaleTable(it, insulin, highlightIndex = null) }
        BigButton(stringResource(R.string.change), onClick = { vm.goTo(SetupStep.SCALE) }, icon = R.drawable.ic_edit, style = ButtonStyle.SECONDARY)
    }
    SectionCard(stringResource(R.string.setup_times_title)) {
        draft.slots.forEach { slot ->
            val parts = buildList {
                add(UiFormat.time(context, slot.time))
                add(stringResource(if (slot.checkBgl) R.string.review_check_on else R.string.review_check_off))
                add(stringResource(if (slot.correctionPrescribed) R.string.review_prescribed_on else R.string.review_prescribed_off, insulin))
                add(stringResource(if (slot.reminderOn) R.string.review_reminder_on else R.string.review_reminder_off))
            }
            FactRow(stringResource(slot.slot.nameRes()), parts.joinToString("\n"))
        }
        BigButton(stringResource(R.string.change), onClick = { vm.goTo(SetupStep.TIMES) }, icon = R.drawable.ic_edit, style = ButtonStyle.SECONDARY)
    }
    SectionCard(stringResource(R.string.setup_medicines_title)) {
        if (draft.medicines.isEmpty()) Body(stringResource(R.string.setup_medicines_none))
        draft.medicines.map(draft::toMedicine).forEach { medicine ->
            val lines = medicine.doses.map { (slot, amount) ->
                stringResource(R.string.review_medicine_line, stringResource(slot.nameRes()), UiFormat.dose(context, amount, medicine.kind, medicine.unit))
            }
            FactRow(medicine.displayName, lines.joinToString("\n"))
        }
        BigButton(stringResource(R.string.change), onClick = { vm.goTo(SetupStep.MEDICINES) }, icon = R.drawable.ic_edit, style = ButtonStyle.SECONDARY)
    }
    SectionCard(stringResource(R.string.setup_goals_title)) {
        val kg = stringResource(R.string.unit_kg)
        val cm = stringResource(R.string.unit_cm)
        val notSet = stringResource(R.string.not_set)
        FactRow(stringResource(R.string.setup_start_date), UiFormat.dayLongYear(draft.startDate))
        FactRow(stringResource(R.string.setup_start_weight), draft.startWeight.ifBlank { null }?.let { "$it $kg" } ?: notSet)
        FactRow(stringResource(R.string.setup_goal_weight), draft.goalWeight.ifBlank { null }?.let { "$it $kg" } ?: notSet)
        FactRow(stringResource(R.string.setup_start_waist), draft.startWaist.ifBlank { null }?.let { "$it $cm" } ?: notSet)
        FactRow(stringResource(R.string.setup_goal_waist), draft.goalWaist.ifBlank { null }?.let { "$it $cm" } ?: notSet)
        FactRow(stringResource(R.string.setup_target_date), draft.targetDate?.let(UiFormat::dayLongYear) ?: notSet)
        FactRow(stringResource(R.string.setup_weigh_every), stringResource(draft.weighEvery.labelRes()))
        FactRow(stringResource(R.string.setup_waist_every), stringResource(draft.waistEvery.labelRes()))
        BigButton(stringResource(R.string.change), onClick = { vm.goTo(SetupStep.GOALS) }, icon = R.drawable.ic_edit, style = ButtonStyle.SECONDARY)
    }
    SectionCard(stringResource(R.string.setup_targets_title)) {
        val mmol = stringResource(R.string.unit_mmol)
        FactRow(stringResource(R.string.review_target_range), stringResource(R.string.review_range_value, draft.targetLow, draft.targetHigh, mmol))
        FactRow(stringResource(R.string.setup_hypo), "${draft.hypo} $mmol")
        BigButton(stringResource(R.string.change), onClick = { vm.goTo(SetupStep.TARGETS) }, icon = R.drawable.ic_edit, style = ButtonStyle.SECONDARY)
    }
}

/** Kept here so the review and the plan screen show weights the same way. */
internal fun kgText(grams: Int?): String? = grams?.let(Measures::formatKg)
