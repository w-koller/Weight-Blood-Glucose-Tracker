package io.github.wkoller.glucosetracker.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.wkoller.glucosetracker.BuildConfig
import io.github.wkoller.glucosetracker.data.db.AppDatabase
import io.github.wkoller.glucosetracker.data.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** Saves, shares and restores backup files. */
class BackupService(
    private val context: Context,
    private val database: AppDatabase,
    private val settings: SettingsStore,
    private val clock: Clock,
    private val afterRestore: suspend () -> Unit,
) {
    fun fileName(): String = "weight-glucose-backup-${LocalDate.now(clock)}.json"

    suspend fun create(password: String?): ByteArray = withContext(Dispatchers.IO) {
        val backup = Backup(
            createdAt = Instant.now(clock).toEpochMilli(),
            appVersion = BuildConfig.VERSION_NAME,
            contents = database.backupDao().everything(),
            settings = settings.current(),
        )
        BackupFile.write(backup, password)
    }

    /** Writes a backup to a place the user chose in the file picker, such as Google Drive or Downloads. */
    suspend fun saveTo(uri: Uri, password: String?) {
        val bytes = create(password)
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: throw IOException("The file could not be opened")
        }
        markSaved()
    }

    /**
     * Writes a backup into the app's cache and returns a link the share sheet can send. It counts as saved only
     * once the user chooses where to send it, through [BackupSharedReceiver].
     */
    suspend fun shareFile(password: String?): Uri {
        val bytes = create(password)
        val file = withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "exports").apply { mkdirs() }
            directory.listFiles()?.forEach { it.delete() }
            File(directory, fileName()).apply { writeBytes(bytes) }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    suspend fun read(uri: Uri, password: String?): BackupReadResult = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            // A backup of several years is well under this size; anything larger is not a backup.
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                if (out.size() > MAX_BYTES) return@withContext BackupReadResult.NotABackup
            }
            out.toByteArray()
        } ?: return@withContext BackupReadResult.NotABackup
        BackupFile.read(bytes, password)
    }

    /** Replaces every record and the person's settings with those in [backup]. */
    suspend fun restore(backup: Backup) {
        database.backupDao().replaceAll(backup.contents)
        settings.update { current ->
            backup.settings.copy(
                setupDone = true,
                // Downloads belong to this phone, so the assistant stays as it is here.
                assistantEnabled = current.assistantEnabled,
                lastBackupAt = maxOf(current.lastBackupAt, backup.createdAt),
                lastBackupReminderAt = current.lastBackupReminderAt,
            )
        }
        afterRestore()
    }

    /** Records that a backup file has just been saved or sent, which puts off the backup reminder. */
    suspend fun markSaved() {
        val now = Instant.now(clock).toEpochMilli()
        settings.update { it.copy(lastBackupAt = now) }
    }

    private companion object {
        const val MAX_BYTES = 50 * 1024 * 1024
    }
}

/** When to remind the user to save a backup file. */
object BackupReminderPolicy {
    const val DAY_MS = 24L * 60 * 60 * 1000
    const val BACKUP_EVERY_MS = 30 * DAY_MS
    const val REMIND_EVERY_MS = 7 * DAY_MS

    /**
     * Due when the last backup file (or, with none, the start of the first plan) is more than 30 days
     * old, and the last reminder was more than 7 days ago.
     */
    fun isDue(now: Long, lastBackupAt: Long, firstPlanAt: Long?, lastReminderAt: Long): Boolean {
        val since = if (lastBackupAt > 0) lastBackupAt else firstPlanAt ?: return false
        return now - since > BACKUP_EVERY_MS && now - lastReminderAt > REMIND_EVERY_MS
    }
}
