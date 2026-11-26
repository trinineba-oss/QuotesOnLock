package com.trinineba.quotesonlock

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Living paintings: three oil-painted scenes that move like the real thing.
 *
 * - Lavender field: gusts of wind roll across the rows, bending them and catching the light as they pass.
 * - Snowy night: snow falls at three depths over moonlit pines at dusk, swung by the wind, with glints on the drifts.
 * - Waves at sunrise: the painting's own surf runs up the sand, slows and slides back, a little differently each time,
 *   leaving the sand dark and wet for a moment.
 *
 * Each painting drifts slowly, like the Painted collection. The bending is done with a bitmap mesh, so it costs one
 * textured draw. Wind and waves come from smooth noise, so the motion never settles into a rhythm. Sizes are given for
 * a 540-wide screen and scaled by [s].
 */
internal object LivingPaintArt {
    fun res(theme: AppTheme): Int? = when (theme) {
        AppTheme.LAVENDER -> R.drawable.living_lavender
        AppTheme.SNOW -> R.drawable.living_snow
        AppTheme.WAVES -> R.drawable.living_waves
        else -> null
    }

    private var key = ""
    private var bitmap: Bitmap? = null
    private var sea: Bitmap? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shade = Paint()
    private var shadeKey = ""
    private val path = Path()
    private val rect = RectF()
    private var dot: Bitmap? = null
    private var s = 1f

    // The drawn painting: where its top-left corner is and how much it is scaled, this frame.
    private var ox = 0f
    private var oy = 0f
    private var sc = 1f

    private const val MW = 28
    private const val MH = 64
    private val verts = FloatArray((MW + 1) * (MH + 1) * 2)

    fun clear() {
        key = ""
        bitmap?.recycle(); bitmap = null
        sea?.recycle(); sea = null
    }

    /** Smooth value noise in about -1..1, a few octaves: gusts and waves that never repeat exactly. */
    private val table = Random(5).let { r -> FloatArray(512) { r.nextFloat() * 2f - 1f } }
    private fun noise(x: Float, seed: Int): Float {
        fun v(x: Float): Float {
            val i = floor(x).toInt()
            var f = x - floor(x)
            f = f * f * (3 - 2 * f)
            val a = table[((i + seed * 97) % 511 + 511) % 511]
            val b = table[((i + 1 + seed * 97) % 511 + 511) % 511]
            return a + (b - a) * f
        }
        return 0.55f * v(x) + 0.3f * v(x * 2.1f + 17f) + 0.15f * v(x * 4.3f + 41f)
    }

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    private fun load(context: Context, theme: AppTheme, w: Float, h: Float): Bitmap? {
        val id = res(theme) ?: return null
        val k = "${theme.key}:${w.toInt()}x${h.toInt()}"
        if (k == key) bitmap?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, id, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= w * 1.1f && bounds.outHeight / (sample * 2) >= h * 1.1f) sample *= 2
        val b = BitmapFactory.decodeResource(context.resources, id, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        clear()
        bitmap = b
        if (theme == AppTheme.WAVES) sea = seaLayer(b)
        if (dot == null) dot = softDot()
        key = k
        return b
    }

    /** The water of the beach painting down to just past the foam, its lower edge fading out: the part that moves. */
    private fun seaLayer(b: Bitmap): Bitmap {
        val rows = (b.height * SEA_CUT).toInt()
        val out = Bitmap.createBitmap(b.width, rows, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawBitmap(b, 0f, 0f, null)
        val fade = Paint().apply {
            shader = LinearGradient(0f, rows * 0.955f, 0f, rows.toFloat(), 0xFF000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        c.drawRect(0f, rows * 0.955f, b.width.toFloat(), rows.toFloat(), fade)
        return out
    }

    private fun softDot(): Bitmap {
        val b = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        Canvas(b).drawCircle(16f, 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(16f, 16f, 16f, intArrayOf(0xFFFFFFFF.toInt(), 0x88FFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        })
        return b
    }

    /** Draws [theme] at [t] seconds; false if it isn't one of these or its picture can't be read. */
    fun draw(context: Context?, theme: AppTheme, canvas: Canvas, w: Float, h: Float, t: Float): Boolean {
        if (context == null || res(theme) == null) return false
        val b = load(context, theme, w, h) ?: return false
        s = w / 540f
        // The slow drift shared with the Painted collection.
        val breathe = sin(2 * PI * t / 60.0).toFloat()
        sc = max(w / b.width, h / b.height) * (1.07f + 0.025f * breathe)
        ox = (w - b.width * sc) / 2f + w * 0.018f * sin(2 * PI * t / 83.0).toFloat()
        oy = (h - b.height * sc) / 2f + h * 0.012f * cos(2 * PI * t / 71.0).toFloat()
        when (theme) {
            AppTheme.LAVENDER -> lavender(canvas, b, w, h, t)
            AppTheme.SNOW -> { plain(canvas, b); snow(canvas, w, h, t) }
            else -> waves(canvas, b, w, h, t)
        }
        val sk = "${w.toInt()}x${h.toInt()}"
        if (sk != shadeKey) {
            shade.shader = LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(0x66000000, 0x00000000, 0x00000000, 0x59000000, 0x99000000.toInt()),
                floatArrayOf(0f, 0.22f, 0.45f, 0.72f, 1f), Shader.TileMode.CLAMP,
            )
            shadeKey = sk
        }
        canvas.drawRect(0f, 0f, w, h, shade)
        return true
    }

    private fun plain(c: Canvas, b: Bitmap) {
        c.save()
        c.translate(ox, oy)
        c.scale(sc, sc)
        c.drawBitmap(b, 0f, 0f, paint)
        c.restore()
    }

    /** Fills [verts] with the painting's grid as drawn, then lets [move] push each point (screen x, y, picture v). */
    private inline fun mesh(bw: Int, bh: Int, move: (x: Float, y: Float, v: Float, out: FloatArray, i: Int) -> Unit) {
        var i = 0
        for (r in 0..MH) {
            val v = r / MH.toFloat()
            val y = oy + v * bh * sc
            for (col in 0..MW) {
                val x = ox + col / MW.toFloat() * bw * sc
                verts[i] = x; verts[i + 1] = y
                move(x, y, v, verts, i)
                i += 2
            }
        }
    }

    // ---- lavender: gusts roll across the field -------------------------------------------------------------

    private const val FIELD_TOP = 0.38f

    private fun lavender(c: Canvas, b: Bitmap, w: Float, h: Float, t: Float) {
        val travel = (t * 0.13f) % 2.2f
        val strength = 0.6f + 0.4f * noise(t * 0.2f, 9)
        mesh(b.width, b.height) { x, y, v, out, i ->
            val k = ((v - FIELD_TOP) / (1f - FIELD_TOP)).coerceIn(0f, 1f)
            if (k > 0f) {
                val front = x / w * 1.6f - travel + 0.3f
                val g = exp(-((front - 0.5f) / 0.35f).pow(2)) * strength
                val ripple = sin(x / (22f * s) - t * 2.2f + y / (30f * s)) * 0.5f + 0.5f
                out[i] = x + (g * (10f + 6f * ripple) + 2.5f * sin(t * 1.1f + y / (50f * s) + x / (90f * s))) * k * s
            }
        }
        c.drawBitmapMesh(b, MW, MH, verts, 0, null, 0, paint)
        // The light on the tops of the flowers as the gust passes.
        val fx = w * (0.5f + travel - 0.3f) / 1.6f
        val top = oy + FIELD_TOP * b.height * sc
        fill.shader = LinearGradient(fx - w * 0.35f, 0f, fx + w * 0.35f, 0f, intArrayOf(0x00FFF2FF, (((0.10f * strength) * 255).toInt() shl 24) or 0xFFF2FF, 0x00FFF2FF), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, top, w, h, fill)
        fill.shader = null
    }

    // ---- snow: three depths of flakes, swung by gusts; glints on the drifts ----------------------------------

    private class Flake(val x: Float, val y: Float, val z: Float, val ph: Float, val sp: Float)
    private val flakes = Random(7).let { r ->
        listOf(0.35f to 220, 0.65f to 110, 1f to 36).flatMap { (z, n) -> List(n) { Flake(r.nextFloat(), r.nextFloat(), z, r.nextFloat() * 6.28f, 0.8f + r.nextFloat() * 0.4f) } }
    }
    private val glints = Random(3).let { r -> List(70) { floatArrayOf(r.nextFloat(), 0.84f + r.nextFloat() * 0.16f, r.nextFloat() * 6.28f, 0.3f + r.nextFloat() * 0.6f) } }

    private fun snow(c: Canvas, w: Float, h: Float, t: Float) {
        val d = dot ?: return
        val gust = noise(t * 0.25f, 3)
        val span = h + 40f * s
        for (f in flakes) {
            val z = f.z
            val fall = (18f + 70f * z) * f.sp * s
            val y = ((f.y * span + t * fall) % span) - 20f * s
            val x = ((f.x * w + t * gust * 60f * z * s + sin(t * 0.9f * f.sp + f.ph) * 10f * z * s) % (w + 40f * s) + (w + 40f * s)) % (w + 40f * s) - 20f * s
            val r = (0.6f + 2.6f * z * z) * s * (if (z == 1f) 2.6f else 1.6f)
            paint.alpha = (((0.55f + 0.4f * z) * (if (z == 1f) 0.55f else 1f)) * 255).toInt()
            rect.set(x - r, y - r, x + r, y + r)
            c.drawBitmap(d, null, rect, paint)
        }
        for (g in glints) {
            val a = max(0f, sin(t * g[3] * 2f + g[2])).pow(12)
            if (a < 0.02f) continue
            paint.alpha = (a * 255).toInt()
            val x = g[0] * w
            val y = g[1] * h
            val r = 3f * s
            rect.set(x - r, y - r, x + r, y + r)
            c.drawBitmap(d, null, rect, paint)
        }
        paint.alpha = 255
    }

    // ---- waves: the surf runs up the sand and slides back ---------------------------------------------------

    private const val SEA_CUT = 0.69f     // the moving water layer: the painting down to just past the foam
    private const val SEA_TOP = 0.47f     // where the movement starts (the far surf)
    private const val EDGE = 0.675f       // the foam's edge on the sand, as painted
    private const val RUN = 0.085f        // how far a wave runs up, as a share of the screen's height

    private fun waves(c: Canvas, b: Bitmap, w: Float, h: Float, t: Float) {
        plain(c, b)
        val layer = sea ?: return
        val n = floor(t / 10f)
        val period = 10f + 1.5f * noise(n, 5)
        val ph = (t % period) / period
        val up = 0.42f
        val reach = if (ph < up) 1f - (1f - ph / up).pow(2.2f) else 1f - ((ph - up) / (1f - up)).pow(1.5f)
        // The sea layer as a mesh over the same place as the painting, pushed down the beach by this wave.
        var i = 0
        for (r in 0..MH) {
            val v = r / MH.toFloat() * SEA_CUT
            val y0 = oy + v * b.height * sc
            for (col in 0..MW) {
                val u = col / MW.toFloat()
                val x = ox + u * layer.width * sc
                val lobes = 1f + 0.18f * noise(x / w * 2.2f + n * 7.3f, 11) + 0.06f * sin(x / w * 7f + n)
                val sh = h * RUN * reach * lobes
                val wgt = smooth(SEA_TOP, EDGE, v)
                // a faint shimmer on the far water
                val shimmer = if (v in 0.45f..0.62f) 1.6f * s * sin(y0 / (40f * s) + t * 1.4f) * sin(x / (60f * s) - t * 0.7f) else 0f
                verts[i] = x + shimmer
                verts[i + 1] = y0 + sh * wgt
                i += 2
            }
        }
        c.drawBitmapMesh(layer, MW, MH, verts, 0, null, 0, paint)
        // Wet sand: where the water has been and gone, darker for a moment, drying before the next wave.
        if (ph >= up) {
            val dry = (ph - up) / (1f - up)
            val edgeY = oy + EDGE * b.height * sc
            path.reset()
            for (col in 0..MW) {
                val x = col / MW.toFloat() * w
                val lobes = 1f + 0.18f * noise(x / w * 2.2f + n * 7.3f, 11) + 0.06f * sin(x / w * 7f + n)
                val yNow = edgeY + h * RUN * reach * lobes
                if (col == 0) path.moveTo(x, yNow) else path.lineTo(x, yNow)
            }
            for (col in MW downTo 0) {
                val x = col / MW.toFloat() * w
                val lobes = 1f + 0.18f * noise(x / w * 2.2f + n * 7.3f, 11) + 0.06f * sin(x / w * 7f + n)
                path.lineTo(x, edgeY + h * RUN * lobes)
            }
            path.close()
            fill.color = (((0.16f * (1f - dry)) * 255).toInt() shl 24) or 0x3A2418
            c.drawPath(path, fill)
        }
    }
}
