package com.nanobuo.mypodder.ui.theme

import android.content.Context
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.materialkolor.ktx.themeColorOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Estrae dalle copertine il colore più adatto a fare da accento (algoritmo di Material You). */
object CoverColors {
    // 0 = copertina senza un colore adatto.
    private val cache = LruCache<String, Int>(64)

    suspend fun seed(context: Context, url: String): Color? {
        cache.get(url)?.let { return if (it == 0) null else Color(it) }
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(Size(128, 128))
            .allowHardware(false)
            .build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return null
        val color = withContext(Dispatchers.Default) {
            runCatching { result.image.toBitmap().asImageBitmap().themeColorOrNull() }.getOrNull()
        }
        cache.put(url, color?.toArgb() ?: 0)
        return color
    }
}

@Composable
fun rememberCoverSeed(url: String?): Color? {
    val context = LocalContext.current
    val seed by produceState<Color?>(initialValue = null, url) {
        value = url?.let { CoverColors.seed(context, it) }
    }
    return seed
}
