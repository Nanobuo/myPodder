package com.nanobuo.mypodder.ui.podcast

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nanobuo.mypodder.R
import com.nanobuo.mypodder.ui.theme.CoverTheme
import com.nanobuo.mypodder.ui.components.Artwork
import com.nanobuo.mypodder.ui.components.EpisodeRow
import com.nanobuo.mypodder.ui.components.LoadingBox
import com.nanobuo.mypodder.util.stripHtml

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PodcastScreen(
    onBack: () -> Unit,
    onEpisode: (Long) -> Unit,
    viewModel: PodcastViewModel = hiltViewModel(),
) {
    val podcast by viewModel.podcast.collectAsStateWithLifecycle()
    val episodes by viewModel.episodeList.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var confirmUnsubscribe by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { viewModel.markSeen() } }

    CoverTheme(podcast?.imageUrl) {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            topBar = {
                TopAppBar(
                    title = { Text(podcast?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                    },
                    actions = {
                        if (refreshing) LoadingIndicator(Modifier.size(40.dp))
                        else IconButton(onClick = viewModel::refresh) { Icon(Icons.Rounded.Refresh, stringResource(R.string.refresh)) }
                    },
                )
            },
        ) { padding ->
            val p = podcast
            val list = episodes
            if (p == null || list == null) {
                LoadingBox(Modifier.padding(padding))
                return@Scaffold
            }
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item(key = "header") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Artwork(p.imageUrl, size = 120.dp, corner = 24.dp)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(p.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                p.author?.let {
                                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                }
                                Text(
                                    pluralStringResource(R.plurals.episodes_count, list.size, list.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        p.description?.stripHtml()?.takeIf { it.isNotBlank() }?.let { desc ->
                            Text(
                                desc,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = if (expanded) Int.MAX_VALUE else 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .animateContentSize()
                                    .clickable { expanded = !expanded },
                            )
                        }
                        if (error) {
                            Text(stringResource(R.string.refresh_error), color = MaterialTheme.colorScheme.error)
                        }
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.auto_download)) },
                            supportingContent = { Text(stringResource(R.string.auto_download_summary)) },
                            trailingContent = { Switch(checked = p.autoDownload, onCheckedChange = viewModel::setAutoDownload) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedButton(onClick = { confirmUnsubscribe = true }) { Text(stringResource(R.string.unsubscribe)) }
                    }
                    HorizontalDivider()
                }
                items(list, key = { it.episode.id }) { item ->
                    EpisodeRow(item, onClick = { onEpisode(item.episode.id) }, showPodcast = false, dimPlayed = true)
                }
            }
        }
    }

    if (confirmUnsubscribe) {
        AlertDialog(
            onDismissRequest = { confirmUnsubscribe = false },
            title = { Text(stringResource(R.string.unsubscribe)) },
            text = { Text(stringResource(R.string.unsubscribe_confirm, podcast?.title.orEmpty())) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnsubscribe = false
                    viewModel.unsubscribe(onBack)
                }) { Text(stringResource(R.string.unsubscribe)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnsubscribe = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
