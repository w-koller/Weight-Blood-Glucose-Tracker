package io.github.wkoller.glucosetracker.ui.reminders

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.data.prefs.Settings as AppSettings
import io.github.wkoller.glucosetracker.ui.UiFormat
import io.github.wkoller.glucosetracker.ui.appContainer
import io.github.wkoller.glucosetracker.ui.components.BigButton
import io.github.wkoller.glucosetracker.ui.components.ButtonStyle
import io.github.wkoller.glucosetracker.ui.components.FactRow
import io.github.wkoller.glucosetracker.ui.components.NoticeCard
import io.github.wkoller.glucosetracker.ui.components.NoticeKind
import io.github.wkoller.glucosetracker.ui.components.ScreenScaffold
import io.github.wkoller.glucosetracker.ui.components.SectionCard
import io.github.wkoller.glucosetracker.ui.components.ToggleRow
import io.github.wkoller.glucosetracker.ui.components.label
import io.github.wkoller.glucosetracker.ui.components.rememberNotificationPermission
import kotlinx.coroutines.launch

@Composable
fun RemindersScreen(onBack: () -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle(AppSettings())
    val plan by container.repository.activePlan.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    // Bumped when the screen returns from the phone's settings, so permission states are read again.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val notificationsAllowed = remember(refresh) { container.reminders.notificationsAllowed }
    val exactAllowed = remember(refresh) { container.reminders.canUseExactAlarms }
    val requestNotifications = rememberNotificationPermission { refresh++ }

    ScreenScaffold(title = stringResource(R.string.settings_reminders), onBack = onBack) {
        ToggleRow(
            title = stringResource(R.string.reminders_on),
            description = null,
            checked = settings.remindersOn,
            onCheckedChange = { on ->
                scope.launch {
                    container.settingsStore.update { it.copy(remindersOn = on) }
                    container.reminders.rescheduleAll()
                }
            },
        )
        if (!notificationsAllowed) {
            NoticeCard(NoticeKind.WARNING, stringResource(R.string.reminders_no_notifications_title), stringResource(R.string.reminders_no_notifications)) {
                BigButton(stringResource(R.string.setup_allow_notifications), onClick = requestNotifications)
            }
        }
        if (!exactAllowed) {
            NoticeCard(NoticeKind.INFO, stringResource(R.string.reminders_no_exact_title), stringResource(R.string.reminders_no_exact)) {
                BigButton(
                    stringResource(R.string.setup_allow_alarms),
                    onClick = { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) },
                )
            }
        }
        plan?.let { current ->
            SectionCard(stringResource(R.string.reminders_times_title)) {
                current.activeSlots.forEach { slot ->
                    FactRow(
                        slot.slot.label(),
                        if (slot.reminderOn) UiFormat.time(context, slot.time) else stringResource(R.string.review_reminder_off),
                    )
                }
                Text(stringResource(R.string.reminders_change_in_plan), style = MaterialTheme.typography.bodyMedium)
            }
            BigButton(
                stringResource(R.string.reminders_test),
                onClick = { current.activeSlots.firstOrNull()?.let { container.reminders.showCheckReminder(current, it.slot, settings.slotNaming) } },
                icon = R.drawable.ic_notifications,
                style = ButtonStyle.SECONDARY,
            )
        }
    }
}
