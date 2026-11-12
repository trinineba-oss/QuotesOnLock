package com.trinineba.quotesonlock

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
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
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Five drawn scenes: Falling blossoms, Misty mountains, Floating lanterns, Autumn leaves and Light under the sea.
 * The parts that never move are one picture each (drawn for the app, the "_bg" pictures in res/drawable-nodpi), scaled to
 * the screen once; petals, mist, lanterns, leaves, light rays and kelp are drawn over it every frame. Sizes are
 * given for a 360-unit-wide screen and scaled by [k]. Called from [CalmArt], on one thread.
 */
internal object NatureArt {
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val soft = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val path = Path()
    private val rect = RectF()
    private var key = ""
    private var bg: Bitmap? = null
    private var mist: Bitmap? = null
    private var glow: Bitmap? = null
    private var k = 1f

    fun clear() {
        key = ""
        bg?.recycle(); bg = null
        mist?.recycle(); mist = null
    }

    private fun bgFor(theme: AppTheme): Int = when (theme) {
        AppTheme.BLOSSOM -> R.drawable.blossom_bg
        AppTheme.MOUNTAINS -> R.drawable.mountains_bg
        AppTheme.LANTERNS -> R.drawable.lanterns_bg
        AppTheme.LEAVES -> R.drawable.leaves_bg
        else -> R.drawable.sea_bg
    }

    /** Scales the theme's picture to cover the screen, once per size. */
    private fun layers(ctx: Context, theme: AppTheme, w: Float, h: Float): Boolean {
        val want = "${theme.key}:${w.toInt()}x${h.toInt()}"
        if (want == key && bg != null) return true
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val pic = BitmapFactory.decodeResource(ctx.resources, bgFor(theme), opts) ?: return false
        val s = maxOf(w / pic.width, h / pic.height)
        val pw = pic.width * s
        val ph = pic.height * s
        val out = Bitmap.createBitmap(max(1, w.toInt()), max(1, h.toInt()), Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(pic, null, RectF((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f), bitmapPaint)
        pic.recycle()
        bg?.recycle(); bg = out
        mist?.recycle()
        mist = if (theme == AppTheme.MOUNTAINS) BitmapFactory.decodeResource(ctx.resources, R.drawable.mountains_mist, opts) else null
        if (glow == null) {
            val g = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            Canvas(g).drawCircle(64f, 64f, 64f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(64f, 64f, 64f, intArrayOf(0xFFFFFFFF.toInt(), 0x66FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
            })
            glow = g
        }
        k = w / 360f
        nearBlur = BlurMaskFilter(2.4f * k, BlurMaskFilter.Blur.NORMAL)
        key = want
        return true
    }

    fun draw(ctx: Context?, theme: AppTheme, c: Canvas, w: Float, h: Float, t: Float): Boolean {
        if (ctx == null || !layers(ctx, theme, w, h)) return false
        c.drawBitmap(bg!!, 0f, 0f, bitmapPaint)
        when (theme) {
            AppTheme.BLOSSOM -> blossoms(c, w, h, t)
            AppTheme.MOUNTAINS -> mountains(c, w, h, t)
            AppTheme.LANTERNS -> lanterns(c, w, h, t)
            AppTheme.LEAVES -> leaves(c, w, h, t)
            else -> sea(c, w, h, t)
        }
        return true
    }

    private fun argb(a: Float, r: Int, g: Int, b: Int): Int = ((a.coerceIn(0f, 1f) * 255).toInt() shl 24) or (r shl 16) or (g shl 8) or b

    private class Drift(val x: Float, val y: Float, val z: Float, val ph: Float, val sp: Float, val kind: Int)
    private fun drifts(seed: Int, n: Int, zMin: Float, zMax: Float): List<Drift> {
        val r = Random(seed)
        return List(n) { Drift(r.nextFloat(), r.nextFloat(), zMin + (zMax - zMin) * r.nextFloat(), r.nextFloat() * 6.28f, 0.6f + 0.8f * r.nextFloat(), r.nextInt(5)) }
    }
    private fun frac(x: Float) = x - kotlin.math.floor(x)

    // ---- falling blossoms: petals drift down and turn, the nearest ones out of focus --------------------------

    private val petals = drifts(31, 36, 0.5f, 1.6f)
    private var nearBlur = BlurMaskFilter(2.4f, BlurMaskFilter.Blur.NORMAL)

    private fun blossoms(c: Canvas, w: Float, h: Float, t: Float) {
        for (p in petals) {
            val y = (frac((p.y + t * 0.045f * p.sp * p.z) / 1.15f) * 1.15f - 0.08f) * h
            val x = frac((p.x + 0.05f * sin(t * 0.7f * p.sp + p.ph) + t * 0.012f * p.z) / 1.1f) * 1.1f * w - 0.05f * w
            val flip = abs(cos(t * 2.1f * p.sp + p.ph))
            val rr = 6f * k * p.z
            val shade = 225 + (30 * flip).toInt()
            fill.color = argb(0.95f, 250, (shade * 0.74f).toInt(), (shade * 0.80f).toInt())
            fill.maskFilter = if (p.z > 1.2f) nearBlur else null
            c.save()
            c.translate(x, y)
            c.rotate(Math.toDegrees((t * 1.6f * p.sp + p.ph).toDouble()).toFloat())
            c.scale(1f, 0.62f * (0.25f + 0.75f * flip))
            rect.set(-rr, -rr, rr, rr)
            c.drawOval(rect, fill)
            c.restore()
        }
        fill.maskFilter = null
    }

    // ---- misty mountains: bands of mist drift through the valleys; a few birds far off ------------------------

    private val bands = floatArrayOf(0.391f, 0.477f, 0.504f, 0.614f, 0.750f, 0.863f)

    private fun mountains(c: Canvas, w: Float, h: Float, t: Float) {
        val m = mist ?: return
        val bh = 0.10f * h
        val bw = bh * m.width / m.height * 2.2f
        for ((i, yc) in bands.withIndex()) {
            glowPaint.alpha = ((0.85f - 0.08f * i) * 255).toInt()
            var x = -frac(t * (4f + i * 2f) * k / bw) * bw
            if (i % 2 == 1) x = -frac(-t * (3f + i) * k / bw) * bw
            while (x < w) {
                rect.set(x, yc * h - bh / 2f, x + bw, yc * h + bh / 2f)
                c.drawBitmap(m, null, rect, glowPaint)
                x += bw
            }
        }
        line.color = 0xB03C3746.toInt()
        line.strokeWidth = 1.1f * k
        val r = Random(5)
        repeat(5) { i ->
            val bx = r.nextFloat(); val by = 0.18f + 0.14f * r.nextFloat(); val sp = 0.5f + 0.5f * r.nextFloat()
            val x = (frac((bx + t * 0.01f * sp) / 1.1f) * 1.1f - 0.05f) * w
            val y = by * h + sin(t * 0.6f + bx * 9f) * 4f * k
            val f = sin(t * 6f * sp + bx * 10f) * 3f * k
            path.reset(); path.moveTo(x - 5f * k, y - f); path.lineTo(x, y); path.lineTo(x + 5f * k, y - f)
            c.drawPath(path, line)
        }
    }

    // ---- floating lanterns: warm paper lanterns rise slowly from the lake, flickering, with reflections ------

    private val lights = drifts(47, 34, 0.35f, 1.0f).sortedBy { it.z }
    private const val HZ = 0.70f

    private fun lanterns(c: Canvas, w: Float, h: Float, t: Float) {
        val g = glow ?: return
        for (p in lights) {
            val y = HZ - 0.02f - frac(p.y + t * 0.02f * p.z) * 0.95f * HZ
            val x = (p.x * 0.9f + 0.05f + 0.02f * sin(t * 0.4f + p.ph)) * w
            val s = (5f + 9f * p.z) * k * 0.5f * (0.6f + 0.4f * (y / HZ))
            val fl = 0.85f + 0.15f * sin(t * 7f + p.ph) * sin(t * 3.1f + p.ph * 2f)
            val cy = y * h
            glowPaint.colorFilter = android.graphics.PorterDuffColorFilter(argb(1f, 255, 150, 60), PorterDuff.Mode.SRC_IN)
            glowPaint.alpha = (0.55f * fl * p.z * 255).toInt()
            rect.set(x - s * 3.2f, cy - s * 3.2f, x + s * 3.2f, cy + s * 3.2f)
            c.drawBitmap(g, null, rect, glowPaint)
            val ry = (2f * HZ - y) * h
            if (ry < h) {
                glowPaint.alpha = (0.35f * fl * p.z * 255).toInt()
                rect.set(x - s * 0.9f, ry - s * 0.6f, x + s * 0.9f, ry + s * 2.2f)
                c.drawBitmap(g, null, rect, glowPaint)
            }
            glowPaint.colorFilter = null
            fill.color = argb(1f, 255, (190 * fl).toInt(), (110 * fl).toInt())
            rect.set(x - s * 0.7f, cy - s, x + s * 0.7f, cy + s)
            c.drawRoundRect(rect, s * 0.5f, s * 0.5f, fill)
            fill.color = argb(1f, 255, 236, 190)
            rect.set(x - s * 0.45f, cy - s * 0.7f, x + s * 0.45f, cy + s * 0.75f)
            c.drawRoundRect(rect, s * 0.35f, s * 0.35f, fill)
            fill.color = argb(1f, 120, 60, 30)
            rect.set(x - s * 0.55f, cy + s * 0.85f, x + s * 0.55f, cy + s)
            c.drawRect(rect, fill)
        }
        glowPaint.alpha = 255
    }

    // ---- autumn leaves: maple leaves twirl down through the golden wood --------------------------------------

    private val fallen = drifts(59, 20, 0.5f, 1.5f)
    private val leafColours = intArrayOf(0xD66028, 0xE89634, 0xBE3C24, 0xF0BE50, 0xA05228)
    private val leafPath: Path by lazy {
        val half = floatArrayOf(0f, -1f, 0.10f, -0.66f, 0.30f, -0.80f, 0.26f, -0.46f, 0.62f, -0.62f, 0.56f, -0.34f, 0.98f, -0.18f, 0.60f, -0.02f, 0.70f, 0.22f, 0.30f, 0.12f, 0.14f, 0.40f, 0.03f, 0.30f)
        Path().apply {
            moveTo(half[0], half[1])
            for (i in 2 until half.size step 2) lineTo(half[i], half[i + 1])
            for (i in half.size - 2 downTo 0 step 2) lineTo(-half[i], half[i + 1])
            close()
        }
    }

    private fun leaves(c: Canvas, w: Float, h: Float, t: Float) {
        for (p in fallen) {
            val y = (frac((p.y + t * 0.035f * p.z) / 1.1f) * 1.1f - 0.05f) * h
            val x = frac(p.x + 0.06f * sin(t * 0.8f + p.ph) + 0.02f * sin(t * 2.1f + p.ph)) * w
            val rot = sin(t * 1.2f + p.ph) * 1.4f + p.ph
            val flip = 0.25f + 0.75f * abs(cos(t * 1.7f + p.ph))
            val r = 15f * k * p.z
            val col = leafColours[p.kind]
            val sh = 0.75f + 0.25f * flip
            fill.color = argb(0.94f, (((col shr 16) and 255) * sh).toInt(), (((col shr 8) and 255) * sh).toInt(), ((col and 255) * sh).toInt())
            fill.maskFilter = if (p.z > 1.3f) nearBlur else null
            c.save()
            c.translate(x, y)
            c.rotate(Math.toDegrees(rot.toDouble()).toFloat())
            c.scale(r, r * flip)
            c.drawPath(leafPath, fill)
            c.restore()
            fill.maskFilter = null
            // the stalk
            line.color = argb(0.85f, 110, 50, 20)
            line.strokeWidth = 1f * k
            val sx = -sin(rot) * 0.62f * r * flip
            val sy = cos(rot) * 0.62f * r * flip
            c.drawLine(x - sin(rot) * 0.3f * r * flip, y + cos(rot) * 0.3f * r * flip, x + sx, y + sy, line)
        }
    }

    // ---- light under the sea: sunbeams sway down through the water; kelp sways; specks drift ----------------

    private val specks = drifts(71, 70, 0.3f, 1.0f)
    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN) }
    private val kelp = floatArrayOf(0.06f, 0.38f, 1.0f, 0.13f, 0.30f, 0.8f, 0.85f, 0.42f, 1.1f, 0.93f, 0.33f, 0.9f, 0.76f, 0.24f, 0.7f)

    private fun sea(c: Canvas, w: Float, h: Float, t: Float) {
        // beams: soft wedges from the surface, fading with depth, swaying slowly
        for (i in 0 until 9) {
            val base = (i + 0.5f) / 9f + 0.04f * sin(t * 0.13f + i * 1.7f)
            val spread = 0.06f + 0.05f * sin(i * 2.3f)
            val a = 0.10f + 0.07f * sin(t * 0.4f + i * 1.3f)
            rayPaint.shader = LinearGradient(0f, 0f, 0f, h * 0.85f, argb(a, 200, 242, 255), argb(0f, 200, 242, 255), Shader.TileMode.CLAMP)
            path.reset()
            path.moveTo((base - 0.015f) * w, 0f)
            path.lineTo((base + 0.015f) * w, 0f)
            path.lineTo((base + spread + 0.12f * sin(t * 0.05f)) * w, h * 0.85f)
            path.lineTo((base - spread + 0.12f * sin(t * 0.05f)) * w, h * 0.85f)
            path.close()
            c.drawPath(path, rayPaint)
        }
        rayPaint.shader = null
        // the shimmering surface
        line.strokeWidth = 1.4f * k
        for (i in 0 until 14) {
            val y = (0.008f + 0.006f * i) * h
            val x0 = frac(i * 0.37f + t * 0.03f * (if (i % 2 == 0) 1f else -1f)) * w
            line.color = argb(0.35f - 0.02f * i, 230, 251, 255)
            path.reset(); path.moveTo(x0 - 30f * k, y)
            path.quadTo(x0, y + 3f * k * sin(t + i), x0 + 30f * k, y)
            c.drawPath(path, line)
        }
        // kelp: stems and blades, swaying
        for (j in 0 until kelp.size / 3) {
            val kx = kelp[j * 3]; val kh = kelp[j * 3 + 1]; val kw = kelp[j * 3 + 2]
            for (strand in 0 until 3) {
                var px = kx * w + strand * 7f * k
                var py = h
                val x0 = px
                for (i in 1 until 30) {
                    val q = i / 29f
                    val nx = x0 + sin(t * 0.6f + q * 3f + strand + kx * 10f) * q * 18f * k
                    val ny = h - q * kh * h
                    line.color = argb(0.92f, 28, 70, 58)
                    line.strokeWidth = (7f - 5f * q) * k * kw * 0.5f
                    c.drawLine(px, py, nx, ny, line)
                    if (i % 2 == 1 && i > 2) {
                        val side = if ((i / 2) % 2 == 0) 1f else -1f
                        val ang = (-PI / 2).toFloat() + side * (0.7f + 0.2f * sin(t * 0.9f + i * 0.4f + kx * 7f))
                        val ln = (26f - 14f * q) * k * kw * 0.5f
                        val wd = 5f * k * kw * 0.5f
                        val tx = nx + cos(ang) * ln; val ty = ny + sin(ang) * ln
                        val mx = (nx + tx) / 2f; val my = (ny + ty) / 2f
                        val ox = -sin(ang) * wd; val oy = cos(ang) * wd
                        path.reset(); path.moveTo(nx, ny); path.lineTo(mx + ox, my + oy); path.lineTo(tx, ty); path.lineTo(mx - ox, my - oy); path.close()
                        fill.color = argb(0.85f, 40, 96, 62)
                        c.drawPath(path, fill)
                    }
                    px = nx; py = ny
                }
            }
        }
        // a few fish in the distance
        fill.color = argb(0.6f, 14, 54, 80)
        val r = Random(9)
        repeat(9) { i ->
            val fx = r.nextFloat(); val fy = 0.3f + 0.2f * r.nextFloat()
            val x = (frac((fx + t * 0.025f) / 1.2f) * 1.2f - 0.1f) * w
            val y = (fy + 0.02f * sin(t * 0.8f + i)) * h
            rect.set(x - 4.5f * k, y - 1.5f * k, x + 4.5f * k, y + 1.5f * k); c.drawOval(rect, fill)
            path.reset(); path.moveTo(x - 4f * k, y); path.lineTo(x - 7f * k, y - 2f * k); path.lineTo(x - 7f * k, y + 2f * k); path.close(); c.drawPath(path, fill)
        }
        // marine snow
        for (p in specks) {
            val x = (p.x + 0.01f * sin(t * 0.5f + p.ph)) * w
            val y = frac(p.y + t * 0.006f * p.z) * h
            soft.color = argb(0.47f * p.z, 220, 240, 255)
            c.drawCircle(x, y, (0.6f + 1.2f * p.z) * k * 0.5f, soft)
        }
    }
}
