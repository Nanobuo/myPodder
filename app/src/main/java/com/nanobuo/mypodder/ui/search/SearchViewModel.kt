package com.nanobuo.mypodder.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.nanobuo.mypodder.data.discover.Countries
import com.nanobuo.mypodder.data.discover.DiscoverStore
import com.nanobuo.mypodder.data.feed.ParsedFeed
import com.nanobuo.mypodder.data.repo.PodcastRepository
import com.nanobuo.mypodder.data.search.SearchRepository
import com.nanobuo.mypodder.data.search.SearchResult
import com.nanobuo.mypodder.data.settings.SettingsRepository
import com.nanobuo.mypodder.ui.nav.PreviewRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
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
    data class Results(
        val items: List<SearchResult>,
        /** Podcast che citano il termine nella descrizione o negli episodi. */
        val fromDescriptions: List<SearchResult> = emptyList(),
        val loadingMore: Boolean = false,
    ) : SearchState
    data object Error : SearchState
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val search: SearchRepository,
    private val store: DiscoverStore,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state = _state.asStateFlow()

    /** Consigliati, classifica e tendenze, preparati in anticipo dal [DiscoverStore]. */
    val discoverContent = store.content

    val usesPodcastIndex = search.usesPodcastIndex

    /** Paese effettivo: quello scelto o, se automatico, quello del dispositivo. */
    val country: StateFlow<String> = settings.flow.map { it.searchCountry.ifBlank { Countries.default() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settings.state.value.searchCountry.ifBlank { Countries.default() })

    val countryIsAutomatic: StateFlow<Boolean> = settings.flow.map { it.searchCountry.isBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    init {
        store.start()
        viewModelScope.launch {
            combine(_query.debounce(500).map { it.trim() }, country) { q, cc -> q to cc }
                .distinctUntilChanged()
                .collectLatest { (q, cc) ->
                    if (q.length < 2 || looksLikeUrl(q)) {
                        _state.value = SearchState.Idle
                        return@collectLatest
                    }
                    _state.value = SearchState.Loading
                    val language = Countries.languageOf(cc)
                    coroutineScope {
                        val extra = async { runCatching { search.searchDescriptions(q, cc, language) }.getOrNull() }
                        val main = runCatching { search.search(q, cc, language) }.getOrNull()
                        if (main != null) _state.value = SearchState.Results(main, loadingMore = true)
                        val more = extra.await()
                        _state.value = when {
                            main == null && more == null -> SearchState.Error
                            else -> {
                                val known = main.orEmpty().map { it.feedUrl.trimEnd('/').lowercase() }.toSet()
                                SearchState.Results(
                                    items = main.orEmpty(),
                                    fromDescriptions = more.orEmpty().filter { it.feedUrl.trimEnd('/').lowercase() !in known },
                                )
                            }
                        }
                    }
                }
        }
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
