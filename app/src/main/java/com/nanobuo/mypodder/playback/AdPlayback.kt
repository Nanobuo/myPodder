package com.nanobuo.mypodder.playback

import com.nanobuo.mypodder.data.ads.AdRange
import com.nanobuo.mypodder.data.ads.AdRepository
import com.nanobuo.mypodder.data.chapters.ChapterRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Pubblicità dell'episodio in riproduzione e pulsante per saltarle (player e mini-player). */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class AdPlayback @Inject constructor(
    private val connection: PlayerConnection,
    private val ads: AdRepository,
    private val chapters: ChapterRepository,
) {
    private val ranges: Flow<List<AdRange>> = connection.state.map { it.episodeId }.distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else ads.observe(id, chapters.observe(id)) { connection.state.value.durationMs }
        }

    /** Pubblicità in corso: si propone di saltarla fino a poco prima della fine. */
    val current: Flow<AdRange?> = combine(connection.positionFlow(500), ranges) { pos, list ->
        list.firstOrNull { pos >= it.startMs && pos < it.endMs - 1_500 }
    }.distinctUntilChanged()

    fun skip(ad: AdRange) = connection.seekTo(ad.endMs)
}
