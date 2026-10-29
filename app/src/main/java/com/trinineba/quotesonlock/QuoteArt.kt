package com.trinineba.quotesonlock

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** Drawing shared by the live wallpaper and the share-as-image picture. */
internal object QuoteArt {
    private val stars = Random(11).let { rnd ->
        List(160) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat() * 0.7f, 0.6f + rnd.nextFloat() * 1.4f, 0.3f + rnd.nextFloat() * 0.7f) }
    }

    /**
     * The northern lights as the app draws them: soft blurred ribbons and a shade that deepens toward
     * the bottom. [t] is the animation phase, [twinkle] makes the stars flicker, and [blur] softens
     * the ribbons (skip it on a software canvas, where blurring every frame would be slow).
     */
    fun drawAurora(canvas: Canvas, w: Float, h: Float, t: Float, twinkle: Boolean, intensity: Float, density: Float, blur: Boolean = true) {
        val sky = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(0xFF02040C.toInt(), 0xFF061024.toInt(), 0xFF0A1A2C.toInt()), null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, h, sky)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        stars.forEachIndexed { i, s ->
            val flicker = if (twinkle) 0.75f + 0.25f * sin(t * 6 + i) else 1f
            dot.color = Color.argb((255 * s[3] * flicker).toInt(), 255, 255, 255)
            canvas.drawCircle(s[0] * w, s[1] * h, s[2] * density, dot)
        }
        val ribbons = listOf(
            Triple(0xFF46FFAA.toInt(), 0.30f, 0f),
            Triple(0xFF28D2DC.toInt(), 0.38f, 1.7f),
            Triple(0xFF965AFF.toInt(), 0.22f, 3.1f),
        )
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val blurRadius = 14f * density
        if (blur) fill.maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
        // Run the ribbons past both edges so the blur doesn't fade them out at the sides.
        val overshoot = if (blur) blurRadius * 2f else 0f
        val k = intensity
        fun alpha(a: Float) = ((a * k).coerceAtMost(1f) * 255).toInt() shl 24
        ribbons.forEachIndexed { i, (color, base, phase) ->
            val amp = h * (0.05f - 0.01f * i) * (0.8f + 0.2f * k)
            val tail = h * (0.22f - 0.04f * i) * (0.6f + 0.4f * k)
            val steps = 48
            fun edge(x: Float): Float {
                val u = x / w * 2f * PI.toFloat()
                return h * base + amp * sin(u * (1.1f + 0.5f * i) + phase + t * (1f + 0.3f * i)) +
                    amp * 0.4f * sin(u * 4.3f - t * 0.8f + phase)
            }
            val path = Path()
            for (j in 0..steps) {
                val x = -overshoot + (w + 2 * overshoot) * j / steps
                if (j == 0) path.moveTo(x, edge(x) - tail) else path.lineTo(x, edge(x) - tail)
            }
            for (j in steps downTo 0) {
                val x = -overshoot + (w + 2 * overshoot) * j / steps
                path.lineTo(x, edge(x))
            }
            path.close()
            fill.shader = LinearGradient(
                0f, h * base - amp - tail, 0f, h * base + amp,
                intArrayOf(Color.TRANSPARENT, (color and 0x00FFFFFF) or alpha(0.35f), (color and 0x00FFFFFF) or alpha(0.75f)),
                floatArrayOf(0f, 0.75f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawPath(path, fill)
        }
        // Darken the lower part, as the app does, so the lights sit in the top of the sky.
        val night = 0xFF040915.toInt()
        val shade = (1.25f - 0.25f * intensity).coerceIn(0.75f, 1.1f)
        fun nightAlpha(a: Float) = ((a * shade).coerceIn(0f, 1f) * 255).toInt() shl 24 or (night and 0x00FFFFFF)
        val shadePaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(Color.TRANSPARENT, nightAlpha(0.55f), nightAlpha(0.92f), night),
                floatArrayOf(0f, 0.4f, 0.62f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w, h, shadePaint)
    }

    private class Block(val quote: StaticLayout, val author: StaticLayout?, val height: Float)

    /**
     * The quote on a card in the theme colour at [cardOpacity], centred at [position] (0–1 of the
     * height). [density] converts dp to pixels and [scale] converts sp to pixels. Long quotes shrink
     * until the block fits within about 70% of the height.
     */
    fun drawQuoteCard(
        canvas: Canvas,
        w: Int,
        h: Int,
        density: Float,
        scale: Float,
        theme: AppTheme,
        style: QuoteStyle,
        text: String,
        author: String?,
        cardOpacity: Float,
        position: Float,
        softness: Float = 0f,
    ) {
        val dark = !theme.isLight
        val textColor = style.color ?: theme.notificationText ?: Color.WHITE
        val authorColor = style.color ?: theme.notificationAuthor ?: Color.WHITE
        val typeface = Typeface.create(style.font.family, Typeface.NORMAL)
        val base = style.size.sizeSp + 4f
        val margin = w * 0.09f
        // A soft edge fades over this distance; the text is padded further in so it sits on solid colour.
        val feather = softness.coerceIn(0f, 1f) * 36f * density
        val pad = 18f * density + feather * 0.55f
        val textWidth = (w - 2 * margin - 2 * pad).toInt().coerceAtLeast(1)
        val gap = 10f * density

        fun build(fit: Float): Block {
            val quotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textSize = base * fit * scale
                this.typeface = typeface
                if (dark) setShadowLayer(6f * density / 2, 0f, 1.5f * density, 0xE6000000.toInt())
            }
            val authorPaint = TextPaint(quotePaint).apply {
                color = authorColor
                textSize = quotePaint.textSize * 0.82f
            }
            val q = layout("“$text”", quotePaint, textWidth)
            val a = author?.let { layout("— $it", authorPaint, textWidth) }
            return Block(q, a, q.height + (a?.let { it.height + gap } ?: 0f))
        }

        var fit = when {
            text.length > 450 -> 0.7f
            text.length > 300 -> 0.8f
            text.length > 180 -> 0.9f
            else -> 1f
        }
        var block = build(fit)
        while (block.height + 2 * pad > h * 0.7f && fit > 0.45f) {
            fit *= 0.92f
            block = build(fit)
        }

        val centerY = h * position.coerceIn(0.2f, 0.85f)
        val top = (centerY - block.height / 2f).coerceIn(h * 0.15f, max(h * 0.15f, h - block.height - 2 * pad - h * 0.05f))
        val card = RectF(margin, top - pad, w - margin, top + block.height + pad)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val cardBase = theme.cardColor ?: 0xFF000000.toInt()
            color = ((cardOpacity * 255).toInt().coerceIn(0, 255) shl 24) or (cardBase and 0x00FFFFFF)
        }
        val radius = 18f * density
        if (feather < 1f) {
            canvas.drawRoundRect(card, radius, radius, cardPaint)
        } else {
            drawSoftCard(canvas, card, radius, feather, theme.cardColor ?: 0xFF000000.toInt(), cardOpacity)
        }

        canvas.save()
        canvas.translate(margin + pad, top)
        block.quote.draw(canvas)
        block.author?.let {
            canvas.translate(0f, block.quote.height + gap)
            it.draw(canvas)
        }
        canvas.restore()
    }

    /**
     * A card whose edge fades out: nested rounded rectangles, the outermost [feather] beyond [card]
     * and the innermost [feather] inside it, each drawn so the layers add up to a smooth ramp from
     * transparent to [opacity]. Plain alpha layering, so it works on any canvas.
     */
    private fun drawSoftCard(canvas: Canvas, card: RectF, radius: Float, feather: Float, color: Int, opacity: Float) {
        val layers = 20
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF()
        var covered = 0f
        for (i in 0 until layers) {
            val x = (i + 1f) / layers
            val target = opacity.coerceIn(0f, 1f) * (x * x * (3f - 2f * x))
            val a = if (covered >= 1f) 0f else (1f - (1f - target) / (1f - covered)).coerceIn(0f, 1f)
            covered = target
            if (a <= 0f) continue
            val inset = -feather + 2f * feather * i / (layers - 1)
            rect.set(card.left + inset, card.top + inset, card.right - inset, card.bottom - inset)
            val r = (radius - inset).coerceAtLeast(2f)
            paint.color = ((a * 255).toInt().coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)
            canvas.drawRoundRect(rect, r, r, paint)
        }
    }

    private fun layout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.15f)
            .build()
}
