package com.nanobuo.mypodder.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nanobuo.mypodder.data.feed.ParsedFeed
import com.nanobuo.mypodder.data.repo.PodcastRepository
import com.nanobuo.mypodder.data.search.SearchRepository
import com.nanobuo.mypodder.data.search.SearchResult
import com.nanobuo.mypodder.ui.nav.PreviewRoute
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Results(val items: List<SearchResult>) : SearchState
    data object Error : SearchState
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val search: SearchRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state = _state.asStateFlow()

    private val _trending = MutableStateFlow<List<SearchResult>>(emptyList())
    val trending = _trending.asStateFlow()

    val usesPodcastIndex = search.usesPodcastIndex

    init {
        viewModelScope.launch { _trending.value = runCatching { search.trending() }.getOrDefault(emptyList()) }
        viewModelScope.launch {
            _query.debounce(500).map { it.trim() }.distinctUntilChanged().collect { q ->
                if (q.length < 2 || looksLikeUrl(q)) {
                    _state.value = SearchState.Idle
                    return@collect
                }
                _state.value = SearchState.Loading
                _state.value = runCatching { search.search(q) }
                    .fold(onSuccess = { SearchState.Results(it) }, onFailure = { SearchState.Error })
            }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    companion object {
        fun looksLikeUrl(text: String) = text.startsWith("http://") || text.startsWith("https://")
    }
}

sealed interface PreviewState {
    data object Loading : PreviewState
    data class Loaded(val feed: ParsedFeed) : PreviewState
    data object Error : PreviewState
}

@HiltViewModel
class PreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val podcasts: PodcastRepository,
) : ViewModel() {
    val feedUrl = savedStateHandle.toRoute<PreviewRoute>().feedUrl

    private val _state = MutableStateFlow<PreviewState>(PreviewState.Loading)
    val state = _state.asStateFlow()

    private val _subscribing = MutableStateFlow(false)
    val subscribing = _subscribing.asStateFlow()

    /** Id del podcast se l'utente è già iscritto. */
    val subscribedId: StateFlow<Long?> = podcasts.observeIdByUrl(feedUrl)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = PreviewState.Loading
            _state.value = runCatching { podcasts.fetchFeed(feedUrl) }
                .fold(onSuccess = { PreviewState.Loaded(it) }, onFailure = { PreviewState.Error })
        }
    }

    fun subscribe(onDone: (Long) -> Unit) {
        if (_subscribing.value) return
        viewModelScope.launch {
            _subscribing.value = true
            val preloaded = (_state.value as? PreviewState.Loaded)?.feed
            runCatching { podcasts.subscribe(feedUrl, preloaded) }
                .onSuccess(onDone)
                .onFailure { _state.value = PreviewState.Error }
            _subscribing.value = false
        }
    }
}
