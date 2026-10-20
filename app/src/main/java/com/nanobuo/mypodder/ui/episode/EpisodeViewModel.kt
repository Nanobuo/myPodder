package com.nanobuo.mypodder.ui.episode

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.nanobuo.mypodder.data.chapters.ChapterRepository
import com.nanobuo.mypodder.data.db.Chapter
import com.nanobuo.mypodder.data.db.EpisodeItem
import com.nanobuo.mypodder.data.repo.EpisodeRepository
import com.nanobuo.mypodder.playback.PlayerConnection
import com.nanobuo.mypodder.ui.nav.EpisodeRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EpisodeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    episodes: EpisodeRepository,
    chapterRepository: ChapterRepository,
    connection: PlayerConnection,
) : ViewModel() {
    /** Posizione del player, usata quando questo episodio è quello in riproduzione. */
    val livePosition = connection.positionFlow(1_000)

    val id = savedStateHandle.toRoute<EpisodeRoute>().id

    val episode: StateFlow<EpisodeItem?> = episodes.observe(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val chapters: StateFlow<List<Chapter>> = chapterRepository.observe(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { chapterRepository.ensureLoaded(id) }
    }
}
