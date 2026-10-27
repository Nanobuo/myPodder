package com.nanobuo.mypodder.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import com.nanobuo.mypodder.data.settings.SettingsRepository
import com.nanobuo.mypodder.data.repo.EpisodeRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

data class NowPlaying(
    val episodeId: Long? = null,
    val title: String = "",
    val podcastTitle: String = "",
    val artworkUri: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0,
    val speed: Float = 1f,
) {
    val hasMedia: Boolean get() = episodeId != null
}

/** Collegamento dell'interfaccia al [PlaybackService] tramite MediaController. */
@Singleton
class PlayerConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    private val episodes: EpisodeRepository,
    private val settings: SettingsRepository,
    private val appScope: CoroutineScope,
) {
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pending = mutableListOf<(MediaController) -> Unit>()

    private val _state = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = update()
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull() ?: return@addListener
            if (future !== f) {
                c.release()
                return@addListener
            }
            controller = c
            c.addListener(listener)
            update()
            if (c.mediaItemCount == 0) restoreIfIdle(c) else reconcile()
            pending.toList().forEach { it(c) }
            pending.clear()
        }, ContextCompat.getMainExecutor(context))
    }

    fun disconnect() {
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else {
            pending += block
            connect()
        }
    }

    /** Riproduce un episodio, opzionalmente da una posizione precisa (es. un capitolo). */
    fun play(episodeId: Long, startMs: Long? = null) = withController { c ->
        if (c.currentMediaItem?.mediaId == episodeId.toString() && c.playbackState != Player.STATE_ENDED) {
            if (startMs != null) c.seekTo(startMs)
            c.play()
            return@withController
        }
        val item = MediaItem.Builder().setMediaId(episodeId.toString()).build()
        if (startMs != null) c.setMediaItem(item, startMs) else c.setMediaItem(item)
        c.prepare()
        c.play()
    }

    fun togglePlayPause() = withController { c ->
        when {
            c.isPlaying -> c.pause()
            c.playbackState == Player.STATE_IDLE -> {
                c.prepare()
                c.play()
            }
            c.playbackState == Player.STATE_ENDED -> {
                c.seekTo(0)
                c.play()
            }
            else -> c.play()
        }
    }

    fun pause() = withController { it.pause() }
    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }
    fun skipBack() = withController { it.seekBack() }
    fun skipForward() = withController { it.seekForward() }
    fun setSpeed(speed: Float) = withController { it.setPlaybackSpeed(speed) }

    fun stop() = withController {
        it.stop()
        it.clearMediaItems()
    }

    val position: Long get() = controller?.currentPosition ?: 0

    /** Posizione corrente, aggiornata periodicamente finché qualcuno la osserva. */
    fun positionFlow(intervalMs: Long = 500): Flow<Long> = flow {
        while (true) {
            emit(controller?.currentPosition ?: 0)
            delay(intervalMs)
        }
    }

    /**
     * Se il player è vuoto (es. app riaperta dopo che il servizio è stato chiuso) carica, senza
     * avviarlo, l'ultimo episodio non finito o il primo della coda: così il mini-player resta
     * visibile finché c'è qualcosa da ascoltare. La riproduzione parte solo con Play.
     */
    private fun restoreIfIdle(c: MediaController) {
        if (c.mediaItemCount > 0 || pending.isNotEmpty()) return
        appScope.launch {
            val last = settings.lastEpisodeId.first()?.let { episodes.get(it) }
            val id = last?.takeIf { !it.episode.completed }?.episode?.id ?: episodes.nextInQueue()
            id ?: return@launch
            withContext(Dispatchers.Main) {
                if (controller === c && c.mediaItemCount == 0) {
                    c.setMediaItem(MediaItem.Builder().setMediaId(id.toString()).build())
                }
            }
        }
    }

    /**
     * Allinea il player fermo al database dopo una sincronizzazione o al ritorno nell'app: carica
     * l'ultimo episodio ascoltato (anche su un altro dispositivo) e si sposta alla posizione salvata,
     * così Play riprende da dove si era arrivati. Durante l'ascolto non fa nulla.
     */
    fun reconcile() {
        val c = controller ?: return
        if (c.isPlaying || c.playWhenReady) return
        val currentId = c.currentMediaItem?.mediaId?.toLongOrNull()
        val playerPosition = c.currentPosition
        appScope.launch {
            val latest = episodes.lastPlayed()
            val current = currentId?.let { episodes.get(it) }
            // Si segue un altro dispositivo solo se ci ha ascoltato dopo l'ultima attività di
            // questo: altrimenti vale il player di qui, con la sua coda.
            val lastActivity = settings.current().lastPlayerActivity
            withContext(Dispatchers.Main) {
                if (controller !== c || c.isPlaying || c.playWhenReady) return@withContext
                if (c.currentMediaItem?.mediaId?.toLongOrNull() != currentId) return@withContext
                val newer = latest != null && latest.episode.id != currentId &&
                    latest.episode.lastPlayedAt > maxOf(current?.episode?.lastPlayedAt ?: 0, lastActivity)
                when {
                    newer -> c.setMediaItem(MediaItem.Builder().setMediaId(latest!!.episode.id.toString()).build())
                    current != null && !current.episode.completed &&
                        kotlin.math.abs(current.episode.positionMs - playerPosition) > 2_000 -> c.seekTo(current.episode.positionMs)
                }
            }
        }
    }

    private fun update() {
        val c = controller ?: return
        val item = c.currentMediaItem
        val meta = c.mediaMetadata
        _state.value = NowPlaying(
            episodeId = item?.mediaId?.toLongOrNull(),
            title = meta.title?.toString().orEmpty(),
            podcastTitle = meta.artist?.toString().orEmpty(),
            artworkUri = meta.artworkUri?.toString(),
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING && c.playWhenReady,
            durationMs = c.duration.takeIf { it != C.TIME_UNSET && it > 0 }
                ?: meta.durationMs?.takeIf { it > 0 } ?: 0,
            speed = c.playbackParameters.speed,
        )
    }
}
