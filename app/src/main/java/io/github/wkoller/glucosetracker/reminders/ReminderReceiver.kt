package io.github.wkoller.glucosetracker.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.wkoller.glucosetracker.TrackerApp
import io.github.wkoller.glucosetracker.domain.model.Slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receives reminder alarms and the "Remind me in 15 minutes" action. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminders = (context.applicationContext as TrackerApp).container.reminders
        val slot = intent.getStringExtra(ReminderScheduler.EXTRA_SLOT)?.let { runCatching { Slot.valueOf(it) }.getOrNull() } ?: return
        val pending = goAsync()
        scope.launch {
            try {
                when {
                    intent.action == ACTION_SNOOZE -> {
                        reminders.cancelNotification(slot)
                        reminders.snooze(slot)
                    }
                    intent.getStringExtra(ReminderScheduler.EXTRA_KIND) == ReminderScheduler.KIND_RECHECK -> reminders.showRecheckReminder()
                    intent.getStringExtra(ReminderScheduler.EXTRA_KIND) == ReminderScheduler.KIND_SNOOZE -> reminders.remindIfNeeded(slot)
                    else -> {
                        reminders.remindIfNeeded(slot)
                        reminders.rescheduleAll()
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ALARM = "io.github.wkoller.glucosetracker.REMINDER"
        const val ACTION_SNOOZE = "io.github.wkoller.glucosetracker.SNOOZE"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

/** Sets the reminders again after a restart, an app update or a change of time or time zone. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val container = (context.applicationContext as TrackerApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                container.reminders.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
