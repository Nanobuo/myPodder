package com.nanobuo.mypodder

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.nanobuo.mypodder.data.discover.DiscoverStore
import com.nanobuo.mypodder.data.settings.SettingsRepository
import com.nanobuo.mypodder.work.RefreshScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), Configuration.Provider, SingletonImageLoader.Factory {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var okHttp: OkHttpClient
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var refreshScheduler: RefreshScheduler
    @Inject lateinit var appScope: CoroutineScope
    @Inject lateinit var discoverStore: DiscoverStore

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Consigliati e classifiche pronti prima di aprire la scheda Cerca.
        discoverStore.start()
        appScope.launch {
            settings.flow.map { it.refreshHours }.distinctUntilChanged().collect { refreshScheduler.schedule(it) }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { okHttp })) }
            .crossfade(true)
            .build()
}

