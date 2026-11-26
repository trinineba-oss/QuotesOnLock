package com.trinineba.quotesonlock

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The Painted collection: oil-painted landscapes at full phone resolution, shown edge to edge with a very slow drift
 * (a gentle zoom and pan, about a minute per breath) and a soft shade at the top and bottom so text reads over them.
 */
object PaintedArt {
    fun res(theme: AppTheme): Int? = when (theme) {
        AppTheme.P_TETONS -> R.drawable.paint_tetons
        AppTheme.P_MAROON -> R.drawable.paint_maroon
        AppTheme.P_BIGSUR -> R.drawable.paint_bigsur
        AppTheme.P_MONUMENT -> R.drawable.paint_monument
        AppTheme.P_LAKEDUSK -> R.drawable.paint_lakedusk
        AppTheme.P_AUTUMN -> R.drawable.paint_autumnriver
        AppTheme.P_SNOW -> R.drawable.paint_snowpines
        AppTheme.P_SUNFLOWERS -> R.drawable.paint_sunflowers
        AppTheme.P_PLAINS -> R.drawable.paint_plains
        else -> null
    }

    fun frame(theme: AppTheme): Int? = when (theme) {
        AppTheme.P_TETONS -> R.drawable.paint_tetons_frame
        AppTheme.P_MAROON -> R.drawable.paint_maroon_frame
        AppTheme.P_BIGSUR -> R.drawable.paint_bigsur_frame
        AppTheme.P_MONUMENT -> R.drawable.paint_monument_frame
        AppTheme.P_LAKEDUSK -> R.drawable.paint_lakedusk_frame
        AppTheme.P_AUTUMN -> R.drawable.paint_autumnriver_frame
        AppTheme.P_SNOW -> R.drawable.paint_snowpines_frame
        AppTheme.P_SUNFLOWERS -> R.drawable.paint_sunflowers_frame
        AppTheme.P_PLAINS -> R.drawable.paint_plains_frame
        else -> null
    }

    private var key = ""
    private var bitmap: Bitmap? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val shade = Paint()
    private var shadeKey = ""
    private val matrix = Matrix()

    /** Decodes the painting no larger than the screen needs (a quarter of the memory on a small screen). */
    private fun load(context: Context, id: Int, w: Float, h: Float): Bitmap? {
        val k = "$id:${w.toInt()}x${h.toInt()}"
        if (k == key) bitmap?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, id, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= w * 1.1f && bounds.outHeight / (sample * 2) >= h * 1.1f) sample *= 2
        val b = BitmapFactory.decodeResource(context.resources, id, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        bitmap?.takeIf { it != b }?.recycle()
        bitmap = b; key = k
        return b
    }

    /** Draws [theme] at time [t] seconds; false if it is not a painted theme or the picture could not be read. */
    fun draw(context: Context?, theme: AppTheme, canvas: Canvas, w: Float, h: Float, t: Float): Boolean {
        val id = res(theme) ?: return false
        if (context == null) return false
        val b = load(context, id, w, h) ?: return false
        val breathe = sin(2 * PI * t / 60.0).toFloat()
        val cover = max(w / b.width, h / b.height) * (1.07f + 0.025f * breathe)
        val dx = (w - b.width * cover) / 2f + w * 0.018f * sin(2 * PI * t / 83.0).toFloat()
        val dy = (h - b.height * cover) / 2f + h * 0.012f * cos(2 * PI * t / 71.0).toFloat()
        matrix.setScale(cover, cover)
        matrix.postTranslate(dx, dy)
        canvas.drawBitmap(b, matrix, paint)
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
}
