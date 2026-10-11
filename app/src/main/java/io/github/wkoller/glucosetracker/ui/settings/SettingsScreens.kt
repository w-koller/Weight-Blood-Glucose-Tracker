package io.github.wkoller.glucosetracker.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.launch

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
        BigButton(stringResource(R.string.settings_reminders), onClick = actions.reminders, icon = R.drawable.ic_notifications, helper = stringResource(R.string.settings_reminders_helper), style = ButtonStyle.SECONDARY)
        BigButton(stringResource(R.string.settings_assistant), onClick = actions.assistant, icon = R.drawable.ic_forum, helper = stringResource(R.string.settings_assistant_helper), style = ButtonStyle.SECONDARY)
        BigButton(stringResource(R.string.settings_export), onClick = actions.export, icon = R.drawable.ic_share, helper = stringResource(R.string.settings_export_helper), style = ButtonStyle.SECONDARY)
        BigButton(stringResource(R.string.settings_about), onClick = actions.about, icon = R.drawable.ic_info, helper = stringResource(R.string.settings_about_helper), style = ButtonStyle.SECONDARY)
    }
}

@Composable
fun DisplaySettingsScreen(onBack: () -> Unit) {
    val container = appContainer()
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle(Settings())
    val scope = rememberCoroutineScope()
    fun update(transform: (Settings) -> Settings) = scope.launch { container.settingsStore.update(transform) }

    ScreenScaffold(title = stringResource(R.string.settings_display), onBack = onBack) {
        Text(stringResource(R.string.display_body), style = MaterialTheme.typography.bodyLarge)
        SectionCard(stringResource(R.string.display_text_size)) {
            TextSize.entries.forEach { size ->
                OptionRow(
                    title = stringResource(
                        when (size) {
                            TextSize.LARGE -> R.string.text_size_large
                            TextSize.EXTRA_LARGE -> R.string.text_size_extra_large
                            TextSize.LARGEST -> R.string.text_size_largest
                        },
                    ),
                    description = null,
                    selected = settings.textSize == size,
                    onSelect = { update { it.copy(textSize = size) } },
                )
            }
        }
        SectionCard(stringResource(R.string.display_colours)) {
            ToggleRow(stringResource(R.string.display_max_contrast), stringResource(R.string.display_max_contrast_helper), settings.maxContrast, { on -> update { it.copy(maxContrast = on) } })
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
            ToggleRow(stringResource(R.string.display_show_icons), stringResource(R.string.display_show_icons_helper), settings.showIcons, { on -> update { it.copy(showIcons = on) } })
            ToggleRow(stringResource(R.string.display_show_helper), stringResource(R.string.display_show_helper_helper), settings.showHelperText, { on -> update { it.copy(showHelperText = on) } })
            ToggleRow(stringResource(R.string.display_reduce_motion), stringResource(R.string.display_reduce_motion_helper), settings.reduceMotion, { on -> update { it.copy(reduceMotion = on) } })
        }
        SectionCard(stringResource(R.string.display_sound_touch)) {
            ToggleRow(stringResource(R.string.display_read_aloud), stringResource(R.string.display_read_aloud_helper), settings.readAloud, { on -> update { it.copy(readAloud = on) } })
            OptionRow(stringResource(R.string.speech_slow), null, settings.speechRate < 0.85f, { update { it.copy(speechRate = 0.75f) } })
            OptionRow(stringResource(R.string.speech_normal), null, settings.speechRate >= 0.85f, { update { it.copy(speechRate = 0.95f) } })
            ToggleRow(stringResource(R.string.display_haptics), stringResource(R.string.display_haptics_helper), settings.haptics, { on -> update { it.copy(haptics = on) } })
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    ScreenScaffold(title = stringResource(R.string.settings_about), onBack = onBack) {
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
