package com.nanobuo.mypodder.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nanobuo.mypodder.data.download.DownloadRepository
import com.nanobuo.mypodder.data.update.UpdateManager
import com.nanobuo.mypodder.data.repo.EpisodeRepository
import com.nanobuo.mypodder.data.settings.AppSettings
import com.nanobuo.mypodder.data.settings.SettingsRepository
import com.nanobuo.mypodder.playback.PlayerConnection
import com.nanobuo.mypodder.ui.components.EpisodeActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import com.nanobuo.mypodder.data.repo.PodcastRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val episodes: EpisodeRepository,
    private val downloads: DownloadRepository,
    private val settingsRepository: SettingsRepository,
    val player: PlayerConnection,
    val updates: UpdateManager,
    podcasts: PodcastRepository,
) : ViewModel(), EpisodeActions {

    /** true se va mostrato il benvenuto con la scelta degli argomenti. */
    val needsOnboarding: StateFlow<Boolean> = combine(settingsRepository.flow, podcasts.podcasts) { s, list ->
        !s.onboardingDone && list.isEmpty()
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, false)


    /** Nuovi episodi da segnalare all'apertura dell'app (una volta per avvio). */
    private val _newEpisodesAtStart = MutableStateFlow<Int?>(null)
    val newEpisodesAtStart: StateFlow<Int?> = _newEpisodesAtStart

    init {
        updates.checkIfDue()
        // Chi aveva già delle iscrizioni prima di questa versione non vede il benvenuto.
        viewModelScope.launch {
            val s = settingsRepository.current()
            if (!s.onboardingDone && podcasts.podcasts.first().isNotEmpty()) settingsRepository.setOnboardingDone(true)
        }
        viewModelScope.launch {
            val count = episodes.countNew()
            if (count > 0) _newEpisodesAtStart.value = count
        }
    }

    fun consumeNewEpisodesAtStart() {
        _newEpisodesAtStart.value = null
    }

    val settings: StateFlow<AppSettings?> = settingsRepository.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val nowPlaying = player.state

    /** Copertina del podcast in ascolto (non quella del capitolo, che cambia spesso). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val nowPlayingCover: StateFlow<String?> = player.state.map { it.episodeId }.distinctUntilChanged()
        .flatMapLatest { id -> id?.let { episodes.observe(it) } ?: flowOf(null) }
        .map { it?.podcastImage ?: it?.artwork }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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
