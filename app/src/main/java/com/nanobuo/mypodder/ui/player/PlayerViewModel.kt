package com.nanobuo.mypodder.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nanobuo.mypodder.data.chapters.ChapterRepository
import com.nanobuo.mypodder.data.db.Chapter
import com.nanobuo.mypodder.data.db.EpisodeItem
import com.nanobuo.mypodder.data.repo.EpisodeRepository
import com.nanobuo.mypodder.data.settings.AppSettings
import com.nanobuo.mypodder.data.settings.SettingsRepository
import com.nanobuo.mypodder.playback.PlayerConnection
import com.nanobuo.mypodder.playback.SleepState
import com.nanobuo.mypodder.playback.SleepTimer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    val connection: PlayerConnection,
    private val episodes: EpisodeRepository,
    private val chapterRepository: ChapterRepository,
    private val sleepTimer: SleepTimer,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val nowPlaying = connection.state

    private val episodeId = nowPlaying.map { it.episodeId }.distinctUntilChanged()

    val episode: StateFlow<EpisodeItem?> = episodeId
        .flatMapLatest { id -> id?.let { episodes.observe(it) } ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val chapters: StateFlow<List<Chapter>> = episodeId
        .onEach { id -> id?.let { viewModelScope.launch { chapterRepository.ensureLoaded(it) } } }
        .flatMapLatest { id -> id?.let { chapterRepository.observe(it) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val position = connection.positionFlow()

    val queue: StateFlow<List<EpisodeItem>> = episodes.queue
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> = settingsRepository.state

    val sleepState: StateFlow<SleepState> = sleepTimer.state

    /** Tempo rimanente del timer, aggiornato ogni secondo (null se non è un conto alla rovescia). */
    val sleepRemaining = flow {
        while (true) {
            emit((sleepTimer.state.value as? SleepState.Countdown)?.remainingMs)
            delay(1_000)
        }
    }

    fun togglePlayPause() = connection.togglePlayPause()
    fun seekTo(ms: Long) = connection.seekTo(ms)
    fun skipBack() = connection.skipBack()
    fun skipForward() = connection.skipForward()
    fun setSpeed(speed: Float) = connection.setSpeed(speed)

    fun playNextInQueue() {
        viewModelScope.launch { episodes.nextInQueue()?.let { connection.play(it) } }
    }

    fun toggleTimeDisplay() {
        viewModelScope.launch { settingsRepository.setShowRemaining(!settings.value.showRemaining) }
    }

    fun playFromQueue(id: Long) = connection.play(id)

    fun removeFromQueue(id: Long) {
        viewModelScope.launch { episodes.removeFromQueue(id) }
    }

    fun startSleep(minutes: Int) {
        sleepTimer.start(minutes)
        viewModelScope.launch { settingsRepository.setSleepMinutes(minutes) }
    }

    fun sleepAtEndOfEpisode() = sleepTimer.stopAtEndOfEpisode()
    fun cancelSleep() = sleepTimer.cancel()
    fun extendSleep() = sleepTimer.extend()
}
