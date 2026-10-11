package io.github.wkoller.glucosetracker.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class TextSize(val scale: Float) { SMALL(0.8f), MEDIUM(0.9f), LARGE(1.0f), EXTRA_LARGE(1.2f), LARGEST(1.4f) }

enum class ThemeChoice { SYSTEM, LIGHT, DARK }

data class Settings(
    val setupDone: Boolean = false,
    val textSize: TextSize = TextSize.LARGE,
    val maxContrast: Boolean = false,
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val showIcons: Boolean = true,
    val showHelperText: Boolean = true,
    val simpleMode: Boolean = false,
    val readAloud: Boolean = false,
    val speechRate: Float = 0.9f,
    val haptics: Boolean = true,
    val reduceMotion: Boolean = false,
    val remindersOn: Boolean = true,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val assistantEnabled: Boolean = false,
    val firstName: String = "",
    val familyName: String = "",
) {
    val hasPin: Boolean get() = pinHash != null && pinSalt != null

    /** The full name for reports, or null when no name was entered. */
    val fullName: String? get() = listOf(firstName, familyName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val setupDone = booleanPreferencesKey("setup_done")
        val textSize = stringPreferencesKey("text_size")
        val maxContrast = booleanPreferencesKey("max_contrast")
        val theme = stringPreferencesKey("theme")
        val showIcons = booleanPreferencesKey("show_icons")
        val showHelperText = booleanPreferencesKey("show_helper_text")
        val simpleMode = booleanPreferencesKey("simple_mode")
        val readAloud = booleanPreferencesKey("read_aloud")
        val speechRate = floatPreferencesKey("speech_rate")
        val haptics = booleanPreferencesKey("haptics")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val remindersOn = booleanPreferencesKey("reminders_on")
        val pinHash = stringPreferencesKey("pin_hash")
        val pinSalt = stringPreferencesKey("pin_salt")
        val assistantEnabled = booleanPreferencesKey("assistant_enabled")
        val firstName = stringPreferencesKey("first_name")
        val familyName = stringPreferencesKey("family_name")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    private fun Preferences.toSettings(): Settings {
        val defaults = Settings()
        return Settings(
            setupDone = this[Keys.setupDone] ?: defaults.setupDone,
            textSize = this[Keys.textSize]?.let { runCatching { TextSize.valueOf(it) }.getOrNull() } ?: defaults.textSize,
            maxContrast = this[Keys.maxContrast] ?: defaults.maxContrast,
            theme = this[Keys.theme]?.let { runCatching { ThemeChoice.valueOf(it) }.getOrNull() } ?: defaults.theme,
            showIcons = this[Keys.showIcons] ?: defaults.showIcons,
            showHelperText = this[Keys.showHelperText] ?: defaults.showHelperText,
            simpleMode = this[Keys.simpleMode] ?: defaults.simpleMode,
            readAloud = this[Keys.readAloud] ?: defaults.readAloud,
            speechRate = this[Keys.speechRate] ?: defaults.speechRate,
            haptics = this[Keys.haptics] ?: defaults.haptics,
            reduceMotion = this[Keys.reduceMotion] ?: defaults.reduceMotion,
            remindersOn = this[Keys.remindersOn] ?: defaults.remindersOn,
            pinHash = this[Keys.pinHash],
            pinSalt = this[Keys.pinSalt],
            assistantEnabled = this[Keys.assistantEnabled] ?: defaults.assistantEnabled,
            firstName = this[Keys.firstName] ?: defaults.firstName,
            familyName = this[Keys.familyName] ?: defaults.familyName,
        )
    }

    private fun MutablePreferences.write(s: Settings) {
        this[Keys.setupDone] = s.setupDone
        this[Keys.textSize] = s.textSize.name
        this[Keys.maxContrast] = s.maxContrast
        this[Keys.theme] = s.theme.name
        this[Keys.showIcons] = s.showIcons
        this[Keys.showHelperText] = s.showHelperText
        this[Keys.simpleMode] = s.simpleMode
        this[Keys.readAloud] = s.readAloud
        this[Keys.speechRate] = s.speechRate
        this[Keys.haptics] = s.haptics
        this[Keys.reduceMotion] = s.reduceMotion
        this[Keys.remindersOn] = s.remindersOn
        if (s.pinHash != null && s.pinSalt != null) {
            this[Keys.pinHash] = s.pinHash
            this[Keys.pinSalt] = s.pinSalt
        } else {
            remove(Keys.pinHash)
            remove(Keys.pinSalt)
        }
        this[Keys.assistantEnabled] = s.assistantEnabled
        this[Keys.firstName] = s.firstName.trim()
        this[Keys.familyName] = s.familyName.trim()
    }
}
