package com.trinineba.quotesonlock

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Saves favourites, your own quotes and settings to one small JSON file, and reads them back.
 * Restoring adds favourites and quotes you don't already have; settings are replaced only when asked.
 */
object Backup {
    private const val APP = "quotes-on-lock"
    private const val FORMAT = 1

    /** Settings worth carrying to another install. Not the rotation state (current quote, history, seen). */
    private val SETTINGS = listOf(
        "enabled", "interval_minutes", "categories_off", "font", "size", "text_color", "theme", "lock_theme",
        "widget_theme", "allow_repeats", "favorites_only", "update_check", "card_softness", "card_opacity",
        "motion_speed", "aurora_intensity", "animate_background", "wallpaper_show_quote", "wallpaper_quote_position", "swipe_card", "ideas_mix", "myths_mix", "random_theme", "sound_volume", "sound_timer", "breathe_sound", "saved_mixes", "calm_wallpaper", "calm_theme", "calm_still", "calm_log", "winddown_minutes", "winddown_sound", "voice_rate", "voice_name", "natural_voice", "notif_read", "fav_collections", "reflect_prompt", "daily_enabled", "daily_hour", "daily_minute",
        "lifeline_letter", "lifeline_helps", "lifeline_people", "lifeline_places", "lifeline_reasons", "lifeline_notes",
    )

    class Result(val favoritesAdded: Int, val customAdded: Int, val hasSettings: Boolean)

    fun export(context: Context): String {
        val prefs = QuotePrefs(context)
        val settings = JSONObject()
        val all = prefs.raw.all
        SETTINGS.forEach { key ->
            val value = all[key] ?: return@forEach
            val entry = JSONObject()
            when (value) {
                is Boolean -> entry.put("type", "boolean").put("value", value)
                is Int -> entry.put("type", "int").put("value", value)
                is Long -> entry.put("type", "long").put("value", value)
                is Float -> entry.put("type", "float").put("value", value.toDouble())
                is String -> entry.put("type", "string").put("value", value)
                is Set<*> -> entry.put("type", "set").put("value", JSONArray(value.map { it.toString() }))
                else -> return@forEach
            }
            settings.put(key, entry)
        }
        return JSONObject()
            .put("app", APP)
            .put("format", FORMAT)
            .put("build", Updater.currentBuild(context))
            .put("saved", System.currentTimeMillis())
            .put("favorites", prefs.favoritesJson?.let { JSONArray(it) } ?: JSONArray())
            .put("custom", prefs.customJson?.let { JSONArray(it) } ?: JSONArray())
            .put("journal", JSONArray().apply { Journal.entries(prefs).forEach { put(it.toJson()) } })
            .put("settings", settings)
            .toString(2)
    }

    private fun parse(text: String): JSONObject {
        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            error("This isn't a Quotes on Lock backup file.")
        }
        if (json.optString("app") != APP) error("This isn't a Quotes on Lock backup file.")
        if (json.optInt("format") > FORMAT) error("This backup is from a newer build. Update the app first.")
        return json
    }

    /** Adds the backup's favourites and own quotes that aren't here yet. Throws with a readable message. */
    fun restoreQuotes(context: Context, text: String): Result {
        val json = parse(text)
        val prefs = QuotePrefs(context)

        val favorites = prefs.favoritesJson?.let { JSONArray(it) } ?: JSONArray()
        val haveFav = (0 until favorites.length()).map { favorites.getJSONObject(it).optString("text") }.toMutableSet()
        var favAdded = 0
        json.optJSONArray("favorites")?.let { incoming ->
            for (i in 0 until incoming.length()) {
                val obj = incoming.optJSONObject(i) ?: continue
                val t = obj.optString("text")
                if (t.isBlank() || !haveFav.add(t)) continue
                favorites.put(JSONObject().apply {
                    put("text", t)
                    obj.optString("author").takeIf { it.isNotBlank() }?.let { put("author", it) }
                    // Your note and collections travel with the favourite.
                    obj.optString("note").takeIf { it.isNotBlank() }?.let { put("note", it) }
                    obj.optJSONArray("lists")?.let { put("lists", it) }
                    obj.optLong("saved", 0L).takeIf { it > 0 }?.let { put("saved", it) }
                })
                favAdded++
            }
        }
        prefs.favoritesJson = favorites.toString()

        val custom = prefs.customJson?.let { JSONArray(it) } ?: JSONArray()
        val haveCustom = (0 until custom.length()).flatMap {
            val o = custom.getJSONObject(it)
            listOf("id:" + o.optString("id"), "text:" + o.optString("text"))
        }.toMutableSet()
        var customAdded = 0
        json.optJSONArray("custom")?.let { incoming ->
            for (i in 0 until incoming.length()) {
                val obj = incoming.optJSONObject(i) ?: continue
                val t = obj.optString("text")
                val id = obj.optString("id").ifBlank { java.util.UUID.randomUUID().toString() }
                if (t.isBlank() || "id:$id" in haveCustom || "text:$t" in haveCustom) continue
                haveCustom += "id:$id"
                haveCustom += "text:$t"
                custom.put(JSONObject().apply {
                    put("id", id)
                    put("text", t)
                    obj.optString("author").takeIf { it.isNotBlank() }?.let { put("author", it) }
                })
                customAdded++
            }
        }
        prefs.customJson = custom.toString()
        // Journal entries are added, never replaced.
        json.optJSONArray("journal")?.let { arr ->
            Journal.merge(prefs, (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(JournalEntry::fromJson) })
        }
        QuoteWidgets.refresh(context)
        val settings = json.optJSONObject("settings")
        return Result(favAdded, customAdded, settings != null && settings.length() > 0)
    }

    /** Replaces the settings with the backup's. */
    fun restoreSettings(context: Context, text: String) {
        val settings = parse(text).optJSONObject("settings") ?: return
        val editor = QuotePrefs(context).raw.edit()
        settings.keys().forEach { key ->
            if (key !in SETTINGS) return@forEach
            val entry = settings.optJSONObject(key) ?: return@forEach
            when (entry.optString("type")) {
                "boolean" -> editor.putBoolean(key, entry.optBoolean("value"))
                "int" -> editor.putInt(key, entry.optInt("value"))
                "long" -> editor.putLong(key, entry.optLong("value"))
                "float" -> editor.putFloat(key, entry.optDouble("value").toFloat())
                "string" -> editor.putString(key, entry.optString("value"))
                "set" -> entry.optJSONArray("value")?.let { arr ->
                    editor.putStringSet(key, (0 until arr.length()).map { arr.optString(it) }.toSet())
                }
            }
        }
        // Settings the backup didn't have (left at their defaults there) go back to the defaults here.
        SETTINGS.filter { !settings.has(it) }.forEach { editor.remove(it) }
        editor.commit()

        val prefs = QuotePrefs(context)
        val repo = QuoteRepository(context, prefs)
        if (prefs.enabled) {
            QuoteScheduler.schedule(context, prefs.intervalMinutes)
            (repo.current() ?: repo.advance())?.let { QuoteNotifier.show(context, it) }
        } else {
            QuoteNotifier.cancel(context)
            if (QuoteWidgets.hasAny(context)) QuoteScheduler.schedule(context, prefs.intervalMinutes)
            else QuoteScheduler.cancel(context)
        }
        Updater.schedule(context, prefs.updateCheck)
        QuoteWidgets.refresh(context)
    }
}
