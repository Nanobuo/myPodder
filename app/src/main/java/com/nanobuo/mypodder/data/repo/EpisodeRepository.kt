package com.nanobuo.mypodder.data.repo

import com.nanobuo.mypodder.data.db.AppDatabase
import com.nanobuo.mypodder.data.download.DownloadRepository
import com.nanobuo.mypodder.data.settings.SettingsRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpisodeRepository @Inject constructor(
    db: AppDatabase,
    private val settings: SettingsRepository,
    private val downloads: DownloadRepository,
) {
    private val episodeDao = db.episodeDao()
    private val queueDao = db.queueDao()
    private val listeningDao = db.listeningDao()

    val queue = queueDao.observe()
    val queueIds = queueDao.observeIds()
    val downloadsList = episodeDao.observeDownloads()
    val downloadedBytes = episodeDao.observeDownloadedBytes()

    fun latest(limit: Int = 150) = episodeDao.observeLatest(limit)
    fun history(limit: Int = 300) = episodeDao.observeHistory(limit)
    suspend fun clearHistory() = episodeDao.clearHistory()
    fun forPodcast(podcastId: Long) = episodeDao.observeForPodcast(podcastId)
    fun observe(id: Long) = episodeDao.observeItem(id)
    suspend fun get(id: Long) = episodeDao.getItem(id)

    suspend fun addToQueue(id: Long) = queueDao.addLast(listOf(id))
    suspend fun playNext(id: Long) = queueDao.addFirst(id)
    suspend fun removeFromQueue(id: Long) = queueDao.remove(id)
    suspend fun moveInQueue(from: Int, to: Int) = queueDao.move(from, to)
    suspend fun setQueue(ids: List<Long>) = queueDao.rewrite(ids)
    suspend fun clearQueue() = queueDao.clear()
    suspend fun nextInQueue(): Long? = queueDao.first()

    suspend fun savePosition(id: Long, positionMs: Long) = episodeDao.savePosition(id, positionMs)
    suspend fun setDuration(id: Long, durationMs: Long) = episodeDao.setDuration(id, durationMs)
    suspend fun clearNew(podcastId: Long) = episodeDao.clearNew(podcastId)

    /** Segna un episodio come ascoltato (o no), con le azioni collegate. */
    suspend fun setCompleted(id: Long, completed: Boolean, fromPlayback: Boolean = false) {
        val ep = episodeDao.get(id) ?: return
        episodeDao.setCompleted(id, completed, if (completed) System.currentTimeMillis() else null)
        if (completed) {
            queueDao.remove(id)
            if (fromPlayback) listeningDao.markCompleted(LocalDate.now().toEpochDay(), id, ep.podcastId)
            if (settings.current().deleteAfterPlayed) downloads.delete(id)
        }
    }
}
