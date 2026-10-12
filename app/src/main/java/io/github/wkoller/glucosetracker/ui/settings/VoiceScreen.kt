package io.github.wkoller.glucosetracker.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.data.prefs.Settings
import io.github.wkoller.glucosetracker.tts.NaturalSpeaker
import io.github.wkoller.glucosetracker.tts.NaturalVoiceState
import io.github.wkoller.glucosetracker.tts.NaturalVoices
import io.github.wkoller.glucosetracker.tts.PhoneVoice
import io.github.wkoller.glucosetracker.tts.VoiceChoice
import io.github.wkoller.glucosetracker.ui.appContainer
import io.github.wkoller.glucosetracker.ui.components.BigButton
import io.github.wkoller.glucosetracker.ui.components.ButtonStyle
import io.github.wkoller.glucosetracker.ui.components.ConfirmDialog
import io.github.wkoller.glucosetracker.ui.components.NoticeCard
import io.github.wkoller.glucosetracker.ui.components.NoticeKind
import io.github.wkoller.glucosetracker.ui.components.OptionRow
import io.github.wkoller.glucosetracker.ui.components.ScreenScaffold
import io.github.wkoller.glucosetracker.ui.components.SectionCard
import io.github.wkoller.glucosetracker.ui.components.rememberDownloadStarter
import io.github.wkoller.glucosetracker.ui.components.rememberMobileDataSwitch
import kotlinx.coroutines.launch
import java.util.Locale

/** Choosing a voice plays a short sample in that voice straight away. */
@Composable
fun VoiceScreen(onBack: () -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle(Settings())
    val natural = container.naturalVoices
    val naturalState by natural.state.collectAsStateWithLifecycle()
    val preparing by natural.preparing.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var phoneVoices by remember { mutableStateOf<List<PhoneVoice>?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val sample = stringResource(R.string.speech_sample)
    val current = VoiceChoice.decode(settings.voice)

    val startDownload = rememberDownloadStarter(NaturalVoices.SIZE_TEXT) { natural.start(it) }
    val useMobileData = rememberMobileDataSwitch(NaturalVoices.SIZE_TEXT) { natural.downloadWithMobileData() }

    LaunchedEffect(Unit) { phoneVoices = container.speaker.phoneVoices() }
    DisposableEffect(Unit) {
        natural.watch()
        onDispose { container.speaker.stop() }
    }

    fun choose(choice: VoiceChoice) {
        scope.launch { container.settingsStore.update { it.copy(voice = choice.encode()) } }
        container.speaker.speak(sample, settings.speechRate, choice)
    }

    ScreenScaffold(title = stringResource(R.string.voice_title), onBack = onBack) {
        SectionCard(stringResource(R.string.voice_phone_title)) {
            OptionRow(stringResource(R.string.voice_phone_default), null, current == VoiceChoice.PhoneDefault, { choose(VoiceChoice.PhoneDefault) })
            val voices = phoneVoices
            if (voices != null) {
                voices.forEach { voice ->
                    OptionRow(phoneVoiceLabel(voice, voices), null, current == VoiceChoice.Phone(voice.name), { choose(VoiceChoice.Phone(voice.name)) })
                }
                if (voices.isEmpty()) Text(stringResource(R.string.voice_phone_none), style = MaterialTheme.typography.bodyLarge)
            }
            BigButton(
                stringResource(R.string.voice_phone_settings),
                onClick = {
                    try {
                        context.startActivity(Intent(TTS_SETTINGS))
                    } catch (_: ActivityNotFoundException) {
                        context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                    }
                },
                icon = R.drawable.ic_settings,
                style = ButtonStyle.SECONDARY,
            )
        }

        SectionCard(stringResource(R.string.voice_natural_title)) {
            when (val state = naturalState) {
                NaturalVoiceState.NotInstalled -> {
                    Text(stringResource(R.string.voice_natural_body), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.voice_natural_download_question), style = MaterialTheme.typography.bodyLarge)
                    BigButton(stringResource(R.string.voice_natural_download), onClick = startDownload, icon = R.drawable.ic_download)
                }
                is NaturalVoiceState.Downloading -> {
                    val fraction = if (state.totalBytes > 0) state.downloadedBytes.toFloat() / state.totalBytes else 0f
                    Text(
                        stringResource(R.string.assistant_downloading, (fraction * 100).toInt(), state.downloadedBytes / MB, state.totalBytes / MB),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().heightIn(min = 12.dp))
                    Text(stringResource(R.string.voice_natural_downloading_body), style = MaterialTheme.typography.bodyLarge)
                    BigButton(stringResource(R.string.assistant_cancel_download), onClick = natural::cancel, style = ButtonStyle.SECONDARY)
                }
                NaturalVoiceState.WaitingForWifi -> {
                    NoticeCard(NoticeKind.INFO, stringResource(R.string.assistant_waiting_wifi_title), stringResource(R.string.assistant_waiting_wifi))
                    BigButton(stringResource(R.string.download_use_mobile), onClick = useMobileData, icon = R.drawable.ic_download, style = ButtonStyle.SECONDARY)
                    BigButton(stringResource(R.string.assistant_cancel_download), onClick = natural::cancel, style = ButtonStyle.SECONDARY)
                }
                NaturalVoiceState.Installing -> Busy(stringResource(R.string.voice_natural_installing))
                NaturalVoiceState.Ready -> {
                    if (preparing) Busy(stringResource(R.string.voice_natural_preparing))
                    NaturalVoices.speakers.forEach { speaker ->
                        OptionRow(
                            title = speaker.name,
                            description = stringResource(accentRes(speaker)),
                            selected = current == VoiceChoice.Natural(speaker.id),
                            onSelect = { choose(VoiceChoice.Natural(speaker.id)) },
                        )
                    }
                    BigButton(stringResource(R.string.voice_natural_delete), onClick = { confirmDelete = true }, icon = R.drawable.ic_delete, style = ButtonStyle.SECONDARY)
                }
                is NaturalVoiceState.Failed -> {
                    val message = when (state.reason) {
                        NaturalVoiceState.Reason.NOT_ENOUGH_SPACE -> R.string.voice_natural_failed_space
                        NaturalVoiceState.Reason.DOWNLOAD_FAILED -> R.string.assistant_failed_download
                        NaturalVoiceState.Reason.CHECK_FAILED -> R.string.assistant_failed_check
                        NaturalVoiceState.Reason.INSTALL_FAILED -> R.string.voice_natural_failed_install
                    }
                    NoticeCard(NoticeKind.WARNING, stringResource(R.string.voice_natural_failed_title), stringResource(message))
                    BigButton(stringResource(R.string.try_again), onClick = startDownload, icon = R.drawable.ic_download)
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.voice_natural_delete_title),
            messages = listOf(stringResource(R.string.voice_natural_delete_body)),
            confirmText = stringResource(R.string.voice_natural_delete),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    if (current is VoiceChoice.Natural) container.settingsStore.update { it.copy(voice = VoiceChoice.PhoneDefault.encode()) }
                    natural.delete()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun Busy(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

/** "English (Australia)", or "English (Australia) 2" when the phone has more than one voice for that place. */
@Composable
private fun phoneVoiceLabel(voice: PhoneVoice, all: List<PhoneVoice>): String {
    val display = LocalConfiguration.current.locales[0]
    val name = voice.locale.getDisplayName(display)
    val sameLocale = all.filter { it.locale == voice.locale }
    return if (sameLocale.size == 1) name else stringResource(R.string.voice_phone_numbered, name, sameLocale.indexOf(voice) + 1)
}

private fun accentRes(speaker: NaturalSpeaker): Int = if (speaker.british) R.string.voice_accent_british else R.string.voice_accent_american

/** The name of the current voice, for the button that opens this screen. */
@Composable
fun currentVoiceLabel(encoded: String): String = when (val choice = VoiceChoice.decode(encoded)) {
    VoiceChoice.PhoneDefault -> stringResource(R.string.voice_phone_default)
    is VoiceChoice.Phone -> stringResource(R.string.voice_phone_other)
    is VoiceChoice.Natural -> NaturalVoices.speakers.firstOrNull { it.id == choice.speakerId }
        ?.let { stringResource(R.string.voice_natural_label, it.name, stringResource(accentRes(it))) }
        ?: stringResource(R.string.voice_phone_default)
}

private const val TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"
private const val MB = 1024L * 1024L
