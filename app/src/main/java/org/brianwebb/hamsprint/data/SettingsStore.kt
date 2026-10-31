package org.brianwebb.hamsprint.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.brianwebb.hamsprint.domain.LicenseClass

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    /** Null until onboarding picks an exam. */
    val license: LicenseClass? = null,
    val shuffleAnswers: Boolean = true,
    val previewNew: Boolean = true,
    val autoAdvance: Boolean = true,
    val haptics: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
)

/** User preferences, exposed as a hot [StateFlow] so the whole UI reacts to changes. */
class SettingsStore(private val prefs: SharedPreferences) {
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun update(transform: (Settings) -> Settings) {
        val next = transform(_settings.value)
        prefs.edit {
            putString(K_LICENSE, next.license?.poolId)
            putBoolean(K_SHUFFLE, next.shuffleAnswers)
            putBoolean(K_PREVIEW, next.previewNew)
            putBoolean(K_AUTO_ADVANCE, next.autoAdvance)
            putBoolean(K_HAPTICS, next.haptics)
            putString(K_THEME, next.theme.name)
        }
        _settings.value = next
    }

    private fun read(): Settings {
        val defaults = Settings()
        return Settings(
            license = prefs.getString(K_LICENSE, null)?.let(LicenseClass::fromPoolId),
            shuffleAnswers = prefs.getBoolean(K_SHUFFLE, defaults.shuffleAnswers),
            previewNew = prefs.getBoolean(K_PREVIEW, defaults.previewNew),
            autoAdvance = prefs.getBoolean(K_AUTO_ADVANCE, defaults.autoAdvance),
            haptics = prefs.getBoolean(K_HAPTICS, defaults.haptics),
            theme = prefs.getString(K_THEME, null)
                ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: defaults.theme,
        )
    }

    private companion object {
        const val K_LICENSE = "license"
        const val K_SHUFFLE = "shuffle_answers"
        const val K_PREVIEW = "preview_new"
        const val K_AUTO_ADVANCE = "auto_advance"
        const val K_HAPTICS = "haptics"
        const val K_THEME = "theme"
    }
}
