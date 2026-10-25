package com.trinineba.quotesonlock

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.SurfaceHolder
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Live wallpaper: the lock-screen theme's artwork (optionally moving) with today's quote drawn on
 * it. Animates only while visible and at most ~30 frames a second.
 */
class QuoteWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = QuoteEngine()

    private inner class QuoteEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val prefs = QuotePrefs(this@QuoteWallpaperService)
        private val handler = Handler(Looper.getMainLooper())
        private val start = SystemClock.elapsedRealtime()
        private var visible = false
        private var width = 0
        private var height = 0
        private val bitmaps = HashMap<Int, Bitmap>()
        private var swirl: RuntimeShader? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val stars = Random(11).let { rnd -> List(160) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat() * 0.7f, 0.6f + rnd.nextFloat() * 1.4f, 0.3f + rnd.nextFloat() * 0.7f) } }
        private val frame = Runnable { draw() }

        private val theme: AppTheme
            get() = (prefs.lockTheme ?: prefs.theme).let { if (it == AppTheme.CLASSIC) AppTheme.STARRY else it }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            prefs.registerListener(this)
        }

        override fun onDestroy() {
            handler.removeCallbacks(frame)
            prefs.unregisterListener(this)
            super.onDestroy()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) draw() else handler.removeCallbacks(frame)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            this.width = width
            this.height = height
            draw()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(frame)
            super.onSurfaceDestroyed(holder)
        }

        // A new quote, theme or style redraws straight away.
        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            if (visible) {
                handler.removeCallbacks(frame)
                handler.post(frame)
            }
        }

        private fun moving() = prefs.animateBackground && theme.motionLabel != null

        private fun draw() {
            if (width == 0 || height == 0) return
            val holder = surfaceHolder
            // Hardware canvas for the GPU swirl; fall back to a software canvas if the surface refuses it.
            val canvas = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) runCatching { holder.lockHardwareCanvas() }.getOrNull() else null)
                ?: runCatching { holder.lockCanvas() }.getOrNull()
                ?: return
            try {
                val t = (SystemClock.elapsedRealtime() - start) / 1000f
                when (theme) {
                    AppTheme.AURORA -> drawAurora(canvas, if (moving()) t * 2f * PI.toFloat() / 10f else 1.2f)
                    AppTheme.BOOK -> drawCover(canvas, bitmap(R.drawable.paper))
                    AppTheme.BUTTERFLY -> ButterflyArt.draw(canvas, width.toFloat(), height.toFloat(), if (moving()) t else 3f, resources.displayMetrics.density)
                    else -> drawStarry(canvas, t * 2f * PI.toFloat() / 6f)
                }
                if (prefs.wallpaperShowQuote) drawQuote(canvas)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
            handler.removeCallbacks(frame)
            if (visible && moving()) handler.postDelayed(frame, 33)
        }

        private fun bitmap(id: Int): Bitmap = bitmaps.getOrPut(id) { BitmapFactory.decodeResource(resources, id) }

        /** Scale to cover the screen, centred horizontally and anchored at the top. */
        private fun coverMatrix(b: Bitmap): Matrix {
            val s = max(width / b.width.toFloat(), height / b.height.toFloat())
            return Matrix().apply { setScale(s, s); postTranslate((width - b.width * s) / 2f, 0f) }
        }

        private fun drawCover(canvas: Canvas, b: Bitmap) {
            canvas.drawBitmap(b, coverMatrix(b), paint)
        }

        private fun drawStarry(canvas: Canvas, time: Float) {
            val b = bitmap(R.drawable.starry_night)
            if (moving() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && canvas.isHardwareAccelerated) {
                val shader = swirl ?: RuntimeShader(SWIRL_SHADER).also { swirl = it }
                val image = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(coverMatrix(b)) }
                shader.setInputShader("image", image)
                val s = max(width / b.width.toFloat(), height / b.height.toFloat())
                val iw = b.width * s
                val ih = b.height * s
                val tx = (width - iw) / 2f
                shader.setFloatUniform("time", time)
                shader.setFloatUniform("k", 900f / ih)
                shader.setFloatUniform("c0", tx + 0.47f * iw, 0.36f * ih, 0.167f * ih, 0.38f)
                shader.setFloatUniform("c1", tx + 0.30f * iw, 0.20f * ih, 0.067f * ih, 0.5f)
                shader.setFloatUniform("c2", tx + 0.62f * iw, 0.12f * ih, 0.061f * ih, 0.5f)
                val p = Paint().apply { this.shader = shader }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
            } else {
                drawCover(canvas, b)
            }
        }

        private fun drawAurora(canvas: Canvas, t: Float) {
            val w = width.toFloat()
            val h = height.toFloat()
            val sky = Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(0xFF02040C.toInt(), 0xFF061024.toInt(), 0xFF0A1A2C.toInt()), null, Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, w, h, sky)
            val dot = Paint(Paint.ANTI_ALIAS_FLAG)
            val density = resources.displayMetrics.density
            stars.forEachIndexed { i, s ->
                val twinkle = if (moving()) 0.75f + 0.25f * sin(t * 6 + i) else 1f
                dot.color = Color.argb((255 * s[3] * twinkle).toInt(), 255, 255, 255)
                canvas.drawCircle(s[0] * w, s[1] * h, s[2] * density, dot)
            }
            val ribbons = listOf(
                Triple(0xFF46FFAA.toInt(), 0.30f, 0f),
                Triple(0xFF28D2DC.toInt(), 0.38f, 1.7f),
                Triple(0xFF965AFF.toInt(), 0.22f, 3.1f),
            )
            val fill = Paint(Paint.ANTI_ALIAS_FLAG)
            val k = prefs.auroraIntensity
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
                    val x = w * j / steps
                    if (j == 0) path.moveTo(x, edge(x) - tail) else path.lineTo(x, edge(x) - tail)
                }
                for (j in steps downTo 0) {
                    val x = w * j / steps
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
        }

        private fun drawQuote(canvas: Canvas) {
            val text = prefs.currentText ?: return
            val author = prefs.currentAuthor
            val theme = theme
            val style = prefs.style
            val density = resources.displayMetrics.scaledDensity
            val dark = !theme.isLight
            val textColor = style.color ?: theme.notificationText ?: Color.WHITE
            val authorColor = style.color ?: theme.notificationAuthor ?: Color.WHITE

            // Long quotes get smaller type so they always fit in the middle of the screen.
            val base = style.size.sizeSp + 4f
            val fit = when {
                text.length > 450 -> 0.7f
                text.length > 300 -> 0.8f
                text.length > 180 -> 0.9f
                else -> 1f
            }
            val typeface = Typeface.create(style.font.family, Typeface.NORMAL)
            val quotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textSize = base * fit * density
                this.typeface = typeface
                if (dark) setShadowLayer(6f * density / 2, 0f, 1.5f * density, 0xE6000000.toInt())
            }
            val authorPaint = TextPaint(quotePaint).apply {
                color = authorColor
                textSize = quotePaint.textSize * 0.82f
            }
            val margin = width * 0.09f
            val pad = 18f * resources.displayMetrics.density
            val textWidth = (width - 2 * margin - 2 * pad).toInt().coerceAtLeast(1)
            val quoteLayout = staticLayout("“$text”", quotePaint, textWidth)
            val authorLayout = author?.let { staticLayout("— $it", authorPaint, textWidth) }
            val gap = 10f * resources.displayMetrics.density
            val blockHeight = quoteLayout.height + (authorLayout?.let { it.height + gap } ?: 0f)
            val centerY = height * prefs.wallpaperQuotePosition.coerceIn(0.2f, 0.85f)
            val top = (centerY - blockHeight / 2f).coerceIn(height * 0.15f, max(height * 0.15f, height - blockHeight - 2 * pad - height * 0.05f))

            // Card behind the text, in the theme colour at the user's chosen opacity.
            val card = RectF(margin, top - pad, width - margin, top + blockHeight + pad)
            val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                val cardBase = theme.cardColor ?: 0xFF000000.toInt()
                color = ((prefs.cardOpacity * 255).toInt().coerceIn(0, 255) shl 24) or (cardBase and 0x00FFFFFF)
            }
            val radius = 18f * resources.displayMetrics.density
            canvas.drawRoundRect(card, radius, radius, cardPaint)

            canvas.save()
            canvas.translate(margin + pad, top)
            quoteLayout.draw(canvas)
            authorLayout?.let {
                canvas.translate(0f, quoteLayout.height + gap)
                it.draw(canvas)
            }
            canvas.restore()
        }

        private fun staticLayout(text: String, paint: TextPaint, width: Int): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.15f)
                .build()
    }
}
