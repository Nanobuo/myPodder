package com.nanobuo.mypodder.ui.search

import androidx.compose.foundation.clickable
import com.nanobuo.mypodder.ui.nav.SearchRoute
import com.nanobuo.mypodder.ui.nav.OnTabReselected
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nanobuo.mypodder.R
import com.nanobuo.mypodder.data.search.SearchResult
import com.nanobuo.mypodder.ui.components.Artwork
import com.nanobuo.mypodder.ui.components.EmptyState
import com.nanobuo.mypodder.ui.components.LoadingBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onPreview: (url: String, title: String?, image: String?) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val trending by viewModel.trending.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val isUrl = SearchViewModel.looksLikeUrl(query.trim())

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_search)) }) },
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
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
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
                        ResultList(items, header = null, footer = viewModel.usesPodcastIndex, onPreview = onPreview)
                    }
                }
                trending.isNotEmpty() -> ResultList(
                    trending,
                    header = stringResource(R.string.trending),
                    footer = true,
                    onPreview = onPreview,
                )
                else -> EmptyState(Icons.Rounded.Search, stringResource(R.string.search_intro))
            }
        }
    }
}

@Composable
private fun ResultList(
    items: List<SearchResult>,
    header: String?,
    footer: Boolean,
    onPreview: (String, String?, String?) -> Unit,
) {
    val listState = rememberLazyListState()
    OnTabReselected(SearchRoute::class) { listState.animateScrollToItem(0) }
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        if (header != null) {
            item {
                Text(
                    header,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        items(items, key = { it.feedUrl }) { r ->
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
        if (footer) {
            item {
                Text(
                    stringResource(R.string.powered_by_podcastindex),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
