package com.nanobuo.mypodder.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.nanobuo.mypodder.data.db.EpisodeItem
import kotlinx.coroutines.launch
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nanobuo.mypodder.R
import com.nanobuo.mypodder.data.db.Chapter
import com.nanobuo.mypodder.playback.SleepState
import com.nanobuo.mypodder.ui.components.Artwork
import com.nanobuo.mypodder.util.formatClock
import java.util.Locale
import kotlin.math.roundToInt

private enum class Sheet { NONE, SPEED, SLEEP, CHAPTERS, QUEUE }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerScreen(
    onClose: () -> Unit,
    onEpisode: (Long) -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val episode by viewModel.episode.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val position by viewModel.position.collectAsStateWithLifecycle(initialValue = viewModel.connection.position)
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val sleepState by viewModel.sleepState.collectAsStateWithLifecycle()
    val sleepRemaining by viewModel.sleepRemaining.collectAsStateWithLifecycle(initialValue = null)
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf(Sheet.NONE) }

    // Chiude il player quando la riproduzione viene interrotta (non prima della connessione).
    var hadMedia by remember { mutableStateOf(false) }
    LaunchedEffect(state.hasMedia) {
        if (state.hasMedia) hadMedia = true else if (hadMedia) onClose()
    }

    val duration = state.durationMs.takeIf { it > 0 } ?: episode?.episode?.durationMs ?: 0
    val currentChapterIndex = chapters.indexOfLast { it.startMs <= position }
    val currentChapter = chapters.getOrNull(currentChapterIndex)

    // Trascinando verso il basso il player si chiude e si torna alla schermata precedente.
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    val dismissPx = with(LocalDensity.current) { 140.dp.toPx() }

    Column(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(0, dragOffset.value.roundToInt()) }
            .graphicsLayer { alpha = 1f - (dragOffset.value / (dismissPx * 4)).coerceIn(0f, 0.5f) }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    scope.launch { dragOffset.snapTo((dragOffset.value + delta).coerceAtLeast(0f)) }
                },
                onDragStopped = { velocity ->
                    if (dragOffset.value > dismissPx || velocity > 1_800f) onClose()
                    else dragOffset.animateTo(0f)
                },
            )
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.close)) }
            Text(
                stringResource(R.string.now_playing),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { state.episodeId?.let(onEpisode) }) {
                Icon(Icons.Rounded.Info, stringResource(R.string.episode_details))
            }
        }

        // Copertina quadrata che occupa lo spazio rimasto libero, senza sovrapporsi al resto.
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(
                targetState = state.artworkUri ?: episode?.artwork,
                label = "artwork",
                modifier = Modifier.aspectRatio(1f, matchHeightConstraintsFirst = true),
            ) { url ->
                val corner by animateDpAsState(if (state.isPlaying) 28.dp else 48.dp, label = "corner")
                Artwork(url, Modifier.fillMaxSize(), size = null, corner = corner)
            }
        }

        Text(
            state.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            state.podcastTitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )

        Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
            if (currentChapter != null) {
                AssistChip(
                    onClick = { sheet = Sheet.CHAPTERS },
                    label = {
                        Text(
                            currentChapter.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.List, null, Modifier.size(18.dp)) },
                )
            }
        }

        // Barra di avanzamento: durante il trascinamento mostra la posizione scelta.
        var dragging by remember { mutableStateOf(false) }
        var dragValue by remember { mutableFloatStateOf(0f) }
        val shown = if (dragging) (dragValue * duration).toLong() else position
        Slider(
            value = if (dragging) dragValue else if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                viewModel.seekTo((dragValue * duration).toLong())
                dragging = false
            },
            enabled = duration > 0,
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatClock(shown), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            // Tocco: alterna tempo rimanente e durata totale.
            Text(
                if (settings.showRemaining) "-" + formatClock(duration - shown) else formatClock(duration),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = viewModel::toggleTimeDisplay)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkipButton(Icons.Rounded.FastRewind, settings.skipBackSec, stringResource(R.string.skip_back), viewModel::skipBack)
            val playCorner by animateDpAsState(if (state.isPlaying) 22.dp else 36.dp, label = "playShape")
            FilledIconButton(
                onClick = viewModel::togglePlayPause,
                modifier = Modifier.size(72.dp),
                shape = RoundedCornerShape(playCorner),
            ) {
                AnimatedContent(state.isPlaying, label = "playIcon") { playing ->
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        stringResource(if (playing) R.string.pause else R.string.play),
                        Modifier.size(36.dp),
                    )
                }
            }
            SkipButton(Icons.Rounded.FastForward, settings.skipForwardSec, stringResource(R.string.skip_forward), viewModel::skipForward)
        }

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { sheet = Sheet.SPEED }) {
                Icon(Icons.Rounded.Speed, null, Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(formatSpeed(state.speed))
            }
            TextButton(onClick = { sheet = Sheet.SLEEP }) {
                Icon(Icons.Rounded.Bedtime, null, Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    when (sleepState) {
                        is SleepState.Countdown -> formatClock(sleepRemaining ?: 0)
                        SleepState.EndOfEpisode -> stringResource(R.string.sleep_end_short)
                        SleepState.Off -> stringResource(R.string.sleep_timer_short)
                    }
                )
            }
            IconButton(onClick = { sheet = Sheet.QUEUE }, enabled = queue.isNotEmpty()) {
                BadgedBox(badge = { if (queue.isNotEmpty()) Badge { Text(queue.size.toString()) } }) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.nav_queue))
                }
            }
            IconButton(onClick = viewModel::playNextInQueue, enabled = queue.isNotEmpty()) {
                Icon(Icons.Rounded.SkipNext, stringResource(R.string.next_in_queue))
            }
        }
    }

    if (sheet != Sheet.NONE) {
        ModalBottomSheet(onDismissRequest = { sheet = Sheet.NONE }) {
            when (sheet) {
                Sheet.SPEED -> SpeedSheet(state.speed, viewModel::setSpeed)
                Sheet.SLEEP -> SleepSheet(
                    state = sleepState,
                    remaining = sleepRemaining,
                    shakeEnabled = settings.shakeToExtend,
                    onStart = { viewModel.startSleep(it); sheet = Sheet.NONE },
                    onEndOfEpisode = { viewModel.sleepAtEndOfEpisode(); sheet = Sheet.NONE },
                    onExtend = viewModel::extendSleep,
                    onCancel = { viewModel.cancelSleep(); sheet = Sheet.NONE },
                )
                Sheet.CHAPTERS -> ChaptersSheet(chapters, currentChapterIndex) {
                    viewModel.seekTo(it.startMs)
                    sheet = Sheet.NONE
                }
                Sheet.QUEUE -> QueueSheet(
                    items = queue,
                    onPlay = { viewModel.playFromQueue(it); sheet = Sheet.NONE },
                    onRemove = viewModel::removeFromQueue,
                )
                Sheet.NONE -> Unit
            }
        }
    }
}

@Composable
private fun SkipButton(icon: androidx.compose.ui.graphics.vector.ImageVector, seconds: Int, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.size(64.dp)) {
            Icon(icon, label, Modifier.size(36.dp))
        }
        Text("${seconds}s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatSpeed(speed: Float): String {
    val s = String.format(Locale.ROOT, "%.2f", speed).trimEnd('0').trimEnd('.')
    return "${s}×"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeedSheet(speed: Float, onChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.playback_speed), style = MaterialTheme.typography.titleMedium)
        Text(formatSpeed(speed), style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(vertical = 12.dp))
        Slider(
            value = speed,
            onValueChange = { onChange((it * 20).roundToInt() / 20f) },
            valueRange = 0.5f..3f,
            steps = 49,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            listOf(0.8f, 1f, 1.2f, 1.5f, 1.75f, 2f, 2.5f).forEach { preset ->
                FilterChip(
                    selected = kotlin.math.abs(preset - speed) < 0.01f,
                    onClick = { onChange(preset) },
                    label = { Text(formatSpeed(preset)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepSheet(
    state: SleepState,
    remaining: Long?,
    shakeEnabled: Boolean,
    onStart: (Int) -> Unit,
    onEndOfEpisode: () -> Unit,
    onExtend: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.sleep_timer), style = MaterialTheme.typography.titleMedium)
        when (state) {
            is SleepState.Countdown -> {
                Text(
                    stringResource(R.string.sleep_remaining, formatClock(remaining ?: 0)),
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (shakeEnabled) {
                    Text(
                        stringResource(R.string.sleep_shake_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onExtend) { Text(stringResource(R.string.sleep_extend)) }
                    OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.sleep_cancel)) }
                }
            }
            SleepState.EndOfEpisode -> {
                Text(stringResource(R.string.sleep_end_active), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.sleep_cancel)) }
            }
            SleepState.Off -> Unit
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 15, 30, 45, 60, 90).forEach { minutes ->
                FilterChip(
                    selected = false,
                    onClick = { onStart(minutes) },
                    label = { Text(stringResource(R.string.minutes_short, minutes)) },
                )
            }
            FilterChip(
                selected = state == SleepState.EndOfEpisode,
                onClick = onEndOfEpisode,
                label = { Text(stringResource(R.string.sleep_end_of_episode)) },
            )
        }
    }
}

@Composable
private fun QueueSheet(items: List<EpisodeItem>, onPlay: (Long) -> Unit, onRemove: (Long) -> Unit) {
    Column {
        Text(
            stringResource(R.string.up_next),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (items.isEmpty()) {
            Text(
                stringResource(R.string.queue_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        LazyColumn(modifier = Modifier.padding(bottom = 16.dp)) {
            itemsIndexed(items, key = { _, item -> item.episode.id }) { _, item ->
                ListItem(
                    headlineContent = { Text(item.episode.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(item.podcastTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Artwork(item.artwork, size = 48.dp, corner = 10.dp) },
                    trailingContent = {
                        IconButton(onClick = { onRemove(item.episode.id) }) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.remove_from_queue))
                        }
                    },
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onPlay(item.episode.id) },
                )
            }
        }
    }
}

@Composable
private fun ChaptersSheet(chapters: List<Chapter>, current: Int, onSelect: (Chapter) -> Unit) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (current - 1).coerceAtLeast(0))
    Column {
        Text(
            stringResource(R.string.chapters),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(state = listState, modifier = Modifier.padding(bottom = 16.dp)) {
            itemsIndexed(chapters, key = { _, c -> c.startMs }) { index, chapter ->
                val selected = index == current
                ListItem(
                    headlineContent = {
                        Text(
                            chapter.title,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = { Text(formatClock(chapter.startMs)) },
                    leadingContent = if (chapter.imageUrl != null) {
                        { Artwork(chapter.imageUrl, size = 48.dp, corner = 8.dp) }
                    } else {
                        null
                    },
                    colors = if (selected) {
                        ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    } else {
                        ListItemDefaults.colors()
                    },
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelect(chapter) },
                )
            }
        }
    }
}
