package com.trinineba.quotesonlock

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * The app's own type: Cormorant Garamond for quotes and headings (a book face), Inter for the interface.
 * Both are under the SIL Open Font License and bundled in res/font, so the app looks the same on every phone.
 */
val BookSerif = FontFamily(
    Font(R.font.book_serif, FontWeight.Normal),
    Font(R.font.book_serif, FontWeight.Medium),
    Font(R.font.book_serif_semibold, FontWeight.SemiBold),
    Font(R.font.book_serif_semibold, FontWeight.Bold),
    Font(R.font.book_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

val UiSans = FontFamily(
    Font(R.font.ui_sans, FontWeight.Normal),
    Font(R.font.ui_sans_medium, FontWeight.Medium),
    Font(R.font.ui_sans_semibold, FontWeight.SemiBold),
    Font(R.font.ui_sans_semibold, FontWeight.Bold),
)

/** Material's type scale in the app's fonts: book serif for display and headlines, Inter for everything else. */
fun appTypography(): Typography {
    val b = Typography()
    fun serif(s: TextStyle) = s.copy(fontFamily = BookSerif, fontWeight = FontWeight.Medium)
    fun sans(s: TextStyle) = s.copy(fontFamily = UiSans)
    return Typography(
        displayLarge = serif(b.displayLarge), displayMedium = serif(b.displayMedium), displaySmall = serif(b.displaySmall),
        headlineLarge = serif(b.headlineLarge), headlineMedium = serif(b.headlineMedium), headlineSmall = serif(b.headlineSmall),
        titleLarge = serif(b.titleLarge), titleMedium = sans(b.titleMedium), titleSmall = sans(b.titleSmall),
        bodyLarge = sans(b.bodyLarge), bodyMedium = sans(b.bodyMedium), bodySmall = sans(b.bodySmall),
        labelLarge = sans(b.labelLarge), labelMedium = sans(b.labelMedium), labelSmall = sans(b.labelSmall),
    )
}

/** The same fonts for drawing on a canvas (live wallpaper, shared pictures, card fitting); falls back to the system's. */
object QuoteTypefaces {
    @Volatile private var app: android.content.Context? = null
    private val cache = java.util.concurrent.ConcurrentHashMap<Int, android.graphics.Typeface>()

    fun init(context: android.content.Context) { if (app == null) app = context.applicationContext }

    fun of(font: QuoteFont): android.graphics.Typeface {
        val res = when (font) { QuoteFont.SERIF -> R.font.book_serif; QuoteFont.SANS -> R.font.ui_sans; else -> 0 }
        val ctx = app
        if (res != 0 && ctx != null) {
            cache[res]?.let { return it }
            runCatching { androidx.core.content.res.ResourcesCompat.getFont(ctx, res) }.getOrNull()?.let { cache[res] = it; return it }
        }
        return android.graphics.Typeface.create(font.family, android.graphics.Typeface.NORMAL)
    }
}
