package com.nanobuo.mypodder.data.opml

import android.content.Context
import android.net.Uri
import com.nanobuo.mypodder.data.repo.PodcastRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface OpmlStatus {
    data object Idle : OpmlStatus
    data class Importing(val done: Int, val total: Int) : OpmlStatus
    data class Imported(val imported: Int, val failed: Int) : OpmlStatus
    data class Exported(val count: Int) : OpmlStatus
    data object Error : OpmlStatus
}

/** Importazione ed esportazione OPML, eseguite fuori dal ciclo di vita delle schermate. */
@Singleton
class OpmlManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val podcasts: PodcastRepository,
    private val appScope: CoroutineScope,
) {
    private val _status = MutableStateFlow<OpmlStatus>(OpmlStatus.Idle)
    val status: StateFlow<OpmlStatus> = _status.asStateFlow()

    fun import(uri: Uri) {
        if (_status.value is OpmlStatus.Importing) return
        appScope.launch {
            _status.value = OpmlStatus.Importing(0, 0)
            _status.value = runCatching {
                context.contentResolver.openInputStream(uri)!!.use { input ->
                    podcasts.importOpml(input) { done, total -> _status.value = OpmlStatus.Importing(done, total) }
                }
            }.fold(
                onSuccess = { OpmlStatus.Imported(it.imported, it.failed) },
                onFailure = { OpmlStatus.Error },
            )
        }
    }

    fun export(uri: Uri, count: Int) {
        appScope.launch {
            _status.value = runCatching {
                context.contentResolver.openOutputStream(uri)!!.use { podcasts.exportOpml(it) }
            }.fold(onSuccess = { OpmlStatus.Exported(count) }, onFailure = { OpmlStatus.Error })
        }
    }

    fun consume() {
        if (_status.value !is OpmlStatus.Importing) _status.value = OpmlStatus.Idle
    }
}
