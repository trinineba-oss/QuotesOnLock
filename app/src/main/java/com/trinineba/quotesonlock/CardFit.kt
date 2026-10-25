package com.trinineba.quotesonlock

import android.content.Context
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue

/** How the expanded lock-screen card shows a quote: type size, frame width, and the parts to tap through. */
class CardLayout(val sp: Float, val frameDp: Int, val parts: List<String>)

/**
 * Notifications can't scroll and the expanded card is only about 256dp tall, so the quote is
 * measured with the real font and card width: it gets the largest size that fits (a thinner frame
 * is tried before smaller type), and a quote too long even at the smallest size is split into parts.
 */
object CardFit {
    /** Height left for our view inside the expanded notification, after the header and buttons. */
    private const val CARD_HEIGHT_DP = 200f
    /** Lock-screen margins plus the notification's own side padding. */
    private const val SIDE_DP = 64f
    private const val MIN_SP = 11.5f
    const val INNER_H_DP = 10
    const val INNER_V_DP = 8

    fun fit(context: Context, text: String, author: String?, style: QuoteStyle, framed: Boolean): CardLayout {
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(style.font.family, Typeface.NORMAL) }
        val frames = if (framed) listOf(6, 3) else listOf(0)
        val quoted = "“$text”"
        var sp = style.size.sizeSp
        while (sp >= MIN_SP) {
            for (frame in frames) {
                val (width, budget) = space(context, paint, sp, frame, framed, author != null)
                if (height(quoted, paint, width) <= budget) return CardLayout(sp, frame, listOf(text))
            }
            sp -= 0.5f
        }
        // Too long even at the smallest size: split it, keeping a line spare for the "…" marks.
        val frame = frames.last()
        val (width, budget) = space(context, paint, MIN_SP, frame, framed, author != null)
        val spare = paint.fontMetricsInt.let { it.descent - it.ascent }
        return CardLayout(MIN_SP, frame, split(text, paint, width, budget - spare))
    }

    /** Text width and height available at [sp]; sets the paint's size. */
    private fun space(context: Context, paint: TextPaint, sp: Float, frame: Int, framed: Boolean, hasAuthor: Boolean): Pair<Int, Int> {
        val dm = context.resources.displayMetrics
        fun dp(v: Float) = v * dm.density
        val innerH = if (framed) INNER_H_DP else 0
        val innerV = if (framed) INNER_V_DP else 0
        // The author line (also used for "1/3") is one line of slightly smaller type.
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp * 0.85f, dm)
        val authorLine = paint.fontMetricsInt.let { it.bottom - it.top } + dp(2f).toInt()
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, dm)
        val width = (dm.widthPixels - dp(SIDE_DP) - dp(2f * (frame + innerH))).toInt().coerceAtLeast(1)
        val budget = (dp(CARD_HEIGHT_DP) - dp(2f * (frame + innerV))).toInt() - authorLine
        return width to budget
    }

    private fun layout(text: CharSequence, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width).setIncludePad(true).build()

    private fun height(text: CharSequence, paint: TextPaint, width: Int) = layout(text, paint, width).height

    /** Cuts [text] into parts that fit [budget], preferring the end of a sentence or verse line. */
    private fun split(text: String, paint: TextPaint, width: Int, budget: Int): List<String> {
        val parts = mutableListOf<String>()
        var rest = text.trim()
        while (rest.isNotEmpty()) {
            val l = layout(rest, paint, width)
            if (l.height <= budget) {
                parts += rest
                break
            }
            var line = l.getLineForVertical(budget)
            if (l.getLineBottom(line) > budget) line--
            var end = l.getLineEnd(line.coerceAtLeast(0))
            val nice = (end - 1 downTo end / 2).firstOrNull { i ->
                rest[i] == '\n' || (rest[i] in ".?!;:" && (i + 1 >= rest.length || rest[i + 1].isWhitespace()))
            }
            if (nice != null) end = nice + 1
            if (end <= 0) end = minOf(rest.length, 1)
            parts += rest.substring(0, end).trim()
            rest = rest.substring(end).trim()
        }
        return parts
    }
}
