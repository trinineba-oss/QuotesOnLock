package com.trinineba.quotesonlock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews

/** The large home-screen widget (4×2): the quote on the theme's picture, with a heart and "next". */
class QuoteWidgetLarge : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) =
        QuoteWidgets.update(context, manager, ids, small = false)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        QuoteWidgets.update(context, manager, intArrayOf(id), small = false)

    override fun onEnabled(context: Context) = QuoteWidgets.startRotation(context)
    override fun onDisabled(context: Context) = QuoteWidgets.stopRotationIfUnused(context)
}

/** The small home-screen widget (4×1): just the line and its author. */
class QuoteWidgetSmall : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) =
        QuoteWidgets.update(context, manager, ids, small = true)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        QuoteWidgets.update(context, manager, intArrayOf(id), small = true)

    override fun onEnabled(context: Context) = QuoteWidgets.startRotation(context)
    override fun onDisabled(context: Context) = QuoteWidgets.stopRotationIfUnused(context)
}

/**
 * Draws the widgets. They show the current quote (the same one as the lock screen and the app) in the
 * widget theme, or the app's theme. Widgets can't animate, so moving themes use their still picture.
 */
object QuoteWidgets {
    private fun ids(context: Context, small: Boolean): IntArray {
        val manager = AppWidgetManager.getInstance(context)
        val provider = if (small) QuoteWidgetSmall::class.java else QuoteWidgetLarge::class.java
        return manager.getAppWidgetIds(ComponentName(context, provider))
    }

    fun hasAny(context: Context): Boolean = try {
        ids(context, small = false).isNotEmpty() || ids(context, small = true).isNotEmpty()
    } catch (e: Exception) {
        false
    }

    /** Redraws every widget, e.g. after the quote, a favourite or the look changed. */
    fun refresh(context: Context) {
        val appContext = context.applicationContext
        try {
            val manager = AppWidgetManager.getInstance(appContext)
            update(appContext, manager, ids(appContext, small = false), small = false)
            update(appContext, manager, ids(appContext, small = true), small = true)
        } catch (e: Exception) {
            // No widget host on this device; nothing to draw.
        }
    }

    /** Asks the launcher to add a widget (Android shows its own confirmation). Returns false if it can't. */
    fun requestPin(context: Context, small: Boolean): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        val provider = if (small) QuoteWidgetSmall::class.java else QuoteWidgetLarge::class.java
        return manager.requestPinAppWidget(ComponentName(context, provider), null, null)
    }

    /** A widget appeared: keep the quotes changing on the chosen schedule, even with the lock screen off. */
    fun startRotation(context: Context) {
        val prefs = QuotePrefs(context)
        QuoteScheduler.ensureScheduled(context, prefs.intervalMinutes)
    }

    fun stopRotationIfUnused(context: Context) {
        if (!QuotePrefs(context).enabled && !hasAny(context)) QuoteScheduler.cancel(context)
    }

    fun update(context: Context, manager: AppWidgetManager, ids: IntArray, small: Boolean) {
        if (ids.isEmpty()) return
        val prefs = QuotePrefs(context)
        val repo = QuoteRepository(context, prefs)
        // A first widget with nothing shown yet picks a quote. advance() redraws the widgets itself.
        if (repo.current() == null && repo.pool().isNotEmpty()) {
            repo.advance()
            return
        }
        ids.forEach { id -> manager.updateAppWidget(id, views(context, manager, id, prefs, repo, small)) }
    }

    private fun views(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        prefs: QuotePrefs,
        repo: QuoteRepository,
        small: Boolean,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, if (small) R.layout.widget_quote_small else R.layout.widget_quote)
        val theme = prefs.widgetTheme ?: prefs.theme
        val quote = repo.current()
        val style = prefs.style
        val classic = theme.cardColor == null

        // Picture and card. Classic has no picture: a plain dark card.
        val picture = widgetPicture(theme)
        if (picture != null) {
            views.setImageViewResource(R.id.widget_art, picture)
            views.setViewVisibility(R.id.widget_art, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_art, View.GONE)
        }
        val cardBase = theme.cardColor ?: 0xFF1E1C26.toInt()
        // The card is lighter than on the lock screen so the picture shows through, as in the app.
        val opacity = if (classic) 0.92f else (prefs.cardOpacity * 0.75f).coerceIn(0f, 0.85f)
        val tint = ((opacity * 255).toInt() shl 24) or (cardBase and 0x00FFFFFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorStateList(R.id.widget_card, "setBackgroundTintList", ColorStateList.valueOf(tint))
        } else {
            views.setInt(R.id.widget_card, "setBackgroundColor", tint)
        }
        val textColor = style.color ?: theme.notificationText ?: 0xFFFFFFFF.toInt()
        val accent = style.color ?: theme.notificationAuthor ?: 0xFFC8B8FF.toInt()
        views.setTextColor(R.id.widget_text, textColor)
        views.setTextColor(R.id.widget_author, accent)

        val open = PendingIntent.getActivity(
            context, 10,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_root, open)

        if (quote == null) {
            views.setTextViewText(R.id.widget_text, "Open the app to choose your quotes.")
            views.setTextViewTextSize(R.id.widget_text, TypedValue.COMPLEX_UNIT_SP, 15f)
            views.setViewVisibility(R.id.widget_author, View.GONE)
        } else {
            val text = quote.title?.let { "$it\n${quote.shownText}" } ?: quote.shownText
            val author = quote.byline?.let { "\u2014 $it" }
            val (sp, lines) = fit(context, manager, id, text, small)
            views.setTextViewText(R.id.widget_text, text)
            views.setTextViewTextSize(R.id.widget_text, TypedValue.COMPLEX_UNIT_SP, sp)
            views.setInt(R.id.widget_text, "setMaxLines", lines)
            views.setTextViewTextSize(R.id.widget_author, TypedValue.COMPLEX_UNIT_SP, if (small) 11f else 12f)
            if (author != null) {
                views.setTextViewText(R.id.widget_author, author)
                views.setViewVisibility(R.id.widget_author, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_author, if (small) View.GONE else View.INVISIBLE)
            }
        }

        if (!small) {
            val favorite = quote != null && repo.isFavorite(quote)
            views.setImageViewResource(R.id.widget_fav, if (favorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
            views.setInt(R.id.widget_fav, "setColorFilter", if (favorite) 0xFFE0566B.toInt() else accent)
            views.setInt(R.id.widget_next, "setColorFilter", accent)
            val buttonTint = ((0x26) shl 24) or (accent and 0x00FFFFFF)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setColorStateList(R.id.widget_fav, "setBackgroundTintList", ColorStateList.valueOf(buttonTint))
                views.setColorStateList(R.id.widget_next, "setBackgroundTintList", ColorStateList.valueOf(buttonTint))
            }
            views.setOnClickPendingIntent(
                R.id.widget_fav,
                PendingIntent.getBroadcast(
                    context, 11, Intent(context, WidgetFavoriteReceiver::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            views.setOnClickPendingIntent(
                R.id.widget_next,
                PendingIntent.getBroadcast(
                    context, 12, Intent(context, NextQuoteReceiver::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            views.setViewVisibility(R.id.widget_fav, if (quote != null) View.VISIBLE else View.GONE)
        }
        return views
    }

    /** Still pictures for the widget background (moving themes use their still frame). */
    private fun widgetPicture(theme: AppTheme): Int? = when (theme) {
        AppTheme.STARRY -> R.drawable.starry_frame
        AppTheme.AURORA -> R.drawable.aurora_frame
        AppTheme.BOOK -> R.drawable.paper
        AppTheme.BUTTERFLY -> R.drawable.butterfly_frame
        AppTheme.SPACE -> R.drawable.space_frame
        AppTheme.WATER -> R.drawable.water_frame
        AppTheme.RAIN -> R.drawable.rain_frame
        AppTheme.RAIN_NIGHT -> R.drawable.rain_night_frame
        AppTheme.SNOW -> R.drawable.snow_frame
        AppTheme.CANDLE -> R.drawable.candle_frame
        AppTheme.FIREFLIES -> R.drawable.fireflies_frame
        AppTheme.LAVENDER -> R.drawable.lavender_frame
        AppTheme.CLOUDS -> R.drawable.clouds_frame
        AppTheme.WAVES -> R.drawable.waves_frame
        AppTheme.CLASSIC -> null
    }

    /**
     * The largest type size (sp) at which the quote fits the widget, and how many lines to allow. A quote
     * too long even at the smallest size is cut with "…"; tapping opens it in the app.
     */
    private fun fit(context: Context, manager: AppWidgetManager, id: Int, text: String, small: Boolean): Pair<Float, Int> {
        val dm = context.resources.displayMetrics
        val options = manager.getAppWidgetOptions(id)
        // Portrait size: the minimum width and maximum height the launcher gives the widget.
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: 300
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 }
            ?: if (small) 70 else 150
        fun px(dp: Float) = dp * dm.density
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.SERIF }
        val width = (px(widthDp.toFloat()) - px(2 * HORIZONTAL_PAD_DP)).toInt().coerceAtLeast(1)
        // Room left after the padding and the author line (and on the large widget, the buttons' row).
        val reserved = if (small) 2 * VERTICAL_PAD_SMALL_DP + 16f else 2 * VERTICAL_PAD_DP + 42f
        val height = (px(heightDp.toFloat()) - px(reserved)).toInt().coerceAtLeast(1)
        val maxSp = if (small) 16f else 20f
        val minSp = if (small) 11f else 12f
        var sp = maxSp
        while (true) {
            paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, dm)
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width).build()
            if (layout.height <= height) return sp to Int.MAX_VALUE
            if (sp <= minSp) {
                val lineHeight = layout.height.toFloat() / layout.lineCount
                return sp to (height / lineHeight).toInt().coerceAtLeast(1)
            }
            sp -= 1f
        }
    }

    private const val HORIZONTAL_PAD_DP = 16f
    private const val VERTICAL_PAD_DP = 14f
    private const val VERTICAL_PAD_SMALL_DP = 10f
}
