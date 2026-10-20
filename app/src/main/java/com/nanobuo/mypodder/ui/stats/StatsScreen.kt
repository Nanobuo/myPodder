package com.nanobuo.mypodder.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import com.nanobuo.mypodder.ui.nav.StatsRoute
import com.nanobuo.mypodder.ui.nav.OnTabReselected
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nanobuo.mypodder.R
import com.nanobuo.mypodder.ui.components.Artwork
import com.nanobuo.mypodder.ui.components.LoadingBox
import com.nanobuo.mypodder.util.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onPodcast: (Long) -> Unit, viewModel: StatsViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            icon = { Icon(Icons.Rounded.RestartAlt, null) },
            title = { Text(stringResource(R.string.stats_reset)) },
            text = { Text(stringResource(R.string.stats_reset_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    viewModel.reset()
                    scope.launch {
                        val result = snackbar.showSnackbar(
                            context.getString(R.string.stats_reset_done),
                            actionLabel = context.getString(R.string.undo),
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.undoReset()
                    }
                }) { Text(stringResource(R.string.stats_reset_action)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_stats)) },
                actions = {
                    if ((ui?.totalMs ?: 0) > 0 || (ui?.completed ?: 0) > 0) {
                        IconButton(onClick = { confirmReset = true }) {
                            Icon(Icons.Rounded.RestartAlt, stringResource(R.string.stats_reset))
                        }
                    }
                },
            )
        },
    ) { padding ->
        val s = ui
        if (s == null) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        val scrollState = rememberScrollState()
        OnTabReselected(StatsRoute::class) { scrollState.animateScrollTo(0) }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Numero principale: tempo totale di ascolto.
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(stringResource(R.string.stats_total), style = MaterialTheme.typography.labelLarge)
                    Text(formatDuration(s.totalMs), style = MaterialTheme.typography.displaySmall)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    Icons.Rounded.LocalFireDepartment,
                    pluralStringResource(R.plurals.days_count, s.streak, s.streak),
                    stringResource(R.string.stats_streak_best, s.bestStreak),
                    Modifier.weight(1f),
                )
                StatTile(
                    Icons.Rounded.CheckCircle,
                    s.completed.toString(),
                    stringResource(R.string.stats_completed),
                    Modifier.weight(1f),
                )
            }
            StatTile(
                Icons.Rounded.Speed,
                formatDuration(s.savedMs),
                stringResource(R.string.stats_saved),
                Modifier.fillMaxWidth(),
            )

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val options = listOf(
                    StatsPeriod.DAY to R.string.period_day,
                    StatsPeriod.WEEK to R.string.period_week,
                    StatsPeriod.MONTH to R.string.period_month,
                )
                options.forEachIndexed { i, (p, label) ->
                    SegmentedButton(
                        selected = period == p,
                        onClick = { viewModel.setPeriod(p) },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    ) { Text(stringResource(label)) }
                }
            }

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.stats_period_total), style = MaterialTheme.typography.labelLarge)
                    Text(formatDuration(s.periodMs), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    BarChart(s.bars, period)
                }
            }

            if (s.top.isNotEmpty()) {
                Text(stringResource(R.string.stats_top_podcasts), style = MaterialTheme.typography.titleMedium)
                val max = s.top.maxOf { it.listenedMs }.coerceAtLeast(1)
                s.top.forEach { t ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPodcast(t.podcastId) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(t.imageUrl, size = 44.dp, corner = 10.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row {
                                Text(
                                    t.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    formatDuration(t.listenedMs),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            LinearProgressIndicator(
                                progress = { t.listenedMs.toFloat() / max },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                drawStopIndicator = {},
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StatTile(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = modifier,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

/**
 * Istogramma a serie singola: barre sottili ancorate alla base con estremità arrotondate,
 * griglia discreta e valore della barra toccata (di default la più recente) sopra il grafico.
 */
@Composable
private fun BarChart(bars: List<Bar>, period: StatsPeriod) {
    if (bars.isEmpty()) return
    var selected by remember(period, bars.size) { mutableStateOf(bars.lastIndex) }
    val barColor = MaterialTheme.colorScheme.primary
    val mutedBar = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val max = bars.maxOf { it.valueMs }.coerceAtLeast(60_000)

    val current = bars.getOrNull(selected)
    if (current != null) {
        Text(
            current.detail + " · " + formatDuration(current.valueMs),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .pointerInput(bars.size) {
                detectTapGestures { offset ->
                    val slot = size.width.toFloat() / bars.size
                    selected = (offset.x / slot).toInt().coerceIn(0, bars.lastIndex)
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val slot = size.width / bars.size
            val barWidth = (slot * 0.6f).coerceAtMost(24.dp.toPx())
            val radius = 4.dp.toPx()
            // Griglia recessiva: base e metà.
            listOf(0f, 0.5f).forEach { f ->
                val y = size.height * (1 - f)
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            bars.forEachIndexed { i, bar ->
                if (bar.valueMs <= 0) return@forEachIndexed
                val h = (bar.valueMs.toFloat() / max * size.height).coerceAtLeast(radius * 2)
                val left = i * slot + (slot - barWidth) / 2
                val top = size.height - h
                // Solo gli angoli superiori sono arrotondati: la barra resta ancorata alla base.
                val path = Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            left = left, top = top, right = left + barWidth, bottom = size.height,
                            topLeftCornerRadius = CornerRadius(radius),
                            topRightCornerRadius = CornerRadius(radius),
                            bottomLeftCornerRadius = CornerRadius.Zero,
                            bottomRightCornerRadius = CornerRadius.Zero,
                        )
                    )
                }
                drawPath(path, if (i == selected) barColor else mutedBar)
            }
            if (selected in bars.indices) {
                val left = selected * slot
                drawRoundRect(
                    color = gridColor,
                    topLeft = Offset(left + 1.dp.toPx(), 0f),
                    size = Size(slot - 2.dp.toPx(), size.height),
                    cornerRadius = CornerRadius(radius * 2),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        bars.forEach { bar ->
            Text(
                bar.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (bars.all { it.valueMs == 0L }) {
        Text(
            stringResource(R.string.stats_no_data),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
