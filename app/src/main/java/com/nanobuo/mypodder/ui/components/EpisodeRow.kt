package com.nanobuo.mypodder.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nanobuo.mypodder.R
import com.nanobuo.mypodder.data.db.DownloadState
import com.nanobuo.mypodder.data.db.EpisodeItem
import com.nanobuo.mypodder.util.formatDate
import com.nanobuo.mypodder.util.formatDuration

@Composable
fun EpisodeRow(
    item: EpisodeItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showPodcast: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    /** Mostra più scuri gli episodi già ascoltati. */
    dimPlayed: Boolean = false,
) {
    val ep = item.episode
    val actions = LocalEpisodeActions.current
    val highlight = LocalPlaybackHighlight.current
    val isCurrent = highlight.episodeId == ep.id
    val inQueue = ep.id in highlight.queueIds

    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .alpha(if (dimPlayed && ep.completed) 0.45f else 1f)
            .padding(start = if (leading != null) 4.dp else 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Artwork(item.artwork, size = 56.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                ep.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (ep.isNew) FontWeight.Bold else FontWeight.Medium,
                color = if (ep.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = buildList {
                if (showPodcast) add(item.podcastTitle)
                formatDate(ep.pubDate).takeIf { it.isNotEmpty() }?.let(::add)
                if (ep.durationMs > 0) {
                    val remaining = ep.durationMs - ep.positionMs
                    add(
                        if (ep.positionMs > 0 && !ep.completed) stringResource(R.string.remaining, formatDuration(remaining))
                        else formatDuration(ep.durationMs)
                    )
                }
            }.joinToString(" · ")
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (ep.completed) {
                    Icon(
                        Icons.Rounded.CheckCircle, stringResource(R.string.played),
                        Modifier
                            .size(14.dp)
                            .padding(end = 2.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (ep.positionMs > 0 && ep.durationMs > 0 && !ep.completed) {
                LinearProgressIndicator(
                    progress = { (ep.positionMs.toFloat() / ep.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .padding(top = 4.dp),
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            DownloadButton(item)
            FilledTonalIconButton(onClick = {
                if (isCurrent) actions.togglePlayPause() else actions.play(ep.id)
            }) {
                if (isCurrent && highlight.isPlaying) {
                    Icon(Icons.Rounded.Pause, stringResource(R.string.pause))
                } else {
                    Icon(Icons.Rounded.PlayArrow, stringResource(R.string.play))
                }
            }
            EpisodeMenu(item, inQueue)
        }
    }
}

@Composable
fun DownloadButton(item: EpisodeItem) {
    val ep = item.episode
    val actions = LocalEpisodeActions.current
    IconButton(onClick = {
        when (ep.downloadState) {
            DownloadState.NONE, DownloadState.FAILED -> actions.download(ep.id)
            else -> actions.deleteDownload(ep.id)
        }
    }) {
        when (ep.downloadState) {
            DownloadState.QUEUED -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 3.dp)
            DownloadState.DOWNLOADING -> Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { ep.downloadProgress / 100f },
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 3.dp,
                )
            }
            DownloadState.DONE -> Icon(
                Icons.Rounded.DownloadDone, stringResource(R.string.delete_download),
                tint = MaterialTheme.colorScheme.primary,
            )
            DownloadState.FAILED -> Icon(
                Icons.Rounded.ErrorOutline, stringResource(R.string.download_failed),
                tint = MaterialTheme.colorScheme.error,
            )
            else -> Icon(Icons.Rounded.Download, stringResource(R.string.download))
        }
    }
}

@Composable
fun EpisodeMenu(item: EpisodeItem, inQueue: Boolean) {
    val ep = item.episode
    val actions = LocalEpisodeActions.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.play_next)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null) },
                onClick = { open = false; actions.playNext(ep.id) },
            )
            if (inQueue) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.remove_from_queue)) },
                    leadingIcon = { Icon(Icons.Rounded.PlaylistRemove, null) },
                    onClick = { open = false; actions.removeFromQueue(ep.id) },
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.add_to_queue)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                    onClick = { open = false; actions.addToQueue(ep.id) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(if (ep.completed) R.string.mark_unplayed else R.string.mark_played)) },
                leadingIcon = {
                    Icon(if (ep.completed) Icons.Rounded.RadioButtonUnchecked else Icons.Rounded.CheckCircle, null)
                },
                onClick = { open = false; actions.setPlayed(ep.id, !ep.completed) },
            )
            if (ep.downloadState != DownloadState.NONE) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_download)) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                    onClick = { open = false; actions.deleteDownload(ep.id) },
                )
            }
        }
    }
}
