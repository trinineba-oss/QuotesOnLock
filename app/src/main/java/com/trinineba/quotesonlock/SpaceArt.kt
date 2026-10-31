package com.trinineba.quotesonlock

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Deep space: a nebula (a pre-rendered picture that slowly drifts and breathes) with stars that
 * float toward the viewer and slide past. Everything is a function of [t] (seconds), so the app,
 * the wallpaper and the share picture draw the same sky without keeping any state.
 */
internal object SpaceArt {
    /** x, y in -1..1 (direction from the centre), phase 0..1 (how far along its flight), speed. */
    private class Star(val x: Float, val y: Float, val phase: Float, val speed: Float, val tint: Int)

    private val tints = intArrayOf(0xFFFFFFFF.toInt(), 0xFFE6E2FF.toInt(), 0xFFD8ECFF.toInt(), 0xFFFFE9F4.toInt())
    private val stars = Random(17).let { rnd ->
        List(170) {
            Star(
                x = rnd.nextFloat() * 2f - 1f,
                y = rnd.nextFloat() * 2f - 1f,
                phase = rnd.nextFloat(),
                speed = 0.018f + rnd.nextFloat() * 0.03f,
                tint = tints[it % tints.size],
            )
        }
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val picture = Paint(Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()

    fun draw(canvas: Canvas, w: Float, h: Float, t: Float, density: Float, nebula: Bitmap) {
        // The nebula drifts and slowly "breathes" in scale, as if floating forward through it.
        val breathe = 1.08f + 0.04f * sin(t * 2f * PI.toFloat() / 70f)
        val s = max(w / nebula.width, h / nebula.height) * breathe
        val dx = w * 0.025f * sin(t * 2f * PI.toFloat() / 95f)
        val dy = h * 0.02f * sin(t * 2f * PI.toFloat() / 120f + 1f)
        matrix.setScale(s, s)
        matrix.postTranslate((w - nebula.width * s) / 2f + dx, (h - nebula.height * s) / 2f + dy)
        canvas.drawColor(0xFF03040C.toInt())
        canvas.drawBitmap(nebula, matrix, picture)

        // Stars come from far away near the centre, grow, brighten and slide out past the edges.
        val cx = w / 2f
        val cy = h * 0.42f
        stars.forEach { star ->
            val progress = (star.phase + t * star.speed) % 1f
            val z = 1f - progress * 0.94f // 1 = far, 0.06 = close
            val sx = cx + star.x / z * w * 0.32f
            val sy = cy + star.y / z * h * 0.22f
            if (sx < -20f || sx > w + 20f || sy < -20f || sy > h + 20f) return@forEach
            val near = 1f - z
            val fadeIn = (progress * 6f).coerceAtMost(1f)
            val alpha = (fadeIn * (0.35f + 0.65f * near)).coerceIn(0f, 1f)
            val r = (0.5f + near * 2.2f) * density
            paint.color = (((alpha * 0.25f) * 255).toInt() shl 24) or (star.tint and 0x00FFFFFF)
            canvas.drawCircle(sx, sy, r * 3f, paint)
            paint.color = ((alpha * 255).toInt() shl 24) or (star.tint and 0x00FFFFFF)
            canvas.drawCircle(sx, sy, r, paint)
        }
    }
}
