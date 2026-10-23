package com.nanobuo.mypodder.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nanobuo.mypodder.R
import com.nanobuo.mypodder.data.discover.Countries
import com.nanobuo.mypodder.data.discover.DiscoverContent
import com.nanobuo.mypodder.data.search.SearchResult
import com.nanobuo.mypodder.ui.components.Artwork
import com.nanobuo.mypodder.ui.components.EmptyState
import com.nanobuo.mypodder.ui.components.LoadingBox
import com.nanobuo.mypodder.ui.nav.OnTabReselected
import com.nanobuo.mypodder.ui.nav.SearchRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onPreview: (url: String, title: String?, image: String?) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val discover by viewModel.discoverContent.collectAsStateWithLifecycle()
    val country by viewModel.country.collectAsStateWithLifecycle()
    val automatic by viewModel.countryIsAutomatic.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val isUrl = SearchViewModel.looksLikeUrl(query.trim())
    var pickCountry by remember { mutableStateOf(false) }
    val countryInfo = remember(country) { Countries.of(country) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_search)) },
                actions = {
                    // Paese di ricerca e classifiche.
                    AssistChip(
                        onClick = { pickCountry = true },
                        label = { Text(countryInfo.flag + "  " + countryInfo.name, maxLines = 1) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            TextField(
                value = query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.clear))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focus.clearFocus()
                    if (isUrl) onPreview(query.trim(), null, null)
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when {
                isUrl -> LazyColumn {
                    item {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.open_feed)) },
                            supportingContent = { Text(query.trim(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Icon(Icons.Rounded.Link, null) },
                            modifier = Modifier.clickable { onPreview(query.trim(), null, null) },
                        )
                    }
                }
                state is SearchState.Loading -> LoadingBox()
                state is SearchState.Error -> EmptyState(Icons.Rounded.WifiOff, stringResource(R.string.search_error))
                state is SearchState.Results -> {
                    val items = (state as SearchState.Results).items
                    if (items.isEmpty()) {
                        EmptyState(Icons.Rounded.SearchOff, stringResource(R.string.search_no_results))
                    } else {
                        val listState = rememberLazyListState()
                        OnTabReselected(SearchRoute::class) { listState.animateScrollToItem(0) }
                        LazyColumn(Modifier.fillMaxSize(), state = listState) {
                            resultItems(items, onPreview)
                            if (viewModel.usesPodcastIndex) footer()
                        }
                    }
                }
                else -> DiscoverList(discover, countryInfo.name, viewModel.usesPodcastIndex, onPreview)
            }
        }
    }

    if (pickCountry) {
        CountryPickerDialog(
            selected = country,
            automatic = automatic,
            onSelect = {
                viewModel.setCountry(it)
                pickCountry = false
            },
            onDismiss = { pickCountry = false },
        )
    }
}

/** Scheda Cerca a riposo: consigliati, classifica locale e tendenze. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DiscoverList(
    content: DiscoverContent,
    countryName: String,
    poweredByPodcastIndex: Boolean,
    onPreview: (String, String?, String?) -> Unit,
) {
    val listState = rememberLazyListState()
    OnTabReselected(SearchRoute::class) { listState.animateScrollToItem(0) }
    val empty = content.suggestions.isEmpty() && content.topCharts.isEmpty() && content.trending.isEmpty()
    if (empty) {
        if (content.loading) {
            LoadingBox()
        } else {
            EmptyState(Icons.Rounded.Search, stringResource(R.string.search_intro))
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        if (content.suggestions.isNotEmpty()) {
            item(key = "suggested-title") { SectionTitle(stringResource(R.string.suggested_for_you)) }
            item(key = "suggested") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(content.suggestions, key = { it.feedUrl }) { r ->
                        Column(
                            Modifier
                                .width(120.dp)
                                .clickable { onPreview(r.feedUrl, r.title, r.imageUrl) },
                        ) {
                            Artwork(
                                r.imageUrl,
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f),
                                size = null,
                                corner = 20.dp,
                            )
                            Text(
                                r.title,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }
        if (content.topCharts.isNotEmpty()) {
            item(key = "charts-title") { SectionTitle(stringResource(R.string.top_charts, countryName)) }
            itemsIndexed(content.topCharts, key = { _, r -> "chart-" + r.feedUrl }) { index, r ->
                ListItem(
                    headlineContent = { Text(r.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = if (r.author != null) {
                        { Text(r.author, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    } else {
                        null
                    },
                    leadingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(28.dp),
                            )
                            Artwork(r.imageUrl, size = 52.dp)
                        }
                    },
                    modifier = Modifier.clickable { onPreview(r.feedUrl, r.title, r.imageUrl) },
                )
            }
        }
        if (content.trending.isNotEmpty()) {
            item(key = "trending-title") { SectionTitle(stringResource(R.string.trending)) }
            resultItems(content.trending, onPreview, keyPrefix = "trend-")
        }
        if (content.loading) {
            item(key = "loading") {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }
        }
        if (poweredByPodcastIndex) footer()
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

private fun LazyListScope.resultItems(
    items: List<SearchResult>,
    onPreview: (String, String?, String?) -> Unit,
    keyPrefix: String = "",
) {
    items(items.distinctBy { it.feedUrl }, key = { keyPrefix + it.feedUrl }) { r ->
        ListItem(
            headlineContent = { Text(r.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            supportingContent = if (r.author != null) {
                { Text(r.author, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            } else {
                null
            },
            leadingContent = { Artwork(r.imageUrl, size = 56.dp) },
            modifier = Modifier.clickable { onPreview(r.feedUrl, r.title, r.imageUrl) },
        )
    }
}

private fun LazyListScope.footer() {
    item(key = "footer") {
        Text(
            stringResource(R.string.powered_by_podcastindex),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}
