package io.github.wkoller.glucosetracker

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Alarms are lost when the app is force-stopped, so they are set again whenever the app starts.
        appScope.launch { container.reminders.rescheduleAll() }
    }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
