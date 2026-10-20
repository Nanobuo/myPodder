package com.nanobuo.mypodder.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nanobuo.mypodder.R
import com.nanobuo.mypodder.data.db.EpisodeItem
import com.nanobuo.mypodder.data.download.DownloadRepository
import com.nanobuo.mypodder.data.repo.EpisodeRepository
import com.nanobuo.mypodder.ui.components.EmptyState
import com.nanobuo.mypodder.ui.components.EpisodeRow
import com.nanobuo.mypodder.ui.components.LoadingBox
import com.nanobuo.mypodder.util.formatBytes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    episodes: EpisodeRepository,
    private val downloads: DownloadRepository,
) : ViewModel() {
    val items: StateFlow<List<EpisodeItem>?> = episodes.downloadsList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val usedBytes: StateFlow<Long> = episodes.downloadedBytes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun freeBytes(): Long = downloads.freeSpace()

    fun deletePlayed(onDone: (Int) -> Unit) {
        viewModelScope.launch { onDone(downloads.deleteCompleted()) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onEpisode: (Long) -> Unit, viewModel: DownloadsViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val used by viewModel.usedBytes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_downloads)) }) },
    ) { padding ->
        val list = items
        if (list == null) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                val free = remember(used) { viewModel.freeBytes() }
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.SdStorage, null)
                            Text(
                                stringResource(R.string.storage_used, formatBytes(context, used)),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                        LinearProgressIndicator(
                            progress = { if (used + free > 0) used.toFloat() / (used + free) else 0f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            stringResource(R.string.storage_free, formatBytes(context, free)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(onClick = {
                            viewModel.deletePlayed { n ->
                                scope.launch {
                                    snackbar.showSnackbar(context.resources.getQuantityString(R.plurals.deleted_downloads, n, n))
                                }
                            }
                        }) {
                            Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(18.dp))
                            Text(stringResource(R.string.delete_played), Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
            if (list.isEmpty()) {
                item { EmptyState(Icons.Rounded.CloudDownload, stringResource(R.string.downloads_empty)) }
            }
            items(list, key = { it.episode.id }) { item ->
                EpisodeRow(item, onClick = { onEpisode(item.episode.id) })
            }
        }
    }
}
