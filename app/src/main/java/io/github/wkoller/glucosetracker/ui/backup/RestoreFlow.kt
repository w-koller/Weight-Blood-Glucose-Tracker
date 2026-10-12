package io.github.wkoller.glucosetracker.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.wkoller.glucosetracker.CrashReporter
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.backup.Backup
import io.github.wkoller.glucosetracker.backup.BackupReadResult
import io.github.wkoller.glucosetracker.ui.UiFormat
import io.github.wkoller.glucosetracker.ui.appContainer
import io.github.wkoller.glucosetracker.ui.components.BigButton
import io.github.wkoller.glucosetracker.ui.components.ButtonStyle
import io.github.wkoller.glucosetracker.ui.components.ConfirmDialog
import io.github.wkoller.glucosetracker.ui.components.LabeledField
import io.github.wkoller.glucosetracker.ui.components.MessageDialog
import io.github.wkoller.glucosetracker.ui.components.NoticeCard
import io.github.wkoller.glucosetracker.ui.components.NoticeKind
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

private sealed interface RestoreStage {
    data object Idle : RestoreStage
    data object Reading : RestoreStage
    data class AskPassword(val uri: Uri, val wrong: Boolean) : RestoreStage
    data class Confirm(val backup: Backup) : RestoreStage
    data object Restoring : RestoreStage
    data object Done : RestoreStage
    data class Failed(val message: Int) : RestoreStage
}

/**
 * Restoring from a backup file: choose the file, enter its password if it has one, check what it
 * holds, and confirm before every record on this phone is replaced. Returns the action that starts it.
 */
@Composable
fun rememberRestoreFlow(onRestored: () -> Unit): () -> Unit {
    val container = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf<RestoreStage>(RestoreStage.Idle) }

    fun handle(result: BackupReadResult, uri: Uri): RestoreStage = when (result) {
        is BackupReadResult.Ok -> RestoreStage.Confirm(result.backup)
        BackupReadResult.NeedsPassword -> RestoreStage.AskPassword(uri, wrong = false)
        BackupReadResult.WrongPassword -> RestoreStage.AskPassword(uri, wrong = true)
        BackupReadResult.NotABackup -> RestoreStage.Failed(R.string.backup_not_a_backup)
        BackupReadResult.NewerVersion -> RestoreStage.Failed(R.string.backup_newer_version)
    }

    fun read(uri: Uri, password: String?) {
        stage = RestoreStage.Reading
        scope.launch {
            stage = try {
                handle(container.backup.read(uri, password), uri)
            } catch (error: Exception) {
                CrashReporter.record(context, error)
                RestoreStage.Failed(R.string.backup_read_failed)
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) read(uri, null) }

    when (val current = stage) {
        RestoreStage.Idle -> Unit
        RestoreStage.Reading -> MessageDialog(stringResource(R.string.backup_restore_title), stringResource(R.string.backup_reading), "", {}, busy = true)
        RestoreStage.Restoring -> MessageDialog(stringResource(R.string.backup_restore_title), stringResource(R.string.backup_restoring), "", {}, busy = true)
        is RestoreStage.AskPassword -> PasswordDialog(
            wrong = current.wrong,
            onOpen = { read(current.uri, it) },
            onCancel = { stage = RestoreStage.Idle },
        )
        is RestoreStage.Confirm -> {
            val summary = current.backup.summary
            val date = UiFormat.dayLongYear(Instant.ofEpochMilli(summary.createdAt).atZone(ZoneId.systemDefault()).toLocalDate())
            ConfirmDialog(
                title = stringResource(R.string.backup_confirm_title),
                messages = listOf(
                    stringResource(
                        R.string.backup_confirm_summary,
                        date,
                        pluralStringResource(R.plurals.readings_plural, summary.readings, summary.readings),
                        pluralStringResource(R.plurals.backup_doses, summary.doses, summary.doses),
                        pluralStringResource(R.plurals.backup_weights, summary.weights, summary.weights),
                    ),
                    stringResource(R.string.backup_confirm_warning),
                ),
                confirmText = stringResource(R.string.backup_restore),
                dismissText = stringResource(R.string.cancel),
                onConfirm = {
                    stage = RestoreStage.Restoring
                    scope.launch {
                        stage = try {
                            container.backup.restore(current.backup)
                            RestoreStage.Done
                        } catch (error: Exception) {
                            CrashReporter.record(context, error)
                            RestoreStage.Failed(R.string.backup_restore_failed)
                        }
                    }
                },
                onDismiss = { stage = RestoreStage.Idle },
            )
        }
        RestoreStage.Done -> MessageDialog(
            stringResource(R.string.backup_restore_title),
            stringResource(R.string.backup_restored),
            stringResource(R.string.ok),
            {
                stage = RestoreStage.Idle
                onRestored()
            },
        )
        is RestoreStage.Failed -> MessageDialog(
            stringResource(R.string.backup_restore_title),
            stringResource(current.message),
            stringResource(R.string.ok),
            { stage = RestoreStage.Idle },
        )
    }
    return { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }
}

@Composable
private fun PasswordDialog(wrong: Boolean, onOpen: (String) -> Unit, onCancel: () -> Unit) {
    var password by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.backup_password_title), style = MaterialTheme.typography.headlineSmall)
                if (wrong) NoticeCard(NoticeKind.DANGER, stringResource(R.string.backup_password_wrong_title), stringResource(R.string.backup_password_wrong))
                LabeledField(stringResource(R.string.backup_password_label), password, { password = it }, password = true)
                BigButton(stringResource(R.string.backup_open), onClick = { onOpen(password) }, enabled = password.isNotEmpty())
                BigButton(stringResource(R.string.cancel), onClick = onCancel, style = ButtonStyle.SECONDARY)
            }
        }
    }
}
