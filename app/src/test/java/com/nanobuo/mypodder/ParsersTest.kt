package com.nanobuo.mypodder

import com.nanobuo.mypodder.data.chapters.DescriptionChapterParser
import com.nanobuo.mypodder.data.chapters.Id3ChapterParser
import com.nanobuo.mypodder.util.parseClockToMs
import com.nanobuo.mypodder.util.parseFeedDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class ParsersTest {
    @Test
    fun clock() {
        assertEquals(3_723_000L, parseClockToMs("1:02:03"))
        assertEquals(62_000L, parseClockToMs("1:02"))
        assertEquals(3_723_000L, parseClockToMs("3723"))
        assertEquals(1_500L, parseClockToMs("00:00:01.500"))
        assertEquals(null, parseClockToMs("abc"))
    }

    @Test
    fun feedDates() {
        assertEquals(1_212_491_130_000L, parseFeedDate("Tue, 03 Jun 2008 11:05:30 GMT"))
        assertEquals(1_212_491_130_000L, parseFeedDate("Tue, 3 Jun 2008 13:05:30 +0200"))
        assertTrue(parseFeedDate("Mon, 6 Jan 2025 08:00:00 PST") > 0)
        assertEquals(0L, parseFeedDate("not a date"))
    }

    @Test
    fun descriptionChapters() {
        val html = """
            <p>Nella puntata di oggi:</p>
            <p>00:00 Introduzione<br>05:30 - Le notizie<br>1:02:10 Saluti finali</p>
        """.trimIndent()
        val chapters = DescriptionChapterParser.parse(html)
        assertEquals(3, chapters.size)
        assertEquals(330_000L, chapters[1].startMs)
        assertEquals("Le notizie", chapters[1].title)
        assertEquals(3_730_000L, chapters[2].startMs)
    }

    @Test
    fun descriptionWithSingleTimestampIsIgnored() {
        assertTrue(DescriptionChapterParser.parse("Alle 12:30 parliamo di cucina").isEmpty())
    }

    @Test
    fun id3Chapters() {
        val tag = buildId3(listOf(0L to "Intro", 90_000L to "Parte due"))
        val chapters = Id3ChapterParser.parse(tag)
        assertEquals(2, chapters.size)
        assertEquals("Intro", chapters[0].title)
        assertEquals(90_000L, chapters[1].startMs)
        assertEquals("Parte due", chapters[1].title)
    }

    /** Costruisce un tag ID3v2.3 minimale con frame CHAP contenenti un TIT2. */
    private fun buildId3(chapters: List<Pair<Long, String>>): ByteArray {
        fun int32(v: Long) = byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
        fun frame(id: String, body: ByteArray) = id.toByteArray() + int32(body.size.toLong()) + byteArrayOf(0, 0) + body
        val frames = ByteArrayOutputStream()
        chapters.forEachIndexed { i, (start, title) ->
            val tit2 = frame("TIT2", byteArrayOf(3) + title.toByteArray())
            val body = "ch$i".toByteArray() + byteArrayOf(0) +
                int32(start) + int32(start + 1000) + int32(-1) + int32(-1) + tit2
            frames.write(frame("CHAP", body))
        }
        val content = frames.toByteArray()
        val size = content.size
        val syncSafe = byteArrayOf(
            (size shr 21 and 0x7F).toByte(), (size shr 14 and 0x7F).toByte(),
            (size shr 7 and 0x7F).toByte(), (size and 0x7F).toByte(),
        )
        return "ID3".toByteArray() + byteArrayOf(3, 0, 0) + syncSafe + content
    }
}
