package com.nanobuo.mypodder.data.chapters

import android.content.Context
import com.nanobuo.mypodder.data.db.AppDatabase
import com.nanobuo.mypodder.data.db.Chapter
import com.nanobuo.mypodder.data.db.Episode
import com.nanobuo.mypodder.data.feed.ParsedChapter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recupera i capitoli di un episodio nell'ordine: JSON Podcasting 2.0, tag ID3 (CHAP) del file
 * audio, timestamp nella descrizione. Il risultato viene salvato nel database.
 */
@Singleton
class ChapterRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    db: AppDatabase,
    private val client: OkHttpClient,
    private val json: Json,
) {
    private val episodeDao = db.episodeDao()
    private val chapterDao = db.chapterDao()
    private val mutex = Mutex()

    fun observe(episodeId: Long): Flow<List<Chapter>> = chapterDao.observe(episodeId)

    suspend fun get(episodeId: Long): List<Chapter> = chapterDao.get(episodeId)

    suspend fun ensureLoaded(episodeId: Long) = mutex.withLock {
        val episode = episodeDao.get(episodeId) ?: return@withLock
        if (episode.chaptersLoaded) return@withLock
        withContext(Dispatchers.IO) {
            val fromJson = episode.chaptersUrl?.let { url -> runCatching { loadJson(episodeId, url) }.getOrNull() }.orEmpty()
            val inline = chapterDao.get(episodeId) // capitoli Podlove letti dal feed
            val chapters = fromJson.ifEmpty { inline }.ifEmpty {
                runCatching { loadId3(episode) }.getOrNull().orEmpty()
            }.ifEmpty {
                DescriptionChapterParser.parse(episode.description).map {
                    Chapter(episodeId, it.startMs, it.title)
                }
            }
            chapterDao.clear(episodeId)
            if (chapters.isNotEmpty()) chapterDao.insertAll(chapters.distinctBy { it.startMs })
            episodeDao.setChaptersLoaded(episodeId)
        }
    }

    private fun loadJson(episodeId: Long, url: String): List<Chapter> {
        val text = client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body.string()
        }
        return JsonChapterParser.parse(json, text).map { it.toChapter(episodeId) }
    }

    private fun ParsedChapter.toChapter(episodeId: Long) = Chapter(episodeId, startMs, title, imageUrl, url)

    private fun loadId3(episode: Episode): List<Chapter> {
        val local = episode.localPath?.let(::File)?.takeIf { it.exists() }
        val tag = if (local != null) {
            local.inputStream().use(::readTag)
        } else {
            // Legge solo l'inizio del file remoto, fino alla fine del tag ID3.
            val request = Request.Builder().url(episode.audioUrl).header("Range", "bytes=0-").build()
            client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                readTag(r.body.byteStream())
            }
        } ?: return emptyList()

        val chapters = Id3ChapterParser.parse(tag)
        val dir = File(context.filesDir, "chapter_images").also { it.mkdirs() }
        return chapters.mapIndexed { i, c ->
            val image = c.image?.let { bytes ->
                val file = File(dir, "${episode.id}_$i.img")
                file.writeBytes(bytes)
                file.toURI().toString()
            }
            Chapter(episode.id, c.startMs, c.title ?: "#${i + 1}", image, c.url)
        }
    }

    private fun readTag(input: InputStream): ByteArray? {
        val header = ByteArray(10)
        if (input.readNBytesCompat(header) < 10) return null
        val size = Id3ChapterParser.tagSize(header) ?: return null
        if (size > MAX_TAG) return null
        val out = ByteArrayOutputStream(size)
        out.write(header)
        val rest = ByteArray(size - 10)
        val read = input.readNBytesCompat(rest)
        out.write(rest, 0, read)
        return out.toByteArray()
    }

    private fun InputStream.readNBytesCompat(buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val n = read(buffer, total, buffer.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }

    private companion object {
        const val MAX_TAG = 8 * 1024 * 1024
    }
}
