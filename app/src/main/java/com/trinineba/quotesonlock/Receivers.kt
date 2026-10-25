package com.trinineba.quotesonlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the "Next quote" button on the notification. */
class NextQuoteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = QuotePrefs(context)
        if (!prefs.enabled) return
        QuoteRepository(context, prefs).advance()?.let { QuoteNotifier.show(context, it) }
    }
}

/** Handles the "Previous" button on the notification. */
class PreviousQuoteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = QuotePrefs(context)
        if (!prefs.enabled) return
        QuoteRepository(context, prefs).previous()?.let { QuoteNotifier.show(context, it) }
    }
}

/** Tapping a long quote on the lock-screen card shows its next part. */
class NextPartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = QuotePrefs(context)
        if (!prefs.enabled) return
        val quote = QuoteRepository(context, prefs).current() ?: return
        val key = quote.text.hashCode()
        prefs.cardPart = if (prefs.cardPartKey == key) prefs.cardPart + 1 else 1
        prefs.cardPartKey = key
        QuoteNotifier.show(context, quote)
    }
}

/** Handles the "Copy" button on the notification. */
class CopyQuoteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val quote = QuoteRepository(context, QuotePrefs(context)).current() ?: return
        QuoteClipboard.copy(context, quote)
    }
}

/** Notifications don't survive a reboot, so put the current quote back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = QuotePrefs(context)
        if (!prefs.enabled) return
        val repo = QuoteRepository(context, prefs)
        (repo.current() ?: repo.advance())?.let { QuoteNotifier.show(context, it) }
    }
}
