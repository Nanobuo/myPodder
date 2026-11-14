package com.nanobuo.mypodder.data.ads

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.nanobuo.mypodder.data.db.AdAnalysis
import com.nanobuo.mypodder.data.db.AdSegment
import com.nanobuo.mypodder.data.db.AppDatabase
import com.nanobuo.mypodder.data.db.Chapter
import com.nanobuo.mypodder.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Tratto da proporre di saltare. */
data class AdRange(val startMs: Long, val endMs: Long)

/**
 * Riconoscimento delle pubblicità negli episodi scaricati (come nella versione desktop):
 * - tratti che si ripetono in più episodi dello stesso podcast (spot, sigle, messaggi);
 * - tratti che cambiano scaricando di nuovo l'episodio (pubblicità inserite al download), se attivo;
 * - capitoli con un titolo da pubblicità ("Sponsor", "Pubblicità", ...).
 * Il player propone il pulsante "Salta pubblicità"; niente viene saltato da solo.
 */
@Singleton
class AdRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    db: AppDatabase,
    private val workManager: WorkManager,
    private val settings: SettingsRepository,
) {
    private val adDao = db.adDao()
    private val episodeDao = db.episodeDao()

    private val fingerprintDir get() = File(context.filesDir, "fingerprints")

    /** Tratti da proporre di saltare nell'episodio, uniti e ordinati. */
    fun observe(episodeId: Long, chapters: Flow<List<Chapter>>, durationMs: () -> Long): Flow<List<AdRange>> =
        combine(adDao.observeSegments(episodeId), chapters, settings.flow.map { it.adDetection }) { stored, ch, enabled ->
            if (!enabled) emptyList()
            else mergeRanges(stored.map { AdRange(it.startMs, it.endMs) } + chapterAds(ch, durationMs()))
        }

    /** Mette in fila l'analisi di un episodio scaricato (in background, a batteria non scarica). */
    fun schedule(episodeId: Long) {
        if (!settings.state.value.adDetection) return
        val request = OneTimeWorkRequestBuilder<AdAnalysisWorker>()
            .setInputData(workDataOf(AdAnalysisWorker.KEY_EPISODE to episodeId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .apply {
                        // Il confronto con un secondo download segue la scelta "solo Wi-Fi" dei download.
                        val s = settings.state.value
                        if (s.adCompareDownload) setRequiredNetworkType(if (s.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    }
                    .build()
            )
            .build()
        workManager.enqueueUniqueWork("ads-$episodeId", ExistingWorkPolicy.KEEP, request)
    }

    /** Episodi già scaricati e non ancora analizzati. */
    suspend fun schedulePending() {
        if (!settings.state.value.adDetection) return
        adDao.pending(30).forEach(::schedule)
    }

    /**
     * Analizza un episodio scaricato. `secondCopy` scarica di nuovo l'audio nel file indicato e
     * dice se ci è riuscito.
     */
    suspend fun analyze(episodeId: Long, isStopped: () -> Boolean, secondCopy: suspend (File) -> Boolean) = lock.withLock {
        analyzeLocked(episodeId, isStopped, secondCopy)
    }

    /** Un'analisi alla volta: decodificare l'audio è pesante. */
    private val lock = Mutex()

    private suspend fun analyzeLocked(episodeId: Long, isStopped: () -> Boolean, secondCopy: suspend (File) -> Boolean) {
        val episode = episodeDao.get(episodeId) ?: return
        val path = episode.localPath ?: return
        val previous = adDao.analysis(episodeId)
        val fpFile = File(fingerprintDir, "$episodeId.fp")
        val fp = AudioDecoder.fingerprint(context, path, isStopped)
        AdFingerprint.save(fpFile, fp)

        // 1. Tratti ripetuti negli altri episodi dello stesso podcast.
        val others = adDao.withFingerprint(episode.podcastId, episodeId).take(COMPARE_WITH)
        val mine = ArrayList<AdRange>()
        for (other in others) {
            val otherFp = runCatching { AdFingerprint.load(File(other.fingerprint!!)) }.getOrNull() ?: continue
            val hits = AdFingerprint.repeated(fp, otherFp)
            if (hits.isEmpty()) continue
            mine += hits.map { AdRange(AdFingerprint.framesToMs(it.aStart), AdFingerprint.framesToMs(it.aEnd)) }
            // Lo stesso tratto vale anche per l'altro episodio.
            val theirs = adDao.segments(other.episodeId, SOURCE_REPEAT).map { AdRange(it.startMs, it.endMs) } +
                hits.map { AdRange(AdFingerprint.framesToMs(it.bStart), AdFingerprint.framesToMs(it.bEnd)) }
            adDao.replaceSegments(other.episodeId, SOURCE_REPEAT, mergeRanges(theirs).map { AdSegment(other.episodeId, it.startMs, it.endMs, SOURCE_REPEAT) })
        }
        adDao.replaceSegments(episodeId, SOURCE_REPEAT, mergeRanges(mine).map { AdSegment(episodeId, it.startMs, it.endMs, SOURCE_REPEAT) })

        // 2. Pubblicità inserite al download: si confronta con una seconda copia dell'episodio.
        var insertedChecked = previous?.insertedChecked ?: false
        if (settings.state.value.adCompareDownload && !insertedChecked) {
            val tmp = File(context.cacheDir, "ads-$episodeId.tmp")
            try {
                if (secondCopy(tmp)) {
                    val other = AudioDecoder.fingerprint(context, tmp.absolutePath, isStopped)
                    val ranges = AdFingerprint.inserted(fp, other)
                        .map { AdSegment(episodeId, AdFingerprint.framesToMs(it.first), AdFingerprint.framesToMs(it.second), SOURCE_INSERTED) }
                    adDao.replaceSegments(episodeId, SOURCE_INSERTED, ranges)
                    insertedChecked = true
                }
            } finally {
                tmp.delete()
            }
        }

        adDao.saveAnalysis(AdAnalysis(episodeId, episode.podcastId, System.currentTimeMillis(), fpFile.absolutePath, insertedChecked))
        // Le impronte più vecchie non servono più.
        adDao.withFingerprint(episode.podcastId, episodeId).drop(KEEP_FINGERPRINTS - 1).forEach {
            it.fingerprint?.let { f -> File(f).delete() }
            adDao.dropFingerprint(it.episodeId)
        }
    }

    companion object {
        const val SOURCE_REPEAT = "repeat"
        const val SOURCE_INSERTED = "inserted"
        /** Impronte tenute per podcast: bastano gli ultimi episodi per trovare gli spot che girano. */
        private const val KEEP_FINGERPRINTS = 8
        /** Episodi con cui si confronta un nuovo episodio. */
        private const val COMPARE_WITH = 5

        private val AD_CHAPTER = Regex(
            "sponsor|pubblicit|annunc|promo\\b|promozion|\\bspot\\b|\\bads?\\b|ad break|advert|commercial|werbung|anuncio|publicit",
            RegexOption.IGNORE_CASE,
        )

        /** Capitoli che sembrano pubblicità, come tratti da saltare. */
        fun chapterAds(chapters: List<Chapter>, durationMs: Long): List<AdRange> =
            chapters.mapIndexedNotNull { i, c ->
                if (!AD_CHAPTER.containsMatchIn(c.title)) return@mapIndexedNotNull null
                val end = chapters.getOrNull(i + 1)?.startMs ?: durationMs
                if (end > c.startMs) AdRange(c.startMs, end) else null
            }

        fun mergeRanges(list: List<AdRange>): List<AdRange> {
            val out = ArrayList<AdRange>()
            for (r in list.sortedBy { it.startMs }) {
                val last = out.lastOrNull()
                if (last != null && r.startMs <= last.endMs + 1000) out[out.size - 1] = last.copy(endMs = maxOf(last.endMs, r.endMs))
                else out += r
            }
            return out
        }
    }
}
