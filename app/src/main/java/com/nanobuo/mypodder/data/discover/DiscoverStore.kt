package com.nanobuo.mypodder.data.discover

import android.content.Context
import com.nanobuo.mypodder.data.repo.PodcastRepository
import com.nanobuo.mypodder.data.search.SearchRepository
import com.nanobuo.mypodder.data.search.SearchResult
import com.nanobuo.mypodder.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Contenuti della scheda Cerca quando non si sta cercando nulla. */
@Serializable
data class DiscoverContent(
    val suggestions: List<SearchResult> = emptyList(),
    val topCharts: List<SearchResult> = emptyList(),
    val trending: List<SearchResult> = emptyList(),
    val loading: Boolean = true,
)

@Serializable
private data class Snapshot(val key: String, val savedAt: Long, val content: DiscoverContent)

/**
 * Tiene pronti i contenuti di scoperta: li prepara all'avvio dell'app, li salva su disco per
 * mostrarli subito alla riapertura e li aggiorna in background (al più ogni 30 minuti, o quando
 * cambiano paese, interessi, classifica o iscrizioni). Le sezioni compaiono man mano che arrivano.
 */
@Singleton
class DiscoverStore @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
    private val search: SearchRepository,
    private val discover: DiscoverRepository,
    private val settings: SettingsRepository,
    private val podcasts: PodcastRepository,
    private val appScope: CoroutineScope,
) {
    private val file = File(context.filesDir, "discover.json")
    private val _content = MutableStateFlow(DiscoverContent())
    val content: StateFlow<DiscoverContent> = _content.asStateFlow()

    private var started = false
    private var job: Job? = null
    private var currentKey: Key? = null

    private data class Key(val country: String, val showCharts: Boolean, val interests: Set<String>, val subscriptions: Int) {
        override fun toString() = "$country|$showCharts|${interests.sorted()}|$subscriptions"
    }

    /** Avvia la preparazione dei contenuti; chiamato all'avvio dell'app. */
    fun start() {
        if (started) return
        started = true
        appScope.launch {
            val cached = withContext(Dispatchers.IO) { readSnapshot() }
            if (cached != null) _content.value = cached.content.copy(loading = false)
            combine(settings.flow, podcasts.podcasts.map { it.size }.distinctUntilChanged()) { s, count ->
                Key(s.searchCountry.ifBlank { Countries.default() }, s.showTopCharts, s.interests, count)
            }.distinctUntilChanged().collect { key ->
                currentKey = key
                val fresh = cached != null && cached.key == key.toString() && System.currentTimeMillis() - cached.savedAt < MAX_AGE_MS
                if (!fresh) load(key)
            }
        }
    }

    /** Aggiorna subito (es. dopo un trascinamento verso il basso). */
    fun refresh() {
        currentKey?.let(::load)
    }

    private fun load(key: Key) {
        job?.cancel()
        job = appScope.launch {
            // Se è cambiato il paese, i vecchi contenuti non valgono più.
            val previous = _content.value
            val sameCountry = readCountry() == key.country
            _content.value = if (sameCountry) previous.copy(loading = true) else DiscoverContent(loading = true)
            saveCountry(key.country)
            coroutineScope {
                val language = Countries.languageOf(key.country)
                val suggestions = async {
                    runCatching { discover.suggestions(key.interests, key.country) }.getOrNull()
                        ?.also { list -> _content.update { it.copy(suggestions = list) } }
                }
                val charts = async {
                    if (!key.showCharts) {
                        _content.update { it.copy(topCharts = emptyList()) }
                    } else {
                        runCatching { search.topCharts(key.country) }.getOrNull()
                            ?.also { list -> _content.update { it.copy(topCharts = list) } }
                    }
                }
                val trending = async {
                    runCatching { search.trending(language) }.getOrNull()
                        ?.also { list -> _content.update { it.copy(trending = list) } }
                }
                suggestions.await(); charts.await(); trending.await()
            }
            _content.update { it.copy(loading = false) }
            withContext(Dispatchers.IO) { writeSnapshot(Snapshot(key.toString(), System.currentTimeMillis(), _content.value)) }
        }
    }

    private var lastCountry: String? = null
    private fun readCountry() = lastCountry ?: readSnapshot()?.key?.substringBefore('|')
    private fun saveCountry(country: String) {
        lastCountry = country
    }

    private fun readSnapshot(): Snapshot? =
        runCatching { if (file.exists()) json.decodeFromString<Snapshot>(file.readText()) else null }.getOrNull()

    private fun writeSnapshot(snapshot: Snapshot) {
        runCatching { file.writeText(json.encodeToString(Snapshot.serializer(), snapshot)) }
    }

    private companion object {
        const val MAX_AGE_MS = 30 * 60 * 1000L
    }
}
