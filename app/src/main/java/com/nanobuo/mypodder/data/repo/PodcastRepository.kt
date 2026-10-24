package com.nanobuo.mypodder.data.repo

import androidx.room.withTransaction
import com.nanobuo.mypodder.data.db.AppDatabase
import com.nanobuo.mypodder.data.db.Chapter
import com.nanobuo.mypodder.data.db.Episode
import com.nanobuo.mypodder.data.db.Podcast
import com.nanobuo.mypodder.data.download.DownloadRepository
import com.nanobuo.mypodder.data.feed.FeedParser
import com.nanobuo.mypodder.data.feed.ParsedEpisode
import com.nanobuo.mypodder.data.feed.ParsedFeed
import com.nanobuo.mypodder.data.opml.Opml
import com.nanobuo.mypodder.data.opml.OpmlFeed
import com.nanobuo.mypodder.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastRepository @Inject constructor(
    private val db: AppDatabase,
    private val client: OkHttpClient,
    private val settings: SettingsRepository,
    private val downloads: DownloadRepository,
) {
    private val podcastDao = db.podcastDao()
    private val episodeDao = db.episodeDao()
    private val queueDao = db.queueDao()
    private val chapterDao = db.chapterDao()

    val podcasts = podcastDao.observeAll()
    val podcastsWithNew = podcastDao.observeAllWithNew()

    fun observe(id: Long) = podcastDao.observe(id)
    fun observeIdByUrl(url: String) = podcastDao.observeIdByUrl(url)

    suspend fun fetchFeed(url: String): ParsedFeed = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            FeedParser.parse(response.body.byteStream())
        }
    }

    /** Si iscrive al feed (se non già presente) e restituisce l'id del podcast. */
    suspend fun subscribe(feedUrl: String, preloaded: ParsedFeed? = null): Long {
        podcastDao.getByUrl(feedUrl)?.let { return it.id }
        val feed = preloaded ?: fetchFeed(feedUrl)
        return db.withTransaction {
            val id = podcastDao.insert(
                Podcast(
                    feedUrl = feedUrl,
                    title = feed.title,
                    author = feed.author,
                    description = feed.description,
                    imageUrl = feed.imageUrl,
                    link = feed.link,
                    categories = feed.categories.joinToString("|").ifEmpty { null },
                    lastRefreshed = System.currentTimeMillis(),
                )
            )
            insertEpisodes(id, feed.episodes, markNew = false)
            id
        }
    }

    /** Legge dal feed le categorie dei podcast che non le hanno ancora (max 4 feed in parallelo). */
    suspend fun fillMissingCategories() = coroutineScope {
        val missing = podcastDao.getAll().filter { it.categories == null }
        if (missing.isEmpty()) return@coroutineScope
        val gate = Semaphore(4)
        missing.map { p ->
            async {
                gate.withPermit {
                    runCatching {
                        val categories = fetchFeed(p.feedUrl).categories
                        // "" segna i feed senza categorie, per non rileggerli ogni volta.
                        podcastDao.setCategories(p.id, categories.joinToString("|").ifEmpty { "" })
                    }
                }
            }
        }.awaitAll()
    }

    suspend fun unsubscribe(podcastId: Long) {
        downloads.deleteForPodcast(podcastId)
        podcastDao.delete(podcastId)
    }

    suspend fun setAutoDownload(podcastId: Long, enabled: Boolean) = podcastDao.setAutoDownload(podcastId, enabled)

    /** Aggiorna un podcast e restituisce il numero di nuovi episodi. */
    suspend fun refresh(podcastId: Long): Int {
        val podcast = podcastDao.get(podcastId) ?: return 0
        val feed = fetchFeed(podcast.feedUrl)
        val known = episodeDao.guids(podcastId).toHashSet()
        val (existing, fresh) = feed.episodes.partition { it.guid in known }
        val newIds = db.withTransaction {
            podcastDao.update(
                podcast.copy(
                    title = feed.title,
                    author = feed.author ?: podcast.author,
                    description = feed.description ?: podcast.description,
                    imageUrl = feed.imageUrl ?: podcast.imageUrl,
                    link = feed.link ?: podcast.link,
                    categories = feed.categories.joinToString("|").ifEmpty { podcast.categories },
                    lastRefreshed = System.currentTimeMillis(),
                )
            )
            existing.forEach {
                episodeDao.updateMetadata(
                    podcastId, it.guid, it.title, it.description, it.audioUrl, it.imageUrl, it.durationMs, it.chaptersUrl,
                )
            }
            insertEpisodes(podcastId, fresh, markNew = true)
        }
        if (newIds.isEmpty()) return 0

        val s = settings.current()
        if (s.addNewToQueue) queueDao.addLast(newIds.reversed()) // dal più vecchio al più recente
        if (podcast.autoDownload && s.autoDownloadCount > 0) {
            newIds.take(s.autoDownloadCount).forEach { downloads.enqueue(it) }
        }
        return newIds.size
    }

    /** Aggiorna tutti i podcast (max 4 in parallelo); restituisce i nuovi episodi trovati. */
    suspend fun refreshAll(): Int = coroutineScope {
        val gate = Semaphore(4)
        val found = podcastDao.getAll().map { p ->
            async { gate.withPermit { runCatching { refresh(p.id) }.getOrDefault(0) } }
        }.awaitAll().sum()
        settings.setLastFullRefresh(System.currentTimeMillis())
        found
    }

    /** Inserisce gli episodi e restituisce gli id dei nuovi, dal più recente. */
    private suspend fun insertEpisodes(podcastId: Long, episodes: List<ParsedEpisode>, markNew: Boolean): List<Long> {
        if (episodes.isEmpty()) return emptyList()
        val sorted = episodes.sortedByDescending { it.pubDate }
        val ids = episodeDao.insertAll(
            sorted.map {
                Episode(
                    podcastId = podcastId,
                    guid = it.guid,
                    title = it.title,
                    description = it.description,
                    audioUrl = it.audioUrl,
                    mimeType = it.mimeType,
                    imageUrl = it.imageUrl,
                    link = it.link,
                    pubDate = it.pubDate,
                    durationMs = it.durationMs,
                    sizeBytes = it.sizeBytes,
                    chaptersUrl = it.chaptersUrl,
                    // I capitoli Podlove inline sono già disponibili: non serve cercarli altrove.
                    chaptersLoaded = it.inlineChapters.isNotEmpty() && it.chaptersUrl == null,
                    isNew = markNew,
                )
            }
        )
        val inserted = mutableListOf<Long>()
        sorted.forEachIndexed { i, parsed ->
            val id = ids[i]
            if (id <= 0) return@forEachIndexed
            inserted += id
            if (parsed.inlineChapters.isNotEmpty()) {
                chapterDao.insertAll(
                    parsed.inlineChapters.map { c -> Chapter(id, c.startMs, c.title, c.imageUrl, c.url) }
                )
            }
        }
        return inserted
    }

    // --- OPML ---

    data class ImportResult(val imported: Int, val failed: Int)

    suspend fun importOpml(input: InputStream, onProgress: (done: Int, total: Int) -> Unit): ImportResult = coroutineScope {
        val feeds = withContext(Dispatchers.IO) { Opml.parse(input) }
        val gate = Semaphore(4)
        var done = 0
        onProgress(0, feeds.size)
        val results = feeds.map { feed ->
            async {
                gate.withPermit {
                    val ok = runCatching { subscribe(feed.url) }.isSuccess
                    synchronized(this@PodcastRepository) { done++ }
                    onProgress(done, feeds.size)
                    ok
                }
            }
        }.awaitAll()
        ImportResult(imported = results.count { it }, failed = results.count { !it })
    }

    suspend fun exportOpml(output: OutputStream) = withContext(Dispatchers.IO) {
        Opml.write(output, podcastDao.getAll().map { OpmlFeed(it.title, it.feedUrl) })
    }
}
