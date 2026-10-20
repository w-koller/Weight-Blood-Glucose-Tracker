package io.github.wkoller.glucosetracker.ui.plan

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.data.prefs.Settings as AppSettings
import io.github.wkoller.glucosetracker.data.prefs.TextSize
import io.github.wkoller.glucosetracker.data.prefs.ThemeChoice
import io.github.wkoller.glucosetracker.domain.model.Frequency
import io.github.wkoller.glucosetracker.domain.model.MedicineKind
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.ui.UiFormat
import io.github.wkoller.glucosetracker.ui.appContainer
import io.github.wkoller.glucosetracker.ui.components.BigButton
import io.github.wkoller.glucosetracker.ui.components.ButtonStyle
import io.github.wkoller.glucosetracker.ui.components.CheckRow
import io.github.wkoller.glucosetracker.ui.components.ConfirmDialog
import io.github.wkoller.glucosetracker.ui.components.DateField
import io.github.wkoller.glucosetracker.ui.components.FactRow
import io.github.wkoller.glucosetracker.ui.components.LabeledField
import io.github.wkoller.glucosetracker.ui.components.NoticeCard
import io.github.wkoller.glucosetracker.ui.components.NoticeKind
import io.github.wkoller.glucosetracker.ui.components.NumberEntryScaffold
import io.github.wkoller.glucosetracker.ui.components.OptionRow
import io.github.wkoller.glucosetracker.ui.components.ScaleTable
import io.github.wkoller.glucosetracker.ui.components.SectionCard
import io.github.wkoller.glucosetracker.ui.components.StepScaffold
import io.github.wkoller.glucosetracker.ui.components.TimeField
import io.github.wkoller.glucosetracker.ui.components.ToggleRow
import io.github.wkoller.glucosetracker.ui.components.label
import io.github.wkoller.glucosetracker.ui.components.rememberNotificationPermission
import io.github.wkoller.glucosetracker.ui.components.labelRes
import io.github.wkoller.glucosetracker.ui.components.phrase
import io.github.wkoller.glucosetracker.ui.settings.SlotNamingCard
import io.github.wkoller.glucosetracker.ui.settings.SupportPersonFields
import kotlinx.coroutines.launch

@Composable
private fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge)

/** Shows the problems that stop the user moving on, as full sentences. */
@Composable
fun DraftErrors(errors: List<DraftError>, draft: PlanDraft? = null) {
    if (errors.isEmpty()) return
    NoticeCard(
        kind = NoticeKind.DANGER,
        title = stringResource(R.string.setup_errors_title),
        body = errors.map { draftErrorText(it, draft) }.joinToString("\n"),
    )
}

/** "the row that starts at 10.1 mmol/L", "the last row" or, when the start is not known yet, "row 3". */
@Composable
private fun rowName(draft: PlanDraft?, row: Int): String {
    val index = row - 1
    if (draft != null && index == draft.bands.lastIndex) return stringResource(R.string.scale_row_name_last)
    val lower = draft?.lowerBoundText(index)
    return if (lower != null) stringResource(R.string.scale_row_name_range, lower) else stringResource(R.string.scale_row_name_number, row)
}

@Composable
fun draftErrorText(error: DraftError, draft: PlanDraft? = null): String = when (error) {
    DraftError.InsulinNameMissing -> stringResource(R.string.error_insulin_name)
    DraftError.ScaleEmpty -> stringResource(R.string.error_scale_empty)
    is DraftError.BandUpperInvalid -> stringResource(R.string.error_band_upper, rowName(draft, error.row))
    is DraftError.BandUnitsInvalid -> stringResource(R.string.error_band_units, rowName(draft, error.row))
    is DraftError.BandNotAscending -> stringResource(R.string.error_band_order, rowName(draft, error.row))
    is DraftError.BandUpperOutOfRange -> stringResource(R.string.error_band_upper_range, rowName(draft, error.row))
    is DraftError.BandUnitsOutOfRange -> stringResource(R.string.error_band_units_range, rowName(draft, error.row))
    DraftError.ScaleNotChecked -> stringResource(R.string.error_scale_not_checked)
    DraftError.NoCheckTimes -> stringResource(R.string.error_no_check_times)
    DraftError.TimesOutOfOrder -> stringResource(R.string.error_times_order)
    DraftError.MedicineNameMissing -> stringResource(R.string.error_medicine_name)
    DraftError.MedicineNoTimes -> stringResource(R.string.error_medicine_times)
    DraftError.MedicineUnitMissing -> stringResource(R.string.error_medicine_unit)
    is DraftError.MedicineAmountInvalid -> stringResource(R.string.error_medicine_amount, error.slot.phrase())
    DraftError.StartWeightInvalid -> stringResource(R.string.error_start_weight)
    DraftError.GoalWeightInvalid -> stringResource(R.string.error_goal_weight)
    DraftError.StartWaistInvalid -> stringResource(R.string.error_start_waist)
    DraftError.GoalWaistInvalid -> stringResource(R.string.error_goal_waist)
    DraftError.TargetDateBeforeStart -> stringResource(R.string.error_target_date)
    DraftError.TargetLowInvalid -> stringResource(R.string.error_target_low)
    DraftError.TargetHighInvalid -> stringResource(R.string.error_target_high)
    DraftError.HypoInvalid -> stringResource(R.string.error_hypo)
    DraftError.TargetRangeOrder -> stringResource(R.string.error_target_order)
    DraftError.HypoAboveRange -> stringResource(R.string.error_hypo_above_range)
}

@Composable
fun ColumnScope.WelcomeStep(onRestore: () -> Unit) {
    val container = appContainer()
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle(AppSettings())
    val scope = rememberCoroutineScope()
    Body(stringResource(R.string.setup_welcome_body))
    Body(stringResource(R.string.setup_welcome_text_size))
    TextSize.entries.forEach { size ->
        OptionRow(
            title = stringResource(size.labelRes()),
            description = null,
            selected = settings.textSize == size,
            onSelect = { scope.launch { container.settingsStore.update { it.copy(textSize = size) } } },
        )
    }
    Body(stringResource(R.string.setup_welcome_theme))
    val themes = listOf(
        stringResource(R.string.theme_system) to { s: AppSettings -> s.copy(theme = ThemeChoice.SYSTEM, maxContrast = false) },
        stringResource(R.string.theme_light) to { s: AppSettings -> s.copy(theme = ThemeChoice.LIGHT, maxContrast = false) },
        stringResource(R.string.theme_dark) to { s: AppSettings -> s.copy(theme = ThemeChoice.DARK, maxContrast = false) },
        stringResource(R.string.display_max_contrast) to { s: AppSettings -> s.copy(maxContrast = true) },
    )
    val selectedTheme = when {
        settings.maxContrast -> 3
        settings.theme == ThemeChoice.LIGHT -> 1
        settings.theme == ThemeChoice.DARK -> 2
        else -> 0
    }
    themes.forEachIndexed { index, (title, apply) ->
        OptionRow(title, null, selectedTheme == index, { scope.launch { container.settingsStore.update(apply) } })
    }
    NoticeCard(NoticeKind.INFO, stringResource(R.string.about_disclaimer_title), stringResource(R.string.about_disclaimer))
    BigButton(stringResource(R.string.backup_restore_from_file), onClick = onRestore, icon = R.drawable.ic_restore, style = ButtonStyle.SECONDARY)
}

@Composable
fun ColumnScope.AboutYouStep(ui: SetupUi, vm: PlanSetupViewModel) {
    Body(stringResource(R.string.setup_about_body))
    LabeledField(
        label = stringResource(R.string.setup_first_name),
        value = ui.firstName,
        onValueChange = vm::setFirstName,
        hint = stringResource(R.string.setup_first_name_hint),
    )
    LabeledField(
        label = stringResource(R.string.setup_family_name),
        value = ui.familyName,
        onValueChange = vm::setFamilyName,
    )
    Text(stringResource(R.string.support_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    SupportPersonFields(ui.supportName, vm::setSupportName, ui.supportPhone, vm::setSupportPhone)
}

@Composable
fun ColumnScope.AssistantStep(ui: SetupUi) {
    val context = LocalContext.current
    val container = appContainer()
    val memoryBytes = remember {
        android.app.ActivityManager.MemoryInfo().also { context.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(it) }.totalMem
    }
    val hasRoom = remember { container.models.hasRoomForModel() }
    Body(stringResource(R.string.assistant_setup_body, io.github.wkoller.glucosetracker.ai.ModelManager.MODEL_NAME))
    NoticeCard(NoticeKind.INFO, stringResource(R.string.assistant_limits_title), stringResource(R.string.assistant_limits))
    Body(stringResource(R.string.setup_assistant_question))
    if (memoryBytes in 1 until RECOMMENDED_MEMORY_BYTES) {
        NoticeCard(NoticeKind.WARNING, stringResource(R.string.setup_assistant_memory_title), stringResource(R.string.setup_assistant_memory))
    }
    if (!hasRoom) {
        NoticeCard(NoticeKind.WARNING, stringResource(R.string.setup_assistant_space_title), stringResource(R.string.assistant_failed_space))
    }
    ui.downloadModel?.let { Text(assistantChoiceText(ui), style = MaterialTheme.typography.titleMedium) }
}

@Composable
private fun assistantChoiceText(ui: SetupUi): String = stringResource(
    when {
        ui.downloadModel != true -> R.string.setup_assistant_chosen_no
        ui.modelOverMobile -> R.string.setup_assistant_chosen_mobile
        else -> R.string.setup_assistant_chosen_yes
    },
)

@Composable
private fun voicesChoiceText(ui: SetupUi): String = stringResource(
    when {
        ui.downloadVoices != true -> R.string.setup_voices_chosen_no
        ui.voicesOverMobile -> R.string.setup_voices_chosen_mobile
        else -> R.string.setup_voices_chosen_yes
    },
)

@Composable
fun ColumnScope.VoicesStep(ui: SetupUi) {
    val container = appContainer()
    val hasRoom = remember { container.naturalVoices.hasRoom() }
    Body(stringResource(R.string.voice_natural_body))
    Body(stringResource(R.string.setup_voices_question))
    if (!hasRoom) {
        NoticeCard(NoticeKind.WARNING, stringResource(R.string.setup_assistant_space_title), stringResource(R.string.voice_natural_failed_space))
    }
    ui.downloadVoices?.let { Text(voicesChoiceText(ui), style = MaterialTheme.typography.titleMedium) }
}

/** The assistant needs about 6 GB of memory to run comfortably beside other apps. */
private const val RECOMMENDED_MEMORY_BYTES = 6L * 1024 * 1024 * 1024

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
    var confirmCommon by remember { mutableStateOf(false) }
    Body(stringResource(R.string.setup_scale_body))
    ScaleErrors(ui.errors, draft)
    if (draft.bands.isEmpty()) {
        BigButton(
            stringResource(R.string.setup_scale_common),
            onClick = vm::useCommonScale,
            helper = stringResource(R.string.setup_scale_common_helper),
        )
        BigButton(
            stringResource(R.string.setup_scale_empty),
            onClick = vm::startEmptyTable,
            style = ButtonStyle.SECONDARY,
        )
        return
    }
    Body(stringResource(R.string.setup_scale_rows_body, draft.correctionInsulin.ifBlank { stringResource(R.string.insulin_fallback) }))
    ScaleRows(ui, vm)
    BigButton(stringResource(R.string.setup_scale_add_row), onClick = vm::addBand, icon = R.drawable.ic_add, style = ButtonStyle.SECONDARY)
    if (draft.bands.size > 2) {
        BigButton(stringResource(R.string.setup_scale_remove_row), onClick = vm::removeBand, icon = R.drawable.ic_remove, style = ButtonStyle.SECONDARY)
    }
    BigButton(
        stringResource(R.string.setup_scale_common),
        onClick = { if (draft.bands.any { it.upper.isNotBlank() || it.units.isNotBlank() }) confirmCommon = true else vm.useCommonScale() },
        helper = stringResource(R.string.setup_scale_common_helper),
        style = ButtonStyle.SECONDARY,
    )
    if (confirmCommon) {
        ConfirmDialog(
            title = stringResource(R.string.setup_scale_common_confirm_title),
            messages = listOf(stringResource(R.string.setup_scale_common_confirm)),
            confirmText = stringResource(R.string.setup_scale_common),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { confirmCommon = false; vm.useCommonScale() },
            onDismiss = { confirmCommon = false },
        )
    }
}

/**
 * Empty boxes are reported once, with the red outlines showing where they are, so a half-filled table
 * does not produce a long list. Numbers that are out of order or out of range are still named one by one.
 */
@Composable
private fun ScaleErrors(errors: List<DraftError>, draft: PlanDraft) {
    if (errors.isEmpty()) return
    fun blank(error: DraftError): Boolean = when (error) {
        is DraftError.BandUpperInvalid -> draft.bands.getOrNull(error.row - 1)?.upper?.isBlank() == true
        is DraftError.BandUnitsInvalid -> draft.bands.getOrNull(error.row - 1)?.units?.isBlank() == true
        else -> false
    }
    val messages = buildList {
        if (errors.any(::blank)) add(stringResource(R.string.error_scale_boxes_empty))
        errors.filterNot(::blank).forEach { add(draftErrorText(it, draft)) }
    }
    NoticeCard(NoticeKind.DANGER, stringResource(R.string.setup_errors_title), messages.joinToString("\n"))
}

/**
 * The scale laid out like the paper order, one row per range, from the lowest to the open top row.
 * Each row is a large button that opens the keypad for that row; nothing is typed on the phone's
 * own keyboard. Rows with a problem show an error icon and say so.
 */
@Composable
private fun ScaleRows(ui: SetupUi, vm: PlanSetupViewModel) {
    val context = LocalContext.current
    val draft = ui.draft
    val rowsWithErrors = ui.errors.mapNotNull {
        when (it) {
            is DraftError.BandUpperInvalid -> it.row
            is DraftError.BandNotAscending -> it.row
            is DraftError.BandUpperOutOfRange -> it.row
            is DraftError.BandUnitsInvalid -> it.row
            is DraftError.BandUnitsOutOfRange -> it.row
            else -> null
        }
    }.toSet()
    draft.bands.forEachIndexed { index, band ->
        val range = if (index == draft.bands.lastIndex) {
            stringResource(R.string.scale_above, draft.bands.getOrNull(index - 1)?.upper?.ifBlank { null } ?: MISSING)
        } else {
            stringResource(R.string.scale_range, draft.lowerBoundText(index) ?: MISSING, band.upper.ifBlank { MISSING })
        }
        val units = band.units.toIntOrNull()?.let { UiFormat.units(context, it.toDouble()) } ?: stringResource(R.string.setup_scale_units_missing)
        val problem = index + 1 in rowsWithErrors
        BigButton(
            stringResource(R.string.scale_row_description, range, units),
            onClick = { vm.startBand(index) },
            icon = if (problem) R.drawable.ic_error else R.drawable.ic_edit,
            helper = stringResource(if (problem) R.string.setup_scale_check_row else R.string.setup_scale_change_row),
            style = ButtonStyle.SECONDARY,
        )
    }
}

/** Shown in place of a number that has not been entered yet. */
private const val MISSING = "…"

/** One number of the sliding scale on the big keypad, named by its row, as on the paper order. */
@Composable
fun BandEntry(ui: SetupUi, edit: BandEdit, vm: PlanSetupViewModel) {
    val draft = ui.draft
    val insulin = draft.correctionInsulin.ifBlank { stringResource(R.string.insulin_fallback) }
    val isLast = edit.index == draft.bands.lastIndex
    val lower = draft.lowerBoundText(edit.index) ?: MISSING
    val title = when {
        edit.field == BandField.UPPER -> stringResource(R.string.setup_scale_upper_description, lower)
        isLast -> stringResource(R.string.setup_scale_units_description_last, insulin, draft.bands.getOrNull(edit.index - 1)?.upper?.ifBlank { null } ?: MISSING)
        else -> stringResource(R.string.setup_scale_units_description, insulin, lower, draft.bands[edit.index].upper.ifBlank { MISSING })
    }
    val done = edit.field == BandField.UNITS && (!edit.guided || isLast)
    val error = when (val problem = edit.problem) {
        null -> null
        BandProblem.Missing -> stringResource(if (edit.field == BandField.UPPER) R.string.setup_band_missing_upper else R.string.setup_band_missing_units)
        is BandProblem.NotAbove -> stringResource(R.string.setup_band_not_above, problem.previous)
        BandProblem.UpperOutOfRange -> stringResource(R.string.setup_band_upper_range)
        BandProblem.UnitsOutOfRange -> stringResource(R.string.setup_band_units_range)
    }
    NumberEntryScaffold(
        title = title,
        onBack = vm::closeBand,
        value = edit.text,
        unit = stringResource(if (edit.field == BandField.UPPER) R.string.unit_mmol else R.string.units_word),
        onValueChange = vm::setBandText,
        error = error,
        maxDecimals = if (edit.field == BandField.UPPER) 1 else 0,
        maxIntegerDigits = 2,
        action = { BigButton(stringResource(if (done) R.string.done else R.string.next), onClick = vm::nextBand) },
    ) {
        Text(stringResource(R.string.setup_scale_row_of, edit.index + 1, draft.bands.size), style = MaterialTheme.typography.titleMedium)
        Body(stringResource(R.string.setup_scale_entry_body))
    }
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
    SlotNamingCard()
    draft.slots.forEach { slot ->
        SectionCard(title = slot.slot.label()) {
            ToggleRow(
                title = stringResource(R.string.setup_times_check),
                description = null,
                checked = slot.checkBgl,
                onCheckedChange = { checked -> vm.setSlot(slot.slot) { it.copy(checkBgl = checked) } },
            )
            ToggleRow(
                title = stringResource(R.string.setup_times_prescribed, draft.correctionInsulin.ifBlank { stringResource(R.string.insulin_fallback) }),
                description = null,
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
                FactRow(slot.label(), UiFormat.dose(context, amount, medicine.kind, medicine.unit))
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
                label = slot.label(),
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
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        alarmsAllowed = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        onPauseOrDispose { }
    }
    val requestNotifications = rememberNotificationPermission { notificationsAllowed = it }

    Body(stringResource(R.string.setup_permissions_body))
    if (notificationsAllowed) {
        NoticeCard(NoticeKind.INFO, stringResource(R.string.setup_notifications_allowed), null)
    } else {
        BigButton(
            stringResource(R.string.setup_allow_notifications),
            onClick = requestNotifications,
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
    if (ui.saveFailed) NoticeCard(NoticeKind.DANGER, stringResource(R.string.setup_save_failed_title), stringResource(R.string.setup_save_failed))
    if (ui.mode != SetupMode.EDIT) Body(stringResource(R.string.setup_review_body))

    if (ui.mode == SetupMode.FIRST) {
        SectionCard(stringResource(R.string.setup_about_title)) {
            val notSet = stringResource(R.string.not_set)
            FactRow(stringResource(R.string.setup_first_name), ui.firstName.ifBlank { notSet })
            FactRow(stringResource(R.string.setup_family_name), ui.familyName.ifBlank { notSet })
            BigButton(stringResource(R.string.change), onClick = { vm.goTo(SetupStep.ABOUT_YOU) }, icon = R.drawable.ic_edit, style = ButtonStyle.SECONDARY)
        }
    }

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
            FactRow(slot.slot.label(), parts.joinToString("\n"))
        }
        BigButton(stringResource(R.string.change), onClick = { vm.goTo(SetupStep.TIMES) }, icon = R.drawable.ic_edit, style = ButtonStyle.SECONDARY)
    }
    SectionCard(stringResource(R.string.setup_medicines_title)) {
        if (draft.medicines.isEmpty()) Body(stringResource(R.string.setup_medicines_none))
        draft.medicines.map(draft::toMedicine).forEach { medicine ->
            val lines = medicine.doses.map { (slot, amount) ->
                stringResource(R.string.review_medicine_line, slot.label(), UiFormat.dose(context, amount, medicine.kind, medicine.unit))
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
    if (ui.mode == SetupMode.FIRST) {
        SectionCard(stringResource(R.string.setup_assistant_title)) {
            Text(assistantChoiceText(ui), style = MaterialTheme.typography.bodyLarge)
            BigButton(stringResource(R.string.change), onClick = { vm.goTo(SetupStep.ASSISTANT) }, icon = R.drawable.ic_edit, style = ButtonStyle.SECONDARY)
        }
        SectionCard(stringResource(R.string.voice_natural_title)) {
            Text(voicesChoiceText(ui), style = MaterialTheme.typography.bodyLarge)
            BigButton(stringResource(R.string.change), onClick = { vm.goTo(SetupStep.VOICES) }, icon = R.drawable.ic_edit, style = ButtonStyle.SECONDARY)
        }
    }
}

