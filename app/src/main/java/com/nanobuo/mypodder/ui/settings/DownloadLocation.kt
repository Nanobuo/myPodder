package com.nanobuo.mypodder.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.documentfile.provider.DocumentFile
import com.nanobuo.mypodder.R
import com.nanobuo.mypodder.data.download.DownloadRepository

/**
 * Scelta di dove salvare gli episodi: memoria del telefono o microSD. Per la microSD Android
 * chiede di scegliere una cartella (si apre sulla scheda): dentro viene usata la cartella
 * "podcasts", creata se manca.
 */
@Composable
fun DownloadLocationRow(treeUri: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    val hasSd = remember { DownloadRepository.removableAppDir(context) != null }
    var choose by remember { mutableStateOf(false) }
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        onChange(uri.toString())
    }

    val folderName = remember(treeUri) {
        treeUri.takeIf { it.isNotBlank() }?.let { runCatching { DocumentFile.fromTreeUri(context, Uri.parse(it))?.name }.getOrNull() }
    }
    val summary = when {
        treeUri.isBlank() -> stringResource(R.string.storage_phone)
        folderName == null -> stringResource(R.string.storage_sd_unavailable)
        folderName.equals(DownloadRepository.SD_FOLDER, ignoreCase = true) -> stringResource(R.string.storage_sd_path, "/$folderName")
        else -> stringResource(R.string.storage_sd_path, "/$folderName/${DownloadRepository.SD_FOLDER}")
    }

    ListItem(
        headlineContent = { Text(stringResource(R.string.storage_location)) },
        supportingContent = { Text(summary) },
        leadingContent = { Icon(if (treeUri.isBlank()) Icons.Rounded.PhoneAndroid else Icons.Rounded.SdCard, null) },
        modifier = Modifier.clickable { choose = true },
    )

    if (choose) {
        AlertDialog(
            onDismissRequest = { choose = false },
            title = { Text(stringResource(R.string.storage_location)) },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.storage_phone)) },
                        leadingContent = { RadioButton(selected = treeUri.isBlank(), onClick = null) },
                        modifier = Modifier.clickable {
                            choose = false
                            if (treeUri.isNotBlank()) {
                                runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(treeUri), flags) }
                                onChange("")
                            }
                        },
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.storage_sd)) },
                        supportingContent = {
                            Text(stringResource(if (hasSd) R.string.storage_sd_hint else R.string.storage_sd_missing))
                        },
                        leadingContent = { RadioButton(selected = treeUri.isNotBlank(), onClick = null, enabled = hasSd) },
                        modifier = Modifier.clickable(enabled = hasSd) {
                            choose = false
                            picker.launch(sdFolderIntent(context.getSystemService(StorageManager::class.java)))
                        },
                    )
                    Text(stringResource(R.string.storage_existing_note))
                }
            },
            confirmButton = { TextButton(onClick = { choose = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

/** Selettore di cartelle aperto sulla microSD, già sulla cartella "podcasts" se esiste. */
private fun sdFolderIntent(storage: StorageManager?): Intent {
    val volume = storage?.storageVolumes?.firstOrNull { it.isRemovable }
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && volume != null) {
        volume.createOpenDocumentTreeIntent()
    } else {
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
    }
    volume?.uuid?.let { uuid ->
        intent.putExtra(
            DocumentsContract.EXTRA_INITIAL_URI,
            DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "$uuid:${DownloadRepository.SD_FOLDER}"),
        )
    }
    return intent.addFlags(
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
    )
}
