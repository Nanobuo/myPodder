package com.nanobuo.mypodder.ui.episode

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import com.nanobuo.mypodder.data.db.Chapter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nanobuo.mypodder.R
import com.nanobuo.mypodder.ui.theme.CoverTheme
import com.nanobuo.mypodder.ui.components.Artwork
import com.nanobuo.mypodder.ui.components.DownloadButton
import com.nanobuo.mypodder.ui.components.EpisodeMenu
import com.nanobuo.mypodder.ui.components.LoadingBox
import com.nanobuo.mypodder.ui.components.LocalEpisodeActions
import com.nanobuo.mypodder.ui.components.LocalPlaybackHighlight
import com.nanobuo.mypodder.util.formatClock
import com.nanobuo.mypodder.util.formatDate
import com.nanobuo.mypodder.util.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeScreen(
    onBack: () -> Unit,
    onPodcast: (Long) -> Unit,
    viewModel: EpisodeViewModel = hiltViewModel(),
) {
    val item by viewModel.episode.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val actions = LocalEpisodeActions.current
    val highlight = LocalPlaybackHighlight.current

    CoverTheme(item?.podcastImage) {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                    },
                    actions = {
                        item?.let {
                            DownloadButton(it)
                            EpisodeMenu(it, it.episode.id in highlight.queueIds)
                        }
                    },
                )
            },
        ) { padding ->
            val current = item
            if (current == null) {
                LoadingBox(Modifier.padding(padding))
                return@Scaffold
            }
            val ep = current.episode
            val isCurrent = highlight.episodeId == ep.id
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Artwork(current.artwork, size = 112.dp, corner = 24.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            current.podcastTitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onPodcast(ep.podcastId) },
                        )
                        Text(ep.title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            listOf(formatDate(ep.pubDate), if (ep.durationMs > 0) formatDuration(ep.durationMs) else "")
                                .filter { it.isNotEmpty() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Button(
                    onClick = { if (isCurrent) actions.togglePlayPause() else actions.play(ep.id) },
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    val playing = isCurrent && highlight.isPlaying
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            playing -> stringResource(R.string.pause)
                            ep.positionMs > 0 && !ep.completed -> stringResource(R.string.resume_from, formatClock(ep.positionMs))
                            else -> stringResource(R.string.play)
                        }
                    )
                }

                if (chapters.isNotEmpty()) {
                    val livePosition by viewModel.livePosition.collectAsStateWithLifecycle(initialValue = 0L)
                    val position = if (isCurrent) livePosition else ep.positionMs
                    val started = isCurrent || (ep.positionMs > 0 && !ep.completed)
                    val currentIndex = if (started) chapters.indexOfLast { it.startMs <= position } else -1
                    ChaptersSection(
                        chapters = chapters,
                        currentIndex = currentIndex,
                        onSelect = { actions.play(ep.id, it.startMs) },
                    )
                }

                ep.description?.takeIf { it.isNotBlank() }?.let { html ->
                    Text(stringResource(R.string.description), style = MaterialTheme.typography.titleMedium)
                    HtmlDescription(html, onSeek = { ms -> actions.play(ep.id, ms) })
                }
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}

/** Capitoli: chiusi di default, con il capitolo in corso sempre in evidenza se l'episodio è avviato. */
@Composable
private fun ChaptersSection(chapters: List<Chapter>, currentIndex: Int, onSelect: (Chapter) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(if (open) 180f else 0f, label = "arrow")

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { open = !open }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.chapters) + " · " + chapters.size,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                stringResource(if (open) R.string.collapse else R.string.expand),
                Modifier.rotate(arrowRotation),
            )
        }

        chapters.getOrNull(currentIndex)?.let { current ->
            Card(
                onClick = { onSelect(current) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (current.imageUrl != null) {
                        Artwork(current.imageUrl, size = 56.dp, corner = 12.dp)
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.current_chapter, currentIndex + 1, chapters.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            current.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            formatClock(current.startMs),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.clip(RoundedCornerShape(20.dp))) {
                chapters.forEachIndexed { index, chapter ->
                    val isCurrent = index == currentIndex
                    ListItem(
                        headlineContent = {
                            Text(
                                chapter.title,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        leadingContent = {
                            Text(
                                formatClock(chapter.startMs),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingContent = when {
                            isCurrent -> {
                                { Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary) }
                            }
                            chapter.imageUrl != null -> {
                                { Artwork(chapter.imageUrl, size = 40.dp, corner = 8.dp) }
                            }
                            else -> null
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer,
                        ),
                        modifier = Modifier.clickable { onSelect(chapter) },
                    )
                    if (index < chapters.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.surface)
                    }
                }
            }
        }
    }
}
