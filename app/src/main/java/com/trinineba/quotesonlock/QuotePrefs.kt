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

    /** Quote categories that take part in rotation. All of them until the user changes it. */
    var enabledCategories: Set<Category>
        get() = prefs.getStringSet("categories", null)
            ?.mapNotNull { Category.fromKey(it) }
            ?.toSet()
            ?: Category.entries.toSet()
        set(value) = prefs.edit().putStringSet("categories", value.map { it.key }.toSet()).apply()

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

    val style: QuoteStyle get() = QuoteStyle(font, size, textColor)

    var currentText: String?
        get() = prefs.getString("current_text", null)
        set(value) = prefs.edit().putString("current_text", value).apply()

    var currentAuthor: String?
        get() = prefs.getString("current_author", null)
        set(value) = prefs.edit().putString("current_author", value).apply()

    var customJson: String?
        get() = prefs.getString("custom", null)
        set(value) = prefs.edit().putString("custom", value).apply()
}
