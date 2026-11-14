package com.nanobuo.mypodder.data.ads

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nanobuo.mypodder.data.db.AppDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** Analizza in background un episodio scaricato per trovare le pubblicità. */
@HiltWorker
class AdAnalysisWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    db: AppDatabase,
    private val ads: AdRepository,
    private val client: OkHttpClient,
) : CoroutineWorker(context, params) {

    private val episodeDao = db.episodeDao()

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_EPISODE, -1)
        return try {
            withContext(Dispatchers.Default) {
                ads.analyze(id, isStopped = { isStopped }) { target -> downloadAgain(id, target) }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: InterruptedException) {
            Result.retry()
        } catch (e: Exception) {
            // Un file che il telefono non sa decodificare non va riprovato all'infinito.
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private suspend fun downloadAgain(id: Long, target: File): Boolean = withContext(Dispatchers.IO) {
        val episode = episodeDao.get(id) ?: return@withContext false
        val request = Request.Builder().url(episode.audioUrl).build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching false
                target.outputStream().use { out -> response.body.byteStream().copyTo(out, 64 * 1024) }
                target.length() > 0
            }
        }.getOrDefault(false)
    }

    companion object {
        const val KEY_EPISODE = "episode"
    }
}
