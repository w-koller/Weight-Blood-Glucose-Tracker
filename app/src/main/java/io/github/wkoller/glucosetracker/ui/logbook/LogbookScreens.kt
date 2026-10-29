package io.github.wkoller.glucosetracker.ui.logbook

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.domain.format.Measures
import io.github.wkoller.glucosetracker.domain.model.DoseRecord
import io.github.wkoller.glucosetracker.domain.model.DoseStatus
import io.github.wkoller.glucosetracker.domain.model.GlucoseReading
import io.github.wkoller.glucosetracker.domain.model.MeterFlag
import io.github.wkoller.glucosetracker.ui.UiFormat
import io.github.wkoller.glucosetracker.ui.appViewModel
import io.github.wkoller.glucosetracker.ui.components.BigButton
import io.github.wkoller.glucosetracker.ui.components.ButtonStyle
import io.github.wkoller.glucosetracker.ui.components.ConfirmDialog
import io.github.wkoller.glucosetracker.ui.components.OptionRow
import io.github.wkoller.glucosetracker.ui.components.ScreenScaffold
import io.github.wkoller.glucosetracker.ui.components.SectionCard
import io.github.wkoller.glucosetracker.ui.components.labelRes
import io.github.wkoller.glucosetracker.ui.components.label
import io.github.wkoller.glucosetracker.ui.components.NoticeCard
import io.github.wkoller.glucosetracker.ui.components.NoticeKind
import java.time.LocalDate

/**
 * The logbook, as a calendar or a list. [onChart] opens a day on the full-screen chart. When the chart closes,
 * [returnDate] is the day it was showing, and the list opens at that day before calling [onReturnHandled].
 */
@Composable
fun LogbookScreen(
    onBack: () -> Unit,
    onDay: (LocalDate) -> Unit,
    onChart: (LocalDate) -> Unit = {},
    returnDate: LocalDate? = null,
    onReturnHandled: () -> Unit = {},
) {
    val vm = appViewModel { container, _ -> LogbookViewModel(container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val calendar by vm.calendar.collectAsStateWithLifecycle()
    var showList by rememberSaveable { mutableStateOf(false) }
    val viewChoice: @Composable () -> Unit = { ViewChoice(showList, onChoose = { showList = it }) }
    if (showList) {
        LogbookListScreen(
            ui,
            onBack = onBack,
            viewChoice = viewChoice,
            onDay = onDay,
            onChart = onChart,
            onEarlier = vm::showEarlier,
            onLoadFrom = vm::showFrom,
            returnDate = returnDate,
            onReturnHandled = onReturnHandled,
        )
        return
    }
    ScreenScaffold(title = stringResource(R.string.logbook_title), onBack = onBack) {
        viewChoice()
        MonthCalendar(
            month = calendar.month,
            statuses = calendar.statuses,
            today = calendar.today,
            canGoLater = calendar.canGoLater,
            onDay = onDay,
            onEarlier = vm::earlierMonth,
            onLater = vm::laterMonth,
        )
        Text(calendarSummary(calendar.month, calendar.statuses), style = MaterialTheme.typography.titleMedium)
        SectionCard(stringResource(R.string.calendar_legend_title)) { CalendarLegend() }
    }
}

/** Calendar or List, the two ways to look through the logbook. They sit side by side when both words fit, and one above the other when they do not. */
@Composable
private fun ViewChoice(showList: Boolean, onChoose: (Boolean) -> Unit) {
    val calendar = stringResource(R.string.logbook_view_calendar)
    val list = stringResource(R.string.logbook_view_list)
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.titleMedium
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val longest = with(density) { listOf(calendar, list).maxOf { measurer.measure(it, style).size.width }.toDp() }
        val sideBySide = (maxWidth - CHOICE_GAP) / 2 >= longest + OPTION_CHROME
        val choices: @Composable (Modifier) -> Unit = { modifier ->
            OptionRow(calendar, null, !showList, { onChoose(false) }, modifier = modifier)
            OptionRow(list, null, showList, { onChoose(true) }, modifier = modifier)
        }
        if (sideBySide) {
            Row(horizontalArrangement = Arrangement.spacedBy(CHOICE_GAP), modifier = Modifier.fillMaxWidth()) { choices(Modifier.weight(1f)) }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(CHOICE_GAP), modifier = Modifier.fillMaxWidth()) { choices(Modifier) }
        }
    }
}

private val CHOICE_GAP = 12.dp

/** The padding, radio button and gap around the words of an [OptionRow]. */
private val OPTION_CHROME = 72.dp

fun readingText(context: Context, reading: GlucoseReading): String {
    val value = when (reading.meterFlag) {
        MeterFlag.LO -> context.getString(R.string.checkin_reading_lo)
        MeterFlag.HI -> context.getString(R.string.checkin_reading_hi)
        null -> context.getString(R.string.bgl_amount, Measures.formatTenths(reading.tenths))
    }
    val text = context.getString(R.string.logbook_reading, value, UiFormat.time(context, reading.takenAt))
    return reading.note?.let { context.getString(R.string.logbook_with_note, text, it) } ?: text
}

fun doseText(context: Context, dose: DoseRecord): String {
    val text = doseStatusText(context, dose)
    return dose.note?.let { context.getString(R.string.logbook_with_note, text, it) } ?: text
}

private fun doseStatusText(context: Context, dose: DoseRecord): String = when (dose.status) {
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
fun DayScreen(onBack: () -> Unit, onEdit: (kind: String, id: Long) -> Unit, onAdd: () -> Unit) {
    val vm = appViewModel { container, handle -> DayViewModel(container, handle) }
    val day by vm.day.collectAsStateWithLifecycle()
    val lastDeleted by vm.lastDeleted.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var deleting by remember { mutableStateOf<Any?>(null) }

    ScreenScaffold(title = UiFormat.dayLong(vm.date), onBack = onBack) {
        // Undo stays until the user leaves the day or deletes something else, so there is no time limit.
        if (lastDeleted != null) {
            NoticeCard(NoticeKind.INFO, stringResource(R.string.day_deleted), stringResource(R.string.day_deleted_body)) {
                BigButton(stringResource(R.string.undo), onClick = vm::undoDelete, icon = R.drawable.ic_restore)
            }
        }
        val current = day
        if (current == null) {
            Text(stringResource(R.string.day_empty), style = MaterialTheme.typography.titleMedium)
        } else {
            current.slots.forEach { entry ->
                SectionCard(entry.slot.label()) {
                    entry.readings.forEach { reading ->
                        RecordRow(readingText(context, reading), onEdit = { onEdit(RecordEditorViewModel.KIND_READING, reading.id) }, onDelete = { deleting = reading })
                    }
                    entry.doses.forEach { dose ->
                        RecordRow(doseText(context, dose), onEdit = { onEdit(RecordEditorViewModel.KIND_DOSE, dose.id) }, onDelete = { deleting = dose })
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
                        val text = (parts + listOfNotNull(m.note)).joinToString("\n")
                        RecordRow(text, onEdit = { onEdit(RecordEditorViewModel.KIND_MEASUREMENT, m.id) }, onDelete = { deleting = m })
                    }
                }
            }
        }
        BigButton(
            stringResource(R.string.day_add),
            onClick = onAdd,
            icon = R.drawable.ic_add,
            helper = stringResource(R.string.day_add_helper),
            style = ButtonStyle.SECONDARY,
        )
    }

    deleting?.let { record ->
        ConfirmDialog(
            title = stringResource(R.string.day_delete_title),
            messages = listOf(stringResource(R.string.day_delete_body)),
            confirmText = stringResource(R.string.day_delete_confirm),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                deleting = null
                vm.delete(record)
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
