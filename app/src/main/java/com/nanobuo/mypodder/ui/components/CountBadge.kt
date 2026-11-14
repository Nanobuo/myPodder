package com.nanobuo.mypodder.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Icona con un numero nell'angolo in alto a destra (es. episodi in coda).
 * Il badge si adatta al testo, così resta tondo anche con caratteri di sistema grandi.
 */
@Composable
fun IconWithCount(count: Int, icon: @Composable () -> Unit) {
    Box {
        icon()
        if (count > 0) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 10.dp, y = (-6).dp),
            ) {
                Text(
                    if (count > 99) "99+" else count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 16.dp)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
    }
}
