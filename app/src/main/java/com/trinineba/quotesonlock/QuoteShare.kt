package com.trinineba.quotesonlock

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.core.content.FileProvider
import java.io.File
import kotlin.math.max

/** Turns a quote into a picture in the app's theme and hands it to Android's share sheet. */
object QuoteShare {
    private const val WIDTH = 1080
    private const val HEIGHT = 1350

    fun share(context: Context, quote: Quote) {
        val appContext = context.applicationContext
        val bitmap = render(appContext, quote, QuotePrefs(appContext))
        val dir = File(appContext.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "quote-${System.currentTimeMillis()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.updates", file)
        val text = "“${quote.text}”" + (quote.author?.let { "\n— $it" } ?: "")
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri("", uri)
        context.startActivity(Intent.createChooser(send, "Share quote").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** A 4:5 picture: the app theme's artwork, the quote card at the chosen opacity, and a small credit. */
    fun render(context: Context, quote: Quote, prefs: QuotePrefs): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val theme = prefs.theme
        val w = WIDTH.toFloat()
        val h = HEIGHT.toFloat()
        val density = WIDTH / 360f
        when (theme) {
            AppTheme.STARRY -> cover(canvas, BitmapFactory.decodeResource(context.resources, R.drawable.starry_night))
            AppTheme.AURORA -> QuoteArt.drawAurora(canvas, w, h, 1.2f, false, prefs.auroraIntensity, density)
            AppTheme.BOOK -> cover(canvas, BitmapFactory.decodeResource(context.resources, R.drawable.paper))
            AppTheme.BUTTERFLY -> ButterflyArt.draw(canvas, w, h, 3f, density)
            AppTheme.SPACE -> {
                val nebula = BitmapFactory.decodeResource(context.resources, R.drawable.nebula)
                SpaceArt.draw(canvas, w, h, 20f, density, nebula)
                nebula.recycle()
            }
            AppTheme.CLASSIC -> canvas.drawRect(
                0f, 0f, w, h,
                Paint().apply { shader = LinearGradient(0f, 0f, 0f, h, 0xFF1B2440.toInt(), 0xFF0B0F1E.toInt(), Shader.TileMode.CLAMP) },
            )
        }
        QuoteArt.drawQuoteCard(
            canvas, WIDTH, HEIGHT, density, density, theme, prefs.style,
            quote.text, quote.author, prefs.cardOpacity.coerceAtLeast(0.55f), 0.5f, prefs.cardSoftness,
        )
        val credit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f * density
            textAlign = Paint.Align.CENTER
            color = Color.argb(150, if (theme.isLight) 60 else 255, if (theme.isLight) 30 else 255, if (theme.isLight) 50 else 255)
            if (!theme.isLight) setShadowLayer(3f * density, 0f, 1f * density, 0xCC000000.toInt())
        }
        canvas.drawText("Quotes on Lock", w / 2f, h - 26f * density, credit)
        return bitmap
    }

    /** Scale to cover the picture, centred. */
    private fun cover(canvas: Canvas, b: Bitmap) {
        val s = max(WIDTH / b.width.toFloat(), HEIGHT / b.height.toFloat())
        val dx = (WIDTH - b.width * s) / 2f
        val dy = (HEIGHT - b.height * s) / 2f
        canvas.save()
        canvas.translate(dx, dy)
        canvas.scale(s, s)
        canvas.drawBitmap(b, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
        b.recycle()
    }
}
