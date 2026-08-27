package com.vircas.mobile.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("settings")

data class UserSettings(
    val onboardingComplete: Boolean = false,
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val reducedMotion: Boolean = false
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_complete")
        val sound = booleanPreferencesKey("sound")
        val vibration = booleanPreferencesKey("vibration")
        val reducedMotion = booleanPreferencesKey("reduced_motion")
    }

    val settings: Flow<UserSettings> = context.settingsDataStore.data.map { p ->
        UserSettings(
            onboardingComplete = p[Keys.onboarding] ?: false,
            sound = p[Keys.sound] ?: true,
            vibration = p[Keys.vibration] ?: true,
            reducedMotion = p[Keys.reducedMotion] ?: false
        )
    }

    suspend fun completeOnboarding() = context.settingsDataStore.edit { it[Keys.onboarding] = true }
}
