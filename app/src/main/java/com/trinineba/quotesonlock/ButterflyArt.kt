package com.trinineba.quotesonlock

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The pink sky with butterflies, drawn on a plain Android canvas so the app background and the
 * live wallpaper share one picture. Not thread-safe; call from one thread at a time.
 */
internal object ButterflyArt {
    private class Fly(
        val y: Float, val speed: Float, val size: Float, val phase: Float,
        val wobble: Float, val color: Int, val flapRate: Float,
    )

    private class Glow(val x: Float, val y: Float, val r: Float, val alpha: Int)

    private val palette = intArrayOf(
        0xFFE84393.toInt(), 0xFFFF7EB6.toInt(), 0xFFB56CF0.toInt(),
        0xFFFFFFFF.toInt(), 0xFFFF5C8A.toInt(), 0xFFF06FC4.toInt(),
    )
    private val flies = Random(7).let { rnd ->
        List(14) {
            Fly(
                y = 0.04f + rnd.nextFloat() * 0.9f,
                speed = 0.018f + rnd.nextFloat() * 0.03f,
                size = 0.7f + rnd.nextFloat() * 0.8f,
                phase = rnd.nextFloat(),
                wobble = 0.02f + rnd.nextFloat() * 0.04f,
                color = palette[it % palette.size],
                flapRate = 7f + rnd.nextFloat() * 5f,
            )
        }
    }
    private val glows = Random(3).let { rnd ->
        List(24) { Glow(rnd.nextFloat(), rnd.nextFloat(), 0.02f + rnd.nextFloat() * 0.07f, 25 + rnd.nextInt(45)) }
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val upperWing = Path()
    private val lowerWing = Path()
    private var sky: LinearGradient? = null
    private var skySize = 0f to 0f

    /** [t] is the animation time in seconds; a fixed value gives a still picture. */
    fun draw(canvas: Canvas, w: Float, h: Float, t: Float, density: Float) {
        if (skySize != w to h) {
            sky = LinearGradient(
                0f, 0f, w * 0.3f, h,
                intArrayOf(0xFFFFE4F0.toInt(), 0xFFFBB8D5.toInt(), 0xFFF290BC.toInt()),
                null, Shader.TileMode.CLAMP,
            )
            skySize = w to h
        }
        paint.style = Paint.Style.FILL
        paint.shader = sky
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // Soft out-of-focus light.
        glows.forEachIndexed { i, g ->
            paint.color = Color.argb(g.alpha, 255, 255, 255)
            canvas.drawCircle((g.x + sin(t * 0.25f + i) * 0.01f) * w, g.y * h, g.r * min(w, h), paint)
        }

        val unit = 22f * density
        flies.forEach { f ->
            // Each butterfly drifts across the screen and wraps round, bobbing as it goes.
            val span = w + 4 * unit
            val x = ((f.phase + t * f.speed) % 1f) * span - 2 * unit
            val bob = f.phase * 20f + t * 1.3f
            val y = f.y * h + sin(bob) * f.wobble * h
            val tilt = 30f + cos(bob) * 12f
            // Seen from above, beating wings look narrower, then wide again.
            val flap = 0.25f + 0.75f * abs(cos(t * f.flapRate + f.phase * 10f))
            butterfly(canvas, x, y, unit * f.size, flap, tilt, f.color)
        }
    }

    private fun butterfly(canvas: Canvas, x: Float, y: Float, s: Float, flap: Float, tilt: Float, color: Int) {
        upperWing.reset()
        upperWing.moveTo(0f, -0.1f * s)
        upperWing.cubicTo(0.35f * s, -1.05f * s, 1.15f * s, -0.95f * s, 0.95f * s, -0.25f * s)
        upperWing.cubicTo(0.85f * s, 0.02f * s, 0.35f * s, 0.05f * s, 0f, 0f)
        lowerWing.reset()
        lowerWing.moveTo(0f, 0f)
        lowerWing.cubicTo(0.5f * s, 0.08f * s, 0.85f * s, 0.5f * s, 0.6f * s, 0.85f * s)
        lowerWing.cubicTo(0.4f * s, 1.05f * s, 0.1f * s, 0.6f * s, 0f, 0.15f * s)

        val light = blend(color, Color.WHITE, 0.55f)
        val edge = blend(color, 0xFF5A1036.toInt(), 0.45f)
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(tilt)
        for (side in intArrayOf(1, -1)) {
            canvas.save()
            canvas.scale(side * flap, 1f)
            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(0f, 0f, 1.1f * s, intArrayOf(light, color), null, Shader.TileMode.CLAMP)
            paint.alpha = 235
            canvas.drawPath(upperWing, paint)
            canvas.drawPath(lowerWing, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.05f * s
            paint.color = edge
            canvas.drawPath(upperWing, paint)
            canvas.drawPath(lowerWing, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(200, 255, 255, 255)
            canvas.drawCircle(0.72f * s, -0.5f * s, 0.09f * s, paint)
            canvas.restore()
        }
        // Body and antennae.
        paint.color = 0xFF4A1C33.toInt()
        canvas.drawOval(-0.06f * s, -0.4f * s, 0.06f * s, 0.5f * s, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.035f * s
        canvas.drawLine(0f, -0.38f * s, -0.2f * s, -0.72f * s, paint)
        canvas.drawLine(0f, -0.38f * s, 0.2f * s, -0.72f * s, paint)
        paint.style = Paint.Style.FILL
        canvas.restore()
    }

    private fun blend(a: Int, b: Int, f: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt(),
    )
}
