package com.nanobuo.mypodder.ui.theme

import android.os.Build
import com.materialkolor.dynamicColorScheme
import com.materialkolor.PaletteStyle
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.nanobuo.mypodder.data.settings.ThemeMode

// Schema di riserva (Android < 12 o colori dinamici disattivati), generato da un seme viola.
private val LightColors = lightColorScheme(
    primary = Color(0xFF65558F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF4D3D75),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF4A4458),
    tertiary = Color(0xFF7E5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E3),
    onTertiaryContainer = Color(0xFF633B48),
    background = Color(0xFFFDF7FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFDF7FF),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE7E0EB),
    onSurfaceVariant = Color(0xFF49454E),
    outline = Color(0xFF7A757F),
    outlineVariant = Color(0xFFCAC4CF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F2FA),
    surfaceContainer = Color(0xFFF2ECF4),
    surfaceContainerHigh = Color(0xFFECE6EE),
    surfaceContainerHighest = Color(0xFFE6E0E9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCFBDFE),
    onPrimary = Color(0xFF36275D),
    primaryContainer = Color(0xFF4D3D75),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFFCBC2DB),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF4A2532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD9E3),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF49454E),
    onSurfaceVariant = Color(0xFFCAC4CF),
    outline = Color(0xFF948F99),
    outlineVariant = Color(0xFF49454E),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F24),
    surfaceContainerHigh = Color(0xFF2B292F),
    surfaceContainerHighest = Color(0xFF36343A),
)

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Tema scuro attivo, per i temi locali generati dalle copertine. */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** Se true, schermate e app prendono l'accento dalla copertina del podcast. */
val LocalCoverColors = staticCompositionLocalOf { false }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MyPodderTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    coverColors: Boolean = false,
    /** Colore estratto dalla copertina del podcast in ascolto (se l'opzione è attiva). */
    seedColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val base: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    val target = if (seedColor != null) rememberCoverScheme(seedColor, darkTheme) else base
    CompositionLocalProvider(LocalDarkTheme provides darkTheme, LocalCoverColors provides coverColors) {
        MaterialExpressiveTheme(
            colorScheme = animateColorScheme(target),
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}

/**
 * Applica al contenuto i colori ricavati dalla copertina [imageUrl], se l'opzione è attiva.
 * Finché il colore non è pronto (o non è adatto) resta il tema corrente.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CoverTheme(imageUrl: String?, content: @Composable () -> Unit) {
    if (!LocalCoverColors.current) {
        content()
        return
    }
    val seed = rememberCoverSeed(imageUrl)
    val current = MaterialTheme.colorScheme
    val target = if (seed != null) rememberCoverScheme(seed, LocalDarkTheme.current) else current
    MaterialExpressiveTheme(colorScheme = animateColorScheme(target), content = content)
}

@Composable
private fun rememberCoverScheme(seed: Color, dark: Boolean): ColorScheme =
    // "Content" resta fedele al colore della copertina pur garantendo i contrasti di Material.
    remember(seed, dark) { dynamicColorScheme(seedColor = seed, isDark = dark, style = PaletteStyle.Content) }

/** Transizione morbida tra due schemi di colore. */
@Composable
private fun animateColorScheme(target: ColorScheme): ColorScheme {
    val spec = tween<Color>(durationMillis = 450)
    @Composable fun a(c: Color) = animateColorAsState(c, spec, label = "scheme").value
    return target.copy(
        primary = a(target.primary),
        onPrimary = a(target.onPrimary),
        primaryContainer = a(target.primaryContainer),
        onPrimaryContainer = a(target.onPrimaryContainer),
        secondary = a(target.secondary),
        onSecondary = a(target.onSecondary),
        secondaryContainer = a(target.secondaryContainer),
        onSecondaryContainer = a(target.onSecondaryContainer),
        tertiary = a(target.tertiary),
        tertiaryContainer = a(target.tertiaryContainer),
        onTertiaryContainer = a(target.onTertiaryContainer),
        background = a(target.background),
        surface = a(target.surface),
        onSurface = a(target.onSurface),
        onSurfaceVariant = a(target.onSurfaceVariant),
        surfaceVariant = a(target.surfaceVariant),
        surfaceContainerLowest = a(target.surfaceContainerLowest),
        surfaceContainerLow = a(target.surfaceContainerLow),
        surfaceContainer = a(target.surfaceContainer),
        surfaceContainerHigh = a(target.surfaceContainerHigh),
        surfaceContainerHighest = a(target.surfaceContainerHighest),
        outline = a(target.outline),
        outlineVariant = a(target.outlineVariant),
    )
}
