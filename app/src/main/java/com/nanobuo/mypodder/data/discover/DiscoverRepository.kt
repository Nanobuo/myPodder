package com.nanobuo.mypodder.data.discover

import com.nanobuo.mypodder.R
import com.nanobuo.mypodder.data.db.AppDatabase
import com.nanobuo.mypodder.data.search.SearchRepository
import com.nanobuo.mypodder.data.search.SearchResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/** Argomento proposto all'utente; [category] è il nome della categoria di Apple Podcasts. */
data class Topic(val category: String, val label: Int)

val Topics = listOf(
    Topic("News", R.string.topic_news),
    Topic("Society & Culture", R.string.topic_society),
    Topic("Comedy", R.string.topic_comedy),
    Topic("Technology", R.string.topic_technology),
    Topic("Science", R.string.topic_science),
    Topic("History", R.string.topic_history),
    Topic("True Crime", R.string.topic_true_crime),
    Topic("Business", R.string.topic_business),
    Topic("Education", R.string.topic_education),
    Topic("Sports", R.string.topic_sports),
    Topic("Health & Fitness", R.string.topic_health),
    Topic("Arts", R.string.topic_arts),
    Topic("Music", R.string.topic_music),
    Topic("TV & Film", R.string.topic_tv_film),
    Topic("Leisure", R.string.topic_leisure),
    Topic("Kids & Family", R.string.topic_kids),
    Topic("Fiction", R.string.topic_fiction),
    Topic("Religion & Spirituality", R.string.topic_religion),
)

/** Suggerimenti di podcast in base a iscrizioni, argomenti scelti e paese. */
@Singleton
class DiscoverRepository @Inject constructor(
    db: AppDatabase,
    private val search: SearchRepository,
) {
    private val podcastDao = db.podcastDao()

    /** Categorie più frequenti tra le iscrizioni, eventualmente completate dagli interessi. */
    suspend fun favouriteCategories(interests: Set<String>, max: Int = 3): List<String> {
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
                runCatching { search.byCategory(category, country, language, max = 20) }.getOrDefault(emptyList())
                    .filter { it.language == null || it.language.lowercase().startsWith(language) }
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
