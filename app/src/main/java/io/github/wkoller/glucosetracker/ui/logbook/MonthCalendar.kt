package io.github.wkoller.glucosetracker.ui.logbook

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.domain.records.DayStatus
import io.github.wkoller.glucosetracker.ui.UiFormat
import io.github.wkoller.glucosetracker.ui.components.BigButton
import io.github.wkoller.glucosetracker.ui.components.ButtonStyle
import io.github.wkoller.glucosetracker.ui.theme.LocalStatusColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

private data class DayLook(val container: Color, val content: Color, val icon: Int?, val label: Int)

@Composable
private fun look(status: DayStatus): DayLook {
    val colors = LocalStatusColors.current
    return when (status) {
        DayStatus.COMPLETE -> DayLook(colors.dayComplete, colors.onDayComplete, R.drawable.ic_check_circle, R.string.calendar_complete)
        DayStatus.PARTIAL -> DayLook(colors.dayPartial, colors.onDayPartial, R.drawable.ic_pending, R.string.calendar_partial)
        DayStatus.NONE -> DayLook(colors.dayNone, colors.onDayNone, R.drawable.ic_cancel, R.string.calendar_none)
        DayStatus.TODAY_IN_PROGRESS -> DayLook(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, R.drawable.ic_schedule, R.string.calendar_today)
        DayStatus.FUTURE -> DayLook(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurfaceVariant, null, R.string.calendar_future)
        DayStatus.NOT_TRACKED -> DayLook(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant, null, R.string.calendar_not_tracked)
    }
}

/**
 * A month of days, each coloured by how much was recorded. Colour is never the only signal: every day
 * also has an icon, the legend explains them in words, and TalkBack reads the status of each day.
 */
@Composable
fun MonthCalendar(
    month: YearMonth,
    statuses: Map<LocalDate, DayStatus>,
    today: LocalDate,
    canGoLater: Boolean,
    onDay: (LocalDate) -> Unit,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    // Weeks start on Monday, as on Australian calendars.
    val firstDay = DayOfWeek.MONDAY
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton(stringResource(R.string.calendar_earlier), onClick = onEarlier, icon = R.drawable.ic_chevron_left, style = ButtonStyle.SECONDARY, modifier = Modifier.weight(1f))
            BigButton(stringResource(R.string.calendar_later), onClick = onLater, icon = R.drawable.ic_chevron_right, style = ButtonStyle.SECONDARY, enabled = canGoLater, modifier = Modifier.weight(1f))
        }
        Row(Modifier.clearAndSetSemantics { }) {
            (0 until 7).forEach { offset ->
                Text(
                    firstDay.plus(offset.toLong()).getDisplayName(TextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val leading = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
        val cells: List<LocalDate?> = List(leading) { null } + (1..month.lengthOfMonth()).map(month::atDay)
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { date -> DayCell(date, statuses[date] ?: DayStatus.NOT_TRACKED, date == today, onDay, Modifier.weight(1f)) }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate?, status: DayStatus, isToday: Boolean, onDay: (LocalDate) -> Unit, modifier: Modifier) {
    if (date == null) {
        Spacer(modifier)
        return
    }
    val look = look(status)
    val description = stringResource(R.string.calendar_day_description, UiFormat.dayLong(date), stringResource(look.label))
    val clickable = status != DayStatus.FUTURE
    Surface(
        onClick = { onDay(date) },
        enabled = clickable,
        color = look.container,
        contentColor = look.content,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(if (isToday) 3.dp else 1.dp, if (isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline),
        modifier = modifier
            .heightIn(min = 60.dp)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                if (clickable) {
                    onClick {
                        onDay(date)
                        true
                    }
                } else {
                    disabled()
                }
            },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(vertical = 6.dp)) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            )
            if (look.icon != null) {
                Icon(painterResource(look.icon), contentDescription = null, modifier = Modifier.size(20.dp))
            } else {
                Spacer(Modifier.size(20.dp))
            }
        }
    }
}

/** What each colour and icon means, in words. */
@Composable
fun CalendarLegend() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            DayStatus.COMPLETE to R.string.calendar_legend_complete,
            DayStatus.PARTIAL to R.string.calendar_legend_partial,
            DayStatus.NONE to R.string.calendar_legend_none,
            DayStatus.TODAY_IN_PROGRESS to R.string.calendar_legend_today,
        ).forEach { (status, text) ->
            val look = look(status)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = look.container,
                    contentColor = look.content,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(if (status == DayStatus.TODAY_IN_PROGRESS) 3.dp else 1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.size(44.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        look.icon?.let { Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(24.dp)) }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** "In October, 20 days were fully recorded, 5 days were partly recorded and 2 days had nothing recorded." */
@Composable
fun calendarSummary(month: YearMonth, statuses: Map<LocalDate, DayStatus>): String {
    val complete = statuses.values.count { it == DayStatus.COMPLETE }
    val partial = statuses.values.count { it == DayStatus.PARTIAL }
    val none = statuses.values.count { it == DayStatus.NONE }
    val locale = LocalConfiguration.current.locales[0]
    return stringResource(
        R.string.calendar_summary,
        month.format(DateTimeFormatter.ofPattern("MMMM", locale)),
        pluralStringResource(R.plurals.calendar_days_complete, complete, complete),
        pluralStringResource(R.plurals.calendar_days_partial, partial, partial),
        pluralStringResource(R.plurals.calendar_days_none, none, none),
    )
}
