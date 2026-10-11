package io.github.wkoller.glucosetracker.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import io.github.wkoller.glucosetracker.CrashReporter
import io.github.wkoller.glucosetracker.ui.UiFormat
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.glucosetracker.BuildConfig
import io.github.wkoller.glucosetracker.R
import io.github.wkoller.glucosetracker.data.prefs.Settings
import io.github.wkoller.glucosetracker.data.prefs.TextSize
import io.github.wkoller.glucosetracker.data.prefs.ThemeChoice
import io.github.wkoller.glucosetracker.ui.appContainer
import io.github.wkoller.glucosetracker.ui.components.BigButton
import io.github.wkoller.glucosetracker.ui.components.ButtonStyle
import io.github.wkoller.glucosetracker.ui.components.NoticeCard
import io.github.wkoller.glucosetracker.ui.components.NoticeKind
import io.github.wkoller.glucosetracker.ui.components.OptionRow
import io.github.wkoller.glucosetracker.ui.components.ScreenScaffold
import io.github.wkoller.glucosetracker.ui.components.SectionCard
import io.github.wkoller.glucosetracker.ui.components.ToggleRow
import io.github.wkoller.glucosetracker.ui.components.labelRes
import io.github.wkoller.glucosetracker.tts.LocalSpeaker
import kotlinx.coroutines.launch

private const val SLOW_RATE = 0.75f
private const val NORMAL_RATE = 0.95f

data class SettingsActions(
    val display: () -> Unit,
    val plan: () -> Unit,
    val reminders: () -> Unit,
    val assistant: () -> Unit,
    val export: () -> Unit,
    val about: () -> Unit,
)

@Composable
fun SettingsScreen(onBack: () -> Unit, actions: SettingsActions) {
    ScreenScaffold(title = stringResource(R.string.settings_title), onBack = onBack) {
        BigButton(stringResource(R.string.settings_display), onClick = actions.display, icon = R.drawable.ic_accessibility, helper = stringResource(R.string.settings_display_helper), style = ButtonStyle.SECONDARY)
        BigButton(stringResource(R.string.settings_plan), onClick = actions.plan, icon = R.drawable.ic_description, helper = stringResource(R.string.settings_plan_helper), style = ButtonStyle.SECONDARY)
        BigButton(stringResource(R.string.settings_reminders), onClick = actions.reminders, icon = R.drawable.ic_notifications, style = ButtonStyle.SECONDARY)
        BigButton(stringResource(R.string.settings_assistant), onClick = actions.assistant, icon = R.drawable.ic_forum, style = ButtonStyle.SECONDARY)
        BigButton(stringResource(R.string.settings_export), onClick = actions.export, icon = R.drawable.ic_share, helper = stringResource(R.string.settings_export_helper), style = ButtonStyle.SECONDARY)
        BigButton(stringResource(R.string.settings_about), onClick = actions.about, icon = R.drawable.ic_info, helper = stringResource(R.string.settings_about_helper), style = ButtonStyle.SECONDARY)
    }
}

@Composable
fun DisplaySettingsScreen(onBack: () -> Unit) {
    val container = appContainer()
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle(Settings())
    val scope = rememberCoroutineScope()
    val speaker = LocalSpeaker.current
    val sample = stringResource(R.string.speech_sample)
    fun update(transform: (Settings) -> Settings) = scope.launch { container.settingsStore.update(transform) }

    ScreenScaffold(title = stringResource(R.string.settings_display), onBack = onBack) {
        SectionCard(stringResource(R.string.display_text_size)) {
            TextSize.entries.forEach { size ->
                OptionRow(
                    title = stringResource(size.labelRes()),
                    description = null,
                    selected = settings.textSize == size,
                    onSelect = { update { it.copy(textSize = size) } },
                )
            }
        }
        SectionCard(stringResource(R.string.display_colours)) {
            ToggleRow(stringResource(R.string.display_max_contrast), null, settings.maxContrast, { on -> update { it.copy(maxContrast = on) } })
            if (!settings.maxContrast) {
                ThemeChoice.entries.forEach { choice ->
                    OptionRow(
                        title = stringResource(
                            when (choice) {
                                ThemeChoice.SYSTEM -> R.string.theme_system
                                ThemeChoice.LIGHT -> R.string.theme_light
                                ThemeChoice.DARK -> R.string.theme_dark
                            },
                        ),
                        description = null,
                        selected = settings.theme == choice,
                        onSelect = { update { it.copy(theme = choice) } },
                    )
                }
            }
        }
        SectionCard(stringResource(R.string.display_clutter)) {
            ToggleRow(stringResource(R.string.display_simple_mode), stringResource(R.string.display_simple_mode_helper), settings.simpleMode, { on -> update { it.copy(simpleMode = on) } })
            ToggleRow(stringResource(R.string.display_show_icons), null, settings.showIcons, { on -> update { it.copy(showIcons = on) } })
            ToggleRow(stringResource(R.string.display_show_helper), null, settings.showHelperText, { on -> update { it.copy(showHelperText = on) } })
            ToggleRow(stringResource(R.string.display_reduce_motion), null, settings.reduceMotion, { on -> update { it.copy(reduceMotion = on) } })
        }
        SectionCard(stringResource(R.string.display_sound_touch)) {
            // Choosing a speed, or turning reading aloud on, plays a short sample so the user can hear the result.
            fun preview(rate: Float) = speaker?.speak(sample, rate)
            ToggleRow(
                stringResource(R.string.display_read_aloud),
                stringResource(R.string.display_read_aloud_helper),
                settings.readAloud,
                { on ->
                    update { it.copy(readAloud = on) }
                    if (on) preview(settings.speechRate)
                },
            )
            OptionRow(stringResource(R.string.speech_slow), null, settings.speechRate < 0.85f, {
                update { it.copy(speechRate = SLOW_RATE) }
                preview(SLOW_RATE)
            })
            OptionRow(stringResource(R.string.speech_normal), null, settings.speechRate >= 0.85f, {
                update { it.copy(speechRate = NORMAL_RATE) }
                preview(NORMAL_RATE)
            })
            ToggleRow(stringResource(R.string.display_haptics), null, settings.haptics, { on -> update { it.copy(haptics = on) } })
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var report by remember { mutableStateOf(CrashReporter.latest(context)) }
    ScreenScaffold(title = stringResource(R.string.settings_about), onBack = onBack) {
        report?.let { file ->
            val shareTitle = stringResource(R.string.about_problem_share_title)
            NoticeCard(
                NoticeKind.WARNING,
                stringResource(R.string.about_problem_title),
                stringResource(R.string.about_problem_body, UiFormat.dayLongYear(java.time.Instant.ofEpochMilli(file.lastModified()).atZone(java.time.ZoneId.systemDefault()).toLocalDate())),
            ) {
                BigButton(stringResource(R.string.about_problem_share), onClick = {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(send, shareTitle))
                }, icon = R.drawable.ic_share)
                BigButton(stringResource(R.string.about_problem_delete), onClick = {
                    CrashReporter.delete(context)
                    report = null
                }, icon = R.drawable.ic_delete, style = ButtonStyle.SECONDARY)
            }
        }
        NoticeCard(NoticeKind.INFO, stringResource(R.string.about_disclaimer_title), stringResource(R.string.about_disclaimer))
        SectionCard(stringResource(R.string.about_data_title)) {
            Text(stringResource(R.string.about_data_body), style = MaterialTheme.typography.bodyLarge)
        }
        SectionCard(stringResource(R.string.about_low_title)) {
            Text(stringResource(R.string.about_low_body), style = MaterialTheme.typography.bodyLarge)
        }
        SectionCard(stringResource(R.string.about_version_title)) {
            Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.about_licence), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
