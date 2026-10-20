package com.nanobuo.mypodder.ui.queue

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nanobuo.mypodder.R
import com.nanobuo.mypodder.data.db.EpisodeItem
import com.nanobuo.mypodder.data.repo.EpisodeRepository
import com.nanobuo.mypodder.ui.components.EmptyState
import com.nanobuo.mypodder.ui.components.EpisodeRow
import com.nanobuo.mypodder.ui.components.LoadingBox
import com.nanobuo.mypodder.ui.components.LocalEpisodeActions
import com.nanobuo.mypodder.util.formatDuration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor(private val episodes: EpisodeRepository) : ViewModel() {
    val queue: StateFlow<List<EpisodeItem>?> = episodes.queue
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(order: List<Long>) {
        viewModelScope.launch { episodes.setQueue(order) }
    }

    fun clear() {
        viewModelScope.launch { episodes.clearQueue() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(onBack: () -> Unit, onEpisode: (Long) -> Unit, viewModel: QueueViewModel = hiltViewModel()) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    val list = queue.orEmpty()
                    val total = list.sumOf { (it.episode.durationMs - it.episode.positionMs).coerceAtLeast(0) }
                    androidx.compose.foundation.layout.Column {
                        Text(stringResource(R.string.nav_queue))
                        if (list.isNotEmpty()) {
                            Text(
                                pluralStringResource(R.plurals.episodes_count, list.size, list.size) +
                                    if (total > 0) " · " + formatDuration(total) else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    if (!queue.isNullOrEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Rounded.ClearAll, stringResource(R.string.clear_queue))
                        }
                    }
                },
            )
        },
    ) { padding ->
        val list = queue
        when {
            list == null -> LoadingBox(Modifier.padding(padding))
            list.isEmpty() -> EmptyState(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.queue_empty), Modifier.padding(padding))
            else -> ReorderableQueue(list, onEpisode, viewModel::save, Modifier.padding(padding))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_queue)) },
            text = { Text(stringResource(R.string.clear_queue_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; viewModel.clear() }) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ReorderableQueue(
    items: List<EpisodeItem>,
    onEpisode: (Long) -> Unit,
    onReordered: (List<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Copia locale per un trascinamento fluido; il database viene aggiornato al rilascio.
    val local = remember { items.toMutableStateList() }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(items) {
        if (!dragging) {
            local.clear()
            local.addAll(items)
        }
    }
    val haptic = LocalHapticFeedback.current
    val actions = LocalEpisodeActions.current
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        local.add(to.index, local.removeAt(from.index))
        haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
        items(local, key = { it.episode.id }) { item ->
            ReorderableItem(reorderState, key = item.episode.id) { isDragging ->
                Surface(
                    tonalElevation = if (isDragging) 6.dp else 0.dp,
                    shadowElevation = if (isDragging) 6.dp else 0.dp,
                ) {
                    EpisodeRow(
                        item,
                        onClick = { onEpisode(item.episode.id) },
                        leading = {
                            IconButton(
                                onClick = {},
                                modifier = Modifier.draggableHandle(
                                    onDragStarted = {
                                        dragging = true
                                        haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                    },
                                    onDragStopped = {
                                        dragging = false
                                        onReordered(local.map { it.episode.id })
                                    },
                                ),
                            ) {
                                Icon(Icons.Rounded.DragHandle, stringResource(R.string.reorder))
                            }
                        },
                        trailing = {
                            IconButton(onClick = { actions.play(item.episode.id) }) {
                                Icon(Icons.Rounded.PlayArrow, stringResource(R.string.play))
                            }
                            IconButton(onClick = { actions.removeFromQueue(item.episode.id) }) {
                                Icon(Icons.Rounded.Close, stringResource(R.string.remove_from_queue))
                            }
                        },
                    )
                }
            }
        }
    }
}
