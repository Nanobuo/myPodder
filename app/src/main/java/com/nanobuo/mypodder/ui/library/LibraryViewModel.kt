package com.nanobuo.mypodder.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nanobuo.mypodder.data.db.EpisodeItem
import com.nanobuo.mypodder.data.db.Podcast
import com.nanobuo.mypodder.data.opml.OpmlManager
import com.nanobuo.mypodder.data.repo.EpisodeRepository
import com.nanobuo.mypodder.data.repo.PodcastRepository
import com.nanobuo.mypodder.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val podcastRepository: PodcastRepository,
    episodeRepository: EpisodeRepository,
    private val opml: OpmlManager,
    private val settings: SettingsRepository,
) : ViewModel() {
    val grid: StateFlow<Boolean> = settings.flow.map { it.libraryGrid }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settings.state.value.libraryGrid)

    fun toggleLayout() {
        viewModelScope.launch { settings.setLibraryGrid(!grid.value) }
    }

    val podcasts: StateFlow<List<Podcast>?> = podcastRepository.podcasts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val latest: StateFlow<List<EpisodeItem>> = episodeRepository.latest()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    /** Numero di nuovi episodi dell'ultimo aggiornamento manuale (per lo snackbar). */
    private val _refreshResult = MutableStateFlow<Int?>(null)
    val refreshResult = _refreshResult.asStateFlow()

    val opmlStatus = opml.status

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            _refreshResult.value = podcastRepository.refreshAll()
            _refreshing.value = false
        }
    }

    fun consumeRefreshResult() {
        _refreshResult.value = null
    }

    fun importOpml(uri: Uri) = opml.import(uri)
    fun exportOpml(uri: Uri) = opml.export(uri, podcasts.value?.size ?: 0)
    fun consumeOpml() = opml.consume()
}
