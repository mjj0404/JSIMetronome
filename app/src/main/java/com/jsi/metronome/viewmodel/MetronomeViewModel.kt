package com.jsi.metronome.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jsi.metronome.data.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class VisualStyle { DIAL, MECHANICAL }

class MetronomeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)

    private val _bpm = MutableStateFlow(120)
    val bpm: StateFlow<Int> = _bpm.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _visualStyle = MutableStateFlow(VisualStyle.DIAL)
    val visualStyle: StateFlow<VisualStyle> = _visualStyle.asStateFlow()

    private val _tickTimeMs = MutableStateFlow(0L)
    val tickTimeMs: StateFlow<Long> = _tickTimeMs.asStateFlow()

    private val _tickCount = MutableStateFlow(0L)
    val tickCount: StateFlow<Long> = _tickCount.asStateFlow()

    private val _subdivision = MutableStateFlow(1)
    val subdivision: StateFlow<Int> = _subdivision.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.bpm.collect { _bpm.value = it }
        }
        viewModelScope.launch {
            prefs.usePendulum.collect { usePendulum ->
                _visualStyle.value = if (usePendulum) VisualStyle.MECHANICAL else VisualStyle.DIAL
            }
        }
        viewModelScope.launch {
            prefs.subdivision.collect { _subdivision.value = it }
        }
    }

    fun setBpm(value: Int) {
        val clamped = value.coerceIn(30, 250)
        _bpm.value = clamped
        viewModelScope.launch { prefs.setBpm(clamped) }
    }

    fun setPlaying(playing: Boolean) {
        _isPlaying.value = playing
        if (!playing) {
            _tickTimeMs.value = 0L
            _tickCount.value = 0L
        }
    }

    fun togglePlayback() {
        val willPlay = !_isPlaying.value
        _isPlaying.value = willPlay
        if (!willPlay) {
            _tickTimeMs.value = 0L
            _tickCount.value = 0L
        }
    }

    fun setSubdivision(value: Int) {
        val clamped = value.coerceIn(1, 9)
        _subdivision.value = clamped
        viewModelScope.launch { prefs.setSubdivision(clamped) }
    }

    fun resetCount() {
        _tickCount.value = 0L
    }

    fun onTick() {
        _tickTimeMs.value = System.currentTimeMillis()
        _tickCount.value++
    }
}
