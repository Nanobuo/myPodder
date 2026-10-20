package com.nanobuo.mypodder.util

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Converte "1:02:03", "62:03", "3723" o "00:01:02.500" in millisecondi. */
fun parseClockToMs(value: String?): Long? {
    val s = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val parts = s.split(":")
    if (parts.size > 3) return null
    var total = 0.0
    for (p in parts) {
        val n = p.toDoubleOrNull() ?: return null
        total = total * 60 + n
    }
    return (total * 1000).toLong()
}

private val dateFormats = listOf(
    DateTimeFormatter.RFC_1123_DATE_TIME,
    DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss zzz", Locale.US),
    DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.US),
    DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm zzz", Locale.US),
    DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss zzz", Locale.US),
    DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss Z", Locale.US),
    DateTimeFormatter.ISO_OFFSET_DATE_TIME,
)

/** Interpreta le date RSS (RFC 822 e varianti comuni). Restituisce 0 se non riconosciuta. */
fun parseFeedDate(value: String?): Long {
    val s = value?.trim()?.replace(Regex("\\s+"), " ") ?: return 0
    if (s.isEmpty()) return 0
    for (f in dateFormats) {
        try {
            return ZonedDateTime.parse(s, f).toInstant().toEpochMilli()
        } catch (_: Exception) {
        }
    }
    // Ultimo tentativo: senza fuso orario.
    return try {
        LocalDateTime.parse(s, DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss", Locale.US))
            .toInstant(ZoneOffset.UTC).toEpochMilli()
    } catch (_: Exception) {
        0
    }
}
