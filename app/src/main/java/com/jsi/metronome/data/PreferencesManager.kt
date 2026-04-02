package com.jsi.metronome.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { System, Light, Dark }

class PreferencesManager(private val context: Context) {

    private object Keys {
        val BPM = intPreferencesKey("bpm")
        val BACKGROUND_PLAYBACK = booleanPreferencesKey("background_playback")
        val USE_PENDULUM = booleanPreferencesKey("use_pendulum")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PITCH_PIPE_DURATION = intPreferencesKey("pitch_pipe_duration")
        val SUBDIVISION = intPreferencesKey("subdivision")
    }

    val bpm: Flow<Int> = context.dataStore.data.map { it[Keys.BPM] ?: 120 }

    val backgroundPlayback: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.BACKGROUND_PLAYBACK] ?: true }

    val usePendulum: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.USE_PENDULUM] ?: false }

    val pitchPipeDuration: Flow<Int> =
        context.dataStore.data.map { it[Keys.PITCH_PIPE_DURATION] ?: 1 }

    val subdivision: Flow<Int> =
        context.dataStore.data.map { (it[Keys.SUBDIVISION] ?: 1).coerceIn(1, 9) }

    val themeMode: Flow<ThemeMode> =
        context.dataStore.data.map {
            val name = it[Keys.THEME_MODE]
            ThemeMode.entries.firstOrNull { mode -> mode.name == name } ?: ThemeMode.System
        }

    suspend fun setBpm(value: Int) {
        context.dataStore.edit { it[Keys.BPM] = value.coerceIn(30, 250) }
    }

    suspend fun setBackgroundPlayback(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BACKGROUND_PLAYBACK] = enabled }
    }

    suspend fun setUsePendulum(enabled: Boolean) {
        context.dataStore.edit { it[Keys.USE_PENDULUM] = enabled }
    }

    suspend fun setPitchPipeDuration(seconds: Int) {
        context.dataStore.edit { it[Keys.PITCH_PIPE_DURATION] = seconds.coerceIn(1, 5) }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setSubdivision(value: Int) {
        context.dataStore.edit { it[Keys.SUBDIVISION] = value.coerceIn(1, 9) }
    }
}
