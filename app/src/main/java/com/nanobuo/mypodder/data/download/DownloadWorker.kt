package com.nanobuo.mypodder.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.nanobuo.mypodder.R
import com.nanobuo.mypodder.data.db.AppDatabase
import com.nanobuo.mypodder.data.db.DownloadState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileOutputStream
import java.io.IOException

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    db: AppDatabase,
    private val client: OkHttpClient,
    private val downloads: DownloadRepository,
) : CoroutineWorker(context, params) {

    private val episodeDao = db.episodeDao()

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_EPISODE, -1)
        val episode = episodeDao.get(id) ?: return Result.failure()
        if (episode.downloadState == DownloadState.DONE) return Result.success()

        // Su Android 12+ può fallire se l'app è in background: il download prosegue comunque.
        runCatching { setForeground(foregroundInfo(episode.title, 0)) }

        episodeDao.setDownloadState(id, DownloadState.DOWNLOADING, episode.downloadProgress)
        val target = downloads.fileFor(episode)
        val part = downloads.partialFileFor(episode)

        return try {
            withContext(Dispatchers.IO) {
                val already = if (part.exists()) part.length() else 0L
                val request = Request.Builder().url(episode.audioUrl)
                    .apply { if (already > 0) header("Range", "bytes=$already-") }
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val append = response.code == 206
                    var done = if (append) already else 0L
                    val length = response.body.contentLength()
                    val total = if (length > 0) length + done else episode.sizeBytes
                    var lastUpdate = 0L
                    FileOutputStream(part, append).use { out ->
                        response.body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                if (isStopped) throw CancellationException("stopped")
                                val n = input.read(buffer)
                                if (n < 0) break
                                out.write(buffer, 0, n)
                                done += n
                                val now = System.currentTimeMillis()
                                if (now - lastUpdate > 700 && total > 0) {
                                    lastUpdate = now
                                    val progress = (done * 100 / total).toInt().coerceIn(0, 99)
                                    episodeDao.setDownloadState(id, DownloadState.DOWNLOADING, progress)
                                    runCatching { setForeground(foregroundInfo(episode.title, progress)) }
                                }
                            }
                        }
                    }
                }
                if (!part.renameTo(target)) {
                    part.copyTo(target, overwrite = true)
                    part.delete()
                }
            }
            episodeDao.setDownloaded(id, target.absolutePath, target.length())
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                episodeDao.setDownloadState(id, DownloadState.QUEUED)
                Result.retry()
            } else {
                episodeDao.setDownloadState(id, DownloadState.FAILED)
                Result.failure()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(
        applicationContext.getString(R.string.downloading), 0,
    )

    private fun foregroundInfo(title: String, progress: Int): ForegroundInfo {
        val ctx = applicationContext
        val manager = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, ctx.getString(R.string.channel_downloads), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.downloading))
            .setContentText(title)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()
        val notificationId = (id.hashCode() and 0x7FFFFFFF) + 1000
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    companion object {
        const val KEY_EPISODE = "episode_id"
        const val TAG = "download"
        private const val CHANNEL = "downloads"
    }
}
