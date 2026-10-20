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
            durationMs = c.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0,
            speed = c.playbackParameters.speed,
        )
    }
}
