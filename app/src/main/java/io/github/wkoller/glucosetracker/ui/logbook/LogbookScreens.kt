package io.github.wkoller.glucosetracker.ui.logbook

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.domain.dosing.DoseAdvisor
import io.github.wkoller.glucosetracker.domain.format.Measures
import io.github.wkoller.glucosetracker.domain.model.BodyMeasurement
import io.github.wkoller.glucosetracker.domain.model.DoseRecord
import io.github.wkoller.glucosetracker.domain.model.DoseStatus
import io.github.wkoller.glucosetracker.domain.model.GlucoseReading
import io.github.wkoller.glucosetracker.domain.model.GlucoseTargets
import io.github.wkoller.glucosetracker.domain.model.MeterFlag
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.domain.records.DayLog
import io.github.wkoller.glucosetracker.domain.validation.InputValidator
import io.github.wkoller.glucosetracker.domain.validation.NumberInput
import io.github.wkoller.glucosetracker.ui.UiFormat
import io.github.wkoller.glucosetracker.ui.appViewModel
import io.github.wkoller.glucosetracker.ui.components.BigButton
import io.github.wkoller.glucosetracker.ui.components.ButtonStyle
import io.github.wkoller.glucosetracker.ui.components.ConfirmDialog
import io.github.wkoller.glucosetracker.ui.components.LabeledField
import io.github.wkoller.glucosetracker.ui.components.OptionRow
import io.github.wkoller.glucosetracker.ui.components.ScreenScaffold
import io.github.wkoller.glucosetracker.ui.components.SectionCard
import io.github.wkoller.glucosetracker.ui.components.StatusChip
import io.github.wkoller.glucosetracker.ui.components.labelRes
import io.github.wkoller.glucosetracker.ui.components.nameRes
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun LogbookScreen(onBack: () -> Unit, onDay: (LocalDate) -> Unit) {
    val vm = appViewModel { container, _ -> LogbookViewModel(container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val targets = ui.plan?.targets ?: GlucoseTargets.DEFAULT
    ScreenScaffold(title = stringResource(R.string.logbook_title), onBack = onBack) {
        Text(stringResource(R.string.logbook_body), style = MaterialTheme.typography.bodyLarge)
        if (!ui.loading && ui.days.isEmpty()) {
            Text(stringResource(R.string.logbook_empty), style = MaterialTheme.typography.titleMedium)
        }
        ui.days.forEach { day ->
            SectionCard(UiFormat.dayLong(day.date)) {
                DaySummary(day, targets)
                BigButton(stringResource(R.string.logbook_open_day), onClick = { onDay(day.date) }, icon = R.drawable.ic_edit, style = ButtonStyle.SECONDARY)
            }
        }
        if (ui.hasEarlier) {
            BigButton(stringResource(R.string.logbook_show_earlier), onClick = vm::showEarlier, icon = R.drawable.ic_history, style = ButtonStyle.SECONDARY)
        }
    }
}

/** A day's records in the order of the day, with each reading's status. */
@Composable
private fun DaySummary(day: DayLog, targets: GlucoseTargets) {
    val context = LocalContext.current
    day.slots.forEachIndexed { index, entry ->
        if (index > 0) HorizontalDivider()
        Text(stringResource(entry.slot.nameRes()), style = MaterialTheme.typography.titleMedium)
        entry.readings.forEach { reading ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(readingText(context, reading), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                StatusChip(DoseAdvisor.statusOf(reading.tenths, reading.meterFlag, targets))
            }
        }
        entry.doses.forEach { dose -> Text(doseText(context, dose), style = MaterialTheme.typography.bodyLarge) }
    }
    day.weightGrams?.let { Text(stringResource(R.string.logbook_weight, Measures.formatKg(it)), style = MaterialTheme.typography.bodyLarge) }
    day.waistMm?.let { Text(stringResource(R.string.logbook_waist, Measures.formatCm(it)), style = MaterialTheme.typography.bodyLarge) }
}

fun readingText(context: Context, reading: GlucoseReading): String {
    val value = when (reading.meterFlag) {
        MeterFlag.LO -> context.getString(R.string.checkin_reading_lo)
        MeterFlag.HI -> context.getString(R.string.checkin_reading_hi)
        null -> context.getString(R.string.bgl_amount, Measures.formatTenths(reading.tenths))
    }
    val text = context.getString(R.string.logbook_reading, value, UiFormat.time(context, reading.takenAt))
    return reading.note?.let { context.getString(R.string.logbook_with_note, text, it) } ?: text
}

fun doseText(context: Context, dose: DoseRecord): String = when (dose.status) {
    DoseStatus.GIVEN -> context.getString(
        R.string.logbook_dose_given,
        dose.medicineName,
        UiFormat.dose(context, dose.givenAmount ?: 0.0, dose.medicineKind, dose.unit),
    )
    DoseStatus.SKIPPED -> {
        val reason = dose.reason?.let { context.getString(it.labelRes()) }
        if (reason != null) context.getString(R.string.logbook_dose_skipped_reason, dose.medicineName, reason)
        else context.getString(R.string.logbook_dose_skipped, dose.medicineName)
    }
    DoseStatus.NOT_NEEDED -> context.getString(R.string.logbook_dose_not_needed, dose.medicineName)
}

@Composable
fun DayScreen(onBack: () -> Unit) {
    val vm = appViewModel { container, handle -> DayViewModel(container, handle) }
    val day by vm.day.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Any?>(null) }
    var deleting by remember { mutableStateOf<Any?>(null) }

    ScreenScaffold(title = UiFormat.dayLong(vm.date), onBack = onBack, snackbarHostState = snackbar) {
        Text(stringResource(R.string.day_body), style = MaterialTheme.typography.bodyLarge)
        val current = day
        if (current == null) {
            Text(stringResource(R.string.day_empty), style = MaterialTheme.typography.titleMedium)
        } else {
            current.slots.forEach { entry ->
                SectionCard(stringResource(entry.slot.nameRes())) {
                    entry.readings.forEach { reading ->
                        RecordRow(readingText(context, reading), onEdit = { editing = reading }, onDelete = { deleting = reading })
                    }
                    entry.doses.forEach { dose ->
                        RecordRow(doseText(context, dose), onEdit = { editing = dose }, onDelete = { deleting = dose })
                    }
                }
            }
            if (current.measurements.isNotEmpty()) {
                SectionCard(stringResource(R.string.day_measurements)) {
                    current.measurements.forEach { m ->
                        val parts = listOfNotNull(
                            m.weightGrams?.let { stringResource(R.string.logbook_weight, Measures.formatKg(it)) },
                            m.waistMm?.let { stringResource(R.string.logbook_waist, Measures.formatCm(it)) },
                        )
                        RecordRow(parts.joinToString("\n"), onEdit = { editing = m }, onDelete = { deleting = m })
                    }
                }
            }
        }
    }

    when (val record = editing) {
        is GlucoseReading -> EditReadingDialog(record, onSave = { vm.updateReading(it); editing = null }, onDismiss = { editing = null })
        is DoseRecord -> EditDoseDialog(record, onSave = { vm.updateDose(it); editing = null }, onDismiss = { editing = null })
        is BodyMeasurement -> EditMeasurementDialog(record, onSave = { vm.updateMeasurement(it); editing = null }, onDismiss = { editing = null })
    }
    deleting?.let { record ->
        val deletedMessage = stringResource(R.string.day_deleted)
        val undoLabel = stringResource(R.string.undo)
        ConfirmDialog(
            title = stringResource(R.string.day_delete_title),
            messages = listOf(stringResource(R.string.day_delete_body)),
            confirmText = stringResource(R.string.day_delete_confirm),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                deleting = null
                vm.delete(record)
                scope.launch {
                    val result = snackbar.showSnackbar(deletedMessage, actionLabel = undoLabel, duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) vm.undoDelete()
                }
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun RecordRow(text: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton(stringResource(R.string.change), onClick = onEdit, icon = R.drawable.ic_edit, style = ButtonStyle.SECONDARY, modifier = Modifier.weight(1f))
            BigButton(stringResource(R.string.delete), onClick = onDelete, icon = R.drawable.ic_delete, style = ButtonStyle.SECONDARY, modifier = Modifier.weight(1f))
        }
        HorizontalDivider()
    }
}

@Composable
private fun EditDialog(title: String, onSave: () -> Unit, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() } },
        confirmButton = { TextButton(onClick = onSave) { Text(stringResource(R.string.save), style = MaterialTheme.typography.labelLarge) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge) } },
    )
}

@Composable
private fun EditReadingDialog(reading: GlucoseReading, onSave: (GlucoseReading) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(if (reading.meterFlag == null) Measures.formatTenths(reading.tenths) else "") }
    var slot by remember { mutableStateOf(reading.slot) }
    var note by remember { mutableStateOf(reading.note.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val invalid = stringResource(R.string.edit_bgl_invalid)
    EditDialog(
        title = stringResource(R.string.edit_reading_title),
        onSave = {
            val cleanNote = note.trim().ifBlank { null }
            when {
                // A LO or HI reading keeps its value unless a new number is typed.
                text.isBlank() && reading.meterFlag != null -> onSave(reading.copy(slot = slot, note = cleanNote))
                else -> when (val input = InputValidator.bgl(text)) {
                    is NumberInput.Valid -> onSave(reading.copy(tenths = input.value, meterFlag = null, slot = slot, note = cleanNote))
                    else -> error = invalid
                }
            }
        },
        onDismiss = onDismiss,
    ) {
        LabeledField(stringResource(R.string.bgl_short_label), text, { text = it; error = null }, suffix = stringResource(R.string.unit_mmol), error = error, numeric = true)
        Slot.entries.forEach { s -> OptionRow(stringResource(s.nameRes()), null, slot == s, { slot = s }) }
        LabeledField(stringResource(R.string.edit_note), note, { note = it }, hint = stringResource(R.string.edit_note_hint), singleLine = false)
    }
}

@Composable
private fun EditDoseDialog(dose: DoseRecord, onSave: (DoseRecord) -> Unit, onDismiss: () -> Unit) {
    var given by remember { mutableStateOf(dose.status == DoseStatus.GIVEN) }
    var text by remember { mutableStateOf(dose.givenAmount?.let(Measures::formatAmount) ?: dose.prescribedAmount?.let(Measures::formatAmount) ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    val invalid = stringResource(R.string.edit_amount_invalid)
    EditDialog(
        title = dose.medicineName,
        onSave = {
            if (!given) {
                onSave(dose.copy(status = DoseStatus.SKIPPED, givenAmount = null))
            } else {
                when (val input = InputValidator.amount(text)) {
                    is NumberInput.Valid -> onSave(dose.copy(status = DoseStatus.GIVEN, givenAmount = input.value, reason = null))
                    else -> error = invalid
                }
            }
        },
        onDismiss = onDismiss,
    ) {
        OptionRow(stringResource(R.string.edit_dose_given), null, given, { given = true })
        OptionRow(stringResource(R.string.dose_not_given), null, !given, { given = false })
        if (given) LabeledField(stringResource(R.string.edit_dose_amount), text, { text = it; error = null }, suffix = dose.unit, error = error, numeric = true)
    }
}

@Composable
private fun EditMeasurementDialog(measurement: BodyMeasurement, onSave: (BodyMeasurement) -> Unit, onDismiss: () -> Unit) {
    var weight by remember { mutableStateOf(measurement.weightGrams?.let(Measures::formatKg) ?: "") }
    var waist by remember { mutableStateOf(measurement.waistMm?.let(Measures::formatCm) ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    val invalid = stringResource(R.string.edit_measurement_invalid)
    EditDialog(
        title = stringResource(R.string.day_measurements),
        onSave = {
            val w = InputValidator.weight(weight)
            val c = InputValidator.waist(waist)
            val wOk = w is NumberInput.Valid || w is NumberInput.Empty
            val cOk = c is NumberInput.Valid || c is NumberInput.Empty
            if (wOk && cOk && (w is NumberInput.Valid || c is NumberInput.Valid)) {
                onSave(measurement.copy(weightGrams = (w as? NumberInput.Valid)?.value, waistMm = (c as? NumberInput.Valid)?.value))
            } else {
                error = invalid
            }
        },
        onDismiss = onDismiss,
    ) {
        LabeledField(stringResource(R.string.measure_weight_title), weight, { weight = it; error = null }, suffix = stringResource(R.string.unit_kg), numeric = true)
        LabeledField(stringResource(R.string.measure_waist_title), waist, { waist = it; error = null }, suffix = stringResource(R.string.unit_cm), error = error, numeric = true)
    }
}
