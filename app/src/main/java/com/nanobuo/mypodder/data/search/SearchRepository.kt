package com.nanobuo.mypodder.data.search

import com.nanobuo.mypodder.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SearchResult(
    val title: String,
    val author: String?,
    val feedUrl: String,
    val imageUrl: String?,
    val description: String?,
    /** Lingua dichiarata dal feed (es. "it", "en-US"), se nota. */
    val language: String? = null,
)

/**
 * Ricerca e scoperta di podcast: Podcast Index (https://podcastindex.org) per ricerca, tendenze
 * e categorie, classifiche di Apple Podcasts per i più ascoltati di ogni paese. Senza credenziali
 * Podcast Index la ricerca usa l'iTunes Search API.
 */
@Singleton
class SearchRepository @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) {
    val usesPodcastIndex: Boolean
        get() = BuildConfig.PODCASTINDEX_KEY.isNotBlank() && BuildConfig.PODCASTINDEX_SECRET.isNotBlank()

    /**
     * Cerca podcast. Podcast Index non filtra per paese, quindi i risultati nella lingua del
     * paese scelto vengono mostrati per primi; con iTunes si usa direttamente il paese.
     */
    suspend fun search(term: String, country: String, language: String): List<SearchResult> = withContext(Dispatchers.IO) {
        if (usesPodcastIndex) {
            val url = "$PI_BASE/search/byterm".toHttpUrl().newBuilder()
                .addQueryParameter("q", term)
                .addQueryParameter("max", "40")
                .build()
            val results = piFeeds(url.toString())
            results.sortedByDescending { it.language?.lowercase()?.startsWith(language) == true }
        } else {
            itunesSearch(term, country, 40)
        }
    }

    /**
     * Podcast che citano [term] senza averlo nel titolo o nell'autore: descrizioni dei podcast
     * (iTunes, nel paese scelto) e persone, titoli e descrizioni degli episodi (Podcast Index).
     * Utile per trovare il podcast di un personaggio di cui non si conosce il nome.
     */
    suspend fun searchDescriptions(term: String, country: String, language: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val inDescriptions = async { runCatching { itunesSearch(term, country, 30, attribute = "descriptionTerm") }.getOrDefault(emptyList()) }
        val byPerson = async {
            if (!usesPodcastIndex) {
                emptyList()
            } else {
                runCatching { piByPerson(term) }.getOrDefault(emptyList())
                    .sortedByDescending { it.language?.lowercase()?.startsWith(language) == true }
            }
        }
        // Alterna le due fonti, senza doppioni.
        val a = inDescriptions.await()
        val b = byPerson.await()
        val seen = HashSet<String>()
        (0 until maxOf(a.size, b.size)).flatMap { listOfNotNull(a.getOrNull(it), b.getOrNull(it)) }
            .filter { seen.add(normalizeUrl(it.feedUrl)) }
    }

    /** Episodi in cui compare una persona, raggruppati per podcast. */
    private fun piByPerson(term: String): List<SearchResult> {
        val url = "$PI_BASE/search/byperson".toHttpUrl().newBuilder()
            .addQueryParameter("q", term)
            .addQueryParameter("max", "60")
            .build()
        return json.decodeFromString<PiPersonResponse>(piGet(url.toString())).items
            .filter { !it.feedUrl.isNullOrBlank() }
            .distinctBy { it.feedId ?: it.feedUrl }
            .map {
                SearchResult(
                    title = it.feedTitle.orEmpty(),
                    author = it.feedAuthor,
                    feedUrl = it.feedUrl!!,
                    imageUrl = it.feedImage?.takeIf(String::isNotBlank),
                    description = null,
                    language = it.feedLanguage,
                )
            }
    }

    /** Podcast di tendenza in una lingua, eventualmente limitati ad alcune categorie. */
    suspend fun trending(language: String, categories: List<String> = emptyList(), max: Int = 30): List<SearchResult> =
        withContext(Dispatchers.IO) {
            if (!usesPodcastIndex) return@withContext emptyList()
            val url = "$PI_BASE/podcasts/trending".toHttpUrl().newBuilder()
                .addQueryParameter("max", max.toString())
                .addQueryParameter("lang", language)
                .apply { if (categories.isNotEmpty()) addQueryParameter("cat", piCategories(categories)) }
                .build()
            piFeeds(url.toString())
        }

    /** Podcast per categoria anche senza Podcast Index: cerca il nome della categoria su iTunes. */
    suspend fun byCategory(category: String, country: String, language: String, max: Int): List<SearchResult> =
        if (usesPodcastIndex) trending(language, listOf(category), max) else itunesSearch(category, country, max)

    /**
     * I podcast più ascoltati in un paese secondo le classifiche di Apple Podcasts, in generale
     * o per un genere ([genreId] di Apple, es. 1318 = Tecnologia).
     */
    suspend fun topCharts(country: String, limit: Int = 10, genreId: Int? = null): List<SearchResult> = withContext(Dispatchers.IO) {
        val cc = country.lowercase()
        val ids = if (genreId != null) {
            chartIdsLegacy(cc, limit, genreId)
        } else {
            runCatching { chartIdsV2(cc, limit) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: chartIdsLegacy(cc, limit)
        }
        if (ids.isEmpty()) return@withContext emptyList()
        // La classifica non contiene l'URL del feed: lo ricava la lookup di iTunes.
        val url = "https://itunes.apple.com/lookup".toHttpUrl().newBuilder()
            .addQueryParameter("id", ids.joinToString(","))
            .addQueryParameter("country", cc)
            .addQueryParameter("entity", "podcast")
            .build()
        val byId = json.decodeFromString<ItunesResponse>(get(Request.Builder().url(url).build())).results
            .filter { !it.feedUrl.isNullOrBlank() }
            .associateBy { it.collectionId.toString() }
        ids.mapNotNull { byId[it]?.toResult() }
    }

    private fun chartIdsV2(cc: String, limit: Int): List<String> {
        val body = get(Request.Builder().url("https://rss.applemarketingtools.com/api/v2/$cc/podcasts/top/$limit/podcasts.json").build())
        return json.decodeFromString<ChartV2>(body).feed.results.map { it.id }
    }

    private fun chartIdsLegacy(cc: String, limit: Int, genreId: Int? = null): List<String> {
        val genre = genreId?.let { "genre=$it/" }.orEmpty()
        val body = get(Request.Builder().url("https://itunes.apple.com/$cc/rss/toppodcasts/limit=$limit/${genre}json").build())
        return json.decodeFromString<ChartLegacy>(body).feed.entry.mapNotNull { it.id.attributes?.id }
    }

    private fun itunesSearch(term: String, country: String, limit: Int, attribute: String? = null): List<SearchResult> {
        val url = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("media", "podcast")
            .addQueryParameter("term", term)
            .addQueryParameter("country", country.lowercase())
            .addQueryParameter("limit", limit.toString())
            .apply { if (attribute != null) addQueryParameter("attribute", attribute) }
            .build()
        return json.decodeFromString<ItunesResponse>(get(Request.Builder().url(url).build())).results
            .filter { !it.feedUrl.isNullOrBlank() }
            .map { it.toResult() }
    }

    private fun ItunesResult.toResult() = SearchResult(
        title = collectionName.orEmpty(),
        author = artistName,
        feedUrl = feedUrl!!,
        imageUrl = artworkUrl600 ?: artworkUrl100,
        description = primaryGenreName,
    )

    /** Podcast Index usa le categorie di Apple divise: "Society & Culture" diventa "Society,Culture". */
    private fun piCategories(categories: List<String>): String =
        categories.flatMap { it.split("&") }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(",")

    private fun piFeeds(url: String): List<SearchResult> {
        return json.decodeFromString<PiResponse>(piGet(url)).feeds
            .filter { !it.url.isNullOrBlank() }
            .map {
                SearchResult(
                    title = it.title.orEmpty(),
                    author = it.author,
                    feedUrl = it.url!!,
                    imageUrl = it.artwork?.takeIf(String::isNotBlank) ?: it.image?.takeIf(String::isNotBlank),
                    description = it.description,
                    language = it.language,
                )
            }
    }

    /** Richiesta autenticata a Podcast Index. */
    private fun piGet(url: String): String {
        val now = (System.currentTimeMillis() / 1000).toString()
        val key = BuildConfig.PODCASTINDEX_KEY
        val hash = MessageDigest.getInstance("SHA-1")
            .digest((key + BuildConfig.PODCASTINDEX_SECRET + now).toByteArray())
            .joinToString("") { "%02x".format(it) }
        val request = Request.Builder()
            .url(url)
            .header("X-Auth-Key", key)
            .header("X-Auth-Date", now)
            .header("Authorization", hash)
            .build()
        return get(request)
    }

    private fun get(request: Request): String =
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body.string()
        }

    @Serializable
    private data class PiPersonResponse(val items: List<PiPersonItem> = emptyList())

    @Serializable
    private data class PiPersonItem(
        val feedId: Long? = null,
        val feedUrl: String? = null,
        val feedTitle: String? = null,
        val feedAuthor: String? = null,
        val feedImage: String? = null,
        val feedLanguage: String? = null,
    )

    @Serializable
    private data class PiResponse(val feeds: List<PiFeed> = emptyList())

    @Serializable
    private data class PiFeed(
        val title: String? = null,
        val url: String? = null,
        val author: String? = null,
        val image: String? = null,
        val artwork: String? = null,
        val description: String? = null,
        val language: String? = null,
    )

    @Serializable
    private data class ItunesResponse(val results: List<ItunesResult> = emptyList())

    @Serializable
    private data class ItunesResult(
        val collectionId: Long = 0,
        val collectionName: String? = null,
        val artistName: String? = null,
        val feedUrl: String? = null,
        val artworkUrl600: String? = null,
        val artworkUrl100: String? = null,
        val primaryGenreName: String? = null,
    )

    @Serializable
    private data class ChartV2(val feed: ChartV2Feed = ChartV2Feed())

    @Serializable
    private data class ChartV2Feed(val results: List<ChartV2Item> = emptyList())

    @Serializable
    private data class ChartV2Item(val id: String)

    @Serializable
    private data class ChartLegacy(val feed: ChartLegacyFeed = ChartLegacyFeed())

    @Serializable
    private data class ChartLegacyFeed(val entry: List<ChartLegacyEntry> = emptyList())

    @Serializable
    private data class ChartLegacyEntry(val id: ChartLegacyId = ChartLegacyId())

    @Serializable
    private data class ChartLegacyId(val attributes: ChartLegacyAttributes? = null)

    @Serializable
    private data class ChartLegacyAttributes(@SerialName("im:id") val id: String? = null)

    private fun normalizeUrl(url: String) =
        url.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')

    private companion object {
        const val PI_BASE = "https://api.podcastindex.org/api/1.0"
    }
}
