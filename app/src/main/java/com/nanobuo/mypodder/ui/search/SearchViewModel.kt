package com.nanobuo.mypodder.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.nanobuo.mypodder.data.discover.Countries
import com.nanobuo.mypodder.data.discover.DiscoverRepository
import com.nanobuo.mypodder.data.feed.ParsedFeed
import com.nanobuo.mypodder.data.repo.PodcastRepository
import com.nanobuo.mypodder.data.search.SearchRepository
import com.nanobuo.mypodder.data.search.SearchResult
import com.nanobuo.mypodder.data.settings.SettingsRepository
import com.nanobuo.mypodder.ui.nav.PreviewRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

/** Contenuti della scheda Cerca quando non si sta cercando nulla. */
data class DiscoverContent(
    val suggestions: List<SearchResult> = emptyList(),
    val topCharts: List<SearchResult> = emptyList(),
    val trending: List<SearchResult> = emptyList(),
    val loading: Boolean = true,
)

/** Ciò da cui dipendono i suggerimenti: se cambia, vanno ricaricati. */
private data class DiscoverKey(val country: String, val showCharts: Boolean, val interests: Set<String>, val subscriptions: Int)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val search: SearchRepository,
    private val discover: DiscoverRepository,
    private val settings: SettingsRepository,
    podcasts: PodcastRepository,
) : ViewModel() {
    private val subscriptionCount = podcasts.podcasts.map { it.size }.distinctUntilChanged()

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state = _state.asStateFlow()

    private val _discover = MutableStateFlow(DiscoverContent())
    val discoverContent = _discover.asStateFlow()

    val usesPodcastIndex = search.usesPodcastIndex

    /** Paese effettivo: quello scelto o, se automatico, quello del dispositivo. */
    val country: StateFlow<String> = settings.flow.map { it.searchCountry.ifBlank { Countries.default() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settings.state.value.searchCountry.ifBlank { Countries.default() })

    val countryIsAutomatic: StateFlow<Boolean> = settings.flow.map { it.searchCountry.isBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    init {
        // Contenuti di scoperta: ricaricati quando cambiano paese, classifica attiva o interessi.
        viewModelScope.launch {
            combine(settings.flow, subscriptionCount) { s, count ->
                DiscoverKey(s.searchCountry.ifBlank { Countries.default() }, s.showTopCharts, s.interests, count)
            }
                .distinctUntilChanged()
                .collect { loadDiscover(it.country, it.showCharts, it.interests) }
        }
        viewModelScope.launch {
            combine(_query.debounce(500).map { it.trim() }, country) { q, cc -> q to cc }
                .distinctUntilChanged()
                .collect { (q, cc) ->
                    if (q.length < 2 || looksLikeUrl(q)) {
                        _state.value = SearchState.Idle
                        return@collect
                    }
                    _state.value = SearchState.Loading
                    _state.value = runCatching { search.search(q, cc, Countries.languageOf(cc)) }
                        .fold(onSuccess = { SearchState.Results(it) }, onFailure = { SearchState.Error })
                }
        }
    }

    private suspend fun loadDiscover(country: String, showCharts: Boolean, interests: Set<String>) {
        _discover.value = _discover.value.copy(loading = true)
        val language = Countries.languageOf(country)
        val suggestions = runCatching { discover.suggestions(interests, country) }.getOrDefault(emptyList())
        val charts = if (showCharts) runCatching { search.topCharts(country) }.getOrDefault(emptyList()) else emptyList()
        val trending = runCatching { search.trending(language) }.getOrDefault(emptyList())
        _discover.value = DiscoverContent(suggestions, charts, trending, loading = false)
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    /** Codice ISO del paese, oppure stringa vuota per tornare all'automatico. */
    fun setCountry(code: String) {
        viewModelScope.launch { settings.setSearchCountry(code) }
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
