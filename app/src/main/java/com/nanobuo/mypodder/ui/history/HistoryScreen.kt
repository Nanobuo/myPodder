package com.nanobuo.mypodder.ui.history

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(private val episodes: EpisodeRepository) : ViewModel() {
    val items: StateFlow<List<EpisodeItem>?> = episodes.history()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun clear() {
        viewModelScope.launch { episodes.clearHistory() }
    }
}

/** Episodi ascoltati, dal più recente, raggruppati per giorno. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onEpisode: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    if (!items.isNullOrEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Rounded.DeleteSweep, stringResource(R.string.history_clear))
                        }
                    }
                },
            )
        },
    ) { padding ->
        val list = items
        when {
            list == null -> LoadingBox(Modifier.padding(padding))
            list.isEmpty() -> EmptyState(Icons.Rounded.History, stringResource(R.string.history_empty), Modifier.padding(padding))
            else -> {
                val today = LocalDate.now()
                val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
                val todayLabel = stringResource(R.string.today)
                val yesterdayLabel = stringResource(R.string.yesterday)
                val groups = list.groupBy {
                    Instant.ofEpochMilli(it.episode.lastPlayedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                }
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    groups.forEach { (day, dayItems) ->
                        item(key = "day-$day") {
                            Text(
                                when (day) {
                                    today -> todayLabel
                                    today.minusDays(1) -> yesterdayLabel
                                    else -> dateFormat.format(day).replaceFirstChar { it.uppercase() }
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                            )
                        }
                        items(dayItems, key = { it.episode.id }) { item ->
                            EpisodeRow(item, onClick = { onEpisode(item.episode.id) })
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.history_clear)) },
            text = { Text(stringResource(R.string.history_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; viewModel.clear() }) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
