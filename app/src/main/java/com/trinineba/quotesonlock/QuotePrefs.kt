package com.trinineba.quotesonlock

import android.content.Context

class QuotePrefs(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("quotesonlock", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    var intervalMinutes: Long
        get() = prefs.getLong("interval_minutes", 60L)
        set(value) = prefs.edit().putLong("interval_minutes", value).apply()

    /**
     * Quote categories that take part in rotation. Stored as the ones switched *off*, so categories
     * added in later versions are on by default.
     */
    var enabledCategories: Set<Category>
        get() {
            val off = prefs.getStringSet("categories_off", null)?.mapNotNull { Category.fromKey(it) }?.toSet()
                ?: emptySet()
            return Category.entries.toSet() - off
        }
        set(value) = prefs.edit()
            .putStringSet("categories_off", (Category.entries.toSet() - value).map { it.key }.toSet())
            .apply()

    var font: QuoteFont
        get() = QuoteFont.fromKey(prefs.getString("font", null))
        set(value) = prefs.edit().putString("font", value.key).apply()

    var size: QuoteSize
        get() = QuoteSize.fromKey(prefs.getString("size", null))
        set(value) = prefs.edit().putString("size", value.key).apply()

    /** ARGB text color, or null to follow the system default. */
    var textColor: Int?
        get() = if (prefs.contains("text_color")) prefs.getInt("text_color", 0) else null
        set(value) {
            val editor = prefs.edit()
            if (value == null) editor.remove("text_color") else editor.putInt("text_color", value)
            editor.apply()
        }

    var theme: AppTheme
        get() = AppTheme.fromKey(prefs.getString("theme", null))
        set(value) = prefs.edit().putString("theme", value.key).apply()

    /** How solid (0–1) the cards behind quotes are in the themed looks, in the app and on the lock screen. */
    /** Brightness of the northern lights, 0.5–2 (1 = normal). */
    var auroraIntensity: Float
        get() = prefs.getFloat("aurora_intensity", 1f)
        set(value) = prefs.edit().putFloat("aurora_intensity", value.coerceIn(0.5f, 2f)).apply()

    var cardOpacity: Float
        get() = prefs.getFloat("card_opacity", 0.8f)
        set(value) = prefs.edit().putFloat("card_opacity", value.coerceIn(0f, 1f)).apply()

    /** Theme of the lock-screen card, or null to follow the app's theme. */
    var lockTheme: AppTheme?
        get() = prefs.getString("lock_theme", null)?.let { AppTheme.fromKey(it) }
        set(value) {
            val editor = prefs.edit()
            if (value == null) editor.remove("lock_theme") else editor.putString("lock_theme", value.key)
            editor.apply()
        }

    /** Whether the Starry Night swirls / northern lights move while the app is open. */
    var animateBackground: Boolean
        get() = prefs.getBoolean("animate_background", true)
        set(value) = prefs.edit().putBoolean("animate_background", value).apply()

    /** Whether the live wallpaper draws today's quote on top of the artwork. */
    var wallpaperShowQuote: Boolean
        get() = prefs.getBoolean("wallpaper_show_quote", true)
        set(value) = prefs.edit().putBoolean("wallpaper_show_quote", value).apply()

    /** Vertical position of the quote on the live wallpaper, as a fraction of the screen height (0–1). */
    var wallpaperQuotePosition: Float
        get() = prefs.getFloat("wallpaper_quote_position", 0.55f)
        set(value) = prefs.edit().putFloat("wallpaper_quote_position", value).apply()

    fun registerListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    val style: QuoteStyle get() = QuoteStyle(font, size, textColor)

    /** Keys of quotes already shown in this pass through the pool, so nothing repeats until all are seen. */
    var seen: Set<String>
        get() = prefs.getStringSet("seen", null)?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet("seen", value.toSet()).apply()

    var currentText: String?
        get() = prefs.getString("current_text", null)
        set(value) = prefs.edit().putString("current_text", value).apply()

    var currentAuthor: String?
        get() = prefs.getString("current_author", null)
        set(value) = prefs.edit().putString("current_author", value).apply()

    /** Quotes shown before the current one (oldest first, as JSON), for the "Previous" button. */
    var historyJson: String?
        get() = prefs.getString("history", null)
        set(value) = prefs.edit().putString("history", value).apply()

    /** Favourite quotes as JSON ({text, author}), newest last. */
    var favoritesJson: String?
        get() = prefs.getString("favorites", null)
        set(value) = prefs.edit().putString("favorites", value).apply()

    /** Rotate only through favourites (when there are any). */
    var favoritesOnly: Boolean
        get() = prefs.getBoolean("favorites_only", false)
        set(value) = prefs.edit().putBoolean("favorites_only", value).apply()

    var customJson: String?
        get() = prefs.getString("custom", null)
        set(value) = prefs.edit().putString("custom", value).apply()
}
