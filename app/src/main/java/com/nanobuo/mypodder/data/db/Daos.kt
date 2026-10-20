package com.nanobuo.mypodder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

private const val EPISODE_ITEM =
    "SELECT e.*, p.title AS podcastTitle, p.imageUrl AS podcastImage FROM episodes e JOIN podcasts p ON p.id = e.podcastId"

@Dao
interface PodcastDao {
    @Query("SELECT * FROM podcasts ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<Podcast>>

    @Query("SELECT * FROM podcasts")
    suspend fun getAll(): List<Podcast>

    @Query("SELECT * FROM podcasts WHERE id = :id")
    fun observe(id: Long): Flow<Podcast?>

    @Query("SELECT * FROM podcasts WHERE id = :id")
    suspend fun get(id: Long): Podcast?

    @Query("SELECT * FROM podcasts WHERE feedUrl = :url")
    suspend fun getByUrl(url: String): Podcast?

    @Query("SELECT id FROM podcasts WHERE feedUrl = :url")
    fun observeIdByUrl(url: String): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(podcast: Podcast): Long

    @Update
    suspend fun update(podcast: Podcast)

    @Query("UPDATE podcasts SET autoDownload = :enabled WHERE id = :id")
    suspend fun setAutoDownload(id: Long, enabled: Boolean)

    @Query("DELETE FROM podcasts WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface EpisodeDao {
    @Query("$EPISODE_ITEM WHERE e.podcastId = :podcastId ORDER BY e.pubDate DESC")
    fun observeForPodcast(podcastId: Long): Flow<List<EpisodeItem>>

    @Query("$EPISODE_ITEM ORDER BY e.pubDate DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<EpisodeItem>>

    @Query("$EPISODE_ITEM WHERE e.downloadState != 0 ORDER BY e.downloadState = 3, e.pubDate DESC")
    fun observeDownloads(): Flow<List<EpisodeItem>>

    @Query("$EPISODE_ITEM WHERE e.id = :id")
    fun observeItem(id: Long): Flow<EpisodeItem?>

    @Query("$EPISODE_ITEM WHERE e.id = :id")
    suspend fun getItem(id: Long): EpisodeItem?

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun get(id: Long): Episode?

    @Query("SELECT guid FROM episodes WHERE podcastId = :podcastId")
    suspend fun guids(podcastId: Long): List<String>

    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId AND downloadState != 0")
    suspend fun downloadedForPodcast(podcastId: Long): List<Episode>

    @Query("SELECT * FROM episodes WHERE downloadState = 3 AND completed = 1")
    suspend fun downloadedAndCompleted(): List<Episode>

    @Query("SELECT COALESCE(SUM(downloadedBytes), 0) FROM episodes WHERE downloadState = 3")
    fun observeDownloadedBytes(): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(episodes: List<Episode>): List<Long>

    @Query(
        """UPDATE episodes SET title = :title, description = :description, audioUrl = :audioUrl,
           imageUrl = :imageUrl, durationMs = CASE WHEN :durationMs > 0 THEN :durationMs ELSE durationMs END,
           chaptersUrl = :chaptersUrl WHERE podcastId = :podcastId AND guid = :guid"""
    )
    suspend fun updateMetadata(
        podcastId: Long, guid: String, title: String, description: String?, audioUrl: String,
        imageUrl: String?, durationMs: Long, chaptersUrl: String?,
    )

    @Query("UPDATE episodes SET positionMs = :positionMs, lastPlayedAt = :now, isNew = 0 WHERE id = :id")
    suspend fun savePosition(id: Long, positionMs: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE episodes SET durationMs = :durationMs WHERE id = :id AND durationMs != :durationMs")
    suspend fun setDuration(id: Long, durationMs: Long)

    @Query("UPDATE episodes SET completed = :completed, completedAt = :at, positionMs = 0, isNew = 0 WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean, at: Long?)

    @Query("UPDATE episodes SET isNew = 0 WHERE podcastId = :podcastId")
    suspend fun clearNew(podcastId: Long)

    @Query("UPDATE episodes SET downloadState = :state, downloadProgress = :progress WHERE id = :id")
    suspend fun setDownloadState(id: Long, state: Int, progress: Int = 0)

    @Query("UPDATE episodes SET downloadState = 3, downloadProgress = 100, localPath = :path, downloadedBytes = :bytes WHERE id = :id")
    suspend fun setDownloaded(id: Long, path: String, bytes: Long)

    @Query("UPDATE episodes SET downloadState = 0, downloadProgress = 0, localPath = NULL, downloadedBytes = 0 WHERE id = :id")
    suspend fun clearDownload(id: Long)

    @Query("UPDATE episodes SET chaptersLoaded = 1 WHERE id = :id")
    suspend fun setChaptersLoaded(id: Long)
}

@Dao
interface QueueDao {
    @Query(
        """SELECT e.*, p.title AS podcastTitle, p.imageUrl AS podcastImage FROM queue q
           JOIN episodes e ON e.id = q.episodeId JOIN podcasts p ON p.id = e.podcastId
           ORDER BY q.position"""
    )
    fun observe(): Flow<List<EpisodeItem>>

    @Query("SELECT episodeId FROM queue ORDER BY position")
    fun observeIds(): Flow<List<Long>>

    @Query("SELECT episodeId FROM queue ORDER BY position")
    suspend fun ids(): List<Long>

    @Query("SELECT episodeId FROM queue ORDER BY position LIMIT 1")
    suspend fun first(): Long?

    @Query("SELECT COALESCE(MAX(position), -1) FROM queue")
    suspend fun maxPosition(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<QueueEntry>)

    @Query("DELETE FROM queue WHERE episodeId = :episodeId")
    suspend fun remove(episodeId: Long)

    @Query("DELETE FROM queue")
    suspend fun clear()

    @Transaction
    suspend fun rewrite(ids: List<Long>) {
        clear()
        insertAll(ids.mapIndexed { i, id -> QueueEntry(id, i) })
    }

    @Transaction
    suspend fun addLast(episodeIds: List<Long>) {
        val current = ids()
        val toAdd = episodeIds.filter { it !in current }
        if (toAdd.isEmpty()) return
        val start = maxPosition() + 1
        insertAll(toAdd.mapIndexed { i, id -> QueueEntry(id, start + i) })
    }

    @Transaction
    suspend fun addFirst(episodeId: Long) {
        rewrite(listOf(episodeId) + ids().filter { it != episodeId })
    }

    @Transaction
    suspend fun move(from: Int, to: Int) {
        val list = ids().toMutableList()
        if (from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
        rewrite(list)
    }
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE episodeId = :episodeId ORDER BY startMs")
    fun observe(episodeId: Long): Flow<List<Chapter>>

    @Query("SELECT * FROM chapters WHERE episodeId = :episodeId ORDER BY startMs")
    suspend fun get(episodeId: Long): List<Chapter>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chapters: List<Chapter>)

    @Query("DELETE FROM chapters WHERE episodeId = :episodeId")
    suspend fun clear(episodeId: Long)
}

@Dao
interface ListeningDao {
    @Query(
        """UPDATE listening SET listenedMs = listenedMs + :listenedMs, contentMs = contentMs + :contentMs
           WHERE day = :day AND episodeId = :episodeId"""
    )
    suspend fun addTo(day: Long, episodeId: Long, listenedMs: Long, contentMs: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: ListeningEntry): Long

    /** Somma il tempo all'eventuale riga esistente (senza UPSERT, non disponibile su API 26). */
    @Transaction
    suspend fun add(day: Long, episodeId: Long, podcastId: Long, listenedMs: Long, contentMs: Long) {
        if (addTo(day, episodeId, listenedMs, contentMs) == 0) {
            insert(ListeningEntry(day, episodeId, podcastId, listenedMs, contentMs))
        }
    }

    @Query("UPDATE listening SET completed = 1 WHERE day = :day AND episodeId = :episodeId")
    suspend fun markCompletedRow(day: Long, episodeId: Long): Int

    @Transaction
    suspend fun markCompleted(day: Long, episodeId: Long, podcastId: Long) {
        if (markCompletedRow(day, episodeId) == 0) {
            insert(ListeningEntry(day, episodeId, podcastId, 0, 0, completed = true))
        }
    }

    @Query(
        """SELECT COALESCE(SUM(listenedMs), 0) AS listenedMs, COALESCE(SUM(contentMs), 0) AS contentMs,
           COALESCE(SUM(completed), 0) AS completed FROM listening WHERE day >= :fromDay"""
    )
    fun observeTotals(fromDay: Long): Flow<StatsTotals>

    @Query("SELECT day, SUM(listenedMs) AS listenedMs FROM listening WHERE day >= :fromDay GROUP BY day ORDER BY day")
    fun observeDays(fromDay: Long): Flow<List<DayTotal>>

    @Query("SELECT day FROM listening GROUP BY day HAVING SUM(listenedMs) >= :minMs ORDER BY day DESC")
    fun observeActiveDays(minMs: Long): Flow<List<Long>>

    @Query(
        """SELECT l.podcastId AS podcastId, p.title AS title, p.imageUrl AS imageUrl, SUM(l.listenedMs) AS listenedMs
           FROM listening l JOIN podcasts p ON p.id = l.podcastId WHERE l.day >= :fromDay
           GROUP BY l.podcastId ORDER BY listenedMs DESC LIMIT :limit"""
    )
    fun observeTopPodcasts(fromDay: Long, limit: Int): Flow<List<PodcastTotal>>
}
