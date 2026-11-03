package com.trinineba.quotesonlock

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * The calm themes (snowfall, candlelight, fireflies, lavender, clouds, waves), drawn on a plain
 * Android canvas so the app, the live wallpaper and shared pictures show the same scene. Everything
 * is a function of [t] (seconds); a fixed t gives a still. Not thread-safe; call from one thread.
 */
internal object CalmArt {
    private const val TAU = (2 * PI).toFloat()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val path = Path()
    private val oval = RectF()

    fun draw(theme: AppTheme, canvas: Canvas, w: Float, h: Float, t: Float, d: Float) {
        when (theme) {
            AppTheme.SNOW -> snow(canvas, w, h, t, d)
            AppTheme.CANDLE -> candle(canvas, w, h, t, d)
            AppTheme.FIREFLIES -> fireflies(canvas, w, h, t, d)
            AppTheme.LAVENDER -> lavender(canvas, w, h, t, d)
            AppTheme.CLOUDS -> clouds(canvas, w, h, t, d)
            AppTheme.WAVES -> waves(canvas, w, h, t, d)
            else -> Unit
        }
    }

    /** A still moment for each scene (used when motion is off and for shared pictures). */
    fun stillTime(theme: AppTheme): Float = when (theme) {
        AppTheme.CANDLE -> 0.4f
        else -> 12.3f
    }

    // ---- helpers -------------------------------------------------------------------------------

    private fun vertical(c: Canvas, x: Float, y0: Float, y1: Float, w: Float, colors: IntArray, stops: FloatArray?) {
        fill.shader = LinearGradient(0f, y0, 0f, y1, colors, stops, Shader.TileMode.CLAMP)
        c.drawRect(x, y0, x + w, y1, fill)
        fill.shader = null
    }

    private fun glow(c: Canvas, x: Float, y: Float, r: Float, rgb: Int, alpha: Float) {
        if (r <= 0f || alpha <= 0f) return
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        fill.shader = RadialGradient(x, y, r, (a shl 24) or (rgb and 0xFFFFFF), rgb and 0xFFFFFF, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, fill)
        fill.shader = null
    }

    private fun color(c: Int, alpha: Float): Int = ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24) or (c and 0xFFFFFF)

    private fun hill(c: Canvas, w: Float, h: Float, base: Float, amp: Float, freq: Float, phase: Float, col: Int) {
        path.reset()
        path.moveTo(0f, h)
        var x = 0f
        while (x <= w + 1f) {
            path.lineTo(x, base - sin(x / w * freq + phase) * amp)
            x += w / 40f
        }
        path.lineTo(w, h)
        path.close()
        fill.color = col
        c.drawPath(path, fill)
    }

    // ---- snowfall at night ---------------------------------------------------------------------

    private class Flake(val x: Float, val p: Float, val speed: Float, val z: Float, val sway: Float)
    private class Pine(val x: Float, val h: Float, val far: Boolean)

    private val flakes = Random(5).let { r -> List(140) { Flake(r.nextFloat(), r.nextFloat(), 0.012f + r.nextFloat() * 0.02f, r.nextFloat(), r.nextFloat() * TAU) } }
    private val pines = Random(6).let { r -> List(14) { Pine(r.nextFloat(), 0.10f + r.nextFloat() * 0.12f, r.nextFloat() < 0.5f) } }

    private fun snow(c: Canvas, w: Float, h: Float, t: Float, d: Float) {
        vertical(c, 0f, 0f, h, w, intArrayOf(0xFF0B1626.toInt(), 0xFF1D2F4A.toInt(), 0xFF3A4F6E.toInt()), floatArrayOf(0f, 0.55f, 1f))
        glow(c, w * 0.72f, h * 0.16f, w * 0.45f, 0xD2E1FF, 0.18f)
        fill.color = 0xF2E1EBF8.toInt()
        c.drawCircle(w * 0.72f, h * 0.16f, w * 0.055f, fill)
        hill(c, w, h, h * 0.80f, h * 0.025f, 5f, 1f, 0xFF41587A.toInt())
        pines.filter { it.far }.forEach { pine(c, w, h, it) }
        hill(c, w, h, h * 0.885f, h * 0.02f, 3f, 2f, 0xFFDFE8F3.toInt())
        pines.filter { !it.far }.forEach { pine(c, w, h, it) }
        flakes.forEach { f ->
            val y = ((f.p + t * f.speed) % 1f) * h * 1.05f - h * 0.02f
            val x = f.x * w + sin(t * 0.5f + f.sway + y / h * 4f) * w * 0.03f * (0.5f + f.z)
            fill.color = color(0xFFFFFF, 0.35f + f.z * 0.6f)
            c.drawCircle(x, y, (0.8f + f.z * 2.4f) * d, fill)
        }
    }

    private fun pine(c: Canvas, w: Float, h: Float, p: Pine) {
        val bx = p.x * w
        val by = if (p.far) h * 0.83f else h * 0.9f
        val ph = p.h * h * (if (p.far) 0.6f else 1f)
        path.reset()
        path.moveTo(bx, by - ph)
        path.lineTo(bx - ph * 0.28f, by)
        path.lineTo(bx + ph * 0.28f, by)
        path.close()
        fill.color = if (p.far) 0xFF2C3F5C.toInt() else 0xFF1B2A40.toInt()
        c.drawPath(path, fill)
    }

    // ---- candlelight ---------------------------------------------------------------------------

    private fun candle(c: Canvas, w: Float, h: Float, t: Float, d: Float) {
        c.drawColor(0xFF0C0704.toInt())
        val flicker = 0.92f + 0.05f * sin(t * 7.1f) + 0.03f * sin(t * 13.7f + 1f) + 0.02f * sin(t * 3.3f)
        val cx = w / 2f
        val top = h * 0.62f
        glow(c, cx, top - h * 0.03f, w * 0.95f * flicker, 0xFF963C, 0.30f)
        glow(c, cx, top - h * 0.03f, w * 0.35f * flicker, 0xFFBE6E, 0.35f)
        val cw = w * 0.16f
        fill.shader = LinearGradient(cx - cw / 2, 0f, cx + cw / 2, 0f,
            intArrayOf(0xFFB89A74.toInt(), 0xFFF2E2C4.toInt(), 0xFFA3845F.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        oval.set(cx - cw / 2, top, cx + cw / 2, top + h * 0.42f)
        c.drawRoundRect(oval, 6 * d, 6 * d, fill)
        fill.shader = null
        fill.color = 0x80FFDCA0.toInt()
        oval.set(cx - cw / 2, top + 2 * d - cw * 0.12f, cx + cw / 2, top + 2 * d + cw * 0.12f)
        c.drawOval(oval, fill)
        stroke.color = 0xFF2A1A10.toInt()
        stroke.strokeWidth = 2 * d
        c.drawLine(cx, top, cx, top - h * 0.012f, stroke)
        val fh = h * 0.085f * flicker
        val fw = w * 0.035f
        val sway = sin(t * 2.3f) * fw * 0.25f + sin(t * 5.7f) * fw * 0.1f
        val fb = top - h * 0.008f
        listOf(1f to 0xD9FFAA3C.toInt(), 0.7f to 0xF2FFE196.toInt(), 0.4f to 0xF2FFFAE6.toInt()).forEach { (k, col) ->
            path.reset()
            path.moveTo(cx, fb)
            path.cubicTo(cx - fw * k, fb - fh * 0.15f * k, cx - fw * 0.6f * k + sway * 0.5f, fb - fh * 0.6f * k, cx + sway, fb - fh * k)
            path.cubicTo(cx + fw * 0.6f * k + sway * 0.5f, fb - fh * 0.6f * k, cx + fw * k, fb - fh * 0.15f * k, cx, fb)
            fill.color = col
            c.drawPath(path, fill)
        }
        fill.color = 0x593C5AC8
        oval.set(cx - fw * 0.35f, fb - fh * 0.18f, cx + fw * 0.35f, fb + fh * 0.02f)
        c.drawOval(oval, fill)
        vertical(c, 0f, h * 0.96f, h, w, intArrayOf(0xFF2A1810.toInt(), 0xFF120A06.toInt()), null)
    }

    // ---- fireflies in a dusk forest --------------------------------------------------------------

    private class Fly(val x: Float, val y: Float, val ax: Float, val ay: Float, val fx: Float, val fy: Float, val ph: Float, val pulse: Float)
    private class Tree(val x: Float, val w: Float, val far: Boolean)

    private val flies = Random(9).let { r ->
        List(46) {
            Fly(r.nextFloat(), 0.35f + r.nextFloat() * 0.6f, 0.03f + r.nextFloat() * 0.08f, 0.02f + r.nextFloat() * 0.05f,
                0.05f + r.nextFloat() * 0.12f, 0.04f + r.nextFloat() * 0.1f, r.nextFloat() * TAU, 0.4f + r.nextFloat() * 0.9f)
        }
    }
    private val trees = Random(10).let { r -> List(18) { Tree(r.nextFloat(), 0.025f + r.nextFloat() * 0.05f, r.nextFloat() < 0.5f) } }

    private fun fireflies(c: Canvas, w: Float, h: Float, t: Float, d: Float) {
        vertical(c, 0f, 0f, h, w, intArrayOf(0xFF16263A.toInt(), 0xFF1A3A3F.toInt(), 0xFF07120F.toInt()), floatArrayOf(0f, 0.45f, 1f))
        glow(c, w * 0.5f, h * 0.35f, w * 0.9f, 0x78A096, 0.12f)
        listOf(true, false).forEach { far ->
            fill.color = if (far) 0xFF183332.toInt() else 0xFF0B1A17.toInt()
            trees.filter { it.far == far }.forEach { tr ->
                val x = tr.x * w
                val tw = tr.w * w * (if (far) 0.7f else 1f)
                c.drawRect(x - tw / 2, h * (if (far) 0.25f else 0.1f), x + tw / 2, h, fill)
                for (k in 0..2) c.drawCircle(x + (k - 1) * tw * 1.6f, h * (if (far) 0.26f else 0.12f) + k * 8 * d, tw * (if (far) 2.2f else 3f), fill)
            }
        }
        hill(c, w, h, h * 0.86f, h * 0.012f, 7f, 0f, 0xFF08130F.toInt())
        flies.forEach { f ->
            val x = (f.x + f.ax * sin(t * f.fx * TAU * 0.3f + f.ph)) * w
            val y = (f.y + f.ay * sin(t * f.fy * TAU * 0.3f + f.ph * 1.7f)) * h
            val a = max(0f, sin(t * f.pulse + f.ph))
            glow(c, x, y, 14 * d, 0xD6FF78, 0.5f * a)
            fill.color = color(0xF5FFBE, 0.9f * a)
            c.drawCircle(x, y, 1.6f * d, fill)
        }
    }

    // ---- lavender field at sunset ----------------------------------------------------------------

    private class Stalk(val row: Int, val y: Float, val x: Float, val ph: Float, val light: Boolean)

    private val stalks = Random(4).let { r ->
        val list = mutableListOf<Stalk>()
        for (row in 0 until 13) {
            val y = 0.6f + (row / 12f).pow(1.6f) * 0.42f
            val n = 12 + row * 3
            for (i in 0 until n) list += Stalk(row, y, (i + r.nextFloat() * 0.8f) / n, r.nextFloat() * TAU, r.nextFloat() < 0.5f)
        }
        list
    }

    private fun lavender(c: Canvas, w: Float, h: Float, t: Float, d: Float) {
        vertical(c, 0f, 0f, h * 0.62f, w, intArrayOf(0xFF8FA3D6.toInt(), 0xFFE9B7C9.toInt(), 0xFFFBD0A8.toInt(), 0xFFFFE0B0.toInt()), floatArrayOf(0f, 0.45f, 0.8f, 1f))
        glow(c, w * 0.5f, h * 0.57f, w * 0.7f, 0xFFDCAA, 0.55f)
        fill.color = 0xF2FFECC8.toInt()
        c.drawCircle(w * 0.5f, h * 0.57f, w * 0.09f, fill)
        hill(c, w, h, h * 0.59f, h * 0.012f, 4f, 0.5f, 0xFF9D86B8.toInt())
        vertical(c, 0f, h * 0.6f, h, w, intArrayOf(0xFF7D63A6.toInt(), 0xFF4A3270.toInt()), null)
        stalks.forEach { s ->
            val k = 0.25f + s.row / 12f
            val bx = s.x * w
            val by = s.y * h
            val len = h * 0.05f * k
            val sway = sin(t * 0.9f + s.ph + s.x * 3f) * len * 0.18f
            stroke.color = color(0x465A3C, 0.5f + 0.4f * k)
            stroke.strokeWidth = max(0.6f, 1.1f * k) * d
            path.reset()
            path.moveTo(bx, by)
            path.quadTo(bx + sway * 0.3f, by - len * 0.5f, bx + sway, by - len)
            c.drawPath(path, stroke)
            fill.color = color(if (s.light) 0xAA7DD7 else 0x966EC8, 0.6f + 0.4f * k)
            for (j in 0 until 4) {
                val ex = bx + sway * (1 - j * 0.06f)
                val ey = by - len - j * len * 0.1f
                val rx = 1.6f * k * d + 0.6f
                val ry = 2.6f * k * d + 0.6f
                oval.set(ex - rx, ey - ry, ex + rx, ey + ry)
                c.drawOval(oval, fill)
            }
        }
    }

    // ---- drifting clouds -----------------------------------------------------------------------

    private class Cloud(val y: Float, val s: Float, val v: Float, val x: Float, val puffs: List<FloatArray>)

    private val cloudList = Random(12).let { r ->
        List(9) {
            Cloud(0.08f + r.nextFloat() * 0.75f, 0.6f + r.nextFloat() * 0.9f, 0.004f + r.nextFloat() * 0.006f, r.nextFloat(),
                List(7) { floatArrayOf(r.nextFloat() * 1.6f - 0.8f, r.nextFloat() * 0.35f - 0.1f, 0.25f + r.nextFloat() * 0.35f) })
        }
    }

    private fun clouds(c: Canvas, w: Float, h: Float, t: Float, d: Float) {
        vertical(c, 0f, 0f, h, w, intArrayOf(0xFF9EC5EA.toInt(), 0xFFCFE1F2.toInt(), 0xFFF6DCCB.toInt(), 0xFFFBE6D6.toInt()), floatArrayOf(0f, 0.55f, 0.85f, 1f))
        glow(c, w * 0.2f, h * 0.85f, w * 0.8f, 0xFFEBD7, 0.5f)
        cloudList.forEach { cl ->
            val cw = w * 0.32f * cl.s
            val x = ((cl.x + t * cl.v) % 1.4f - 0.2f) * w
            val y = cl.y * h
            cl.puffs.forEach { p ->
                val px = x + p[0] * cw
                val py = y + p[1] * cw
                val r = cw * p[2]
                fill.shader = RadialGradient(px, py, r, intArrayOf(0xBFFFFFFF.toInt(), 0x73FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(px, py, r, fill)
            }
            fill.shader = null
        }
    }

    // ---- ocean waves at dusk ---------------------------------------------------------------------

    private fun waves(c: Canvas, w: Float, h: Float, t: Float, d: Float) {
        val hz = h * 0.5f
        vertical(c, 0f, 0f, hz, w, intArrayOf(0xFF1C2A52.toInt(), 0xFF5A4D7A.toInt(), 0xFFD98F8A.toInt(), 0xFFF2B38E.toInt()), floatArrayOf(0f, 0.6f, 0.9f, 1f))
        glow(c, w * 0.35f, hz, w * 0.5f, 0xFFBE96, 0.45f)
        vertical(c, 0f, hz, h * 0.8f, w, intArrayOf(0xFF6A6390.toInt(), 0xFF3B4A72.toInt(), 0xFF22324F.toInt()), floatArrayOf(0f, 0.3f, 1f))
        for (i in 0 until 26) {
            val y = hz + (i / 26f).pow(1.5f) * h * 0.28f
            val ww = w * (0.05f + i * 0.006f) * (0.7f + 0.3f * sin(t * 1.3f + i))
            fill.color = color(0xFFC8A0, 0.35f - i * 0.011f)
            val cx = w * 0.35f + sin(t + i) * 4 * d
            c.drawRect(cx - ww / 2, y, cx + ww / 2, y + 1.4f * d, fill)
        }
        for (i in 0 until 14) {
            val p = ((i / 14f) + t * 0.03f) % 1f
            val y = hz + p * p * h * 0.32f
            stroke.color = color(0xC8D2F0, 0.06f + p * 0.12f)
            stroke.strokeWidth = (0.6f + p * 1.6f) * d
            path.reset()
            var x = 0f
            while (x <= w + 1f) {
                val yy = y + sin(x / w * 8f + i + t * 0.4f) * p * 4 * d
                if (x == 0f) path.moveTo(x, yy) else path.lineTo(x, yy)
                x += w / 40f
            }
            c.drawPath(path, stroke)
        }
        vertical(c, 0f, h * 0.8f, h, w, intArrayOf(0xFF8A7A86.toInt(), 0xFF5E5260.toInt()), null)
        val s = 0.5f + 0.5f * sin(t * TAU / 9f)
        val fy = h * (0.8f + s * 0.07f)
        path.reset()
        path.moveTo(0f, h * 0.8f)
        path.lineTo(w, h * 0.8f)
        var x = w
        while (x >= -1f) {
            path.lineTo(x, fy + sin(x / w * 6f + t * 0.7f) * 5 * d)
            x -= w / 40f
        }
        path.close()
        fill.shader = LinearGradient(0f, h * 0.8f, 0f, fy + 1f, 0xE63C5078.toInt(), 0x9996AAC8.toInt(), Shader.TileMode.CLAMP)
        c.drawPath(path, fill)
        fill.shader = null
        stroke.color = color(0xF5F8FF, 0.55f + 0.3f * (1 - s))
        stroke.strokeWidth = 2.2f * d
        path.reset()
        x = 0f
        while (x <= w + 1f) {
            val yy = fy + sin(x / w * 6f + t * 0.7f) * 5 * d
            if (x == 0f) path.moveTo(x, yy) else path.lineTo(x, yy)
            x += w / 40f
        }
        c.drawPath(path, stroke)
    }
}
