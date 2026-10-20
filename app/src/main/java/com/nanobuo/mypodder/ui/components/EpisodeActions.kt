package com.nanobuo.mypodder.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

/** Azioni sugli episodi disponibili da qualsiasi schermata. */
interface EpisodeActions {
    fun play(episodeId: Long, startMs: Long? = null)
    fun togglePlayPause()
    fun download(episodeId: Long)
    fun deleteDownload(episodeId: Long)
    fun addToQueue(episodeId: Long)
    fun playNext(episodeId: Long)
    fun removeFromQueue(episodeId: Long)
    fun setPlayed(episodeId: Long, played: Boolean)
}

val LocalEpisodeActions = staticCompositionLocalOf<EpisodeActions> { error("EpisodeActions not provided") }

/** Stato di riproduzione minimo per evidenziare l'episodio corrente nelle liste. */
data class PlaybackHighlight(val episodeId: Long?, val isPlaying: Boolean, val queueIds: Set<Long>)

val LocalPlaybackHighlight = compositionLocalOf { PlaybackHighlight(null, false, emptySet()) }
