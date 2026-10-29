package io.github.wkoller.glucosetracker.ui.backup

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.glucosetracker.CrashReporter
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.backup.BackupSharedReceiver
import io.github.wkoller.glucosetracker.data.prefs.Settings
import io.github.wkoller.glucosetracker.ui.UiFormat
import io.github.wkoller.glucosetracker.ui.appContainer
import io.github.wkoller.glucosetracker.ui.components.BigButton
import io.github.wkoller.glucosetracker.ui.components.ButtonStyle
import io.github.wkoller.glucosetracker.ui.components.LabeledField
import io.github.wkoller.glucosetracker.ui.components.NoticeCard
import io.github.wkoller.glucosetracker.ui.components.NoticeKind
import io.github.wkoller.glucosetracker.ui.components.ScreenScaffold
import io.github.wkoller.glucosetracker.ui.components.SectionCard
import io.github.wkoller.glucosetracker.ui.components.ToggleRow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle(Settings())
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var saved by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    val shareTitle = stringResource(R.string.backup_share_title)
    val restore = rememberRestoreFlow(onRestored = {})

    fun chosenPassword(): String? = password.ifEmpty { null }

    /** Checks the optional password before a file is made. */
    fun passwordOk(): Boolean {
        error = when {
            password.isEmpty() && again.isEmpty() -> null
            password.length < MIN_PASSWORD -> R.string.backup_password_short
            password != again -> R.string.backup_password_mismatch
            else -> null
        }
        saved = null
        return error == null
    }

    fun failed(throwable: Throwable) {
        CrashReporter.record(context, throwable)
        error = R.string.backup_save_failed
    }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            try {
                container.backup.saveTo(uri, chosenPassword())
                saved = R.string.backup_saved
            } catch (throwable: Exception) {
                failed(throwable)
            } finally {
                busy = false
            }
        }
    }

    ScreenScaffold(title = stringResource(R.string.backup_title), onBack = onBack) {
        SectionCard(stringResource(R.string.backup_google_title), icon = R.drawable.ic_cloud) {
            Text(stringResource(R.string.backup_google_body), style = MaterialTheme.typography.bodyLarge)
            BigButton(
                stringResource(R.string.backup_google_settings),
                onClick = { openBackupSettings(context) },
                icon = R.drawable.ic_settings,
                style = ButtonStyle.SECONDARY,
            )
        }

        SectionCard(stringResource(R.string.backup_file_title), icon = R.drawable.ic_save) {
            val last = settings.lastBackupAt
            Text(
                if (last > 0) {
                    stringResource(R.string.backup_last, UiFormat.dayLongYear(Instant.ofEpochMilli(last).atZone(ZoneId.systemDefault()).toLocalDate()))
                } else {
                    stringResource(R.string.backup_never)
                },
                style = MaterialTheme.typography.titleMedium,
            )
            LabeledField(stringResource(R.string.backup_password_optional), password, { password = it; error = null }, password = true)
            LabeledField(stringResource(R.string.backup_password_again), again, { again = it; error = null }, password = true)
            Text(
                stringResource(if (password.isEmpty()) R.string.backup_private_note else R.string.backup_password_note),
                style = MaterialTheme.typography.bodyLarge,
            )
            error?.let { NoticeCard(NoticeKind.DANGER, stringResource(R.string.setup_errors_title), stringResource(it)) }
            saved?.let { NoticeCard(NoticeKind.INFO, stringResource(it), null) }
            BigButton(
                stringResource(R.string.backup_save),
                onClick = { if (passwordOk()) saver.launch(container.backup.fileName()) },
                icon = R.drawable.ic_save,
                enabled = !busy,
            )
            BigButton(
                stringResource(R.string.backup_share),
                onClick = {
                    if (passwordOk()) {
                        busy = true
                        scope.launch {
                            try {
                                val uri = container.backup.shareFile(chosenPassword())
                                val send = Intent(Intent.ACTION_SEND)
                                    .setType("application/json")
                                    .putExtra(Intent.EXTRA_STREAM, uri)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                // The backup counts as saved once the user chooses where to send it.
                                context.startActivity(Intent.createChooser(send, shareTitle, BackupSharedReceiver.chooserCallback(context)))
                            } catch (throwable: Exception) {
                                failed(throwable)
                            } finally {
                                busy = false
                            }
                        }
                    }
                },
                icon = R.drawable.ic_share,
                style = ButtonStyle.SECONDARY,
                enabled = !busy,
            )
        }

        SectionCard(stringResource(R.string.backup_restore_title), icon = R.drawable.ic_restore) {
            BigButton(stringResource(R.string.backup_restore_from_file), onClick = restore, icon = R.drawable.ic_restore, style = ButtonStyle.SECONDARY)
        }

        ToggleRow(
            title = stringResource(R.string.backup_reminder_toggle),
            description = null,
            checked = settings.backupReminderOn,
            onCheckedChange = { on -> scope.launch { container.settingsStore.update { it.copy(backupReminderOn = on) } } },
        )
    }
}

/** Opens Google's backup settings where the phone has them, otherwise the phone's Settings. */
private fun openBackupSettings(context: android.content.Context) {
    val candidates = listOf(
        Intent().setComponent(ComponentName("com.google.android.gms", "com.google.android.gms.backup.component.BackupSettingsActivity")),
        Intent(AndroidSettings.ACTION_SETTINGS),
    )
    for (intent in candidates) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            // Try the next one.
        } catch (_: SecurityException) {
            // Try the next one.
        }
    }
}

private const val MIN_PASSWORD = 6
