package com.vircas.mobile.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("settings")

data class UserSettings(
    val onboardingComplete: Boolean = false,
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val animations: Boolean = true,
    val reducedMotion: Boolean = false,
    val darkMode: Boolean = true,
    val secureRng: Boolean = true,
    val debugSeed: Long = 527L,
    val clientSeed: String = "vircas-local",
    val developerDiagnostics: Boolean = false
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_complete")
        val sound = booleanPreferencesKey("sound")
        val vibration = booleanPreferencesKey("vibration")
        val animations = booleanPreferencesKey("animations")
        val reducedMotion = booleanPreferencesKey("reduced_motion")
        val darkMode = booleanPreferencesKey("dark_mode")
        val secureRng = booleanPreferencesKey("secure_rng")
        val debugSeed = longPreferencesKey("debug_seed")
        val clientSeed = stringPreferencesKey("client_seed")
        val developerDiagnostics = booleanPreferencesKey("developer_diagnostics")
    }

    val settings: Flow<UserSettings> = context.settingsDataStore.data.map { p ->
        UserSettings(
            onboardingComplete = p[Keys.onboarding] ?: false,
            sound = p[Keys.sound] ?: true,
            vibration = p[Keys.vibration] ?: true,
            animations = p[Keys.animations] ?: true,
            reducedMotion = p[Keys.reducedMotion] ?: false,
            darkMode = p[Keys.darkMode] ?: true,
            secureRng = p[Keys.secureRng] ?: true,
            debugSeed = p[Keys.debugSeed] ?: 527L,
            clientSeed = p[Keys.clientSeed] ?: "vircas-local",
            developerDiagnostics = p[Keys.developerDiagnostics] ?: false
        )
    }

    suspend fun completeOnboarding() = context.settingsDataStore.edit { it[Keys.onboarding] = true }
    suspend fun setSound(value: Boolean) = context.settingsDataStore.edit { it[Keys.sound] = value }
    suspend fun setVibration(value: Boolean) = context.settingsDataStore.edit { it[Keys.vibration] = value }
    suspend fun setAnimations(value: Boolean) = context.settingsDataStore.edit { it[Keys.animations] = value }
    suspend fun setReducedMotion(value: Boolean) = context.settingsDataStore.edit { it[Keys.reducedMotion] = value }
    suspend fun setDarkMode(value: Boolean) = context.settingsDataStore.edit { it[Keys.darkMode] = value }
    suspend fun setSecureRng(value: Boolean) = context.settingsDataStore.edit { it[Keys.secureRng] = value }
    suspend fun setDebugSeed(value: Long) = context.settingsDataStore.edit { it[Keys.debugSeed] = value }
    suspend fun setClientSeed(value: String) = context.settingsDataStore.edit { it[Keys.clientSeed] = value.take(64) }
    suspend fun setDeveloperDiagnostics(value: Boolean) = context.settingsDataStore.edit { it[Keys.developerDiagnostics] = value }
    suspend fun reset() = context.settingsDataStore.edit { it.clear() }
}
