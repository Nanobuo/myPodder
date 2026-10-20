package com.nanobuo.mypodder.data.chapters

import com.nanobuo.mypodder.data.feed.ParsedChapter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.charset.Charset

/** Formato JSON dei capitoli Podcasting 2.0. */
object JsonChapterParser {
    @Serializable
    private data class JsonChapters(val chapters: List<JsonChapter> = emptyList())

    @Serializable
    private data class JsonChapter(
        val startTime: Double = 0.0,
        val title: String? = null,
        val img: String? = null,
        val url: String? = null,
        val toc: Boolean = true,
    )

    fun parse(json: Json, text: String): List<ParsedChapter> =
        json.decodeFromString<JsonChapters>(text).chapters
            .filter { it.toc }
            .map {
                ParsedChapter(
                    startMs = (it.startTime * 1000).toLong(),
                    title = it.title.orEmpty(),
                    imageUrl = it.img,
                    url = it.url,
                )
            }
            .sortedBy { it.startMs }
}

/**
 * Lettore dei frame CHAP di un tag ID3v2.3/2.4. Le immagini incorporate (APIC) vengono
 * restituite come byte e salvate dal chiamante.
 */
object Id3ChapterParser {
    class Id3Chapter(
        val startMs: Long,
        val title: String?,
        val url: String?,
        val image: ByteArray?,
    )

    /** Dimensione totale del tag (header incluso) dai primi 10 byte, o null se non c'è un tag ID3. */
    fun tagSize(header: ByteArray): Int? {
        if (header.size < 10 || header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
            return null
        }
        return syncSafe(header, 6) + 10
    }

    fun parse(data: ByteArray): List<Id3Chapter> {
        val size = tagSize(data) ?: return emptyList()
        val version = data[3].toInt()
        if (version < 3) return emptyList()
        val flags = data[5].toInt()
        var pos = 10
        if (flags and 0x40 != 0) {
            // Header esteso: in v2.4 la dimensione include sé stessa ed è syncsafe.
            pos += if (version == 4) syncSafe(data, pos) else int32(data, pos) + 4
        }
        val end = minOf(size, data.size)
        val result = mutableListOf<Id3Chapter>()
        forEachFrame(data, pos, end, version) { id, start, len ->
            if (id == "CHAP") parseChap(data, start, start + len, version)?.let { result += it }
        }
        return result.sortedBy { it.startMs }
    }

    private inline fun forEachFrame(
        data: ByteArray, from: Int, to: Int, version: Int,
        block: (id: String, start: Int, length: Int) -> Unit,
    ) {
        var pos = from
        while (pos + 10 <= to) {
            if (data[pos].toInt() == 0) break // padding
            val id = String(data, pos, 4, Charsets.ISO_8859_1)
            val len = if (version == 4) syncSafe(data, pos + 4) else int32(data, pos + 4)
            val start = pos + 10
            if (len <= 0 || start + len > to) break
            block(id, start, len)
            pos = start + len
        }
    }

    private fun parseChap(data: ByteArray, from: Int, to: Int, version: Int): Id3Chapter? {
        var pos = from
        while (pos < to && data[pos].toInt() != 0) pos++ // element ID
        pos++
        if (pos + 16 > to) return null
        val startMs = int32(data, pos).toLong() and 0xFFFFFFFFL
        pos += 16
        var title: String? = null
        var url: String? = null
        var image: ByteArray? = null
        forEachFrame(data, pos, to, version) { id, s, len ->
            when (id) {
                "TIT2" -> title = decodeText(data, s, len)
                "WXXX" -> url = decodeWxxx(data, s, len)
                "APIC" -> image = decodeApic(data, s, len)
            }
        }
        return Id3Chapter(startMs, title, url, image)
    }

    private fun charset(enc: Int): Charset = when (enc) {
        1 -> Charsets.UTF_16
        2 -> Charsets.UTF_16BE
        3 -> Charsets.UTF_8
        else -> Charsets.ISO_8859_1
    }

    private fun decodeText(data: ByteArray, start: Int, len: Int): String? {
        if (len < 2) return null
        val enc = data[start].toInt()
        return String(data, start + 1, len - 1, charset(enc)).trim('\u0000', ' ').ifEmpty { null }
    }

    /** Fine di una stringa terminata: per UTF-16 il terminatore è di due byte. */
    private fun terminator(data: ByteArray, from: Int, to: Int, enc: Int): Int {
        var p = from
        if (enc == 1 || enc == 2) {
            while (p + 1 < to && !(data[p].toInt() == 0 && data[p + 1].toInt() == 0)) p += 2
            return minOf(p + 2, to)
        }
        while (p < to && data[p].toInt() != 0) p++
        return minOf(p + 1, to)
    }

    private fun decodeWxxx(data: ByteArray, start: Int, len: Int): String? {
        val end = start + len
        val enc = data[start].toInt()
        val urlStart = terminator(data, start + 1, end, enc)
        return String(data, urlStart, end - urlStart, Charsets.ISO_8859_1).trim('\u0000', ' ').ifEmpty { null }
    }

    private fun decodeApic(data: ByteArray, start: Int, len: Int): ByteArray? {
        val end = start + len
        val enc = data[start].toInt()
        var p = start + 1
        while (p < end && data[p].toInt() != 0) p++ // MIME type
        p += 2 // terminatore + tipo immagine
        p = terminator(data, p, end, enc) // descrizione
        return if (p < end) data.copyOfRange(p, end) else null
    }

    private fun syncSafe(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0x7F shl 21) or (b[o + 1].toInt() and 0x7F shl 14) or
            (b[o + 2].toInt() and 0x7F shl 7) or (b[o + 3].toInt() and 0x7F)

    private fun int32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF shl 24) or (b[o + 1].toInt() and 0xFF shl 16) or
            (b[o + 2].toInt() and 0xFF shl 8) or (b[o + 3].toInt() and 0xFF)
}

/** Capitoli ricavati dai timestamp (es. "12:34 Titolo") presenti nella descrizione. */
object DescriptionChapterParser {
    val TIMESTAMP = Regex("""(?<![\d:])(\d{1,2}:)?\d{1,2}:\d{2}(?![\d:])""")

    fun parse(html: String?): List<ParsedChapter> {
        if (html.isNullOrBlank()) return emptyList()
        val text = html
            .replace(Regex("(?i)<br\\s*/?>|</p>|</li>|</div>|</h\\d>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"")
        val chapters = mutableListOf<ParsedChapter>()
        for (line in text.lines()) {
            val match = TIMESTAMP.find(line) ?: continue
            val ms = timestampToMs(match.value) ?: continue
            val title = line.removeRange(match.range)
                .trim()
                .trim('-', '–', '—', ':', '|', '(', ')', '[', ']', '•', '·', ' ')
                .trim()
            chapters += ParsedChapter(ms, title.ifEmpty { match.value }, null, null)
        }
        // Accetta solo una sequenza crescente di almeno due capitoli: altrimenti sono timestamp sparsi.
        if (chapters.size < 2) return emptyList()
        if (chapters.zipWithNext().any { (a, b) -> b.startMs <= a.startMs }) return emptyList()
        return chapters
    }

    fun timestampToMs(value: String): Long? {
        val parts = value.split(":").map { it.toLongOrNull() ?: return null }
        return when (parts.size) {
            2 -> (parts[0] * 60 + parts[1]) * 1000
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
            else -> null
        }
    }
}
