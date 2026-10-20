package com.nanobuo.mypodder.data.search

import com.nanobuo.mypodder.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class SearchResult(
    val title: String,
    val author: String?,
    val feedUrl: String,
    val imageUrl: String?,
    val description: String?,
)

/**
 * Ricerca tramite Podcast Index (https://podcastindex.org). Se le credenziali non sono
 * state configurate in fase di build usa come riserva l'iTunes Search API.
 */
@Singleton
class SearchRepository @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) {
    val usesPodcastIndex: Boolean
        get() = BuildConfig.PODCASTINDEX_KEY.isNotBlank() && BuildConfig.PODCASTINDEX_SECRET.isNotBlank()

    suspend fun search(term: String): List<SearchResult> = withContext(Dispatchers.IO) {
        if (usesPodcastIndex) {
            val url = "$PI_BASE/search/byterm".toHttpUrl().newBuilder()
                .addQueryParameter("q", term)
                .addQueryParameter("max", "40")
                .build()
            piFeeds(url.toString())
        } else {
            val url = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
                .addQueryParameter("media", "podcast")
                .addQueryParameter("term", term)
                .addQueryParameter("limit", "40")
                .build()
            val body = get(Request.Builder().url(url).build())
            json.decodeFromString<ItunesResponse>(body).results
                .filter { !it.feedUrl.isNullOrBlank() }
                .map {
                    SearchResult(
                        title = it.collectionName.orEmpty(),
                        author = it.artistName,
                        feedUrl = it.feedUrl!!,
                        imageUrl = it.artworkUrl600 ?: it.artworkUrl100,
                        description = it.primaryGenreName,
                    )
                }
        }
    }

    /** Podcast di tendenza nella lingua del dispositivo (solo Podcast Index). */
    suspend fun trending(): List<SearchResult> = withContext(Dispatchers.IO) {
        if (!usesPodcastIndex) return@withContext emptyList()
        val url = "$PI_BASE/podcasts/trending".toHttpUrl().newBuilder()
            .addQueryParameter("max", "30")
            .addQueryParameter("lang", Locale.getDefault().language)
            .build()
        piFeeds(url.toString())
    }

    private fun piFeeds(url: String): List<SearchResult> {
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
        return json.decodeFromString<PiResponse>(get(request)).feeds
            .filter { !it.url.isNullOrBlank() }
            .map {
                SearchResult(
                    title = it.title.orEmpty(),
                    author = it.author,
                    feedUrl = it.url!!,
                    imageUrl = it.artwork?.takeIf(String::isNotBlank) ?: it.image?.takeIf(String::isNotBlank),
                    description = it.description,
                )
            }
    }

    private fun get(request: Request): String =
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body.string()
        }

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
    )

    @Serializable
    private data class ItunesResponse(val results: List<ItunesResult> = emptyList())

    @Serializable
    private data class ItunesResult(
        val collectionName: String? = null,
        val artistName: String? = null,
        val feedUrl: String? = null,
        val artworkUrl600: String? = null,
        val artworkUrl100: String? = null,
        val primaryGenreName: String? = null,
    )

    private companion object {
        const val PI_BASE = "https://api.podcastindex.org/api/1.0"
    }
}
