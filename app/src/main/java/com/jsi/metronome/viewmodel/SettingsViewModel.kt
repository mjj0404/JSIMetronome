package com.jsi.metronome.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jsi.metronome.data.PreferencesManager
import com.jsi.metronome.data.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)

    private val _backgroundPlayback = MutableStateFlow(true)
    val backgroundPlayback: StateFlow<Boolean> = _backgroundPlayback.asStateFlow()

    private val _usePendulum = MutableStateFlow(false)
    val usePendulum: StateFlow<Boolean> = _usePendulum.asStateFlow()

    private val _pitchPipeDuration = MutableStateFlow(1)
    val pitchPipeDuration: StateFlow<Int> = _pitchPipeDuration.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.System)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.backgroundPlayback.collect { _backgroundPlayback.value = it }
        }
        viewModelScope.launch {
            prefs.usePendulum.collect { _usePendulum.value = it }
        }
        viewModelScope.launch {
            prefs.pitchPipeDuration.collect { _pitchPipeDuration.value = it }
        }
        viewModelScope.launch {
            prefs.themeMode.collect { _themeMode.value = it }
        }
    }

    fun setBackgroundPlayback(enabled: Boolean) {
        _backgroundPlayback.value = enabled
        viewModelScope.launch { prefs.setBackgroundPlayback(enabled) }
    }

    fun setUsePendulum(enabled: Boolean) {
        _usePendulum.value = enabled
        viewModelScope.launch { prefs.setUsePendulum(enabled) }
    }

    fun setPitchPipeDuration(seconds: Int) {
        _pitchPipeDuration.value = seconds.coerceIn(1, 5)
        viewModelScope.launch { prefs.setPitchPipeDuration(seconds) }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }
}
