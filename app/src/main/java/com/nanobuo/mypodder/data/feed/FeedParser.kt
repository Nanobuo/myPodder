package com.nanobuo.mypodder.data.feed

import android.util.Xml
import com.nanobuo.mypodder.util.parseClockToMs
import com.nanobuo.mypodder.util.parseFeedDate
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

data class ParsedChapter(val startMs: Long, val title: String, val imageUrl: String?, val url: String?)

data class ParsedEpisode(
    val guid: String,
    val title: String,
    val description: String?,
    val audioUrl: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val imageUrl: String?,
    val link: String?,
    val pubDate: Long,
    val durationMs: Long,
    val chaptersUrl: String?,
    val inlineChapters: List<ParsedChapter>,
)

data class ParsedFeed(
    val title: String,
    val author: String?,
    val description: String?,
    val imageUrl: String?,
    val link: String?,
    val episodes: List<ParsedEpisode>,
    /** Categorie iTunes principali (es. "Technology", "Society & Culture"). */
    val categories: List<String> = emptyList(),
)

class FeedParseException(message: String) : Exception(message)

/** Parser RSS 2.0 con estensioni iTunes, content:encoded, Podcasting 2.0 e Podlove Simple Chapters. */
object FeedParser {
    private const val NS_ITUNES = "http://www.itunes.com/dtds/podcast-1.0.dtd"
    private const val NS_CONTENT = "http://purl.org/rss/1.0/modules/content/"
    private const val NS_PSC = "http://podlove.org/simple-chapters"
    private const val NS_MEDIA = "http://search.yahoo.com/mrss/"

    private fun isPodcastNs(ns: String) = ns.contains("podcastindex.org/namespace")

    private class ItemBuilder {
        var guid: String? = null
        var title: String? = null
        var description: String? = null
        var contentEncoded: String? = null
        var summary: String? = null
        var audioUrl: String? = null
        var mimeType: String? = null
        var size: Long = 0
        var image: String? = null
        var link: String? = null
        var pubDate: Long = 0
        var duration: Long = 0
        var chaptersUrl: String? = null
        val chapters = mutableListOf<ParsedChapter>()

        fun build(): ParsedEpisode? {
            val url = audioUrl ?: return null
            val desc = listOfNotNull(contentEncoded, description, summary).maxByOrNull { it.length }
            return ParsedEpisode(
                guid = guid?.takeIf { it.isNotBlank() } ?: url,
                title = title?.trim().orEmpty().ifEmpty { url.substringAfterLast('/') },
                description = desc,
                audioUrl = url,
                mimeType = mimeType,
                sizeBytes = size,
                imageUrl = image,
                link = link,
                pubDate = pubDate,
                durationMs = duration,
                chaptersUrl = chaptersUrl,
                inlineChapters = chapters.sortedBy { it.startMs },
            )
        }
    }

    fun parse(input: InputStream): ParsedFeed {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(input, null)

        var title: String? = null
        var author: String? = null
        var description: String? = null
        var summary: String? = null
        var itunesImage: String? = null
        var rssImage: String? = null
        var link: String? = null
        val episodes = mutableListOf<ParsedEpisode>()

        var item: ItemBuilder? = null
        var sawChannel = false
        // Profondità dentro elementi da ignorare per i campi del canale (es. <image>, <itunes:owner>).
        var nestedDepth = 0
        var inImage = false
        val categories = mutableListOf<String>()
        var categoryDepth = 0

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val ns = parser.namespace.orEmpty()
                val name = parser.name
                val cur = item
                when {
                    ns.isEmpty() && name == "channel" -> sawChannel = true
                    ns.isEmpty() && name == "item" -> item = ItemBuilder()
                    cur != null -> handleItemTag(parser, ns, name, cur)
                    ns.isEmpty() && name == "image" -> inImage = true
                    inImage && ns.isEmpty() && name == "url" -> rssImage = parser.textOrNull()
                    ns == NS_ITUNES && name == "category" -> {
                        // Solo le categorie di primo livello, non le sottocategorie annidate.
                        if (categoryDepth == 0) parser.getAttributeValue(null, "text")?.trim()?.takeIf { it.isNotEmpty() }?.let { categories += it }
                        categoryDepth++
                    }
                    nestedDepth > 0 -> Unit
                    ns.isEmpty() && name == "title" -> title = parser.textOrNull()
                    ns.isEmpty() && name == "description" -> description = parser.textOrNull()
                    ns.isEmpty() && name == "link" -> link = parser.textOrNull()
                    ns == NS_ITUNES && name == "author" -> author = parser.textOrNull()
                    ns == NS_ITUNES && name == "summary" -> summary = parser.textOrNull()
                    ns == NS_ITUNES && name == "image" -> itunesImage = parser.getAttributeValue(null, "href")
                    ns == NS_ITUNES && name == "owner" -> nestedDepth++
                }
            } else if (event == XmlPullParser.END_TAG) {
                val ns = parser.namespace.orEmpty()
                val name = parser.name
                when {
                    ns.isEmpty() && name == "item" -> {
                        item?.build()?.let { episodes += it }
                        item = null
                    }
                    ns.isEmpty() && name == "image" && item == null -> inImage = false
                    ns == NS_ITUNES && name == "owner" -> nestedDepth--
                    ns == NS_ITUNES && name == "category" && item == null -> categoryDepth = (categoryDepth - 1).coerceAtLeast(0)
                }
            }
            event = parser.next()
        }

        if (!sawChannel || title.isNullOrBlank()) throw FeedParseException("Not a podcast RSS feed")
        return ParsedFeed(
            title = title!!.trim(),
            author = author?.trim(),
            description = listOfNotNull(description, summary).maxByOrNull { it.length },
            imageUrl = itunesImage ?: rssImage,
            categories = categories.distinct(),
            link = link,
            episodes = episodes,
        )
    }

    private fun handleItemTag(parser: XmlPullParser, ns: String, name: String, item: ItemBuilder) {
        when {
            ns.isEmpty() -> when (name) {
                "title" -> item.title = parser.textOrNull()
                "guid" -> item.guid = parser.textOrNull()
                "link" -> item.link = parser.textOrNull()
                "description" -> item.description = parser.textOrNull()
                "pubDate" -> item.pubDate = parseFeedDate(parser.textOrNull())
                "enclosure" -> {
                    val type = parser.getAttributeValue(null, "type")
                    val url = parser.getAttributeValue(null, "url")
                    // Preferisce la prima enclosure audio/video.
                    if (url != null && (item.audioUrl == null)) {
                        item.audioUrl = url.trim()
                        item.mimeType = type
                        item.size = parser.getAttributeValue(null, "length")?.trim()?.toLongOrNull() ?: 0
                    }
                }
            }
            ns == NS_CONTENT && name == "encoded" -> item.contentEncoded = parser.textOrNull()
            ns == NS_ITUNES -> when (name) {
                "summary" -> item.summary = parser.textOrNull()
                "duration" -> item.duration = parseClockToMs(parser.textOrNull()) ?: 0
                "image" -> item.image = parser.getAttributeValue(null, "href")?.trim()
                "title" -> if (item.title == null) item.title = parser.textOrNull()
            }
            ns == NS_MEDIA && name == "content" && item.audioUrl == null -> {
                val type = parser.getAttributeValue(null, "type")
                if (type == null || type.startsWith("audio")) {
                    item.audioUrl = parser.getAttributeValue(null, "url")
                    item.mimeType = type
                }
            }
            isPodcastNs(ns) && name == "chapters" -> item.chaptersUrl = parser.getAttributeValue(null, "url")?.trim()
            ns == NS_PSC && name == "chapter" -> {
                val start = parseClockToMs(parser.getAttributeValue(null, "start"))
                val title = parser.getAttributeValue(null, "title")
                if (start != null && title != null) {
                    item.chapters += ParsedChapter(
                        startMs = start,
                        title = title,
                        imageUrl = parser.getAttributeValue(null, "image"),
                        url = parser.getAttributeValue(null, "href"),
                    )
                }
            }
        }
    }

    /** Legge il testo dell'elemento corrente; null se vuoto o se contiene sotto-elementi. */
    private fun XmlPullParser.textOrNull(): String? = try {
        nextText()?.trim()?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }
}
