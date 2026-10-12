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
