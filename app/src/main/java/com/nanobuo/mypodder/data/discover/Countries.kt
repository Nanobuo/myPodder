package com.nanobuo.mypodder.data.discover

import java.util.Locale

data class Country(val code: String, val name: String) {
    /** Bandiera emoji ricavata dal codice ISO (due "regional indicator"). */
    val flag: String
        get() = code.uppercase().map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("")
}

object Countries {
    private val languageDefaults = mapOf(
        "it" to "IT", "en" to "US", "fr" to "FR", "de" to "DE", "es" to "ES", "pt" to "BR",
        "nl" to "NL", "sv" to "SE", "da" to "DK", "nb" to "NO", "fi" to "FI", "pl" to "PL",
        "ja" to "JP", "ko" to "KR", "zh" to "CN", "ru" to "RU", "tr" to "TR",
    )

    /** Paese predefinito: quello del dispositivo, altrimenti quello tipico della sua lingua. */
    fun default(): String {
        val locale = Locale.getDefault()
        return locale.country.takeIf { it.length == 2 }
            ?: languageDefaults[locale.language]
            ?: "US"
    }

    /** Lingua principale parlata in un paese (es. IT -> it, CH -> de). */
    fun languageOf(country: String): String {
        val cc = country.uppercase()
        languageDefaults.entries.firstOrNull { it.value == cc }?.let { return it.key }
        return Locale.getAvailableLocales().firstOrNull { it.country == cc && it.language.length == 2 }?.language
            ?: Locale.getDefault().language
    }

    /** Paesi principali per i podcast (classifiche Apple disponibili e cataloghi ampi). */
    private val main = listOf(
        "IT", "US", "GB", "IE", "CA", "AU", "NZ", "IN", "FR", "BE", "CH", "DE", "AT", "ES", "PT",
        "MX", "AR", "CO", "CL", "BR", "NL", "SE", "NO", "DK", "FI", "PL", "JP", "KR",
    )

    /** Paesi proposti nel selettore, più quello del dispositivo se non è tra i principali. */
    fun all(displayLocale: Locale = Locale.getDefault()): List<Country> =
        (main + default()).distinct()
            .map { Country(it, Locale.Builder().setRegion(it).build().getDisplayCountry(displayLocale)) }
            .sortedBy { it.name.lowercase(displayLocale) }

    fun of(code: String): Country =
        Country(code.uppercase(), Locale.Builder().setRegion(code.uppercase()).build().getDisplayCountry(Locale.getDefault()))
}
