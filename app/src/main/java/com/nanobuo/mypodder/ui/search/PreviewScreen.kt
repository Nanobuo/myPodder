package com.nanobuo.mypodder.ui.search

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nanobuo.mypodder.R
import com.nanobuo.mypodder.ui.components.Artwork
import com.nanobuo.mypodder.ui.components.EmptyState
import com.nanobuo.mypodder.ui.components.LoadingBox
import com.nanobuo.mypodder.util.formatDate
import com.nanobuo.mypodder.util.formatDuration
import com.nanobuo.mypodder.util.stripHtml

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PreviewScreen(
    initialTitle: String?,
    initialImage: String?,
    onBack: () -> Unit,
    onSubscribed: (Long) -> Unit,
    viewModel: PreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val subscribing by viewModel.subscribing.collectAsStateWithLifecycle()
    val subscribedId by viewModel.subscribedId.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.preview)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            PreviewState.Loading -> Column(Modifier.padding(padding)) {
                if (initialTitle != null) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Artwork(initialImage, size = 96.dp, corner = 20.dp)
                        Spacer(Modifier.width(16.dp))
                        Text(initialTitle, style = MaterialTheme.typography.titleLarge)
                    }
                }
                LoadingBox()
            }
            PreviewState.Error -> EmptyState(
                Icons.Rounded.ErrorOutline,
                stringResource(R.string.feed_error),
                Modifier.padding(padding),
            ) {
                OutlinedButton(onClick = viewModel::load) { Text(stringResource(R.string.retry)) }
            }
            is PreviewState.Loaded -> {
                val feed = s.feed
                LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                    item {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Artwork(feed.imageUrl ?: initialImage, size = 120.dp, corner = 24.dp)
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(feed.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    feed.author?.let {
                                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Text(
                                        pluralStringResource(R.plurals.episodes_count, feed.episodes.size, feed.episodes.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            val id = subscribedId
                            if (id != null) {
                                FilledTonalButton(onClick = { onSubscribed(id) }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.subscribed_open))
                                }
                            } else {
                                Button(
                                    onClick = { viewModel.subscribe(onSubscribed) },
                                    enabled = !subscribing,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    if (subscribing) {
                                        LoadingIndicator(Modifier.size(24.dp))
                                    } else {
                                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.subscribe))
                                }
                            }
                            feed.description?.stripHtml()?.takeIf { it.isNotBlank() }?.let { desc ->
                                Text(
                                    desc,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = if (expanded) Int.MAX_VALUE else 5,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .animateContentSize()
                                        .clickable { expanded = !expanded },
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                    items(feed.episodes.distinctBy { it.guid }.sortedByDescending { it.pubDate }.take(100), key = { it.guid }) { ep ->
                        ListItem(
                            headlineContent = { Text(ep.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(
                                    listOf(formatDate(ep.pubDate), if (ep.durationMs > 0) formatDuration(ep.durationMs) else "")
                                        .filter { it.isNotEmpty() }.joinToString(" · ")
                                )
                            },
                            leadingContent = { Artwork(ep.imageUrl ?: feed.imageUrl, size = 48.dp, corner = 10.dp) },
                        )
                    }
                }
            }
        }
    }
}
