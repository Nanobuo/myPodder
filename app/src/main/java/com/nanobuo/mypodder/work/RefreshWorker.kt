package com.nanobuo.mypodder.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nanobuo.mypodder.data.repo.PodcastRepository
import com.nanobuo.mypodder.data.sync.SyncManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class RefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val podcasts: PodcastRepository,
    private val sync: SyncManager,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        podcasts.refreshAll()
        // Tiene allineati i dispositivi (e attivo il progetto Supabase) anche senza aprire l'app.
        sync.syncNow()
        return Result.success()
    }
}

@Singleton
class RefreshScheduler @Inject constructor(private val workManager: WorkManager) {
    /** Pianifica l'aggiornamento automatico dei feed; 0 ore = solo manuale. */
    fun schedule(hours: Int) {
        if (hours <= 0) {
            workManager.cancelUniqueWork(NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(hours.toLong(), TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        workManager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private companion object {
        const val NAME = "feed-refresh"
    }
}
