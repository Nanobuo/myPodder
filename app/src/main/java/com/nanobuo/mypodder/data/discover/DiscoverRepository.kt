package com.nanobuo.mypodder.data.discover

import com.nanobuo.mypodder.R
import com.nanobuo.mypodder.data.db.AppDatabase
import com.nanobuo.mypodder.data.repo.PodcastRepository
import com.nanobuo.mypodder.data.search.SearchRepository
import com.nanobuo.mypodder.data.search.SearchResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/** Argomento proposto all'utente; [category] è il nome della categoria di Apple Podcasts. */
data class Topic(val category: String, val label: Int, val genreId: Int)

val Topics = listOf(
    Topic("News", R.string.topic_news, 1489),
    Topic("Society & Culture", R.string.topic_society, 1324),
    Topic("Comedy", R.string.topic_comedy, 1303),
    Topic("Technology", R.string.topic_technology, 1318),
    Topic("Science", R.string.topic_science, 1533),
    Topic("History", R.string.topic_history, 1487),
    Topic("True Crime", R.string.topic_true_crime, 1488),
    Topic("Business", R.string.topic_business, 1321),
    Topic("Education", R.string.topic_education, 1304),
    Topic("Sports", R.string.topic_sports, 1545),
    Topic("Health & Fitness", R.string.topic_health, 1512),
    Topic("Arts", R.string.topic_arts, 1301),
    Topic("Music", R.string.topic_music, 1310),
    Topic("TV & Film", R.string.topic_tv_film, 1309),
    Topic("Leisure", R.string.topic_leisure, 1502),
    Topic("Kids & Family", R.string.topic_kids, 1305),
    Topic("Fiction", R.string.topic_fiction, 1483),
    Topic("Religion & Spirituality", R.string.topic_religion, 1314),
)

/** Generi Apple Podcasts noti, anche con i nomi delle sottocategorie più comuni. */
private val extraGenres = mapOf("government" to 1511, "documentary" to 1324, "politics" to 1489, "tech news" to 1318)

fun genreIdOf(category: String): Int? {
    val key = category.trim().lowercase()
    return Topics.firstOrNull { it.category.lowercase() == key }?.genreId ?: extraGenres[key]
}

/** Suggerimenti di podcast in base a iscrizioni, argomenti scelti e paese. */
@Singleton
class DiscoverRepository @Inject constructor(
    db: AppDatabase,
    private val search: SearchRepository,
    private val podcasts: PodcastRepository,
) {
    private val podcastDao = db.podcastDao()

    /** Categorie più frequenti tra le iscrizioni, eventualmente completate dagli interessi. */
    suspend fun favouriteCategories(interests: Set<String>, max: Int = 3): List<String> {
        // Podcast seguiti prima che l'app salvasse le categorie: le recupera dai feed.
        runCatching { podcasts.fillMissingCategories() }
        val fromSubscriptions = podcastDao.getAll()
            .flatMap { it.categories?.split("|").orEmpty() }
            .filter { it.isNotBlank() }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .map { it.key }
        return (fromSubscriptions + interests).distinct().take(max)
    }

    /** Podcast consigliati per le categorie preferite, esclusi quelli già seguiti. */
    suspend fun suggestions(interests: Set<String>, country: String, limit: Int = 15): List<SearchResult> {
        val categories = favouriteCategories(interests)
        if (categories.isEmpty()) return emptyList()
        return forTopics(categories, country, limit)
    }

    /** Podcast locali per uno o più argomenti, alternando gli argomenti per varietà. */
    suspend fun forTopics(categories: Collection<String>, country: String, limit: Int): List<SearchResult> = coroutineScope {
        val language = Countries.languageOf(country)
        val subscribed = podcastDao.getAll().map { normalize(it.feedUrl) }.toSet()
        val perTopic = categories.map { category ->
            async {
                // Prima la classifica del genere nel paese (già locale), poi Podcast Index/iTunes.
                val charts = genreIdOf(category)?.let { id ->
                    runCatching { search.topCharts(country, limit = 25, genreId = id) }.getOrNull()
                }.orEmpty()
                charts.ifEmpty {
                    runCatching { search.byCategory(category, country, language, max = 20) }.getOrDefault(emptyList())
                        .filter { it.language == null || it.language.lowercase().startsWith(language) }
                }
            }
        }.awaitAll()
        val seen = HashSet<String>()
        val merged = mutableListOf<SearchResult>()
        var index = 0
        while (merged.size < limit && perTopic.any { index < it.size }) {
            for (list in perTopic) {
                val item = list.getOrNull(index) ?: continue
                val key = normalize(item.feedUrl)
                if (key !in subscribed && seen.add(key)) merged += item
                if (merged.size >= limit) break
            }
            index++
        }
        merged
    }

    private fun normalize(url: String) =
        url.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
}
