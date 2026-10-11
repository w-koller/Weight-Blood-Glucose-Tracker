package io.github.wkoller.glucosetracker

import android.content.Context
import io.github.wkoller.glucosetracker.data.TrackerRepository
import io.github.wkoller.glucosetracker.data.db.AppDatabase
import io.github.wkoller.glucosetracker.data.prefs.SettingsStore
import io.github.wkoller.glucosetracker.reminders.ReminderScheduler
import io.github.wkoller.glucosetracker.tts.Speaker
import java.time.Clock

/** Builds the app's shared objects once. Screens reach it through [TrackerApp]. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val clock: Clock = Clock.systemDefaultZone()
    val database: AppDatabase = AppDatabase.create(appContext)
    val repository = TrackerRepository(database, clock)
    val settingsStore = SettingsStore(appContext)
    val speaker = Speaker(appContext)
    val reminders = ReminderScheduler(appContext, repository, settingsStore, clock)

    /** Called after a plan is saved, so anything that depends on the plan can catch up. */
    suspend fun onPlanChanged() = reminders.rescheduleAll()

    /** Called when the user starts the 15-minute timer after a low reading. */
    fun onRecheckRequested(at: java.time.Instant) = reminders.scheduleRecheck(at)
}
