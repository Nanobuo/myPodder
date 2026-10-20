package com.nanobuo.mypodder.util

import android.content.Context
import android.text.format.Formatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** 75_000 -> "1:15", 3_725_000 -> "1:02:05". */
fun formatClock(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.ROOT, "%d:%02d", m, s)
}

/** Durata leggibile: "1 h 05 min", "42 min", "30 s". */
fun formatDuration(ms: Long): String {
    val totalMin = ms / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 -> String.format(Locale.ROOT, "%d h %02d min", h, m)
        totalMin > 0 -> "$m min"
        else -> "${(ms / 1000).coerceAtLeast(0)} s"
    }
}

fun formatDate(epochMs: Long): String =
    if (epochMs <= 0) "" else DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

fun formatBytes(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

/** Rimuove i tag HTML per anteprime brevi. */
fun String.stripHtml(): String =
    replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace(Regex("\\s+"), " ")
        .trim()
