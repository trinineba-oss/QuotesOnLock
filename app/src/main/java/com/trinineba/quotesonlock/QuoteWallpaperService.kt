package com.trinineba.quotesonlock

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.PI
import kotlin.math.max

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

        private fun drawAurora(canvas: Canvas, t: Float) =
            QuoteArt.drawAurora(
                canvas, width.toFloat(), height.toFloat(), t, moving(), prefs.auroraIntensity,
                resources.displayMetrics.density, blur = canvas.isHardwareAccelerated,
            )

        private fun drawQuote(canvas: Canvas) {
            val text = prefs.currentText ?: return
            val metrics = resources.displayMetrics
            QuoteArt.drawQuoteCard(
                canvas, width, height, metrics.density, metrics.scaledDensity, theme, prefs.style,
                text, prefs.currentAuthor, prefs.cardOpacity, prefs.wallpaperQuotePosition, prefs.cardSoftness,
            )
        }
    }
}
