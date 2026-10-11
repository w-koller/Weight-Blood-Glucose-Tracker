package io.github.wkoller.glucosetracker.ui.assistant

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.ai.ModelManager
import io.github.wkoller.glucosetracker.ai.ModelState
import io.github.wkoller.glucosetracker.data.prefs.Settings
import io.github.wkoller.glucosetracker.domain.readout.ReadoutPeriod
import io.github.wkoller.glucosetracker.ui.appContainer
import io.github.wkoller.glucosetracker.ui.appViewModel
import io.github.wkoller.glucosetracker.ui.components.BigButton
import io.github.wkoller.glucosetracker.ui.components.ButtonStyle
import io.github.wkoller.glucosetracker.ui.components.ConfirmDialog
import io.github.wkoller.glucosetracker.ui.components.LabeledField
import io.github.wkoller.glucosetracker.ui.components.NoticeCard
import io.github.wkoller.glucosetracker.ui.components.NoticeKind
import io.github.wkoller.glucosetracker.ui.components.OptionRow
import io.github.wkoller.glucosetracker.ui.components.ScreenScaffold
import io.github.wkoller.glucosetracker.ui.components.SectionCard
import io.github.wkoller.glucosetracker.ui.components.SpeakButton
import io.github.wkoller.glucosetracker.ui.components.ToggleRow
import io.github.wkoller.glucosetracker.ui.components.rememberDownloadStarter
import io.github.wkoller.glucosetracker.ui.components.rememberMobileDataSwitch
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun AssistantScreen(onBack: () -> Unit, onSetUp: () -> Unit, onCheckIn: () -> Unit) {
    val vm = appViewModel { container, _ -> AssistantViewModel(container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ScreenScaffold(title = stringResource(R.string.assistant_title), onBack = onBack) {

        val facts = ui.context?.facts(ui.period)
        SectionCard(stringResource(R.string.assistant_readout_title)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OptionRow(stringResource(R.string.assistant_today), null, ui.period == ReadoutPeriod.TODAY, { vm.setPeriod(ReadoutPeriod.TODAY) }, modifier = Modifier.weight(1f))
                OptionRow(stringResource(R.string.assistant_week), null, ui.period == ReadoutPeriod.WEEK, { vm.setPeriod(ReadoutPeriod.WEEK) }, modifier = Modifier.weight(1f))
            }
            if (facts == null) {
                Text(stringResource(R.string.assistant_no_plan), style = MaterialTheme.typography.bodyLarge)
            } else {
                val sentences = ReadoutText.sentences(context, facts)
                sentences.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                SpeakButton(sentences.joinToString(" "))
                if (ui.modelReady) {
                    val summary = ui.summary
                    if (summary == null) {
                        BigButton(stringResource(R.string.assistant_summarise), onClick = vm::summarise, icon = R.drawable.ic_forum, style = ButtonStyle.SECONDARY)
                    } else {
                        AnswerCard(summary, onCheckIn, onStop = vm::stop)
                    }
                }
            }
        }

        if (!ui.modelReady) {
            NoticeCard(NoticeKind.INFO, stringResource(R.string.assistant_not_set_up_title), stringResource(R.string.assistant_not_set_up)) {
                BigButton(stringResource(R.string.assistant_set_up), onClick = onSetUp, icon = R.drawable.ic_download)
            }
            return@ScreenScaffold
        }

        SectionCard(stringResource(R.string.assistant_questions_title)) {
            listOf(R.string.assistant_suggest_week, R.string.assistant_suggest_highest, R.string.assistant_suggest_weight, R.string.assistant_suggest_bgl)
                .forEach { res ->
                    val text = stringResource(res)
                    BigButton(text, onClick = { vm.ask(text) }, style = ButtonStyle.SECONDARY, enabled = !ui.busy)
                }
        }
        ui.exchanges.forEach { exchange ->
            SectionCard(title = null) {
                Text(stringResource(R.string.assistant_you_asked), style = MaterialTheme.typography.titleSmall)
                Text(exchange.question, style = MaterialTheme.typography.titleMedium)
                AnswerCard(exchange, onCheckIn, onStop = vm::stop)
            }
        }
        QuestionBox(ui.question, vm::setQuestion, onAsk = { vm.ask() }, busy = ui.busy)
    }
}

@Composable
private fun AnswerCard(exchange: Exchange, onCheckIn: () -> Unit, onStop: () -> Unit) {
    when (exchange.kind) {
        AnswerKind.DOSE -> NoticeCard(NoticeKind.INFO, stringResource(R.string.assistant_dose_title), stringResource(R.string.assistant_dose_answer)) {
            BigButton(stringResource(R.string.home_check_bgl), onClick = onCheckIn, icon = R.drawable.ic_water_drop)
        }
        AnswerKind.URGENT -> NoticeCard(NoticeKind.DANGER, stringResource(R.string.assistant_urgent_title), stringResource(R.string.assistant_urgent_answer))
        AnswerKind.FAILED -> NoticeCard(NoticeKind.WARNING, stringResource(R.string.assistant_failed_title), stringResource(R.string.assistant_failed))
        AnswerKind.MODEL -> {
            if (exchange.loadingModel) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.assistant_loading), style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (exchange.answer.isNotBlank()) {
                Text(exchange.answer, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Text(stringResource(R.string.assistant_written_by_model), style = MaterialTheme.typography.bodySmall)
            }
            if (exchange.streaming) {
                BigButton(stringResource(R.string.assistant_stop), onClick = onStop, icon = R.drawable.ic_stop_circle, style = ButtonStyle.SECONDARY)
            } else if (exchange.answer.isNotBlank()) {
                SpeakButton(exchange.answer)
            }
        }
    }
}

@Composable
private fun QuestionBox(question: String, onChange: (String) -> Unit, onAsk: () -> Unit, busy: Boolean) {
    val context = LocalContext.current
    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    }
    val canSpeak = remember { speechIntent.resolveActivity(context.packageManager) != null }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onChange)
        }
    }
    LabeledField(
        label = stringResource(R.string.assistant_your_question),
        value = question,
        onValueChange = onChange,
        hint = stringResource(R.string.assistant_question_hint),
        singleLine = false,
    )
    if (canSpeak) {
        BigButton(stringResource(R.string.assistant_speak), onClick = { speech.launch(speechIntent) }, icon = R.drawable.ic_mic, style = ButtonStyle.SECONDARY, enabled = !busy)
    }
    BigButton(stringResource(R.string.assistant_ask), onClick = onAsk, icon = R.drawable.ic_send, enabled = !busy && question.isNotBlank())
}

@Composable
fun AssistantSettingsScreen(onBack: () -> Unit) {
    val container = appContainer()
    val models = container.models
    val state by models.state.collectAsStateWithLifecycle()
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle(Settings())
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        models.watchDownload()
        onDispose { }
    }
    val startDownload = rememberDownloadStarter(ModelManager.SIZE_TEXT) { models.startDownload(it) }
    val useMobileData = rememberMobileDataSwitch(ModelManager.SIZE_TEXT) { models.downloadWithMobileData() }

    ScreenScaffold(title = stringResource(R.string.settings_assistant), onBack = onBack) {
        Text(stringResource(R.string.assistant_setup_body, ModelManager.MODEL_NAME), style = MaterialTheme.typography.bodyLarge)
        NoticeCard(NoticeKind.INFO, stringResource(R.string.assistant_limits_title), stringResource(R.string.assistant_limits))
        when (val current = state) {
            ModelState.NotDownloaded -> {
                Text(stringResource(R.string.assistant_download_question), style = MaterialTheme.typography.bodyLarge)
                BigButton(stringResource(R.string.assistant_download), onClick = startDownload, icon = R.drawable.ic_download)
            }
            is ModelState.Downloading -> {
                val fraction = if (current.totalBytes > 0) current.downloadedBytes.toFloat() / current.totalBytes else 0f
                Text(
                    stringResource(R.string.assistant_downloading, (fraction * 100).toInt(), current.downloadedBytes / MB, current.totalBytes / MB),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().heightIn(min = 12.dp))
                Text(stringResource(R.string.assistant_downloading_body), style = MaterialTheme.typography.bodyLarge)
                BigButton(stringResource(R.string.assistant_cancel_download), onClick = models::cancelDownload, style = ButtonStyle.SECONDARY)
            }
            ModelState.WaitingForWifi -> {
                NoticeCard(NoticeKind.INFO, stringResource(R.string.assistant_waiting_wifi_title), stringResource(R.string.assistant_waiting_wifi))
                BigButton(stringResource(R.string.download_use_mobile), onClick = useMobileData, icon = R.drawable.ic_download, style = ButtonStyle.SECONDARY)
                BigButton(stringResource(R.string.assistant_cancel_download), onClick = models::cancelDownload, style = ButtonStyle.SECONDARY)
            }
            ModelState.Verifying -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.assistant_verifying), style = MaterialTheme.typography.titleMedium)
                }
            }
            ModelState.Ready -> {
                NoticeCard(NoticeKind.INFO, stringResource(R.string.assistant_ready_title), stringResource(R.string.assistant_ready, ModelManager.MODEL_NAME))
                ToggleRow(
                    title = stringResource(R.string.assistant_use),
                    description = null,
                    checked = settings.assistantEnabled,
                    onCheckedChange = { on -> scope.launch { container.settingsStore.update { it.copy(assistantEnabled = on) } } },
                )
                BigButton(stringResource(R.string.assistant_delete), onClick = { confirmDelete = true }, icon = R.drawable.ic_delete, style = ButtonStyle.SECONDARY)
            }
            is ModelState.Failed -> {
                val message = when (current.reason) {
                    ModelState.Reason.NOT_ENOUGH_SPACE -> R.string.assistant_failed_space
                    ModelState.Reason.DOWNLOAD_FAILED -> R.string.assistant_failed_download
                    ModelState.Reason.CHECK_FAILED -> R.string.assistant_failed_check
                }
                NoticeCard(NoticeKind.WARNING, stringResource(R.string.assistant_failed_setup_title), stringResource(message))
                BigButton(stringResource(R.string.try_again), onClick = startDownload, icon = R.drawable.ic_download)
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.assistant_delete_title),
            messages = listOf(stringResource(R.string.assistant_delete_body)),
            confirmText = stringResource(R.string.assistant_delete),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    container.settingsStore.update { it.copy(assistantEnabled = false) }
                    models.delete()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

private const val MB = 1024L * 1024L
