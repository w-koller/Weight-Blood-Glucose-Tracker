package io.github.wkoller.glucosetracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.tts.LocalSpeaker
import io.github.wkoller.glucosetracker.ui.nav.AppNavHost
import io.github.wkoller.glucosetracker.ui.theme.TrackerTheme

class MainActivity : ComponentActivity() {
    /** A check-in a reminder asked to open. */
    private val openSlot = mutableStateOf<Slot?>(null)

    /** True when the backup reminder asked to open Backup and restore. */
    private val openBackup = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as TrackerApp).container
        if (savedInstanceState == null) {
            openSlot.value = slotFrom(intent)
            openBackup.value = intent?.getStringExtra(EXTRA_OPEN) == OPEN_BACKUP
        }
        setContent {
            val settings by container.settingsStore.settings.collectAsStateWithLifecycle(initialValue = null)
            val loaded = settings ?: return@setContent
            // Decided once, so finishing setup does not rebuild the navigation graph.
            val startWithSetup = remember { !loaded.setupDone }
            TrackerTheme(loaded) {
                CompositionLocalProvider(LocalSpeaker provides container.speaker) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        AppNavHost(
                            startWithSetup = startWithSetup,
                            openSlot = openSlot.value,
                            onSlotOpened = { openSlot.value = null },
                            openBackup = openBackup.value,
                            onBackupOpened = { openBackup.value = false },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        slotFrom(intent)?.let { openSlot.value = it }
        if (intent.getStringExtra(EXTRA_OPEN) == OPEN_BACKUP) openBackup.value = true
    }

    override fun onStop() {
        super.onStop()
        (application as TrackerApp).container.speaker.stop()
    }

    private fun slotFrom(intent: Intent?): Slot? =
        intent?.getStringExtra(EXTRA_SLOT)?.let { runCatching { Slot.valueOf(it) }.getOrNull() }

    companion object {
        const val EXTRA_SLOT = "io.github.wkoller.glucosetracker.SLOT"
        const val EXTRA_OPEN = "io.github.wkoller.glucosetracker.OPEN"
        const val OPEN_BACKUP = "backup"
    }
}
