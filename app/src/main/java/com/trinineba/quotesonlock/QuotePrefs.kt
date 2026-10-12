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

    var includeBundled: Boolean
        get() = prefs.getBoolean("include_bundled", true)
        set(value) = prefs.edit().putBoolean("include_bundled", value).apply()

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
