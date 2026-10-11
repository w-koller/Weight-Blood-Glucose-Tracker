package io.github.wkoller.glucosetracker

import android.content.Context
import io.github.wkoller.glucosetracker.ai.AssistantService
import io.github.wkoller.glucosetracker.ai.ModelManager
import io.github.wkoller.glucosetracker.data.TrackerRepository
import io.github.wkoller.glucosetracker.data.db.AppDatabase
import io.github.wkoller.glucosetracker.data.prefs.SettingsStore
import io.github.wkoller.glucosetracker.reminders.ReminderScheduler
import io.github.wkoller.glucosetracker.tts.NaturalVoices
import io.github.wkoller.glucosetracker.tts.Speaker
import io.github.wkoller.glucosetracker.tts.VoiceChoice
import kotlinx.coroutines.launch
import java.time.Clock

/** Builds the app's shared objects once. Screens reach it through [TrackerApp]. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val clock: Clock = Clock.systemDefaultZone()
    val database: AppDatabase = AppDatabase.create(appContext)
    val repository = TrackerRepository(database, clock)
    val settingsStore = SettingsStore(appContext)
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
    val naturalVoices by lazy { NaturalVoices(appContext) }
    val speaker = Speaker(appContext) { naturalVoices }
    val reminders = ReminderScheduler(appContext, repository, settingsStore, clock)
    val models by lazy { ModelManager(appContext) }
    val assistant by lazy { AssistantService(repository, models, settingsStore) }

    init {
        // The speaker always uses the voice chosen in settings.
        scope.launch { settingsStore.settings.collect { speaker.choice = VoiceChoice.decode(it.voice) } }
    }

    /** Called after a plan is saved, so anything that depends on the plan can catch up. */
    suspend fun onPlanChanged() = reminders.rescheduleAll()

    /** Called when the user starts the 15-minute timer after a low reading. */
    fun onRecheckRequested(at: java.time.Instant) = reminders.scheduleRecheck(at)
}
