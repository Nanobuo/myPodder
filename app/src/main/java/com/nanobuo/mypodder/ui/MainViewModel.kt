package com.nanobuo.mypodder.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nanobuo.mypodder.data.download.DownloadRepository
import com.nanobuo.mypodder.data.repo.EpisodeRepository
import com.nanobuo.mypodder.data.settings.AppSettings
import com.nanobuo.mypodder.data.settings.SettingsRepository
import com.nanobuo.mypodder.playback.PlayerConnection
import com.nanobuo.mypodder.ui.components.EpisodeActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val episodes: EpisodeRepository,
    private val downloads: DownloadRepository,
    settingsRepository: SettingsRepository,
    val player: PlayerConnection,
) : ViewModel(), EpisodeActions {

    val settings: StateFlow<AppSettings?> = settingsRepository.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val nowPlaying = player.state

    val queueIds: StateFlow<Set<Long>> = episodes.queueIds.map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    override fun play(episodeId: Long, startMs: Long?) = player.play(episodeId, startMs)
    override fun togglePlayPause() = player.togglePlayPause()
    override fun download(episodeId: Long) {
        viewModelScope.launch { downloads.enqueue(episodeId) }
    }
    override fun deleteDownload(episodeId: Long) {
        viewModelScope.launch { downloads.delete(episodeId) }
    }
    override fun addToQueue(episodeId: Long) {
        viewModelScope.launch { episodes.addToQueue(episodeId) }
    }
    override fun playNext(episodeId: Long) {
        viewModelScope.launch { episodes.playNext(episodeId) }
    }
    override fun removeFromQueue(episodeId: Long) {
        viewModelScope.launch { episodes.removeFromQueue(episodeId) }
    }
    override fun setPlayed(episodeId: Long, played: Boolean) {
        viewModelScope.launch { episodes.setCompleted(episodeId, played) }
    }
}
