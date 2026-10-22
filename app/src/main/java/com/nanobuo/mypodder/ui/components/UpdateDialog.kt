package com.nanobuo.mypodder.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nanobuo.mypodder.BuildConfig
import com.nanobuo.mypodder.R
import com.nanobuo.mypodder.data.update.UpdateInfo
import com.nanobuo.mypodder.data.update.UpdateState

/** Finestra con le novità della nuova versione, il download e l'installazione. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateDialog(
    state: UpdateState,
    onUpdate: (UpdateInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val info: UpdateInfo = when (state) {
        is UpdateState.Available -> state.info
        is UpdateState.NeedsPermission -> state.info
        is UpdateState.Downloading -> state.info
        is UpdateState.Installing -> state.info
        is UpdateState.Error -> state.info ?: return
        else -> return
    }
    val busy = state is UpdateState.Downloading || state is UpdateState.Installing

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Rounded.SystemUpdate, null) },
        title = { Text(stringResource(R.string.update_available)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.update_versions, info.version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                )
                when (state) {
                    is UpdateState.Downloading -> {
                        Text(stringResource(R.string.update_downloading), Modifier.padding(top = 16.dp))
                        LinearWavyProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    }
                    is UpdateState.Installing -> Text(stringResource(R.string.update_installing), Modifier.padding(top = 16.dp))
                    is UpdateState.NeedsPermission -> Text(stringResource(R.string.update_permission), Modifier.padding(top = 16.dp))
                    is UpdateState.Error -> Text(
                        stringResource(R.string.update_error),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    else -> Unit
                }
                val notes = cleanNotes(info.notes)
                if (notes.isNotBlank() && !busy) {
                    Text(
                        notes,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.NeedsPermission -> TextButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    )
                    onUpdate(info)
                }) { Text(stringResource(R.string.update_allow)) }
                else -> TextButton(onClick = { onUpdate(info) }, enabled = !busy) {
                    Text(stringResource(if (state is UpdateState.Error) R.string.retry else R.string.update_now))
                }
            }
        },
        dismissButton = {
            if (!busy) TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) }
        },
    )
}

/** Note di rilascio in Markdown rese come testo semplice. */
private fun cleanNotes(markdown: String): String =
    markdown
        .replace(Regex("(?m)^#{1,6}\\s*"), "")
        .replace("**", "")
        .replace(Regex("(?m)^- "), "• ")
        .replace("`", "")
        .trim()
