package io.github.wkoller.glucosetracker.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.wkoller.glucosetracker.TrackerApp
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Checks once a day whether to remind the user to save a backup file. */
class BackupReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as TrackerApp).container
        val settings = container.settingsStore.current()
        if (!settings.setupDone || !settings.backupReminderOn) return Result.success()
        val firstPlanAt = container.repository.planHistory.first().minOfOrNull { it.effectiveFrom.toEpochMilli() }
        val now = System.currentTimeMillis()
        if (BackupReminderPolicy.isDue(now, settings.lastBackupAt, firstPlanAt, settings.lastBackupReminderAt)) {
            container.reminders.showBackupReminder()
            container.settingsStore.update { it.copy(lastBackupReminderAt = now) }
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "backup-reminder"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BackupReminderWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
