package com.nanobuo.mypodder.ui.settings

import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nanobuo.mypodder.BuildConfig
import com.nanobuo.mypodder.R
import com.nanobuo.mypodder.data.opml.OpmlManager
import com.nanobuo.mypodder.data.repo.PodcastRepository
import com.nanobuo.mypodder.data.settings.SettingsRepository
import com.nanobuo.mypodder.data.settings.NewBadgeStyle
import com.nanobuo.mypodder.data.settings.ThemeMode
import com.nanobuo.mypodder.ui.components.OpmlStatusEffect
import com.nanobuo.mypodder.ui.components.rememberOpmlLaunchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val repo: SettingsRepository,
    private val opml: OpmlManager,
    podcasts: PodcastRepository,
) : ViewModel() {
    val settings = repo.state
    val opmlStatus = opml.status
    private val podcastCount = podcasts.podcasts.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun update(block: suspend SettingsRepository.() -> Unit) {
        viewModelScope.launch { repo.block() }
    }

    fun importOpml(uri: Uri) = opml.import(uri)
    fun exportOpml(uri: Uri) = opml.export(uri, podcastCount.value)
    fun consumeOpml() = opml.consume()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val opmlStatus by viewModel.opmlStatus.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val opml = rememberOpmlLaunchers(viewModel::importOpml, viewModel::exportOpml)
    OpmlStatusEffect(opmlStatus, snackbar, viewModel::consumeOpml)

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Section(stringResource(R.string.settings_appearance))
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.theme), style = MaterialTheme.typography.bodyLarge)
                SingleChoiceSegmentedButtonRow(Modifier.padding(top = 8.dp)) {
                    val modes = listOf(
                        ThemeMode.SYSTEM to R.string.theme_system,
                        ThemeMode.LIGHT to R.string.theme_light,
                        ThemeMode.DARK to R.string.theme_dark,
                    )
                    modes.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(
                            selected = s.theme == mode,
                            onClick = { viewModel.update { setTheme(mode) } },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                        ) { Text(stringResource(label)) }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchRow(
                    stringResource(R.string.dynamic_color),
                    stringResource(R.string.dynamic_color_summary),
                    s.dynamicColor,
                ) { v -> viewModel.update { setDynamicColor(v) } }
            }

            val badgeLabels = mapOf(
                NewBadgeStyle.DOT to stringResource(R.string.badge_dot),
                NewBadgeStyle.COUNT to stringResource(R.string.badge_count),
                NewBadgeStyle.NONE to stringResource(R.string.badge_none),
            )
            ChoiceRow(stringResource(R.string.new_badge), badgeLabels.keys.toList(), s.newBadge, { badgeLabels[it].orEmpty() }) {
                viewModel.update { setNewBadge(it) }
            }

            Section(stringResource(R.string.settings_playback))
            ChoiceRow(stringResource(R.string.skip_back_interval), listOf(5, 10, 15, 30), s.skipBackSec, { "${it}s" }) {
                viewModel.update { setSkipBack(it) }
            }
            ChoiceRow(stringResource(R.string.skip_forward_interval), listOf(10, 15, 30, 45, 60), s.skipForwardSec, { "${it}s" }) {
                viewModel.update { setSkipForward(it) }
            }
            SwitchRow(
                stringResource(R.string.shake_to_extend),
                stringResource(R.string.shake_to_extend_summary),
                s.shakeToExtend,
            ) { v -> viewModel.update { setShakeToExtend(v) } }

            Section(stringResource(R.string.settings_downloads))
            SwitchRow(stringResource(R.string.wifi_only), stringResource(R.string.wifi_only_summary), s.wifiOnly) { v ->
                viewModel.update { setWifiOnly(v) }
            }
            SwitchRow(
                stringResource(R.string.delete_after_played),
                stringResource(R.string.delete_after_played_summary),
                s.deleteAfterPlayed,
            ) { v -> viewModel.update { setDeleteAfterPlayed(v) } }
            ChoiceRow(
                stringResource(R.string.auto_download_count),
                listOf(1, 3, 5, 10),
                s.autoDownloadCount,
                { it.toString() },
            ) { viewModel.update { setAutoDownloadCount(it) } }

            Section(stringResource(R.string.settings_feeds))
            val refreshLabels = mapOf(
                0 to stringResource(R.string.refresh_manual),
                1 to stringResource(R.string.hours_short, 1),
                3 to stringResource(R.string.hours_short, 3),
                6 to stringResource(R.string.hours_short, 6),
                12 to stringResource(R.string.hours_short, 12),
                24 to stringResource(R.string.hours_short, 24),
            )
            ChoiceRow(stringResource(R.string.refresh_interval), refreshLabels.keys.toList(), s.refreshHours, { refreshLabels[it].orEmpty() }) {
                viewModel.update { setRefreshHours(it) }
            }
            SwitchRow(
                stringResource(R.string.add_new_to_queue),
                stringResource(R.string.add_new_to_queue_summary),
                s.addNewToQueue,
            ) { v -> viewModel.update { setAddNewToQueue(v) } }
            ListItem(
                headlineContent = { Text(stringResource(R.string.import_opml)) },
                supportingContent = { Text(stringResource(R.string.import_opml_summary)) },
                leadingContent = { Icon(Icons.Rounded.FileDownload, null) },
                modifier = Modifier.clickable { opml.import() },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.export_opml)) },
                leadingContent = { Icon(Icons.Rounded.FileUpload, null) },
                modifier = Modifier.clickable { opml.export() },
            )

            Section(stringResource(R.string.settings_about))
            ListItem(
                headlineContent = { Text(stringResource(R.string.app_name)) },
                supportingContent = { Text(stringResource(R.string.version, BuildConfig.VERSION_NAME)) },
                leadingContent = { Icon(Icons.Rounded.Info, null) },
            )
            Box(Modifier.padding(16.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(title: String, summary: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (summary != null) {
            { Text(summary) }
        } else {
            null
        },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            options.forEach { option ->
                FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
            }
        }
    }
}
