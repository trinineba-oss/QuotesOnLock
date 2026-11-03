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
    /** Which part of a long quote the lock-screen card shows, for the quote with this text hash. */
    var cardPartKey: Int
        get() = prefs.getInt("card_part_key", 0)
        set(value) = prefs.edit().putInt("card_part_key", value).apply()

    var cardPart: Int
        get() = prefs.getInt("card_part", 0)
        set(value) = prefs.edit().putInt("card_part", value).apply()

    /** Pick freely each time instead of going through every quote before any repeats. */
    var allowRepeats: Boolean
        get() = prefs.getBoolean("allow_repeats", false)
        set(value) = prefs.edit().putBoolean("allow_repeats", value).apply()

    /** Check GitHub weekly for a new build. */
    var updateCheck: Boolean
        get() = prefs.getBoolean("update_check", true)
        set(value) = prefs.edit().putBoolean("update_check", value).apply()

    /** The newest build already announced in an "update available" notification. */
    var notifiedBuild: Long
        get() = prefs.getLong("notified_build", 0L)
        set(value) = prefs.edit().putLong("notified_build", value).apply()

    /** How far the card's edges fade into the artwork: 0 = a solid card, 1 = a full vignette. */
    var cardSoftness: Float
        get() = prefs.getFloat("card_softness", 0f)
        set(value) = prefs.edit().putFloat("card_softness", value.coerceIn(0f, 1f)).apply()

    /** How fast the moving themes move, 0.25–2 (1 = normal). */
    var motionSpeed: Float
        get() = prefs.getFloat("motion_speed", 1f)
        set(value) = prefs.edit().putFloat("motion_speed", value.coerceIn(0.25f, 2f)).apply()

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

    /** Swiping the home-screen card in the app changes the quote (left: next, right: previous). */
    var swipeCard: Boolean
        get() = prefs.getBoolean("swipe_card", true)
        set(value) = prefs.edit().putBoolean("swipe_card", value).apply()

    /** How often a great idea comes up: 0 = by its share of everything switched on, n = about 1 in n. */
    var ideasMix: Int
        get() = prefs.getInt("ideas_mix", 0)
        set(value) = prefs.edit().putInt("ideas_mix", value).apply()

    /** How often a myth comes up: 0 = by its share, n = about 1 in n. */
    var mythsMix: Int
        get() = prefs.getInt("myths_mix", 0)
        set(value) = prefs.edit().putInt("myths_mix", value).apply()

    /** My lifeline (When it's hard): written by the user, kept on the phone only. */
    var lifelineLetter: String
        get() = prefs.getString("lifeline_letter", "") ?: ""
        set(value) = prefs.edit().putString("lifeline_letter", value).apply()

    var lifelineHelps: String
        get() = prefs.getString("lifeline_helps", "") ?: ""
        set(value) = prefs.edit().putString("lifeline_helps", value).apply()

    var lifelinePeople: String
        get() = prefs.getString("lifeline_people", "") ?: ""
        set(value) = prefs.edit().putString("lifeline_people", value).apply()

    var lifelinePlaces: String
        get() = prefs.getString("lifeline_places", "") ?: ""
        set(value) = prefs.edit().putString("lifeline_places", value).apply()

    var lifelineReasons: String
        get() = prefs.getString("lifeline_reasons", "") ?: ""
        set(value) = prefs.edit().putString("lifeline_reasons", value).apply()

    /** Kinder thoughts kept from "Look at a thought again", newest last. */
    val lifelineNotes: List<String> get() = notesFromJson(prefs.getString("lifeline_notes", null))

    fun addLifelineNote(note: String) {
        if (note.isBlank()) return
        val a = org.json.JSONArray(lifelineNotes + note)
        prefs.edit().putString("lifeline_notes", a.toString()).apply()
    }

    fun removeLifelineNote(index: Int) {
        val list = lifelineNotes.toMutableList()
        if (index in list.indices) list.removeAt(index)
        prefs.edit().putString("lifeline_notes", org.json.JSONArray(list).toString()).apply()
    }

    /** A random theme: "off", "daily" (a new one each day) or "open" (each time the app starts). */
    var randomTheme: String
        get() = prefs.getString("random_theme", "off") ?: "off"
        set(value) = prefs.edit().putString("random_theme", value).apply()

    /** The day (yyyy-MM-dd) the daily random theme was last picked. */
    var randomThemeDay: String
        get() = prefs.getString("random_theme_day", "") ?: ""
        set(value) = prefs.edit().putString("random_theme_day", value).apply()

    /** Picks a different theme at random (never Classic) and makes it the app's theme. */
    fun shuffleTheme(): AppTheme {
        val pick = AppTheme.entries.filter { it != AppTheme.CLASSIC && it != theme }.random()
        theme = pick
        return pick
    }

    /** Theme of the home-screen widgets, or null to follow the app's theme. */
    var widgetTheme: AppTheme?
        get() = prefs.getString("widget_theme", null)?.let { AppTheme.fromKey(it) }
        set(value) {
            val editor = prefs.edit()
            if (value == null) editor.remove("widget_theme") else editor.putString("widget_theme", value.key)
            editor.apply()
        }

    /** Whether the first-launch welcome has been seen. Installs from before it existed count as seen. */
    var welcomeDone: Boolean
        get() = prefs.getBoolean("welcome_done", prefs.contains("current_text") || prefs.contains("enabled"))
        set(value) = prefs.edit().putBoolean("welcome_done", value).apply()

    /** When a backup file was last saved (milliseconds), or 0. */
    var lastBackup: Long
        get() = prefs.getLong("last_backup", 0L)
        set(value) = prefs.edit().putLong("last_backup", value).apply()

    /** The underlying preferences, for backup and restore. */
    internal val raw: android.content.SharedPreferences get() = prefs

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
