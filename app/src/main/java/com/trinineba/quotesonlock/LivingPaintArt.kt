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
import kotlin.math.abs
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
 * - Blossom by the water: petals drift down and turn, the flowering branch stirs, and the reflection trembles.
 * - Lanterns on the lake: paper lanterns rise from the water into the dusk, flickering, each mirrored below.
 * - Butterfly meadow: gusts move through the grass and flowers while butterflies wander and flutter over them.
 * - Candle on the table: a pillar candle with a living flame: it flickers all the time, sways, stretches and shivers,
 *   and now and then a draught bends it further; its light on the wall and table brightens and dims with it.
 * - Mystic Mountain: Hubble's picture of the Carina Nebula drifts slowly closer, with stars twinkling in front.
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
        AppTheme.L_BLOSSOM -> R.drawable.living_blossom
        AppTheme.L_LANTERNS -> R.drawable.living_lanterns
        AppTheme.L_MEADOW -> R.drawable.living_meadow
        AppTheme.L_CANDLE -> R.drawable.living_candle
        AppTheme.L_SPACE -> R.drawable.living_space
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
            AppTheme.L_BLOSSOM -> blossom(canvas, b, w, h, t)
            AppTheme.L_LANTERNS -> lanterns(canvas, b, w, h, t)
            AppTheme.L_MEADOW -> meadow(canvas, b, w, h, t)
            AppTheme.L_SPACE -> { plain(canvas, b); stars(canvas, w, h, t) }
            AppTheme.L_CANDLE -> { plain(canvas, b); candle(canvas, b, t) }
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

    private fun lavender(c: Canvas, b: Bitmap, w: Float, h: Float, t: Float, fieldTop: Float = FIELD_TOP) {
        val travel = (t * 0.13f) % 2.2f
        val strength = 0.6f + 0.4f * noise(t * 0.2f, 9)
        mesh(b.width, b.height) { x, y, v, out, i ->
            val k = ((v - fieldTop) / (1f - fieldTop)).coerceIn(0f, 1f)
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
        val top = oy + fieldTop * b.height * sc
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

    // ---- blossom: petals drift and turn; the branch stirs; the reflection trembles ---------------------------

    private class Petal(val x: Float, val y: Float, val z: Float, val ph: Float, val sp: Float)
    private val petals = Random(31).let { r -> List(64) { Petal(r.nextFloat(), r.nextFloat(), 0.4f + r.nextFloat(), r.nextFloat() * 6.28f, 0.7f + r.nextFloat() * 0.6f) } }

    private fun blossom(c: Canvas, b: Bitmap, w: Float, h: Float, t: Float) {
        val gust = noise(t * 0.2f, 4)
        mesh(b.width, b.height) { x, y, v, out, i ->
            // the branch across the top left stirs; the water below the far shore trembles
            val kx = ((0.75f - x / w) / 0.75f).coerceIn(0f, 1f)
            val ky = ((0.42f - v) / 0.42f).coerceIn(0f, 1f)
            val k = (kx * ky).pow(1.5f)
            var dx = (3f * sin(t * 0.7f + y / (90f * s)) + 4f * gust) * k * s
            if (v > 0.6f) dx += 1.4f * s * ((v - 0.6f) / 0.05f).coerceAtMost(1f) * sin(y / (6f * s) + t * 2f) * sin(x / (50f * s) - t * 0.5f)
            out[i] = x + dx
        }
        c.drawBitmapMesh(b, MW, MH, verts, 0, null, 0, paint)
        val span = h + 60f * s
        for (p in petals) {
            val z = p.z
            val y = ((p.y * span + t * (22f + 30f * z) * p.sp * s) % span) - 30f * s
            val x = (((p.x * w + t * (8f + 26f * gust) * z * s + sin(t * 0.8f * p.sp + p.ph) * 18f * z * s) % (w + 60f * s)) + (w + 60f * s)) % (w + 60f * s) - 30f * s
            val flip = abs(cos(t * 1.7f * p.sp + p.ph))
            val r = (5.5f * z + 2f) * s
            c.save()
            c.translate(x, y)
            c.rotate(((t * 0.9f * p.sp + p.ph) * 57.3f) % 360f)
            fill.color = argb(0.95f, 255, (199 + 30 * flip).toInt().coerceAtMost(255), (219 + 20 * flip).toInt().coerceAtMost(255))
            rect.set(-r, -r * (0.25f + 0.6f * flip), r, r * (0.25f + 0.6f * flip))
            c.drawOval(rect, fill)
            c.restore()
        }
    }

    // ---- lanterns: they rise from the lake into the dusk, flickering, mirrored below ------------------------

    private class Lantern(val x: Float, val p: Float, val z: Float, val ph: Float, val sp: Float)
    private val lanternList = Random(26).let { r -> List(26) { Lantern(0.08f + r.nextFloat() * 0.84f, r.nextFloat(), 0.35f + r.nextFloat() * 0.65f, r.nextFloat() * 6.28f, 0.8f + r.nextFloat() * 0.4f) } }
    private const val SHORE = 0.505f
    private var lanternGlow: Bitmap? = null

    private fun lanternBitmap(): Bitmap = lanternGlow ?: Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also { g ->
        Canvas(g).drawCircle(32f, 32f, 32f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(32f, 32f, 32f, intArrayOf(0xFFFFE6B0.toInt(), 0xFFFFA040.toInt(), 0x55FF8A30, 0x00FF8A30), floatArrayOf(0f, 0.18f, 0.4f, 1f), Shader.TileMode.CLAMP)
        })
        lanternGlow = g
    }

    private fun lanterns(c: Canvas, b: Bitmap, w: Float, h: Float, t: Float) {
        mesh(b.width, b.height) { x, y, v, out, i ->
            if (v > SHORE) out[i] = x + 1.8f * s * ((v - SHORE) / 0.02f).coerceAtMost(1f) * sin(y / (7f * s) + t * 1.6f) * sin(x / (60f * s) - t * 0.4f)
        }
        c.drawBitmapMesh(b, MW, MH, verts, 0, null, 0, paint)
        val g = lanternBitmap()
        val sy = oy + SHORE * b.height * sc
        for (l in lanternList) {
            val z = l.z
            val p = (l.p + t * 0.012f * l.sp * (0.6f + 0.6f * z)) % 1f
            val y = sy - 10f * s - p * sy * 1.05f * (0.55f + 0.45f * z)
            val x = ((l.x * w + sin(t * 0.3f * l.sp + l.ph) * 14f * z * s + t * 2.5f * z * s) % w + w) % w
            val fl = 0.85f + 0.1f * sin(t * 5.3f + l.ph) + 0.05f * sin(t * 11.7f + l.ph * 2f)
            val fade = (p / 0.06f).coerceAtMost(1f) * (1f - ((p - 0.8f) / 0.2f).coerceAtLeast(0f))
            val r = (2.6f + 7.5f * z * z) * s * 2.2f
            paint.alpha = ((0.9f * fl * fade).coerceIn(0f, 1f) * 255).toInt()
            rect.set(x - r, y - r * 1.2f, x + r, y + r * 1.2f)
            c.drawBitmap(g, null, rect, paint)
            val ry = 2f * sy - y + 8f * s
            if (ry < h) {
                paint.alpha = ((0.35f * fl * fade * (0.6f + 0.4f * sin(t * 3f + ry / 9f))).coerceIn(0f, 1f) * 255).toInt()
                val rx = x + 2f * s * sin(t * 2f + ry / (6f * s))
                rect.set(rx - r * 1.1f, ry - r * 1.4f, rx + r * 1.1f, ry + r * 1.4f)
                c.drawBitmap(g, null, rect, paint)
            }
        }
        paint.alpha = 255
    }

    // ---- meadow: gusts in the grass; butterflies wander and flutter -------------------------------------------

    private class Fly(val seed: Int, val sp: Float, val wing: Int, val edge: Int)
    private val butterflies = listOf(
        Fly(21, 0.9f, 0xFFF08A1E.toInt(), 0xFF20140C.toInt()), Fly(37, 0.8f, 0xFFF29A2A.toInt(), 0xFF24160C.toInt()),
        Fly(53, 1.05f, 0xFFF6F4EA.toInt(), 0xFF6A6A66.toInt()), Fly(71, 0.95f, 0xFFFFDA4C.toInt(), 0xFF7A6418.toInt()),
    )

    private fun meadow(c: Canvas, b: Bitmap, w: Float, h: Float, t: Float) {
        lavender(c, b, w, h, t, 0.55f)
        for (f in butterflies) {
            val tt = t * f.sp + f.seed
            val x = w * (0.5f + 0.42f * noise(tt * 0.09f, f.seed))
            val y = h * (0.74f + 0.16f * noise(tt * 0.07f, f.seed + 10))
            val ahead = w * (0.5f + 0.42f * noise((tt + 0.2f) * 0.09f, f.seed))
            val flap = tt * 9f
            val open = 0.25f + 0.75f * abs(cos(flap))      // the wings seen edge-on as they close
            val size = 17f * s
            c.save()
            c.translate(x, y + 3f * s * sin(flap))
            c.rotate(if (ahead > x) 14f else -14f)
            for (side in intArrayOf(-1, 1)) {
                c.save()
                c.scale(side * open, 1f)
                fill.color = f.wing
                rect.set(size * 0.08f, -size * 0.85f, size * 1.05f, size * 0.15f)
                c.drawOval(rect, fill)
                rect.set(size * 0.08f, -size * 0.05f, size * 0.75f, size * 0.85f)
                c.drawOval(rect, fill)
                fill.color = f.edge
                rect.set(size * 0.75f, -size * 0.8f, size * 1.05f, -size * 0.45f)
                c.drawOval(rect, fill)
                c.restore()
            }
            fill.color = 0xFF14100C.toInt()
            rect.set(-size * 0.07f, -size * 0.55f, size * 0.07f, size * 0.55f)
            c.drawOval(rect, fill)
            c.restore()
        }
    }

    // ---- space: stars in front of the nebula, twinkling and sliding slowly past -------------------------------

    private val starList = Random(16).let { r -> List(160) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.2f + r.nextFloat() * 0.8f, r.nextFloat() * 6.28f) } }

    private fun stars(c: Canvas, w: Float, h: Float, t: Float) {
        val d = dot ?: return
        for (st in starList) {
            val z = st[2]
            val x = ((st[0] * w + t * 4f * z * s) % w + w) % w
            val y = st[1] * h
            val tw = 0.55f + 0.45f * sin(t * (0.8f + z) + st[3])
            paint.alpha = ((0.55f * tw * z).coerceIn(0f, 1f) * 255).toInt()
            val r = (0.5f + 1.1f * z) * 2.2f * s
            rect.set(x - r, y - r, x + r, y + r)
            c.drawBitmap(d, null, rect, paint)
        }
        paint.alpha = 255
    }

    // ---- candle: a living flame and its light ------------------------------------------------------------------

    private const val FLAME_X = 0.5f
    private const val WICK_Y = 0.482f     // where the flame stands, as a share of the painting's height
    private const val GLOW_Y = 0.452f
    private val flamePath = Path()
    private val flamePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun candle(c: Canvas, b: Bitmap, t: Float) {
        // the flame flickers all the time; now and then a draught bends it further, then it settles
        val draught = ((noise(t * 0.12f, 3) - 0.15f) / 0.85f).coerceAtLeast(0f)
        val lean = noise(t * 0.7f, 4) * (0.35f + 1.2f * draught)
        val shiver = noise(t * 7f, 5) * (0.3f + 0.6f * draught)
        val stretch = 1f + 0.12f * noise(t * 2.4f, 6) + 0.25f * draught + 0.08f * shiver
        val bright = 1f + 0.10f * noise(t * 4f, 7) + 0.05f * noise(t * 11f, 9) - 0.12f * draught * (0.5f + 0.5f * noise(t * 7f, 8))
        val bw = b.width * sc
        val bh = b.height * sc
        val fx = ox + FLAME_X * bw
        val wy = oy + WICK_Y * bh
        // the light on the wall and table brightens and dims with the flame
        val gy = oy + GLOW_Y * bh
        val lr = bw * 0.55f
        val k = ((bright - 1f) * 2.2f).coerceIn(-0.5f, 0.5f)
        lightPaint.shader = if (k >= 0f) {
            RadialGradient(fx, gy, lr, intArrayOf(((k * 0.55f * 255).toInt() shl 24) or 0xFFB060, 0x00FFB060), null, Shader.TileMode.CLAMP)
        } else {
            RadialGradient(fx, gy, lr, intArrayOf(((-k * 0.6f * 255).toInt() shl 24) or 0x0A0502, 0x000A0502), null, Shader.TileMode.CLAMP)
        }
        c.drawCircle(fx, gy, lr, lightPaint)
        // the flame: a teardrop that leans and bends from its base, orange at the edges, a pale core, a blue root
        val fh = 0.05f * bh * stretch
        val fw = 0.018f * bw * (1f - 0.1f * (stretch - 1f))
        fun bend(v: Float) = (lean * 0.35f + shiver * 0.25f) * fw * 2.2f * v * v
        fun outline(scale: Float, top: Float) {
            flamePath.reset()
            val n = 18
            for (j in 0..n) {
                val v = j / n.toFloat() * top
                val half = 1.15f * kotlin.math.sqrt(v) * (1f - v).coerceAtLeast(0f).pow(0.75f) / 0.48f * fw * scale
                val px = fx + bend(v) - half
                val py = wy - v * fh
                if (j == 0) flamePath.moveTo(px, py) else flamePath.lineTo(px, py)
            }
            for (j in n downTo 0) {
                val v = j / n.toFloat() * top
                val half = 1.15f * kotlin.math.sqrt(v) * (1f - v).coerceAtLeast(0f).pow(0.75f) / 0.48f * fw * scale
                flamePath.lineTo(fx + bend(v) + half, wy - v * fh)
            }
            // a round base just below the wick
            rect.set(fx - fw * 0.55f * scale, wy - fw * 0.5f * scale, fx + fw * 0.55f * scale, wy + fw * 0.35f * scale)
            flamePath.addOval(rect, Path.Direction.CW)
        }
        val a = bright.coerceIn(0.7f, 1.2f)
        // the bloom around it
        flamePaint.shader = RadialGradient(fx + bend(0.5f) * 0.5f, wy - fh * 0.45f, bw * 0.14f,
            intArrayOf(((0.42f * a * 255).toInt().coerceAtMost(255) shl 24) or 0xFF9E40, ((0.12f * a * 255).toInt() shl 24) or 0xFF9E40, 0x00FF9E40),
            floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(fx + bend(0.5f) * 0.5f, wy - fh * 0.45f, bw * 0.14f, flamePaint)
        flamePaint.shader = null
        // soft edges: layers from a faint, wide orange glow in to a small, pale core near the base
        val layers = arrayOf(
            floatArrayOf(1.25f, 1.05f, 0.22f, 255f, 120f, 30f),
            floatArrayOf(1.0f, 1.0f, 0.55f, 255f, 140f, 35f),
            floatArrayOf(0.82f, 0.94f, 0.75f, 255f, 185f, 70f),
            floatArrayOf(0.6f, 0.72f, 0.85f, 255f, 225f, 150f),
            floatArrayOf(0.4f, 0.52f, 0.9f, 255f, 246f, 214f),
        )
        for (l in layers) {
            outline(l[0], l[1])
            flamePaint.color = argb(l[2] * a.coerceAtMost(1f), l[3].toInt(), l[4].toInt(), l[5].toInt())
            c.drawPath(flamePath, flamePaint)
        }
        flamePaint.color = argb(0.28f, 70, 95, 230)
        rect.set(fx - fw * 0.3f, wy - fw * 0.35f, fx + fw * 0.3f, wy + fw * 0.1f)
        c.drawOval(rect, flamePaint)
    }

    private fun argb(a: Float, r: Int, g: Int, b: Int): Int = ((a.coerceIn(0f, 1f) * 255).toInt() shl 24) or (r shl 16) or (g shl 8) or b
}
