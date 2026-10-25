package com.nanobuo.mypodder.data.download

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.InputStream

/**
 * Un episodio scaricato può stare in un file dell'app (percorso) oppure, sulla microSD, in una
 * cartella scelta dall'utente (URI content://). Queste funzioni gestiscono entrambi i casi.
 */
object LocalMedia {
    fun isDocument(path: String) = path.startsWith("content://")

    fun exists(context: Context, path: String?): Boolean = when {
        path.isNullOrBlank() -> false
        isDocument(path) -> runCatching { DocumentFile.fromSingleUri(context, Uri.parse(path))?.exists() == true }.getOrDefault(false)
        else -> File(path).exists()
    }

    fun uri(path: String): Uri = if (isDocument(path)) Uri.parse(path) else Uri.fromFile(File(path))

    fun open(context: Context, path: String): InputStream? =
        if (isDocument(path)) context.contentResolver.openInputStream(Uri.parse(path)) else File(path).inputStream()

    fun delete(context: Context, path: String?) {
        if (path.isNullOrBlank()) return
        if (isDocument(path)) {
            runCatching { DocumentFile.fromSingleUri(context, Uri.parse(path))?.delete() }
        } else {
            File(path).delete()
        }
    }
}
