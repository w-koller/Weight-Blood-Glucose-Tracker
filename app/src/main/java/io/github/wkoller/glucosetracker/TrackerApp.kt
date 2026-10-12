package io.github.wkoller.glucosetracker

import android.app.Application
import io.github.wkoller.glucosetracker.backup.BackupReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        container = AppContainer(this)
        // Alarms are lost when the app is force-stopped, so they are set again whenever the app starts.
        appScope.launch { container.reminders.rescheduleAll() }
        // Not available in every test environment, so a failure here must not stop the app.
        runCatching { BackupReminderWorker.schedule(this) }
    }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
