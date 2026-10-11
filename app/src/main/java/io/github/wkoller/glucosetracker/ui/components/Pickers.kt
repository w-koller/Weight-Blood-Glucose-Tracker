package io.github.wkoller.glucosetracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.text.format.DateFormat
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.ui.UiFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** A labelled date with a button that opens a calendar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    date: LocalDate?,
    onDateChange: (LocalDate?) -> Unit,
    allowClear: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text(date?.let(UiFormat::dayLongYear) ?: stringResource(R.string.not_set), style = MaterialTheme.typography.titleLarge)
        BigButton(
            text = stringResource(if (date == null) R.string.choose_date else R.string.change_date),
            onClick = { open = true },
            icon = R.drawable.ic_event,
            style = ButtonStyle.SECONDARY,
        )
        if (allowClear && date != null) {
            BigButton(text = stringResource(R.string.remove_date), onClick = { onDateChange(null) }, style = ButtonStyle.SECONDARY)
        }
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.ok), style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge)
                }
            },
        ) { DatePicker(state = state) }
    }
}

/** A labelled time of day with a button that opens a clock. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(label: String, time: LocalTime, onTimeChange: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text(UiFormat.time(context, time), style = MaterialTheme.typography.titleLarge)
        BigButton(text = stringResource(R.string.change_time), onClick = { open = true }, icon = R.drawable.ic_schedule, style = ButtonStyle.SECONDARY)
    }
    if (open) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label, style = MaterialTheme.typography.headlineSmall) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(LocalTime.of(state.hour, state.minute))
                    open = false
                }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.ok), style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge)
                }
            },
        )
    }
}
