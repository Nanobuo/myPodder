package com.nanobuo.mypodder.ui.podcast

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.nanobuo.mypodder.data.db.EpisodeItem
import com.nanobuo.mypodder.data.db.Podcast
import com.nanobuo.mypodder.data.repo.EpisodeRepository
import com.nanobuo.mypodder.data.repo.PodcastRepository
import com.nanobuo.mypodder.ui.nav.PodcastRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PodcastViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val podcasts: PodcastRepository,
    private val episodes: EpisodeRepository,
    private val appScope: CoroutineScope,
) : ViewModel() {
    private val id = savedStateHandle.toRoute<PodcastRoute>().id

    val podcast: StateFlow<Podcast?> = podcasts.observe(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val episodeList: StateFlow<List<EpisodeItem>?> = episodes.forPodcast(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    private val _error = MutableStateFlow(false)
    val error = _error.asStateFlow()

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            _error.value = runCatching { podcasts.refresh(id) }.isFailure
            _refreshing.value = false
        }
    }

    fun setAutoDownload(enabled: Boolean) {
        viewModelScope.launch { podcasts.setAutoDownload(id, enabled) }
    }

    fun unsubscribe(onDone: () -> Unit) {
        appScope.launch { podcasts.unsubscribe(id) }
        onDone()
    }

    /** Toglie l'evidenza "nuovo" agli episodi quando si lascia la schermata. */
    fun markSeen() {
        appScope.launch { episodes.clearNew(id) }
    }
}
