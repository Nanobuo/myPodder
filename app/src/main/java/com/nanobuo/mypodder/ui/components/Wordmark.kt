package com.nanobuo.mypodder.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nanobuo.mypodder.R

/** Inter a peso 750, ridotto alle sole lettere del logo. */
private val WordmarkFont = FontFamily(Font(R.font.inter_wordmark, FontWeight(750)))

/**
 * Logo testuale "myPodder": "my" nel colore del testo e "Podder" nel colore primario,
 * così segue tema chiaro/scuro e colori dinamici.
 */
@Composable
fun Wordmark(modifier: Modifier = Modifier, fontSize: TextUnit = 26.sp) {
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) { append("my") }
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("Podder") }
    }
    Text(
        text,
        fontFamily = WordmarkFont,
        fontWeight = FontWeight(750),
        fontSize = fontSize,
        // Nel file originale: letter-spacing -5 su 112 px.
        letterSpacing = (-0.045).em,
        modifier = modifier.semantics { contentDescription = "myPodder" },
    )
}
