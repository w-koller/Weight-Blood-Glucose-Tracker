package io.github.wkoller.glucosetracker.backup

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import io.github.wkoller.glucosetracker.TrackerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Hears from the share sheet that the user chose where to send a backup file, so the backup counts as saved. A
 * share sheet closed without choosing anything does not put off the backup reminder.
 */
class BackupSharedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val backup = (context.applicationContext as TrackerApp).container.backup
        val pending = goAsync()
        scope.launch {
            try {
                backup.markSaved()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE = 600
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** What the share sheet calls once something is chosen. Android adds the chosen app to it, so it must be mutable. */
        fun chooserCallback(context: Context): IntentSender = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, BackupSharedReceiver::class.java),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ).intentSender
    }
}
