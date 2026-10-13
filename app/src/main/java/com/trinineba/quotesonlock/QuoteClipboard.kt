package com.trinineba.quotesonlock

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

object QuoteClipboard {
    /** Puts the quote on the clipboard as: "text" — author. */
    fun copy(context: Context, quote: Quote) {
        val app = context.applicationContext
        val text = "\u201C${quote.text}\u201D" + (quote.author?.let { " \u2014 $it" } ?: "")
        val clipboard = app.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Quote", text))
        Toast.makeText(app, R.string.quote_copied, Toast.LENGTH_SHORT).show()
    }
}
