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
    private companion object {
        /** Width to height of the centred panel a tall scene is drawn in on a wide screen. */
        const val PANEL_ASPECT = 0.56f

        /** Pictures that are themselves wide, which fill a wide screen edge to edge. */
        val FULL_WIDTH = setOf(AppTheme.STARRY, AppTheme.CLOUDS, AppTheme.BLACK)
    }

    override fun onCreateEngine(): Engine = QuoteEngine()

    private inner class QuoteEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val prefs = QuotePrefs(this@QuoteWallpaperService)
        private val handler = Handler(Looper.getMainLooper())
        // Animation clock: adds up time at the chosen motion speed, so a speed change never jumps.
        private var clock = 0f
        private var lastFrame = SystemClock.elapsedRealtime()
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

        private fun moving() = !stillOnly && prefs.animateBackground && theme.motionLabel != null

        private fun draw() {
            if (width == 0 || height == 0) return
            val holder = surfaceHolder
            // Hardware canvas for the GPU swirl; fall back to a software canvas if the surface refuses it.
            val canvas = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) runCatching { holder.lockHardwareCanvas() }.getOrNull() else null)
                ?: runCatching { holder.lockCanvas() }.getOrNull()
                ?: return
            try {
                val now = SystemClock.elapsedRealtime()
                clock += ((now - lastFrame) / 1000f).coerceAtMost(0.1f) * prefs.motionSpeed
                lastFrame = now
                val t = clock
                if (panelled()) drawPanelled(canvas, t) else drawScene(canvas, t)
                if (prefs.wallpaperShowQuote) drawQuote(canvas)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
            handler.removeCallbacks(frame)
            if (visible && moving()) handler.postDelayed(frame, 33)
        }

        /** The theme's artwork at the current [width] x [height]. */
        private fun drawScene(canvas: Canvas, t: Float) {
            when (theme) {
                AppTheme.BLACK -> canvas.drawColor(android.graphics.Color.BLACK)
                AppTheme.AURORA -> drawAurora(canvas, if (moving()) t * 2f * PI.toFloat() / 10f else 1.2f)
                AppTheme.BUTTERFLY -> ButterflyArt.draw(canvas, width.toFloat(), height.toFloat(), if (moving()) t else 3f, resources.displayMetrics.density)
                AppTheme.WATER -> drawWater(canvas, t)
                AppTheme.DROP -> drawDrop(canvas, t)
                AppTheme.RAIN -> drawRain(canvas, t, night = false)
                AppTheme.RAIN_NIGHT -> drawRain(canvas, t, night = true)
                AppTheme.SNOW, AppTheme.CANDLE, AppTheme.FIREFLIES, AppTheme.LAVENDER, AppTheme.CLOUDS, AppTheme.WAVES, AppTheme.BAMBOO, AppTheme.ZEN, AppTheme.BOOK, AppTheme.BLOSSOM, AppTheme.MOUNTAINS, AppTheme.LANTERNS, AppTheme.LEAVES, AppTheme.SEA -> {
                    CalmArt.attach(this@QuoteWallpaperService)
                    runCatching { CalmArt.draw(theme, canvas, width.toFloat(), height.toFloat(), if (moving()) t else CalmArt.stillTime(theme), resources.displayMetrics.density) }
                        .onFailure { CalmArt.reset() }
                }
                AppTheme.SPACE -> SpaceArt.draw(canvas, width.toFloat(), height.toFloat(), if (moving()) t else 20f, resources.displayMetrics.density, bitmap(R.drawable.nebula))
                else -> drawStarry(canvas, t * 2f * PI.toFloat() / 6f)
            }
        }

        // ---- landscape -----------------------------------------------------------------------

        /** Most scenes are painted for a tall screen; on a wide one they sit in a centred panel. */
        private fun panelled() = width > height && theme !in FULL_WIDTH

        private var sideKey = ""
        private var side: Bitmap? = null
        private var stillOnly = false

        /**
         * A wide screen: the live scene in a centred, phone-shaped panel, and either side a soft, dimmed,
         * blurred copy of the same scene (a still, painted once), so the screen is filled without
         * stretching or cropping the artwork.
         */
        private fun drawPanelled(canvas: Canvas, t: Float) {
            val fullW = width
            val fullH = height
            val pw = minOf(fullW, (fullH * PANEL_ASPECT).toInt()).coerceAtLeast(1)
            val left = (fullW - pw) / 2f
            val blurred = sideImage(pw, fullH)
            if (blurred != null) {
                val s = max(fullW / blurred.width.toFloat(), fullH / blurred.height.toFloat())
                val m = Matrix().apply { setScale(s, s); postTranslate((fullW - blurred.width * s) / 2f, (fullH - blurred.height * s) / 2f) }
                canvas.drawBitmap(blurred, m, paint)
                canvas.drawColor(0x73000000)
            } else {
                canvas.drawColor(android.graphics.Color.BLACK)
            }
            canvas.save()
            canvas.clipRect(left, 0f, left + pw, fullH.toFloat())
            canvas.translate(left, 0f)
            width = pw
            try {
                drawScene(canvas, t)
            } finally {
                width = fullW
                canvas.restore()
            }
            // A soft shadow where the panel meets the blurred sides.
            val edge = 18f * resources.displayMetrics.density
            val shade = Paint()
            shade.shader = android.graphics.LinearGradient(left - edge, 0f, left, 0f, 0x00000000, 0x55000000, Shader.TileMode.CLAMP)
            canvas.drawRect(left - edge, 0f, left, fullH.toFloat(), shade)
            shade.shader = android.graphics.LinearGradient(left + pw, 0f, left + pw + edge, 0f, 0x55000000, 0x00000000, Shader.TileMode.CLAMP)
            canvas.drawRect(left + pw, 0f, left + pw + edge, fullH.toFloat(), shade)
        }

        /** The scene as a still, painted small in software and shrunk further, which blurs it when drawn large. */
        private fun sideImage(pw: Int, ph: Int): Bitmap? {
            val key = "${theme.key}:$pw:$ph:${prefs.auroraIntensity}"
            if (key == sideKey) side?.let { return it }
            val sw = 160
            val sh = (sw * ph / pw.toFloat()).toInt().coerceAtLeast(1)
            val small = runCatching { Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return null
            val savedW = width
            val savedH = height
            width = sw
            height = sh
            stillOnly = true
            try {
                runCatching { drawScene(Canvas(small), CalmArt.stillTime(theme)) }
            } finally {
                stillOnly = false
                width = savedW
                height = savedH
            }
            val tiny = Bitmap.createScaledBitmap(small, 12, (12f * sh / sw).toInt().coerceAtLeast(1), true)
            small.recycle()
            side = tiny
            sideKey = key
            return tiny
        }

        private fun bitmap(id: Int): Bitmap = bitmaps.getOrPut(id) { BitmapFactory.decodeResource(resources, id) }

        /** Scale to cover the screen, centred horizontally and anchored at the top. */
        private fun coverMatrix(b: Bitmap): Matrix {
            val s = max(width / b.width.toFloat(), height / b.height.toFloat())
            // Anchored at the top on a tall screen; centred when the picture is cropped top and bottom.
            val ty = if (width > height) (height - b.height * s) / 2f else 0f
            return Matrix().apply { setScale(s, s); postTranslate((width - b.width * s) / 2f, ty) }
        }

        private fun drawCover(canvas: Canvas, b: Bitmap) {
            canvas.drawBitmap(b, coverMatrix(b), paint)
        }

        private fun drawStarry(canvas: Canvas, time: Float) {
            val b = bitmap(R.drawable.starry_night)
            if (moving() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && canvas.isHardwareAccelerated) {
                val shader = swirl ?: RuntimeShader(SWIRL_SHADER).also { swirl = it }
                val cover = coverMatrix(b)
                val image = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(cover) }
                shader.setInputShader("image", image)
                // Only the sky turns: the mask (half size) keeps the cypress, hills and village still.
                val mask = bitmap(R.drawable.starry_sky)
                val sky = BitmapShader(mask, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    setLocalMatrix(Matrix(cover).apply { preScale(b.width / mask.width.toFloat(), b.height / mask.height.toFloat()) })
                }
                shader.setInputShader("sky", sky)
                val s = max(width / b.width.toFloat(), height / b.height.toFloat())
                val iw = b.width * s
                val ih = b.height * s
                val tx = (width - iw) / 2f
                val ty = if (width > height) (height - ih) / 2f else 0f
                shader.setFloatUniform("time", time)
                shader.setFloatUniform("k", 900f / ih)
                shader.setFloatUniform("c0", tx + 0.47f * iw, ty + 0.36f * ih, 0.167f * ih, 0.38f)
                shader.setFloatUniform("c1", tx + 0.30f * iw, ty + 0.20f * ih, 0.067f * ih, 0.5f)
                shader.setFloatUniform("c2", tx + 0.62f * iw, ty + 0.12f * ih, 0.061f * ih, 0.5f)
                val p = Paint().apply { this.shader = shader }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
            } else {
                drawCover(canvas, b)
            }
        }

        private var rainShader: RuntimeShader? = null
        private var rainShaderFailed = false
        private var rainScene: BitmapShader? = null
        private var rainFog: BitmapShader? = null
        private var rainNight: Boolean? = null

        /** Rain on the glass on the GPU (Android 13+, hardware canvas); otherwise the still picture. */
        private fun drawRain(canvas: Canvas, t: Float, night: Boolean) {
            val shader = if (moving() && !rainShaderFailed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && canvas.isHardwareAccelerated) {
                rainShader ?: runCatching { RuntimeShader(RainArt.SHADER) }.getOrNull().also {
                    rainShader = it
                    rainShaderFailed = it == null
                }
            } else {
                null
            }
            if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (rainNight != night) {
                    rainScene = null
                    rainFog = null
                    rainNight = night
                }
                val scene = bitmap(if (night) R.drawable.rain_night_scene else R.drawable.rain_scene)
                val sceneShader = rainScene ?: BitmapShader(scene, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also { rainScene = it }
                val fogShader = rainFog ?: BitmapShader(
                    bitmap(if (night) R.drawable.rain_night_fog else R.drawable.rain_fog), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP,
                ).also { rainFog = it }
                RainArt.bind(
                    shader, sceneShader, fogShader, scene.width, scene.height, width.toFloat(), height.toFloat(), t,
                    streaks = if (night) 0f else 1f, lensLift = if (night) 0.6f else 0f,
                )
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { this.shader = shader })
            } else {
                drawCover(canvas, bitmap(if (night) R.drawable.rain_night_still else R.drawable.rain_still))
            }
        }

        private var dropShader: RuntimeShader? = null
        private var dropShaderFailed = false

        /** A single drop on the GPU (Android 13+, hardware canvas); otherwise the still picture. */
        private fun drawDrop(canvas: Canvas, t: Float) {
            val shader = if (moving() && !dropShaderFailed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && canvas.isHardwareAccelerated) {
                dropShader ?: runCatching { RuntimeShader(DropArt.SHADER) }.getOrNull().also {
                    dropShader = it
                    dropShaderFailed = it == null
                }
            } else {
                null
            }
            if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                shader.setFloatUniform("res", width.toFloat(), height.toFloat())
                shader.setFloatUniform("time", t % 3600f)
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { this.shader = shader })
            } else {
                drawCover(canvas, bitmap(R.drawable.drop_still))
            }
        }

        private var waterShader: RuntimeShader? = null
        private var waterShaderFailed = false

        /** Moving water on the GPU (Android 13+, hardware canvas); otherwise the still picture. */
        private fun drawWater(canvas: Canvas, t: Float) {
            val shader = if (moving() && !waterShaderFailed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && canvas.isHardwareAccelerated) {
                waterShader ?: runCatching { RuntimeShader(WaterArt.SHADER) }.getOrNull().also {
                    waterShader = it
                    waterShaderFailed = it == null
                }
            } else {
                null
            }
            if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                shader.setFloatUniform("res", width.toFloat(), height.toFloat())
                shader.setFloatUniform("time", t % WaterArt.LOOP_SECONDS)
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { this.shader = shader })
            } else {
                drawCover(canvas, bitmap(R.drawable.water))
            }
        }

        private fun drawAurora(canvas: Canvas, t: Float) =
            QuoteArt.drawAurora(
                canvas, width.toFloat(), height.toFloat(), t, moving(), prefs.auroraIntensity,
                resources.displayMetrics.density, blur = canvas.isHardwareAccelerated,
            )

        private fun drawQuote(canvas: Canvas) {
            val quote = QuoteRepository(this@QuoteWallpaperService, prefs).current() ?: return
            val metrics = resources.displayMetrics
            // On the wallpaper an idea shows its name and text; the explanation would crowd the screen.
            val body = quote.title?.let { "$it\n\n${quote.shownText}" }
            QuoteArt.drawQuoteCard(
                canvas, width, height, metrics.density, android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 1f, metrics), theme, prefs.style,
                quote.text, quote.byline, prefs.cardOpacity, prefs.wallpaperQuotePosition, prefs.cardSoftness,
                body = body,
            )
        }
    }
}
