package com.nanobuo.mypodder.ui.episode

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
                    Text(ep.title, style = MaterialTheme.typography.titleLarge, maxLines = 4, overflow = TextOverflow.Ellipsis)
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
                Text(stringResource(R.string.chapters), style = MaterialTheme.typography.titleMedium)
                Column(Modifier.clip(RoundedCornerShape(20.dp))) {
                    chapters.forEachIndexed { index, chapter ->
                        ListItem(
                            headlineContent = { Text(chapter.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            leadingContent = {
                                Text(
                                    formatClock(chapter.startMs),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            },
                            trailingContent = if (chapter.imageUrl != null) {
                                { Artwork(chapter.imageUrl, size = 40.dp, corner = 8.dp) }
                            } else {
                                null
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier.clickable { actions.play(ep.id, chapter.startMs) },
                        )
                        if (index < chapters.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.surface)
                        }
                    }
                }
            }

            ep.description?.takeIf { it.isNotBlank() }?.let { html ->
                Text(stringResource(R.string.description), style = MaterialTheme.typography.titleMedium)
                HtmlDescription(html, onSeek = { ms -> actions.play(ep.id, ms) })
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}
