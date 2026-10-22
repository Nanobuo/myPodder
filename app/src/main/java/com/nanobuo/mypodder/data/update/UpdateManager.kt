package com.nanobuo.mypodder.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.nanobuo.mypodder.BuildConfig
import com.nanobuo.mypodder.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateInfo(val version: String, val notes: String, val apkUrl: String, val size: Long)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    /** La build di debug ha un altro nome di pacchetto e non si aggiorna dalle release. */
    data object Unsupported : UpdateState
    data class Available(val info: UpdateInfo, val fromUser: Boolean) : UpdateState
    data class NeedsPermission(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Installing(val info: UpdateInfo) : UpdateState
    data class Error(val info: UpdateInfo?) : UpdateState
}

/** Controlla le release su GitHub e installa l'APK aggiornato con l'installer di sistema. */
@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val json: Json,
    private val settings: SettingsRepository,
    private val appScope: CoroutineScope,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Controllo automatico, al massimo una volta al giorno. */
    fun checkIfDue() {
        appScope.launch {
            val s = settings.current()
            if (!s.autoCheckUpdates || BuildConfig.DEBUG) return@launch
            if (System.currentTimeMillis() - s.lastUpdateCheck < DAY_MS) return@launch
            check(fromUser = false)
        }
    }

    fun checkNow() {
        appScope.launch { check(fromUser = true) }
    }

    private suspend fun check(fromUser: Boolean) {
        if (BuildConfig.DEBUG) {
            _state.value = UpdateState.Unsupported
            return
        }
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        val result = runCatching { latestRelease() }
        settings.setLastUpdateCheck(System.currentTimeMillis())
        val info = result.getOrNull()
        _state.value = when {
            result.isFailure -> if (fromUser) UpdateState.Error(null) else UpdateState.Idle
            info != null && isNewer(info.version, BuildConfig.VERSION_NAME) -> UpdateState.Available(info, fromUser)
            fromUser -> UpdateState.UpToDate
            else -> UpdateState.Idle
        }
    }

    fun dismiss() {
        _state.value = UpdateState.Idle
    }

    fun downloadAndInstall(info: UpdateInfo) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsPermission(info)
            return
        }
        appScope.launch {
            try {
                val file = download(info)
                _state.value = UpdateState.Installing(info)
                install(file)
            } catch (e: Exception) {
                _state.value = UpdateState.Error(info)
            }
        }
    }

    /** Chiamato dal [UpdateReceiver] quando l'installazione non va a buon fine. */
    fun installFailed() {
        val info = (_state.value as? UpdateState.Installing)?.info
        _state.value = UpdateState.Error(info)
    }

    private suspend fun latestRelease(): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        val body = client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body.string()
        }
        val release = json.decodeFromString<GithubRelease>(body)
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk") && !it.name.contains("debug") }
            ?: return@withContext null
        UpdateInfo(
            version = release.tagName.removePrefix("v"),
            notes = release.body.orEmpty(),
            apkUrl = apk.url,
            size = apk.size,
        )
    }

    private suspend fun download(info: UpdateInfo): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val file = File(dir, "myPodder-${info.version}.apk")
        _state.value = UpdateState.Downloading(info, 0f)
        client.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val total = r.body.contentLength().takeIf { it > 0 } ?: info.size
            var done = 0L
            var lastEmit = 0L
            file.outputStream().use { out ->
                r.body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        if (total > 0 && done - lastEmit > 128 * 1024) {
                            lastEmit = done
                            _state.value = UpdateState.Downloading(info, (done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        }
        file
    }

    private suspend fun install(file: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            // Dopo il primo consenso, da Android 12 l'app si può aggiornare senza ulteriori conferme.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, UpdateReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context, sessionId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pending.intentSender)
        }
    }

    @Serializable
    private data class GithubRelease(
        @SerialName("tag_name") val tagName: String,
        val body: String? = null,
        val assets: List<GithubAsset> = emptyList(),
    )

    @Serializable
    private data class GithubAsset(
        val name: String,
        val size: Long = 0,
        @SerialName("browser_download_url") val url: String,
    )

    companion object {
        private const val REPO = "Nanobuo/myPodder"
        private const val DAY_MS = 24 * 60 * 60 * 1000L

        /** Confronta versioni del tipo 1.2.10: true se [candidate] è più recente di [current]. */
        fun isNewer(candidate: String, current: String): Boolean {
            fun parts(v: String) = v.split('.', '-').map { it.toIntOrNull() ?: 0 }
            val a = parts(candidate)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
