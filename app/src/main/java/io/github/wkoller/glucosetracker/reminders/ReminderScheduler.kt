package io.github.wkoller.glucosetracker.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.wkoller.glucosetracker.MainActivity
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.data.TrackerRepository
import io.github.wkoller.glucosetracker.data.prefs.SettingsStore
import io.github.wkoller.glucosetracker.domain.model.Plan
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.domain.schedule.NextStepEngine
import io.github.wkoller.glucosetracker.domain.schedule.SlotState
import io.github.wkoller.glucosetracker.ui.components.phraseRes
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZonedDateTime

/**
 * Sets one alarm for each check time in the plan. When an alarm fires, [ReminderReceiver] shows a
 * notification unless that check has already been recorded, then sets the alarm for the next day.
 */
class ReminderScheduler(
    private val context: Context,
    private val repository: TrackerRepository,
    private val settings: SettingsStore,
    private val clock: Clock,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = NotificationManagerCompat.from(context)

    init {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_HIGH)
            .apply { description = context.getString(R.string.reminder_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    val canUseExactAlarms: Boolean get() = alarms.canScheduleExactAlarms()

    val notificationsAllowed: Boolean get() = notifications.areNotificationsEnabled()

    suspend fun rescheduleAll() {
        Slot.scheduled.forEach { alarms.cancel(pending(it, KIND_DAILY)) }
        val plan = repository.currentPlan() ?: return
        if (!settings.current().remindersOn) return
        plan.activeSlots.filter { it.reminderOn }.forEach { schedule ->
            val now = ZonedDateTime.now(clock)
            var next = now.with(schedule.time).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            set(next.toInstant(), pending(schedule.slot, KIND_DAILY))
        }
    }

    fun snooze(slot: Slot) = set(Instant.now(clock).plus(SNOOZE), pending(slot, KIND_SNOOZE))

    fun scheduleRecheck(at: Instant) = set(at, pending(Slot.OTHER, KIND_RECHECK))

    /** Shows a check reminder unless everything for that time has already been recorded today. */
    suspend fun remindIfNeeded(slot: Slot) {
        val plan = repository.currentPlan() ?: return
        val now = LocalDateTime.now(clock)
        val day = repository.dayRecords(repository.logicalToday(plan))
        val state = NextStepEngine.progress(plan, now, day).firstOrNull { it.slot == slot }?.state
        if (state == SlotState.DONE) return
        showCheckReminder(plan, slot)
    }

    fun showCheckReminder(plan: Plan, slot: Slot) {
        val phrase = context.getString(slot.phraseRes())
        val checks = plan.schedule(slot)?.checkBgl == true
        val title = context.getString(if (checks) R.string.reminder_check_title else R.string.reminder_medicines_title)
        val body = context.getString(if (checks) R.string.reminder_check_body else R.string.reminder_medicines_body, phrase)
        val snoozeIntent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_SNOOZE)
            .putExtra(EXTRA_SLOT, slot.name)
        val snooze = PendingIntent.getBroadcast(context, 300 + slot.ordinal, snoozeIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        notify(
            slot.ordinal,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_water_drop)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(openApp(slot))
                .addAction(R.drawable.ic_water_drop, context.getString(R.string.reminder_record_now), openApp(slot))
                .addAction(R.drawable.ic_schedule, context.getString(R.string.reminder_snooze), snooze)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true),
        )
    }

    fun showRecheckReminder() {
        val body = context.getString(R.string.reminder_recheck_body)
        notify(
            RECHECK_NOTIFICATION,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_water_drop)
                .setContentTitle(context.getString(R.string.reminder_recheck_title))
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(openApp(Slot.OTHER))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true),
        )
    }

    fun cancelNotification(slot: Slot) = notifications.cancel(slot.ordinal)

    private fun notify(id: Int, builder: NotificationCompat.Builder) {
        if (!notifications.areNotificationsEnabled()) return
        try {
            notifications.notify(id, builder.build())
        } catch (_: SecurityException) {
            // The notification permission was withdrawn after the check above.
        }
    }

    private fun openApp(slot: Slot): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_SLOT, slot.name)
        return PendingIntent.getActivity(context, 400 + slot.ordinal, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun pending(slot: Slot, kind: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_ALARM)
            .putExtra(EXTRA_SLOT, slot.name)
            .putExtra(EXTRA_KIND, kind)
        val code = when (kind) {
            KIND_DAILY -> slot.ordinal
            KIND_SNOOZE -> 100 + slot.ordinal
            else -> 200
        }
        return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun set(at: Instant, operation: PendingIntent) {
        val millis = at.toEpochMilli()
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
        } else {
            // Without permission for exact alarms, the phone may deliver the reminder up to ten minutes late.
            alarms.setWindow(AlarmManager.RTC_WAKEUP, millis, WINDOW.toMillis(), operation)
        }
    }

    companion object {
        const val CHANNEL_ID = "reminders"
        const val EXTRA_SLOT = "slot"
        const val EXTRA_KIND = "kind"
        const val KIND_DAILY = "daily"
        const val KIND_SNOOZE = "snooze"
        const val KIND_RECHECK = "recheck"
        private const val RECHECK_NOTIFICATION = 50
        val SNOOZE: Duration = Duration.ofMinutes(15)
        private val WINDOW: Duration = Duration.ofMinutes(10)
    }
}
