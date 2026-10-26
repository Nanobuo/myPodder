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

    @Query(
        """SELECT p.*, (SELECT COUNT(*) FROM episodes e WHERE e.podcastId = p.id AND e.isNew = 1) AS newCount
           FROM podcasts p ORDER BY p.title COLLATE NOCASE"""
    )
    fun observeAllWithNew(): Flow<List<PodcastWithNew>>

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

    @Query("UPDATE podcasts SET categories = :categories WHERE id = :id")
    suspend fun setCategories(id: Long, categories: String)

    @Query("UPDATE podcasts SET autoDownload = :enabled WHERE id = :id")
    suspend fun setAutoDownload(id: Long, enabled: Boolean)

    @Query("DELETE FROM podcasts WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface EpisodeDao {
    @Query("$EPISODE_ITEM WHERE e.podcastId = :podcastId ORDER BY e.pubDate DESC")
    fun observeForPodcast(podcastId: Long): Flow<List<EpisodeItem>>

    @Query("$EPISODE_ITEM WHERE e.completed = 0 ORDER BY e.pubDate DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<EpisodeItem>>

    @Query("$EPISODE_ITEM WHERE e.downloadState != 0 ORDER BY e.downloadState = 3, e.pubDate DESC")
    fun observeDownloads(): Flow<List<EpisodeItem>>

    @Query("$EPISODE_ITEM WHERE e.lastPlayedAt > 0 ORDER BY e.lastPlayedAt DESC LIMIT :limit")
    fun observeHistory(limit: Int): Flow<List<EpisodeItem>>

    @Query("UPDATE episodes SET lastPlayedAt = 0")
    suspend fun clearHistory()

    @Query("$EPISODE_ITEM WHERE e.id = :id")
    fun observeItem(id: Long): Flow<EpisodeItem?>

    @Query("$EPISODE_ITEM WHERE e.id = :id")
    suspend fun getItem(id: Long): EpisodeItem?

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun get(id: Long): Episode?

    @Query("SELECT COUNT(*) FROM episodes WHERE isNew = 1")
    suspend fun countNew(): Int

    @Query("SELECT COUNT(*) FROM episodes WHERE pubDate > :since AND completed = 0")
    suspend fun countPublishedSince(since: Long): Int

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

    @Query("UPDATE episodes SET positionMs = :positionMs, lastPlayedAt = :now, stateUpdatedAt = MAX(:now, stateUpdatedAt + 1), isNew = 0 WHERE id = :id")
    suspend fun savePosition(id: Long, positionMs: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE episodes SET durationMs = :durationMs WHERE id = :id AND durationMs != :durationMs")
    suspend fun setDuration(id: Long, durationMs: Long)

    @Query(
        """UPDATE episodes SET completed = :completed, completedAt = :at, positionMs = 0, isNew = 0,
           stateUpdatedAt = MAX(:now, stateUpdatedAt + 1) WHERE id = :id"""
    )
    suspend fun setCompleted(id: Long, completed: Boolean, at: Long?, now: Long = System.currentTimeMillis())

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

    // --- Sincronizzazione ---

    @Query(
        """SELECT e.id, p.feedUrl, e.guid, e.positionMs, e.completed, e.lastPlayedAt, e.stateUpdatedAt
           FROM episodes e JOIN podcasts p ON p.id = e.podcastId
           WHERE e.stateUpdatedAt > :since ORDER BY e.stateUpdatedAt LIMIT :limit"""
    )
    suspend fun changedSince(since: Long, limit: Int): List<EpisodeSyncState>

    @Query(
        """SELECT e.id, p.feedUrl, e.guid, e.positionMs, e.completed, e.lastPlayedAt, e.stateUpdatedAt
           FROM episodes e JOIN podcasts p ON p.id = e.podcastId
           WHERE p.feedUrl = :feedUrl AND e.guid = :guid"""
    )
    suspend fun syncState(feedUrl: String, guid: String): EpisodeSyncState?

    @Query(
        """UPDATE episodes SET positionMs = :positionMs, completed = :completed,
           completedAt = CASE WHEN :completed THEN COALESCE(completedAt, :updatedAt) ELSE NULL END,
           lastPlayedAt = :lastPlayedAt, stateUpdatedAt = :updatedAt,
           isNew = CASE WHEN :completed OR :positionMs > 0 THEN 0 ELSE isNew END
           WHERE id = :id"""
    )
    suspend fun applyRemoteState(id: Long, positionMs: Long, completed: Boolean, lastPlayedAt: Long, updatedAt: Long)

    @Query(
        """SELECT e.id, p.feedUrl, e.guid FROM queue q JOIN episodes e ON e.id = q.episodeId
           JOIN podcasts p ON p.id = e.podcastId ORDER BY q.position"""
    )
    suspend fun queueKeys(): List<EpisodeKey>

    @Query("SELECT e.id FROM episodes e JOIN podcasts p ON p.id = e.podcastId WHERE p.feedUrl = :feedUrl AND e.guid = :guid")
    suspend fun idFor(feedUrl: String, guid: String): Long?
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
        """UPDATE listening SET listenedMs = listenedMs + :listenedMs, contentMs = contentMs + :contentMs,
           updatedAt = :now WHERE day = :day AND episodeId = :episodeId"""
    )
    suspend fun addTo(day: Long, episodeId: Long, listenedMs: Long, contentMs: Long, now: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: ListeningEntry): Long

    /** Somma il tempo all'eventuale riga esistente (senza UPSERT, non disponibile su API 26). */
    @Transaction
    suspend fun add(day: Long, episodeId: Long, podcastId: Long, listenedMs: Long, contentMs: Long) {
        val now = System.currentTimeMillis()
        if (addTo(day, episodeId, listenedMs, contentMs, now) == 0) {
            insert(ListeningEntry(day, episodeId, podcastId, listenedMs, contentMs, updatedAt = now))
        }
    }

    @Query("SELECT * FROM listening")
    suspend fun getAll(): List<ListeningEntry>

    @Query("DELETE FROM listening")
    suspend fun clearLocal()

    @Query("DELETE FROM remote_listening")
    suspend fun clearRemote()

    /** Azzera le statistiche: quelle di questo dispositivo e quelle scaricate dagli altri. */
    @Transaction
    suspend fun clearAll() {
        clearLocal()
        clearRemote()
    }

    @Query("DELETE FROM listening WHERE updatedAt <= :until")
    suspend fun deleteUpTo(until: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRemote(rows: List<RemoteListening>)

    @Query(
        """SELECT l.day, p.feedUrl, e.guid, l.listenedMs, l.contentMs, l.completed, l.updatedAt
           FROM listening l JOIN episodes e ON e.id = l.episodeId JOIN podcasts p ON p.id = e.podcastId
           WHERE l.updatedAt > :since ORDER BY l.updatedAt LIMIT :limit"""
    )
    suspend fun changedSince(since: Long, limit: Int): List<ListeningSyncRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<ListeningEntry>)

    @Query("UPDATE listening SET completed = 1, updatedAt = :now WHERE day = :day AND episodeId = :episodeId")
    suspend fun markCompletedRow(day: Long, episodeId: Long, now: Long): Int

    @Transaction
    suspend fun markCompleted(day: Long, episodeId: Long, podcastId: Long) {
        val now = System.currentTimeMillis()
        if (markCompletedRow(day, episodeId, now) == 0) {
            insert(ListeningEntry(day, episodeId, podcastId, 0, 0, completed = true, updatedAt = now))
        }
    }

    @Query(
        """SELECT COALESCE(SUM(listenedMs), 0) AS listenedMs, COALESCE(SUM(contentMs), 0) AS contentMs,
           COALESCE(SUM(completed), 0) AS completed FROM (SELECT day, listenedMs, contentMs, completed FROM listening UNION ALL SELECT day, listenedMs, contentMs, completed FROM remote_listening) WHERE day >= :fromDay"""
    )
    fun observeTotals(fromDay: Long): Flow<StatsTotals>

    // Le statistiche sommano gli ascolti di questo dispositivo e quelli sincronizzati dagli altri.
    @Query("SELECT day, SUM(listenedMs) AS listenedMs FROM (SELECT day, listenedMs, contentMs, completed FROM listening UNION ALL SELECT day, listenedMs, contentMs, completed FROM remote_listening) WHERE day >= :fromDay GROUP BY day ORDER BY day")
    fun observeDays(fromDay: Long): Flow<List<DayTotal>>

    @Query("SELECT day FROM (SELECT day, listenedMs, contentMs, completed FROM listening UNION ALL SELECT day, listenedMs, contentMs, completed FROM remote_listening) GROUP BY day HAVING SUM(listenedMs) >= :minMs ORDER BY day DESC")
    fun observeActiveDays(minMs: Long): Flow<List<Long>>

    @Query(
        """SELECT t.podcastId AS podcastId, p.title AS title, p.imageUrl AS imageUrl, SUM(t.listenedMs) AS listenedMs
           FROM (SELECT podcastId, day, listenedMs FROM listening
                 UNION ALL SELECT p2.id AS podcastId, r.day, r.listenedMs FROM remote_listening r
                 JOIN podcasts p2 ON p2.feedUrl = r.feedUrl) t
           JOIN podcasts p ON p.id = t.podcastId WHERE t.day >= :fromDay
           GROUP BY t.podcastId ORDER BY listenedMs DESC LIMIT :limit"""
    )
    fun observeTopPodcasts(fromDay: Long, limit: Int): Flow<List<PodcastTotal>>
}
