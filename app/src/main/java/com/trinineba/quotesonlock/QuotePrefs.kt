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

    var customJson: String?
        get() = prefs.getString("custom", null)
        set(value) = prefs.edit().putString("custom", value).apply()
}
