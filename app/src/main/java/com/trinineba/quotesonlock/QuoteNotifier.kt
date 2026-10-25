package com.trinineba.quotesonlock

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object QuoteNotifier {
    private const val CHANNEL_ID = "quote"
    private const val NOTIFICATION_ID = 1

    @SuppressLint("MissingPermission") // guarded by areNotificationsEnabled()
    fun show(context: Context, quote: Quote) {
        val appContext = context.applicationContext
        ensureChannel(appContext)
        val manager = NotificationManagerCompat.from(appContext)
        if (!manager.areNotificationsEnabled()) return

        val openApp = PendingIntent.getActivity(
            appContext,
            0,
            Intent(appContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val next = PendingIntent.getBroadcast(
            appContext,
            1,
            Intent(appContext, NextQuoteReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val previous = PendingIntent.getBroadcast(
            appContext,
            2,
            Intent(appContext, PreviousQuoteReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val copy = PendingIntent.getBroadcast(
            appContext,
            3,
            Intent(appContext, CopyQuoteReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val prefs = QuotePrefs(appContext)
        val style = prefs.style
        val lockTheme = prefs.lockTheme ?: prefs.theme
        val card = CardFit.fit(appContext, quote.text, quote.author, style, lockTheme.notificationFrame != null)
        // A new quote starts at its first part; tapping moves on and wraps round.
        val key = quote.text.hashCode()
        if (prefs.cardPartKey != key) {
            prefs.cardPartKey = key
            prefs.cardPart = 0
        }
        val part = prefs.cardPart.mod(card.parts.size)
        val nextPart = PendingIntent.getBroadcast(
            appContext,
            4,
            Intent(appContext, NextPartReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quote)
            .setContentTitle(quote.author ?: appContext.getString(R.string.app_name))
            .setContentText(quote.text)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(quoteView(appContext, quote, style, lockTheme, card, part, nextPart, expanded = false))
            .setCustomBigContentView(quoteView(appContext, quote, style, lockTheme, card, part, nextPart, expanded = true))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (QuoteRepository(appContext, prefs).hasPrevious()) {
            builder.addAction(0, appContext.getString(R.string.action_previous), previous)
        }
        builder.addAction(0, appContext.getString(R.string.action_copy), copy)
        builder.addAction(0, appContext.getString(R.string.action_next), next)
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    /** Custom layout so the user's font, size and color apply; the system still draws the background. */
    private fun quoteView(
        context: Context,
        quote: Quote,
        style: QuoteStyle,
        theme: AppTheme,
        card: CardLayout,
        part: Int,
        nextPart: PendingIntent,
        expanded: Boolean,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, style.font.layout)
        val text = "\u201C${quote.text}\u201D"
        val author = quote.author?.let { "\u2014 $it" }

        if (expanded) {
            val parts = card.parts
            val last = part == parts.lastIndex
            // Long quotes come in parts: "…" marks where a part continues, and tapping shows the next.
            val shown = (if (part == 0) "\u201C" else "\u2026") + parts[part] + (if (last) "\u201D" else "\u2026")
            views.setTextViewText(R.id.quote_text, if (parts.size == 1) text else shown)
            views.setInt(R.id.quote_text, "setMaxLines", 60)
            val counter = "${part + 1}/${parts.size}"
            val authorLine = when {
                parts.size == 1 -> author
                !last -> "Tap for more  \u00B7  $counter \u203A"
                author != null -> "$counter  $author"
                else -> counter
            }
            if (authorLine != null) {
                views.setTextViewText(R.id.quote_author, authorLine)
                views.setViewVisibility(R.id.quote_author, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.quote_author, View.GONE)
            }
            if (parts.size > 1) views.setOnClickPendingIntent(R.id.quote_inner, nextPart)
        } else {
            // The collapsed area is short, so the author shares the text and the size is capped.
            views.setTextViewText(R.id.quote_text, if (author != null) "$text $author" else text)
            views.setInt(R.id.quote_text, "setMaxLines", 2)
            views.setViewVisibility(R.id.quote_author, View.GONE)
        }

        // The expanded size is measured to fit the card (see CardFit).
        val sp = if (expanded) card.sp else minOf(style.size.sizeSp, 16f)
        views.setTextViewTextSize(R.id.quote_text, TypedValue.COMPLEX_UNIT_SP, sp)
        views.setTextViewTextSize(R.id.quote_author, TypedValue.COMPLEX_UNIT_SP, sp * 0.85f)
        // Themes frame the expanded quote in their own artwork. The collapsed view is too short for a
        // frame, so it keeps the system background (and the system text colour unless one is chosen).
        val framed = expanded && theme.notificationFrame != null
        if (framed) {
            val density = context.resources.displayMetrics.density
            // Outer view shows the artwork as a frame; the inner card is the theme colour at the
            // user's chosen opacity.
            views.setInt(R.id.quote_root, "setBackgroundResource", theme.notificationFrame!!)
            val frame = (card.frameDp * density).toInt()
            views.setViewPadding(R.id.quote_root, frame, frame, frame, frame)
            val alpha = (QuotePrefs(context).cardOpacity * 255).toInt().coerceIn(0, 255)
            val card = (alpha shl 24) or (theme.cardColor!! and 0x00FFFFFF)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setInt(R.id.quote_inner, "setBackgroundResource", R.drawable.notif_inner)
                views.setColorStateList(R.id.quote_inner, "setBackgroundTintList", ColorStateList.valueOf(card))
            } else {
                views.setInt(R.id.quote_inner, "setBackgroundColor", card)
            }
            val h = (CardFit.INNER_H_DP * density).toInt()
            val v = (CardFit.INNER_V_DP * density).toInt()
            views.setViewPadding(R.id.quote_inner, h, v, h, v)
        }
        val textColor = style.color ?: if (framed) theme.notificationText else null
        val authorColor = style.color ?: if (framed) theme.notificationAuthor else null
        textColor?.let { views.setTextColor(R.id.quote_text, it) }
        authorColor?.let { views.setTextColor(R.id.quote_author, it) }
        return views
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_description)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
