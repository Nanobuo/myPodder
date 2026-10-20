package com.nanobuo.mypodder.ui.library

import androidx.compose.foundation.clickable
import com.nanobuo.mypodder.ui.components.Wordmark
import com.nanobuo.mypodder.data.settings.NewBadgeStyle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nanobuo.mypodder.R
import com.nanobuo.mypodder.data.db.PodcastWithNew
import com.nanobuo.mypodder.data.opml.OpmlStatus
import com.nanobuo.mypodder.ui.components.Artwork
import com.nanobuo.mypodder.ui.components.EmptyState
import com.nanobuo.mypodder.ui.components.EpisodeRow
import com.nanobuo.mypodder.ui.components.LoadingBox
import com.nanobuo.mypodder.ui.components.OpmlStatusEffect
import com.nanobuo.mypodder.ui.components.rememberOpmlLaunchers

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryScreen(
    onPodcast: (Long) -> Unit,
    onEpisode: (Long) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val podcasts by viewModel.podcasts.collectAsStateWithLifecycle()
    val latest by viewModel.latest.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val refreshResult by viewModel.refreshResult.collectAsStateWithLifecycle()
    val opmlStatus by viewModel.opmlStatus.collectAsStateWithLifecycle()
    val grid by viewModel.grid.collectAsStateWithLifecycle()
    val badgeStyle by viewModel.badgeStyle.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    val opml = rememberOpmlLaunchers(viewModel::importOpml, viewModel::exportOpml)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    OpmlStatusEffect(opmlStatus, snackbar, viewModel::consumeOpml)
    LaunchedEffect(refreshResult) {
        val count = refreshResult ?: return@LaunchedEffect
        viewModel.consumeRefreshResult()
        snackbar.showSnackbar(
            if (count == 0) context.getString(R.string.no_new_episodes)
            else context.resources.getQuantityString(R.plurals.new_episodes, count, count)
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Wordmark() },
                scrollBehavior = scrollBehavior,
                actions = {
                    if (refreshing) {
                        LoadingIndicator(Modifier.size(40.dp))
                    } else {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Rounded.Refresh, stringResource(R.string.refresh))
                        }
                    }
                    if (tab == 0 && podcasts?.isNotEmpty() == true) {
                        IconButton(onClick = viewModel::toggleLayout) {
                            if (grid) {
                                Icon(Icons.AutoMirrored.Rounded.ViewList, stringResource(R.string.show_as_list))
                            } else {
                                Icon(Icons.Rounded.GridView, stringResource(R.string.show_as_grid))
                            }
                        }
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings)) }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.import_opml)) },
                                leadingIcon = { Icon(Icons.Rounded.FileDownload, null) },
                                onClick = { menuOpen = false; opml.import() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.export_opml)) },
                                leadingIcon = { Icon(Icons.Rounded.FileUpload, null) },
                                onClick = { menuOpen = false; opml.export() },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            (opmlStatus as? OpmlStatus.Importing)?.let { s ->
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.opml_importing, s.done, s.total), style = MaterialTheme.typography.bodyMedium)
                    LinearWavyProgressIndicator(
                        progress = { if (s.total > 0) s.done.toFloat() / s.total else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            }
            val list = podcasts
            when {
                list == null -> LoadingBox()
                list.isEmpty() -> EmptyState(Icons.Rounded.Podcasts, stringResource(R.string.library_empty)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onSearch) {
                            Icon(Icons.Rounded.Search, null, Modifier.size(18.dp))
                            Text(stringResource(R.string.find_podcasts), Modifier.padding(start = 8.dp))
                        }
                        OutlinedButton(onClick = opml.import) { Text(stringResource(R.string.import_opml)) }
                    }
                }
                else -> {
                    PrimaryTabRow(selectedTabIndex = tab) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.tab_podcasts)) })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.tab_episodes)) })
                    }
                    when {
                        tab == 1 -> LatestEpisodes(latest, onEpisode)
                        grid -> PodcastGrid(list, badgeStyle, onPodcast)
                        else -> PodcastList(list, badgeStyle, onPodcast)
                    }
                }
            }
        }
    }
}

@Composable
private fun PodcastGrid(podcasts: List<PodcastWithNew>, badgeStyle: NewBadgeStyle, onPodcast: (Long) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(podcasts, key = { it.podcast.id }) { item ->
            val podcast = item.podcast
            Column(
                Modifier
                    .clickable { onPodcast(podcast.id) }
                    .padding(2.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                ) {
                    Artwork(podcast.imageUrl, Modifier.fillMaxSize(), size = null, corner = 20.dp)
                    if (item.newCount > 0 && badgeStyle != NewBadgeStyle.NONE) {
                        NewBadge(
                            item.newCount,
                            badgeStyle,
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp),
                        )
                    }
                }
                Text(
                    podcast.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp, start = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun PodcastList(podcasts: List<PodcastWithNew>, badgeStyle: NewBadgeStyle, onPodcast: (Long) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(podcasts, key = { it.podcast.id }) { item ->
            val podcast = item.podcast
            ListItem(
                headlineContent = { Text(podcast.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                supportingContent = if (podcast.author != null) {
                    { Text(podcast.author, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                } else {
                    null
                },
                leadingContent = { Artwork(podcast.imageUrl, size = 56.dp) },
                trailingContent = if (item.newCount > 0 && badgeStyle != NewBadgeStyle.NONE) {
                    { NewBadge(item.newCount, badgeStyle) }
                } else {
                    null
                },
                modifier = Modifier.clickable { onPodcast(podcast.id) },
            )
        }
    }
}

@Composable
private fun LatestEpisodes(items: List<com.nanobuo.mypodder.data.db.EpisodeItem>, onEpisode: (Long) -> Unit) {
    if (items.isEmpty()) {
        EmptyState(Icons.Rounded.NewReleases, stringResource(R.string.no_episodes))
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(items, key = { it.episode.id }) { item ->
            EpisodeRow(item, onClick = { onEpisode(item.episode.id) })
            HorizontalDivider(Modifier.padding(start = 84.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest)
        }
    }
}

/** Segnala i nuovi episodi con il numero oppure con un semplice punto. */
@Composable
private fun NewBadge(count: Int, style: NewBadgeStyle, modifier: Modifier = Modifier) {
    val dot = style == NewBadgeStyle.DOT
    val description = pluralStringResource(R.plurals.new_episodes, count, count)
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(50),
        shadowElevation = 2.dp,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
        modifier = modifier.semantics { contentDescription = description },
    ) {
        if (dot) {
            Box(Modifier.size(12.dp))
        } else {
            Text(
                if (count > 99) "99+" else count.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}
