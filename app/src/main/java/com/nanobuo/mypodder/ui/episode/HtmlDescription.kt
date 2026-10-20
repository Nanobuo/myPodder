package com.nanobuo.mypodder.ui.episode

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import com.nanobuo.mypodder.data.chapters.DescriptionChapterParser

private const val SEEK_PREFIX = "mypodder-seek:"
private val TAG = Regex("<[^>]+>")

/**
 * Trasforma i timestamp del testo (fuori dai tag e dai link esistenti) in link interni,
 * così toccandoli si salta a quel punto dell'episodio.
 */
internal fun linkifyTimestamps(source: String): String {
    val html = if (!TAG.containsMatchIn(source)) source.replace("\n", "<br>") else source
    val out = StringBuilder()
    var last = 0
    var insideLink = false
    fun appendText(text: String) {
        if (insideLink) {
            out.append(text)
            return
        }
        out.append(
            DescriptionChapterParser.TIMESTAMP.replace(text) { m ->
                val ms = DescriptionChapterParser.timestampToMs(m.value)
                if (ms == null) m.value else "<a href=\"$SEEK_PREFIX$ms\">${m.value}</a>"
            }
        )
    }
    for (m in TAG.findAll(html)) {
        appendText(html.substring(last, m.range.first))
        val tag = m.value.lowercase()
        if (tag.startsWith("<a ") || tag == "<a>") insideLink = true
        if (tag.startsWith("</a")) insideLink = false
        out.append(m.value)
        last = m.range.last + 1
    }
    appendText(html.substring(last))
    return out.toString()
}

@Composable
fun HtmlDescription(html: String, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val seek by rememberUpdatedState(onSeek)
    val linkColor = MaterialTheme.colorScheme.primary
    val text = remember(html, linkColor) {
        AnnotatedString.fromHtml(
            linkifyTimestamps(html),
            linkStyles = TextLinkStyles(
                style = SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.None),
            ),
            linkInteractionListener = { link ->
                val url = (link as? LinkAnnotation.Url)?.url ?: return@fromHtml
                if (url.startsWith(SEEK_PREFIX)) {
                    url.removePrefix(SEEK_PREFIX).toLongOrNull()?.let(seek)
                } else {
                    runCatching { uriHandler.openUri(url) }
                }
            },
        )
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = modifier)
}
