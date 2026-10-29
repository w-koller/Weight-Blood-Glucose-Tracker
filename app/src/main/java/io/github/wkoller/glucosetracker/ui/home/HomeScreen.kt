package io.github.wkoller.glucosetracker.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.domain.format.Measures
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.domain.schedule.Greeting
import io.github.wkoller.glucosetracker.domain.schedule.Greetings
import io.github.wkoller.glucosetracker.domain.schedule.NextStep
import io.github.wkoller.glucosetracker.domain.schedule.SlotState
import io.github.wkoller.glucosetracker.ui.UiFormat
import io.github.wkoller.glucosetracker.ui.appContainer
import io.github.wkoller.glucosetracker.ui.appViewModel
import io.github.wkoller.glucosetracker.ui.components.BigButton
import io.github.wkoller.glucosetracker.ui.components.ButtonStyle
import io.github.wkoller.glucosetracker.ui.components.NoticeCard
import io.github.wkoller.glucosetracker.ui.components.NoticeKind
import io.github.wkoller.glucosetracker.ui.components.SectionCard
import io.github.wkoller.glucosetracker.ui.components.label
import io.github.wkoller.glucosetracker.ui.components.phrase
import io.github.wkoller.glucosetracker.ui.components.rememberNotificationPermission
import io.github.wkoller.glucosetracker.ui.components.tomorrowPhrase
import io.github.wkoller.glucosetracker.ui.theme.LocalStatusColors
import io.github.wkoller.glucosetracker.ui.theme.LocalUiPrefs

data class HomeActions(
    val checkIn: (Slot?, Boolean) -> Unit,
    val calendar: () -> Unit,
    val measure: (String) -> Unit,
    val medicines: () -> Unit,
    val logbook: () -> Unit,
    val progress: () -> Unit,
    val assistant: () -> Unit,
    val settings: () -> Unit,
)

@Composable
fun HomeScreen(actions: HomeActions) {
    val vm = appViewModel { container, _ -> HomeViewModel(container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val simple = LocalUiPrefs.current.simpleMode
    val container = appContainer()
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle(null)
    // Read again each time the screen returns, for example from the phone's settings.
    var notificationsAllowed by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = container.reminders.notificationsAllowed
        onPauseOrDispose { }
    }
    val requestNotifications = rememberNotificationPermission { notificationsAllowed = it }
    val wantsReminders = settings?.remindersOn == true && ui.plan?.activeSlots?.any { it.reminderOn } == true

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(greetingText(Greetings.at(ui.now.toLocalTime()), ui.firstName), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            DateChip(UiFormat.dayLong(ui.now.toLocalDate()), actions.calendar)

            // Shown even in simple mode: repeated lows are something the doctor needs to know about.
            ui.lowPattern?.let { pattern ->
                NoticeCard(
                    NoticeKind.WARNING,
                    stringResource(R.string.lows_title),
                    pluralStringResource(R.plurals.lows_body, pattern.episodes, pattern.episodes, pattern.days),
                ) {
                    BigButton(stringResource(R.string.lows_understood), onClick = vm::acknowledgeLows, icon = R.drawable.ic_check)
                }
            }

            // Also shown in simple mode: without notifications, no reminder can appear.
            if (wantsReminders && !notificationsAllowed) {
                NoticeCard(NoticeKind.WARNING, stringResource(R.string.reminders_no_notifications_title), stringResource(R.string.home_reminders_blocked)) {
                    BigButton(stringResource(R.string.setup_allow_notifications), onClick = requestNotifications, icon = R.drawable.ic_notifications)
                }
            }

            ui.nextStep?.let { NextStepCard(it, actions) }

            // Near the top and shown in simple mode too, so the assistant is easy to find.
            SectionCard(stringResource(R.string.home_assistant_title), icon = R.drawable.ic_forum) {
                if (LocalUiPrefs.current.showHelperText) {
                    Text(stringResource(R.string.home_assistant_body), style = MaterialTheme.typography.bodyLarge)
                }
                BigButton(stringResource(R.string.home_assistant), onClick = actions.assistant, icon = R.drawable.ic_forum)
            }

            if (!simple && ui.slots.isNotEmpty()) TodayCard(ui)

            if (!simple) {
                ui.weight?.let { weight ->
                    SectionCard(stringResource(R.string.home_weight_title), icon = R.drawable.ic_monitor_weight) {
                        val current = weight.current ?: return@SectionCard
                        Text(
                            stringResource(
                                R.string.home_weight_latest,
                                Measures.formatKg(current),
                                weight.currentDate?.let(UiFormat::dayLong) ?: "",
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        weight.remaining?.let { left ->
                            Text(
                                if (left <= 0) stringResource(R.string.home_weight_goal_reached)
                                else stringResource(R.string.home_weight_to_go, Measures.formatKgChange(left)),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }

            BigButton(
                stringResource(R.string.home_check_bgl),
                onClick = { actions.checkIn(null, false) },
                icon = R.drawable.ic_water_drop,
                style = ButtonStyle.SECONDARY,
            )
            BigButton(
                stringResource(R.string.home_record_weight),
                onClick = { actions.measure("weight") },
                icon = R.drawable.ic_monitor_weight,
                style = ButtonStyle.SECONDARY,
            )
            if (ui.plan?.medicines?.isNotEmpty() == true) {
                BigButton(
                    stringResource(R.string.home_medicines),
                    onClick = actions.medicines,
                    icon = R.drawable.ic_medication,
                    style = ButtonStyle.SECONDARY,
                )
            }
            BigButton(
                stringResource(R.string.home_logbook),
                onClick = actions.logbook,
                icon = R.drawable.ic_menu_book,
                style = ButtonStyle.SECONDARY,
            )
            if (!simple) {
                BigButton(
                    stringResource(R.string.home_progress),
                    onClick = actions.progress,
                    icon = R.drawable.ic_show_chart,
                    style = ButtonStyle.SECONDARY,
                )
            }
            BigButton(
                stringResource(R.string.home_settings),
                onClick = actions.settings,
                icon = R.drawable.ic_settings,
                helper = stringResource(R.string.home_settings_helper),
                style = ButtonStyle.SECONDARY,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun greetingText(greeting: Greeting, firstName: String): String {
    val name = firstName.trim()
    return if (name.isEmpty()) {
        stringResource(
            when (greeting) {
                Greeting.MORNING -> R.string.home_good_morning
                Greeting.AFTERNOON -> R.string.home_good_afternoon
                Greeting.EVENING -> R.string.home_good_evening
                Greeting.HELLO -> R.string.home_hello
            },
        )
    } else {
        stringResource(
            when (greeting) {
                Greeting.MORNING -> R.string.home_good_morning_name
                Greeting.AFTERNOON -> R.string.home_good_afternoon_name
                Greeting.EVENING -> R.string.home_good_evening_name
                Greeting.HELLO -> R.string.home_hello_name
            },
            name,
        )
    }
}

/** Today's date, which opens the logbook calendar. It looks like a button so it is clear it can be tapped. */
@Composable
private fun DateChip(date: String, onClick: () -> Unit) {
    val description = stringResource(R.string.home_date_description, date)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Icon(painterResource(R.drawable.ic_calendar_month), contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(date, style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.home_date_label), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun NextStepCard(step: NextStep, actions: HomeActions) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.home_next_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            when (step) {
                is NextStep.CheckIn -> {
                    Text(stringResource(R.string.home_next_check, step.slot.phrase()), style = MaterialTheme.typography.headlineSmall)
                    BigButton(stringResource(R.string.home_check_bgl), onClick = { actions.checkIn(step.slot, false) }, icon = R.drawable.ic_water_drop)
                }
                is NextStep.Medicines -> {
                    Text(stringResource(R.string.home_next_medicines, step.slot.phrase()), style = MaterialTheme.typography.headlineSmall)
                    BigButton(stringResource(R.string.home_record_medicines), onClick = { actions.checkIn(step.slot, true) }, icon = R.drawable.ic_medication)
                }
                NextStep.Weigh -> {
                    Text(stringResource(R.string.home_next_weigh), style = MaterialTheme.typography.headlineSmall)
                    BigButton(stringResource(R.string.home_record_weight), onClick = { actions.measure("weight") }, icon = R.drawable.ic_monitor_weight)
                }
                NextStep.MeasureWaist -> {
                    Text(stringResource(R.string.home_next_waist), style = MaterialTheme.typography.headlineSmall)
                    BigButton(stringResource(R.string.home_record_waist), onClick = { actions.measure("waist") }, icon = R.drawable.ic_straighten)
                }
                is NextStep.AllDone -> {
                    Text(stringResource(R.string.home_next_done), style = MaterialTheme.typography.headlineSmall)
                    val nextSlot = step.nextSlot
                    val nextTime = step.nextTime
                    if (nextSlot != null && nextTime != null) {
                        Text(
                            stringResource(
                                R.string.home_next_later,
                                if (step.isTomorrow) nextSlot.tomorrowPhrase() else nextSlot.phrase(),
                                UiFormat.time(context, nextTime),
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayCard(ui: HomeUi) {
    val context = LocalContext.current
    val status = LocalStatusColors.current
    // Just after midnight the checks still belong to the evening before, so the card names that day.
    val title = if (ui.today == ui.now.toLocalDate()) stringResource(R.string.home_today_title) else UiFormat.dayLong(ui.today)
    SectionCard(title) {
        ui.slots.forEach { slot ->
            val (icon, label, tint) = when (slot.state) {
                SlotState.DONE -> Triple(R.drawable.ic_check_circle, R.string.slot_state_done, status.inTarget)
                SlotState.PARTLY_DONE -> Triple(R.drawable.ic_pending, R.string.slot_state_partly, status.above)
                SlotState.DUE -> Triple(R.drawable.ic_schedule, R.string.slot_state_due, MaterialTheme.colorScheme.primary)
                SlotState.UPCOMING -> Triple(R.drawable.ic_radio_button_unchecked, R.string.slot_state_upcoming, MaterialTheme.colorScheme.onSurfaceVariant)
                SlotState.MISSED -> Triple(R.drawable.ic_warning, R.string.slot_state_missed, status.above)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.home_slot_line, slot.slot.label(), UiFormat.time(context, slot.time)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
