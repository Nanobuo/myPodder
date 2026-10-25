package com.nanobuo.mypodder.data.download

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import android.net.Uri
import android.os.Environment
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.nanobuo.mypodder.data.db.AppDatabase
import com.nanobuo.mypodder.data.db.DownloadState
import com.nanobuo.mypodder.data.db.Episode
import com.nanobuo.mypodder.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    db: AppDatabase,
    private val workManager: WorkManager,
    private val settings: SettingsRepository,
) {
    private val episodeDao = db.episodeDao()

    val directory: File
        get() = (context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS) ?: File(context.filesDir, "podcasts"))
            .also { it.mkdirs() }

    /** Spazio libero dove finiscono i download: microSD se scelta, altrimenti telefono. */
    fun freeSpace(): Long {
        val sd = settings.state.value.downloadTreeUri.isNotBlank()
        return (if (sd) removableAppDir(context) else null)?.usableSpace ?: directory.usableSpace
    }

    /**
     * Cartella "podcasts" sulla microSD (dentro quella scelta dall'utente, o la stessa se si chiama
     * già così), creata se manca. Null se non è stata scelta o non è più accessibile.
     */
    fun sdPodcastsFolder(): DocumentFile? {
        val tree = settings.state.value.downloadTreeUri.takeIf { it.isNotBlank() } ?: return null
        val root = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(tree)) }.getOrNull() ?: return null
        if (!root.canWrite()) return null
        if (root.name.equals(SD_FOLDER, ignoreCase = true)) return root
        return root.findFile(SD_FOLDER)?.takeIf { it.isDirectory } ?: root.createDirectory(SD_FOLDER)
    }

    /** Sposta un file scaricato nella cartella della microSD; restituisce l'URI o null se non riesce. */
    fun moveToSd(file: File, podcastTitle: String, mimeType: String?): Pair<String, Long>? {
        val podcasts = sdPodcastsFolder() ?: return null
        val folderName = podcastTitle.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().take(80).ifEmpty { "podcast" }
        val folder = podcasts.findFile(folderName)?.takeIf { it.isDirectory } ?: podcasts.createDirectory(folderName) ?: return null
        folder.findFile(file.name)?.delete()
        val doc = folder.createFile(mimeType ?: "audio/mpeg", file.name) ?: return null
        return try {
            context.contentResolver.openOutputStream(doc.uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: throw IllegalStateException("no stream")
            val size = file.length()
            file.delete()
            doc.uri.toString() to size
        } catch (e: Exception) {
            doc.delete()
            null
        }
    }

    fun fileFor(episode: Episode): File {
        val ext = when {
            episode.mimeType?.contains("mp4") == true || episode.mimeType?.contains("m4a") == true -> "m4a"
            episode.mimeType?.contains("ogg") == true -> "ogg"
            episode.mimeType?.contains("opus") == true -> "opus"
            episode.mimeType?.contains("aac") == true -> "aac"
            else -> episode.audioUrl.substringBefore('?').substringAfterLast('.', "mp3")
                .takeIf { it.length in 2..4 && it.all(Char::isLetterOrDigit) } ?: "mp3"
        }
        val dir = File(directory, episode.podcastId.toString()).also { it.mkdirs() }
        return File(dir, "${episode.id}.$ext")
    }

    fun partialFileFor(episode: Episode) = File(fileFor(episode).path + ".part")

    suspend fun enqueue(episodeId: Long) {
        val episode = episodeDao.get(episodeId) ?: return
        if (episode.downloadState == DownloadState.DONE) return
        episodeDao.setDownloadState(episodeId, DownloadState.QUEUED)
        val wifiOnly = settings.current().wifiOnly
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_EPISODE to episodeId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(DownloadWorker.TAG)
            .build()
        workManager.enqueueUniqueWork(workName(episodeId), ExistingWorkPolicy.KEEP, request)
    }

    /** Annulla un download in corso o elimina il file scaricato. */
    suspend fun delete(episodeId: Long) {
        workManager.cancelUniqueWork(workName(episodeId))
        val episode = episodeDao.get(episodeId) ?: return
        withContext(Dispatchers.IO) {
            LocalMedia.delete(context, episode.localPath)
            fileFor(episode).delete()
            partialFileFor(episode).delete()
        }
        episodeDao.clearDownload(episodeId)
    }

    suspend fun deleteForPodcast(podcastId: Long) {
        episodeDao.downloadedForPodcast(podcastId).forEach { delete(it.id) }
        withContext(Dispatchers.IO) { File(directory, podcastId.toString()).deleteRecursively() }
    }

    /** Elimina i download degli episodi già ascoltati; restituisce quanti ne ha eliminati. */
    suspend fun deleteCompleted(): Int {
        val list = episodeDao.downloadedAndCompleted()
        list.forEach { delete(it.id) }
        return list.size
    }

    private fun workName(id: Long) = "download-$id"

    companion object {
        const val SD_FOLDER = "podcasts"

        /** true se c'è una memoria esterna rimovibile (microSD) inserita e utilizzabile. */
        fun hasRemovableStorage(context: Context): Boolean {
            val storage = context.getSystemService(android.os.storage.StorageManager::class.java)
            val mounted = storage?.storageVolumes.orEmpty().any { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
            return mounted && removableAppDir(context) != null
        }

        /** Cartella dell'app sulla scheda rimovibile, se presente (serve per leggere lo spazio libero). */
        fun removableAppDir(context: Context): File? =
            context.getExternalFilesDirs(null).drop(1).firstOrNull { dir ->
                dir != null && runCatching { Environment.isExternalStorageRemovable(dir) }.getOrDefault(false)
            }
    }
}
