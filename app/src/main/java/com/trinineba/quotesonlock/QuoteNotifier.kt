package com.trinineba.quotesonlock

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quote)
            .setContentTitle(quote.author ?: appContext.getString(R.string.app_name))
            .setContentText(quote.text)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(quoteView(appContext, quote, style, expanded = false))
            .setCustomBigContentView(quoteView(appContext, quote, style, expanded = true))
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
        expanded: Boolean,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, style.font.layout)
        val text = "\u201C${quote.text}\u201D"
        val author = quote.author?.let { "\u2014 $it" }

        if (expanded) {
            views.setTextViewText(R.id.quote_text, text)
            views.setInt(R.id.quote_text, "setMaxLines", 16)
            if (author != null) {
                views.setTextViewText(R.id.quote_author, author)
                views.setViewVisibility(R.id.quote_author, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.quote_author, View.GONE)
            }
        } else {
            // The collapsed area is short, so the author shares the text and the size is capped.
            views.setTextViewText(R.id.quote_text, if (author != null) "$text $author" else text)
            views.setInt(R.id.quote_text, "setMaxLines", 2)
            views.setViewVisibility(R.id.quote_author, View.GONE)
        }

        val sp = if (expanded) style.size.sizeSp else minOf(style.size.sizeSp, 16f)
        views.setTextViewTextSize(R.id.quote_text, TypedValue.COMPLEX_UNIT_SP, sp)
        views.setTextViewTextSize(R.id.quote_author, TypedValue.COMPLEX_UNIT_SP, sp * 0.85f)
        style.color?.let {
            views.setTextColor(R.id.quote_text, it)
            views.setTextColor(R.id.quote_author, it)
        }
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
