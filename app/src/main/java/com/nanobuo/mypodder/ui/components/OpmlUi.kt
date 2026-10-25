package com.nanobuo.mypodder.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.nanobuo.mypodder.R
import com.nanobuo.mypodder.data.opml.OpmlStatus

class OpmlLaunchers(val import: () -> Unit, val export: () -> Unit)

@Composable
fun rememberOpmlLaunchers(onImport: (Uri) -> Unit, onExport: (Uri) -> Unit): OpmlLaunchers {
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImport)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-opml")) { uri ->
        uri?.let(onExport)
    }
    return OpmlLaunchers(
        import = { importLauncher.launch(arrayOf("text/xml", "application/xml", "text/x-opml", "application/octet-stream", "*/*")) },
        export = { exportLauncher.launch("mypodder.opml") },
    )
}

/** Mostra l'esito di importazione/esportazione OPML in uno snackbar. */
@Composable
fun OpmlStatusEffect(status: OpmlStatus, snackbar: SnackbarHostState, consume: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(status) {
        val message = when (status) {
            is OpmlStatus.Imported -> context.getString(R.string.opml_imported, status.imported) +
                if (status.failed > 0) " " + context.getString(R.string.opml_failed, status.failed) else ""
            is OpmlStatus.Exported -> context.getString(R.string.opml_exported, status.count)
            OpmlStatus.Error -> context.getString(R.string.opml_error)
            else -> null
        } ?: return@LaunchedEffect
        try {
            snackbar.showSnackbar(message)
        } finally {
            consume() // dopo, altrimenti l'effetto verrebbe annullato prima di mostrare il messaggio
        }
    }
}
