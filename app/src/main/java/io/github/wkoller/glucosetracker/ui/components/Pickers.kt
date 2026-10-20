package io.github.wkoller.glucosetracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.text.format.DateFormat
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.ui.UiFormat
import java.time.LocalDate
import java.time.LocalTime

/** A labelled date with a button that opens the date stepper. */
@Composable
fun DateField(
    label: String,
    date: LocalDate?,
    onDateChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    allowClear: Boolean = false,
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
        var draft by remember { mutableStateOf(date ?: LocalDate.now()) }
        Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(label, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    DateStepper(draft, { draft = it })
                    BigButton(stringResource(R.string.save), onClick = { onDateChange(draft); open = false })
                    BigButton(stringResource(R.string.cancel), onClick = { open = false }, style = ButtonStyle.SECONDARY)
                }
            }
        }
    }
}

/**
 * Sets a date with large Earlier and Later buttons for the day, the month and the year. A calendar has small
 * days to tap and gets cramped at large text sizes, so it is not used.
 */
@Composable
fun DateStepper(date: LocalDate, onDateChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(
                UiFormat.dayLongYear(date),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp).testTag("date_value"),
            )
        }
        StepperRow(
            label = stringResource(R.string.date_day_label),
            earlier = stringResource(R.string.date_day_earlier),
            later = stringResource(R.string.date_day_later),
            onEarlier = { onDateChange(date.minusDays(1)) },
            onLater = { onDateChange(date.plusDays(1)) },
        )
        StepperRow(
            label = stringResource(R.string.date_month_label),
            earlier = stringResource(R.string.date_month_earlier),
            later = stringResource(R.string.date_month_later),
            onEarlier = { onDateChange(date.minusMonths(1)) },
            onLater = { onDateChange(date.plusMonths(1)) },
        )
        StepperRow(
            label = stringResource(R.string.date_year_label),
            earlier = stringResource(R.string.date_year_earlier),
            later = stringResource(R.string.date_year_later),
            onEarlier = { onDateChange(date.minusYears(1)) },
            onLater = { onDateChange(date.plusYears(1)) },
        )
    }
}

/** A labelled time of day with a button that opens the time stepper. */
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
        var draft by remember { mutableStateOf(time) }
        Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(label, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    TimeStepper(draft, { draft = it })
                    BigButton(stringResource(R.string.save), onClick = { onTimeChange(draft); open = false })
                    BigButton(stringResource(R.string.cancel), onClick = { open = false }, style = ButtonStyle.SECONDARY)
                }
            }
        }
    }
}

/**
 * Sets a time with large Earlier and Later buttons, an hour or five minutes at a time. A clock face
 * needs precise dragging and gets cramped at large text sizes, so it is not used.
 */
@Composable
fun TimeStepper(time: LocalTime, onTimeChange: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val twelveHour = !DateFormat.is24HourFormat(context)
    val shown = UiFormat.time(context, time)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(
                shown,
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp).testTag("time_value"),
            )
        }
        StepperRow(
            label = stringResource(R.string.time_hour_label),
            earlier = stringResource(R.string.time_hour_earlier),
            later = stringResource(R.string.time_hour_later),
            onEarlier = { onTimeChange(time.minusHours(1)) },
            onLater = { onTimeChange(time.plusHours(1)) },
        )
        StepperRow(
            label = stringResource(R.string.time_minutes_label),
            earlier = stringResource(R.string.time_minutes_earlier),
            later = stringResource(R.string.time_minutes_later),
            onEarlier = { onTimeChange(time.minusMinutes(5)) },
            onLater = { onTimeChange(time.plusMinutes(5)) },
        )
        if (twelveHour) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OptionRow(stringResource(R.string.time_am), null, time.hour < 12, { if (time.hour >= 12) onTimeChange(time.minusHours(12)) }, modifier = Modifier.weight(1f))
                OptionRow(stringResource(R.string.time_pm), null, time.hour >= 12, { if (time.hour < 12) onTimeChange(time.plusHours(12)) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** "Earlier" and "Later" under a heading. Screen readers hear the full wording, such as "1 hour earlier". */
@Composable
private fun StepperRow(label: String, earlier: String, later: String, onEarlier: () -> Unit, onLater: () -> Unit) {
    Text(label, style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        BigButton(
            stringResource(R.string.time_earlier),
            onClick = onEarlier,
            icon = R.drawable.ic_remove,
            style = ButtonStyle.SECONDARY,
            modifier = Modifier.weight(1f).semantics { contentDescription = earlier },
        )
        BigButton(
            stringResource(R.string.time_later),
            onClick = onLater,
            icon = R.drawable.ic_add,
            style = ButtonStyle.SECONDARY,
            modifier = Modifier.weight(1f).semantics { contentDescription = later },
        )
    }
}
