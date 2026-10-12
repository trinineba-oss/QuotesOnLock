package com.trinineba.quotesonlock

import androidx.annotation.LayoutRes

/** Built-in Android font families, so nothing has to be downloaded or bundled. */
enum class QuoteFont(
    val key: String,
    val label: String,
    val family: String,
    @LayoutRes val layout: Int,
) {
    SANS("sans", "Sans-serif", "sans-serif", R.layout.notif_quote_sans),
    SERIF("serif", "Serif", "serif", R.layout.notif_quote_serif),
    HANDWRITING("cursive", "Handwriting", "cursive", R.layout.notif_quote_cursive),
    CASUAL("casual", "Casual", "casual", R.layout.notif_quote_casual),
    MONO("mono", "Monospace", "monospace", R.layout.notif_quote_mono),
    CONDENSED("condensed", "Condensed", "sans-serif-condensed", R.layout.notif_quote_condensed);

    companion object {
        fun fromKey(key: String?): QuoteFont = entries.firstOrNull { it.key == key } ?: SANS
    }
}

enum class QuoteSize(val key: String, val label: String, val sizeSp: Float) {
    SMALL("small", "Small", 14f),
    MEDIUM("medium", "Medium", 16f),
    LARGE("large", "Large", 20f);

    companion object {
        fun fromKey(key: String?): QuoteSize = entries.firstOrNull { it.key == key } ?: MEDIUM
    }
}

/** [color] is an ARGB int, or null to follow the system's notification text color. */
data class QuoteStyle(val font: QuoteFont, val size: QuoteSize, val color: Int?)

/** Preset text colors. Mid-tones read acceptably on both light and dark notification backgrounds. */
val COLOR_PRESETS: List<Pair<String, Int>> = listOf(
    "Black" to 0xFF000000.toInt(),
    "White" to 0xFFFFFFFF.toInt(),
    "Gold" to 0xFFF9A825.toInt(),
    "Orange" to 0xFFF4511E.toInt(),
    "Rose" to 0xFFD81B60.toInt(),
    "Purple" to 0xFF8E24AA.toInt(),
    "Blue" to 0xFF1E88E5.toInt(),
    "Teal" to 0xFF00897B.toInt(),
    "Green" to 0xFF43A047.toInt(),
)
