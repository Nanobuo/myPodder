package com.nanobuo.mypodder.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.ListenableFuture
import com.nanobuo.mypodder.MainActivity
import com.nanobuo.mypodder.R
import com.nanobuo.mypodder.data.chapters.ChapterRepository
import com.nanobuo.mypodder.data.db.AppDatabase
import com.nanobuo.mypodder.data.db.Chapter
import com.nanobuo.mypodder.data.db.EpisodeItem
import com.nanobuo.mypodder.data.repo.EpisodeRepository
import com.nanobuo.mypodder.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

/**
 * Servizio di riproduzione: espone l'ExoPlayer tramite MediaSession (notifica, schermata di
 * blocco, Bluetooth), salva la posizione, registra le statistiche, gestisce coda e sleep timer.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject lateinit var episodes: EpisodeRepository
    @Inject lateinit var chapters: ChapterRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var sleepTimer: SleepTimer
    @Inject lateinit var db: AppDatabase
    @Inject lateinit var okHttp: OkHttpClient
    @Inject lateinit var appScope: CoroutineScope

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private lateinit var shake: ShakeDetector

    private var current: EpisodeItem? = null
    private var currentChapters: List<Chapter> = emptyList()
    private var shownArtwork: String? = null
    private var chaptersJob: Job? = null
    private var tickJob: Job? = null
    private var sleepJob: Job? = null

    // Accumulo per le statistiche, scritto periodicamente nel database.
    private var pendingListenedMs = 0L
    private var pendingContentMs = 0L
    private var lastTickElapsed = 0L
    private var lastTickPosition = 0L
    private var lastFlush = 0L

    override fun onCreate() {
        super.onCreate()
        val dataSource = DefaultDataSource.Factory(this, OkHttpDataSource.Factory(okHttp))
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(listener)

        val sessionPlayer = object : ForwardingPlayer(player) {
            override fun getSeekBackIncrement() = settings.state.value.skipBackSec * 1000L
            override fun getSeekForwardIncrement() = settings.state.value.skipForwardSec * 1000L
            override fun seekBack() = seekTo((currentPosition - seekBackIncrement).coerceAtLeast(0))
            override fun seekForward() {
                val target = currentPosition + seekForwardIncrement
                seekTo(if (duration != C.TIME_UNSET) target.coerceAtMost(duration) else target)
            }
        }

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, sessionPlayer)
            .setCallback(SessionCallback())
            .setSessionActivity(openApp)
            .setMediaButtonPreferences(mediaButtons())
            .build()

        shake = ShakeDetector(this) {
            sleepTimer.extend()
            vibrate()
        }

        scope.launch { player.setPlaybackSpeed(settings.current().speed) }
        scope.launch {
            settings.flow.map { it.skipBackSec to it.skipForwardSec }.distinctUntilChanged().collect {
                session?.setMediaButtonPreferences(mediaButtons())
            }
        }
        scope.launch { sleepTimer.state.collect { onSleepStateChanged(it) } }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        val ep = current
        val position = player.currentPosition
        val listened = pendingListenedMs
        val content = pendingContentMs
        val ended = player.playbackState == Player.STATE_ENDED
        appScope.launch {
            ep ?: return@launch
            if (listened > 0) {
                db.listeningDao().add(today(), ep.episode.id, ep.episode.podcastId, listened, content)
            }
            if (!ended) episodes.savePosition(ep.episode.id, position)
        }
        shake.stop()
        session?.release()
        session = null
        player.release()
        scope.cancel()
        super.onDestroy()
    }

    // --- Pulsanti nella notifica ---

    private fun mediaButtons(): List<CommandButton> {
        val s = settings.state.value
        val backIcon = when (s.skipBackSec) {
            5 -> CommandButton.ICON_SKIP_BACK_5
            10 -> CommandButton.ICON_SKIP_BACK_10
            15 -> CommandButton.ICON_SKIP_BACK_15
            30 -> CommandButton.ICON_SKIP_BACK_30
            else -> CommandButton.ICON_SKIP_BACK
        }
        val forwardIcon = when (s.skipForwardSec) {
            5 -> CommandButton.ICON_SKIP_FORWARD_5
            10 -> CommandButton.ICON_SKIP_FORWARD_10
            15 -> CommandButton.ICON_SKIP_FORWARD_15
            30 -> CommandButton.ICON_SKIP_FORWARD_30
            else -> CommandButton.ICON_SKIP_FORWARD
        }
        return listOf(
            CommandButton.Builder(backIcon)
                .setDisplayName(getString(R.string.skip_back_seconds, s.skipBackSec))
                .setPlayerCommand(Player.COMMAND_SEEK_BACK)
                .setSlots(CommandButton.SLOT_BACK)
                .build(),
            CommandButton.Builder(forwardIcon)
                .setDisplayName(getString(R.string.skip_forward_seconds, s.skipForwardSec))
                .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
                .setSlots(CommandButton.SLOT_FORWARD)
                .build(),
        )
    }

    // --- Risoluzione degli episodi richiesti dai controller ---

    private inner class SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = scope.future {
            mediaItems.mapNotNull { item ->
                item.mediaId.toLongOrNull()?.let { episodes.get(it) }?.let(::buildItem)
            }.toMutableList()
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaItemsWithStartPosition> = scope.future {
            val item = mediaItems.firstOrNull()?.mediaId?.toLongOrNull()?.let { episodes.get(it) }
                ?: throw IllegalArgumentException("Unknown episode")
            val start = if (startPositionMs != C.TIME_UNSET && startPositionMs > 0) startPositionMs else resumePosition(item)
            MediaItemsWithStartPosition(listOf(buildItem(item)), 0, start)
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaItemsWithStartPosition> = scope.future {
            val id = settings.lastEpisodeId.first() ?: episodes.nextInQueue()
            val item = id?.let { episodes.get(it) } ?: throw UnsupportedOperationException("Nothing to resume")
            MediaItemsWithStartPosition(listOf(buildItem(item)), 0, resumePosition(item))
        }
    }

    private fun buildItem(item: EpisodeItem): MediaItem {
        val ep = item.episode
        val uri: Uri = ep.localPath?.let(::File)?.takeIf { it.exists() }?.let(Uri::fromFile) ?: ep.audioUrl.toUri()
        return MediaItem.Builder()
            .setMediaId(ep.id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(ep.title)
                    .setArtist(item.podcastTitle)
                    .setAlbumTitle(item.podcastTitle)
                    .setArtworkUri(item.artwork?.toUri())
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                    .apply { if (ep.durationMs > 0) setDurationMs(ep.durationMs) }
                    .build()
            )
            .build()
    }

    private fun resumePosition(item: EpisodeItem): Long {
        val ep = item.episode
        return when {
            ep.completed -> 0
            ep.durationMs > 0 && ep.positionMs > ep.durationMs - 5_000 -> 0
            else -> ep.positionMs
        }
    }

    // --- Eventi del player ---

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val id = mediaItem?.mediaId?.toLongOrNull()
            // Anche il cambio di copertina (replaceMediaItem) genera una transizione: va ignorata.
            if (id != null && id == current?.episode?.id) return
            onEpisodeChanged(id)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                startTicker()
            } else {
                tickJob?.cancel()
                accumulate()
                scope.launch { flush() }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    val id = current?.episode?.id ?: return
                    val duration = player.duration
                    if (duration > 0) scope.launch { episodes.setDuration(id, duration) }
                }
                Player.STATE_ENDED -> scope.launch { onEnded() }
                else -> Unit
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            lastTickPosition = newPosition.positionMs
            updateChapterArtwork()
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            scope.launch { settings.setSpeed(playbackParameters.speed) }
        }
    }

    private fun onEpisodeChanged(id: Long?) {
        accumulate()
        val previous = current
        val listened = pendingListenedMs
        val content = pendingContentMs
        pendingListenedMs = 0
        pendingContentMs = 0
        chaptersJob?.cancel()
        currentChapters = emptyList()
        current = null
        scope.launch {
            if (previous != null && listened > 0) {
                db.listeningDao().add(today(), previous.episode.id, previous.episode.podcastId, listened, content)
            }
            if (id == null) return@launch
            val item = episodes.get(id) ?: return@launch
            current = item
            shownArtwork = item.artwork
            settings.setLastEpisode(id)
            episodes.removeFromQueue(id)
            chaptersJob = scope.launch {
                chapters.ensureLoaded(id)
                chapters.observe(id).collect {
                    currentChapters = it
                    updateChapterArtwork()
                }
            }
        }
    }

    private suspend fun onEnded() {
        val item = current ?: return
        tickJob?.cancel()
        accumulate()
        flush(savePosition = false)
        episodes.setCompleted(item.episode.id, true, fromPlayback = true)
        val stopHere = sleepTimer.state.value == SleepState.EndOfEpisode
        if (stopHere) sleepTimer.cancel()
        val next = episodes.nextInQueue()?.let { episodes.get(it) }
        if (next == null) {
            // Coda finita: niente più da riprodurre, il mini-player sparisce.
            player.clearMediaItems()
            return
        }
        player.setMediaItem(buildItem(next), resumePosition(next))
        // Con lo sleep timer "fine episodio" il prossimo resta pronto, ma non parte.
        if (stopHere) return
        player.prepare()
        player.play()
    }

    // --- Avanzamento: statistiche, posizione, immagini dei capitoli ---

    private fun startTicker() {
        tickJob?.cancel()
        lastTickElapsed = SystemClock.elapsedRealtime()
        lastTickPosition = player.currentPosition
        tickJob = scope.launch {
            while (isActive) {
                delay(1_000)
                accumulate()
                updateChapterArtwork()
                if (SystemClock.elapsedRealtime() - lastFlush > 15_000) flush()
            }
        }
    }

    private fun accumulate() {
        val now = SystemClock.elapsedRealtime()
        val position = player.currentPosition
        val wall = now - lastTickElapsed
        val delta = position - lastTickPosition
        if (current != null && lastTickElapsed > 0 && wall in 1..5_000) {
            pendingListenedMs += wall
            // Esclude i salti (seek): conta solo l'avanzamento compatibile con la velocità.
            if (delta in 0..(wall * 4 + 500)) pendingContentMs += delta
        }
        lastTickElapsed = now
        lastTickPosition = position
    }

    private suspend fun flush(savePosition: Boolean = true) {
        lastFlush = SystemClock.elapsedRealtime()
        val item = current ?: return
        if (pendingListenedMs > 0) {
            val listened = pendingListenedMs
            val content = pendingContentMs
            pendingListenedMs = 0
            pendingContentMs = 0
            db.listeningDao().add(today(), item.episode.id, item.episode.podcastId, listened, content)
        }
        if (savePosition && player.playbackState != Player.STATE_ENDED && player.currentMediaItem != null) {
            episodes.savePosition(item.episode.id, player.currentPosition)
        }
    }

    /** Mostra l'immagine del capitolo corrente, se presente, al posto della copertina. */
    private fun updateChapterArtwork() {
        val item = current ?: return
        val mediaItem = player.currentMediaItem ?: return
        if (mediaItem.mediaId != item.episode.id.toString()) return
        val position = player.currentPosition
        val art = currentChapters.lastOrNull { it.startMs <= position }?.imageUrl ?: item.artwork
        if (art == shownArtwork) return
        shownArtwork = art
        player.replaceMediaItem(
            player.currentMediaItemIndex,
            mediaItem.buildUpon()
                .setMediaMetadata(mediaItem.mediaMetadata.buildUpon().setArtworkUri(art?.toUri()).build())
                .build(),
        )
    }

    // --- Sleep timer ---

    private fun onSleepStateChanged(state: SleepState) {
        sleepJob?.cancel()
        if (state is SleepState.Countdown) {
            if (settings.state.value.shakeToExtend) shake.start() else shake.stop()
            sleepJob = scope.launch {
                while (isActive) {
                    val countdown = sleepTimer.state.value as? SleepState.Countdown ?: break
                    val remaining = countdown.remainingMs
                    when {
                        remaining <= 0 -> {
                            player.pause()
                            player.volume = 1f
                            sleepTimer.cancel()
                        }
                        remaining < FADE_MS -> player.volume = remaining.toFloat() / FADE_MS
                        player.volume < 1f -> player.volume = 1f
                    }
                    delay(250)
                }
            }
        } else {
            shake.stop()
            player.volume = 1f
        }
    }

    private fun vibrate() {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun today() = LocalDate.now().toEpochDay()

    private companion object {
        const val FADE_MS = 10_000L
    }
}
