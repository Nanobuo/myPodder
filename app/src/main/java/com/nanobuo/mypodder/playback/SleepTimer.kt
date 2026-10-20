package com.nanobuo.mypodder.playback

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SleepState {
    data object Off : SleepState
    data class Countdown(val endsAtElapsed: Long, val durationMs: Long) : SleepState {
        val remainingMs: Long get() = (endsAtElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0)
    }
    data object EndOfEpisode : SleepState
}

/** Stato del timer di spegnimento, condiviso tra interfaccia e servizio di riproduzione. */
@Singleton
class SleepTimer @Inject constructor() {
    private val _state = MutableStateFlow<SleepState>(SleepState.Off)
    val state: StateFlow<SleepState> = _state.asStateFlow()

    fun start(minutes: Int) {
        val ms = minutes * 60_000L
        _state.value = SleepState.Countdown(SystemClock.elapsedRealtime() + ms, ms)
    }

    fun stopAtEndOfEpisode() {
        _state.value = SleepState.EndOfEpisode
    }

    fun cancel() {
        _state.value = SleepState.Off
    }

    /** Prolunga il timer della sua durata iniziale (es. scuotendo il telefono). */
    fun extend() {
        val s = _state.value as? SleepState.Countdown ?: return
        val base = maxOf(s.endsAtElapsed, SystemClock.elapsedRealtime())
        _state.value = s.copy(endsAtElapsed = base + s.durationMs)
    }
}
