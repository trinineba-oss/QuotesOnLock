package com.trinineba.quotesonlock

import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** The look of the app (and of the lock-screen card). */
enum class AppTheme(
    val key: String,
    val label: String,
    /** Label of the "make it move" switch, or null if the theme doesn't move. */
    val motionLabel: String?,
    /** Artwork framing the expanded lock-screen card, or null for the system's plain background. */
    @DrawableRes val notificationFrame: Int?,
    /** Colour of the card behind the quote (inside the frame); its opacity is the user's choice. */
    val cardColor: Int?,
    /** Default notification text and author colours when the user hasn't picked a colour. */
    val notificationText: Int?,
    val notificationAuthor: Int?,
) {
    CLASSIC("classic", "Classic", null, null, null, null, null),
    STARRY(
        "starry", "Starry Night", "Moving swirls", R.drawable.notif_frame_starry, 0xFF0B1A3D.toInt(),
        0xFFFFFFFF.toInt(), 0xFFF4C94A.toInt(),
    ),
    AURORA(
        "aurora", "Northern lights", "Moving lights", R.drawable.notif_frame_aurora, 0xFF040915.toInt(),
        0xFFFFFFFF.toInt(), 0xFF6CF5C8.toInt(),
    ),
    BOOK(
        "book", "Old book", null, R.drawable.notif_frame_book, 0xFFF1E4C6.toInt(),
        0xFF3B2A1A.toInt(), 0xFF7A2E1F.toInt(),
    ),
    BUTTERFLY(
        "butterfly", "Butterflies", "Flying butterflies", R.drawable.notif_frame_butterfly, 0xFFFFF1F7.toInt(),
        0xFF4A1C33.toInt(), 0xFFC2185B.toInt(),
    ),
    SPACE(
        "space", "Deep space", "Drifting stars", R.drawable.notif_frame_space, 0xFF0A0B22.toInt(),
        0xFFFFFFFF.toInt(), 0xFFC8B8FF.toInt(),
    ),
    WATER(
        "water", "Calm water", "Gentle ripples", R.drawable.notif_frame_water, 0xFF0B1A30.toInt(),
        0xFFFFFFFF.toInt(), 0xFFFFD39A.toInt(),
    );

    /** Light themes use dark text. */
    val isLight: Boolean get() = this == BOOK || this == BUTTERFLY

    companion object {
        fun fromKey(key: String?): AppTheme = entries.firstOrNull { it.key == key } ?: CLASSIC
    }
}

private val Night = Color(0xFF0B1A3D)
private val AuroraNight = Color(0xFF040915)

/** [cardOpacity] (0–1) is how solid the cards behind the text are over the theme's artwork. */
fun AppTheme.colorScheme(cardOpacity: Float): ColorScheme? = when (this) {
    AppTheme.CLASSIC -> null
    AppTheme.STARRY -> darkColorScheme(
        primary = Color(0xFFF4C94A),
        onPrimary = Color(0xFF1A1300),
        primaryContainer = Color(0xFF3A64B0),
        onPrimaryContainer = Color.White,
        secondary = Color(0xFF8FB3E0),
        onSecondary = Night,
        secondaryContainer = Color(0xFF3A64B0),
        onSecondaryContainer = Color.White,
        background = Color.Transparent,
        onBackground = Color(0xFFEEF3FF),
        surface = Night,
        onSurface = Color(0xFFEEF3FF),
        onSurfaceVariant = Color(0xFFC9D6EE),
        surfaceContainerHighest = Color(0xFF102A66).copy(alpha = cardOpacity),
        surfaceVariant = Color(0xFF22305A),
        outline = Color(0xFF8FB3E0),
        outlineVariant = Color(0x663A64B0),
    )
    AppTheme.AURORA -> darkColorScheme(
        primary = Color(0xFF6CF5C8),
        onPrimary = Color(0xFF002A1D),
        primaryContainer = Color(0xFF1F6E6A),
        onPrimaryContainer = Color.White,
        secondary = Color(0xFFB79CFF),
        onSecondary = Color(0xFF1C0F3D),
        secondaryContainer = Color(0xFF2B5F6E),
        onSecondaryContainer = Color.White,
        background = Color.Transparent,
        onBackground = Color(0xFFEAF7F4),
        surface = AuroraNight,
        onSurface = Color(0xFFEAF7F4),
        onSurfaceVariant = Color(0xFFB4D2CC),
        surfaceContainerHighest = Color(0xFF0A1A2A).copy(alpha = cardOpacity),
        surfaceVariant = Color(0xFF1C2E3A),
        outline = Color(0xFF6CF5C8),
        outlineVariant = Color(0x662B5F6E),
    )
    AppTheme.BOOK -> lightColorScheme(
        primary = Color(0xFF7A2E1F),
        onPrimary = Color(0xFFF7EEDB),
        primaryContainer = Color(0xFFD9C29A),
        onPrimaryContainer = Color(0xFF2E2012),
        secondary = Color(0xFF6B4F2A),
        onSecondary = Color(0xFFF7EEDB),
        secondaryContainer = Color(0xFFD9C29A),
        onSecondaryContainer = Color(0xFF2E2012),
        background = Color.Transparent,
        onBackground = Color(0xFF3B2A1A),
        surface = Color(0xFFF1E4C6),
        onSurface = Color(0xFF3B2A1A),
        onSurfaceVariant = Color(0xFF5E4A30),
        surfaceContainerHighest = Color(0xFFF7EDD5).copy(alpha = cardOpacity),
        surfaceVariant = Color(0xFFE2D0A8),
        outline = Color(0xFF8B6B45),
        outlineVariant = Color(0x668B6B45),
    )
    AppTheme.SPACE -> darkColorScheme(
        primary = Color(0xFFC8B8FF),
        onPrimary = Color(0xFF1C1240),
        primaryContainer = Color(0xFF4B3A9C),
        onPrimaryContainer = Color.White,
        secondary = Color(0xFF8FD3E8),
        onSecondary = Color(0xFF06222C),
        secondaryContainer = Color(0xFF2B4C6E),
        onSecondaryContainer = Color.White,
        background = Color.Transparent,
        onBackground = Color(0xFFEDEBFF),
        surface = Color(0xFF05061A),
        onSurface = Color(0xFFEDEBFF),
        onSurfaceVariant = Color(0xFFC9C3EA),
        surfaceContainerHighest = Color(0xFF0A0B22).copy(alpha = cardOpacity),
        surfaceVariant = Color(0xFF1E1B3E),
        outline = Color(0xFFC8B8FF),
        outlineVariant = Color(0x664B3A9C),
    )
    AppTheme.WATER -> darkColorScheme(
        primary = Color(0xFFFFD39A),
        onPrimary = Color(0xFF2B1A05),
        primaryContainer = Color(0xFF7A4E2A),
        onPrimaryContainer = Color.White,
        secondary = Color(0xFFE6A6B0),
        onSecondary = Color(0xFF2E1218),
        secondaryContainer = Color(0xFF26405E),
        onSecondaryContainer = Color.White,
        background = Color.Transparent,
        onBackground = Color(0xFFF4EEE8),
        surface = Color(0xFF0B1A30),
        onSurface = Color(0xFFF4EEE8),
        onSurfaceVariant = Color(0xFFD9CFC8),
        surfaceContainerHighest = Color(0xFF0B1A30).copy(alpha = cardOpacity),
        surfaceVariant = Color(0xFF1C2C44),
        outline = Color(0xFFFFD39A),
        outlineVariant = Color(0x667A4E2A),
    )
    AppTheme.BUTTERFLY -> lightColorScheme(
        primary = Color(0xFFC2185B),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFF8BBD9),
        onPrimaryContainer = Color(0xFF4A1C33),
        secondary = Color(0xFF8E3A8C),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF3C6EC),
        onSecondaryContainer = Color(0xFF4A1C33),
        background = Color.Transparent,
        onBackground = Color(0xFF4A1C33),
        surface = Color(0xFFFFE4F0),
        onSurface = Color(0xFF4A1C33),
        onSurfaceVariant = Color(0xFF7A3E5C),
        surfaceContainerHighest = Color(0xFFFFF1F7).copy(alpha = cardOpacity),
        surfaceVariant = Color(0xFFF8D3E4),
        outline = Color(0xFFC2185B),
        outlineVariant = Color(0x66C2185B),
    )
}

/** Night themes get a soft shadow under all text so it reads over the painting; the book page gets serif type. */
fun AppTheme.typography(base: Typography): Typography = when (this) {
    AppTheme.CLASSIC -> base
    AppTheme.STARRY, AppTheme.AURORA, AppTheme.SPACE, AppTheme.WATER -> base.map(
        headings = { it.copy(fontFamily = FontFamily.Serif) },
        all = { it.copy(shadow = Shadow(Color(0xE6000000), Offset(0f, 2f), 8f)) },
    )
    AppTheme.BOOK -> base.map(headings = { it }, all = { it.copy(fontFamily = FontFamily.Serif) })
    AppTheme.BUTTERFLY -> base.map(headings = { it.copy(fontFamily = FontFamily.Serif) }, all = { it })
}

private fun Typography.map(headings: (TextStyle) -> TextStyle, all: (TextStyle) -> TextStyle) = Typography(
    displayLarge = all(headings(displayLarge)),
    displayMedium = all(headings(displayMedium)),
    displaySmall = all(headings(displaySmall)),
    headlineLarge = all(headings(headlineLarge)),
    headlineMedium = all(headings(headlineMedium)),
    headlineSmall = all(headings(headlineSmall)),
    titleLarge = all(headings(titleLarge)),
    titleMedium = all(headings(titleMedium)),
    titleSmall = all(titleSmall),
    bodyLarge = all(bodyLarge),
    bodyMedium = all(bodyMedium),
    bodySmall = all(bodySmall),
    labelLarge = all(labelLarge),
    labelMedium = all(labelMedium),
    labelSmall = all(labelSmall),
)

/** Draws the theme's background behind the whole app. */
@Composable
fun ThemedBackground(theme: AppTheme, animate: Boolean, auroraIntensity: Float = 1f) {
    when (theme) {
        AppTheme.CLASSIC -> Unit
        AppTheme.STARRY -> StarryBackground(animate)
        AppTheme.AURORA -> AuroraBackground(animate, auroraIntensity)
        AppTheme.BOOK -> Image(
            painter = painterResource(R.drawable.paper),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        AppTheme.BUTTERFLY -> ButterflyBackground(animate)
        AppTheme.SPACE -> SpaceBackground(animate)
        AppTheme.WATER -> WaterBackground(animate)
    }
}

@Composable
private fun WaterBackground(animate: Boolean) {
    // If the GPU can't build the shader, show the still water rather than fail.
    val shader = if (animate && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        remember { runCatching { RuntimeShader(WaterArt.SHADER) }.getOrNull() }
    } else {
        null
    }
    if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        RipplingWater(shader)
    } else {
        Image(
            painter = painterResource(R.drawable.water),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun RipplingWater(shader: RuntimeShader) {
    val brush = remember(shader) { ShaderBrush(shader) }
    // A loop of 2π·1000 ms makes the value plain seconds; every wave repeats in LOOP_SECONDS.
    val time by rememberLoopAngle(2000f * PI.toFloat())
    Canvas(Modifier.fillMaxSize()) {
        shader.setFloatUniform("res", size.width, size.height)
        shader.setFloatUniform("time", time % WaterArt.LOOP_SECONDS)
        drawRect(brush)
    }
}

@Composable
private fun SpaceBackground(animate: Boolean) {
    val nebula = ImageBitmap.imageResource(R.drawable.nebula).asAndroidBitmap()
    // A loop of 2π·1000 ms makes the value plain seconds.
    val time: State<Float> = if (animate) rememberLoopAngle(2000f * PI.toFloat()) else remember { mutableFloatStateOf(20f) }
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        drawIntoCanvas { SpaceArt.draw(it.nativeCanvas, size.width, size.height, t, density, nebula) }
    }
}

@Composable
private fun ButterflyBackground(animate: Boolean) {
    // A loop of 2π·1000 ms makes the value plain seconds.
    val time: State<Float> = if (animate) rememberLoopAngle(2000f * PI.toFloat()) else remember { mutableFloatStateOf(3f) }
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        drawIntoCanvas { ButterflyArt.draw(it.nativeCanvas, size.width, size.height, t, density) }
    }
}

@Composable
private fun StarryBackground(animate: Boolean) {
    Box(Modifier.fillMaxSize().background(Night)) {
        if (animate && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            SwirlingStarryNight()
        } else {
            Image(
                painter = painterResource(R.drawable.starry_night),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
            )
        }
        // The painting shows at the top and fades into night blue so the lists stay readable.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Night.copy(alpha = 0.13f),
                    0.32f to Night.copy(alpha = 0.6f),
                    0.52f to Night.copy(alpha = 0.95f),
                    0.7f to Night,
                )
            )
        )
    }
}

/**
 * Turns the big vortex and two of the stars gently back and forth and lets the brushstrokes
 * shimmer. Runs on the GPU and only while the app is on screen.
 */
internal const val SWIRL_SHADER = """
uniform shader image;
uniform float time;
uniform float k;
uniform float4 c0;
uniform float4 c1;
uniform float4 c2;

float2 swirl(float2 p, float4 c, float t) {
    float2 d = p - c.xy;
    float r = length(d) / c.z;
    float a = c.w * sin(t) * exp(-r * r);
    float ca = cos(a);
    float sa = sin(a);
    return c.xy + float2(ca * d.x - sa * d.y, sa * d.x + ca * d.y);
}

half4 main(float2 p) {
    float2 q = swirl(p, c0, time);
    q = swirl(q, c1, time + 1.3);
    q = swirl(q, c2, time + 2.6);
    q += float2(sin(p.y * k / 37.0 + time), cos(p.x * k / 41.0 + time)) * (3.0 / k);
    return image.eval(q);
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun SwirlingStarryNight() {
    val image = ImageBitmap.imageResource(R.drawable.starry_night)
    val shader = remember { RuntimeShader(SWIRL_SHADER) }
    val bitmapShader = remember(image) {
        BitmapShader(image.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
    val time by rememberLoopAngle(6000f)
    val brush = remember(shader) { ShaderBrush(shader) }
    Canvas(Modifier.fillMaxSize()) {
        val bw = image.width.toFloat()
        val bh = image.height.toFloat()
        // Same framing as the still image: cover the screen, anchored at the top.
        val s = max(size.width / bw, size.height / bh)
        val tx = (size.width - bw * s) / 2f
        bitmapShader.setLocalMatrix(Matrix().apply { setScale(s, s); postTranslate(tx, 0f) })
        shader.setInputShader("image", bitmapShader)
        val iw = bw * s
        val ih = bh * s
        shader.setFloatUniform("time", time)
        shader.setFloatUniform("k", 900f / ih)
        shader.setFloatUniform("c0", tx + 0.47f * iw, 0.36f * ih, 0.167f * ih, 0.38f)
        shader.setFloatUniform("c1", tx + 0.30f * iw, 0.20f * ih, 0.067f * ih, 0.5f)
        shader.setFloatUniform("c2", tx + 0.62f * iw, 0.12f * ih, 0.061f * ih, 0.5f)
        drawRect(brush)
    }
}

/**
 * Seconds-based clock driven by the screen's frame clock. Unlike Compose's animation specs it keeps
 * running when battery saver or "Remove animations" sets the system animation scale to zero.
 * Returns an ever-growing angle (radians) that completes one cycle per [periodMs].
 */
@Composable
private fun rememberLoopAngle(periodMs: Float): State<Float> {
    val angle = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(periodMs) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> angle.floatValue = (now - start) / 1_000_000f / periodMs * 2f * PI.toFloat() }
        }
    }
    return angle
}

private class Star(val x: Float, val y: Float, val r: Float, val a: Float, val phase: Float)

@Composable
private fun AuroraBackground(animate: Boolean, intensity: Float) {
    val time: State<Float> = if (animate) rememberLoopAngle(10000f) else remember { mutableFloatStateOf(1.2f) }
    val stars = remember {
        val rnd = Random(11)
        List(140) { Star(rnd.nextFloat(), rnd.nextFloat() * 0.75f, 0.6f + rnd.nextFloat() * 1.4f, 0.3f + rnd.nextFloat() * 0.7f, rnd.nextFloat() * 6.28f) }
    }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF02040C), Color(0xFF061024), Color(0xFF0A1A2C))))
            val t = time.value
            stars.forEach { st ->
                val twinkle = if (animate) 0.75f + 0.25f * sin(t * 6 + st.phase) else 1f
                drawCircle(Color.White.copy(alpha = st.a * twinkle), st.r * density, Offset(st.x * size.width, st.y * size.height))
            }
        }
        // The curtains of light: bright lower edges fading upwards, softened by blur.
        Canvas(Modifier.fillMaxSize().blur(18.dp)) {
            val t = time.value
            val w = size.width
            val h = size.height
            val ribbons = listOf(
                Triple(Color(0xFF46FFAA), 0.30f, 0f),
                Triple(Color(0xFF28D2DC), 0.38f, 1.7f),
                Triple(Color(0xFF965AFF), 0.22f, 3.1f),
            )
            ribbons.forEachIndexed { i, (color, base, phase) ->
                // Stronger lights are taller, wave more and glow brighter.
                val amp = h * (0.05f - 0.01f * i) * (0.8f + 0.2f * intensity)
                val tail = h * (0.22f - 0.04f * i) * (0.6f + 0.4f * intensity)
                val steps = 48
                val path = Path()
                for (j in 0..steps) {
                    val x = w * j / steps
                    val u = x / w * 2f * PI.toFloat()
                    val y = h * base + amp * sin(u * (1.1f + 0.5f * i) + phase + t * (1f + 0.3f * i)) +
                        amp * 0.4f * sin(u * 4.3f - t * 0.8f + phase)
                    if (j == 0) path.moveTo(x, y - tail) else path.lineTo(x, y - tail)
                }
                for (j in steps downTo 0) {
                    val x = w * j / steps
                    val u = x / w * 2f * PI.toFloat()
                    val y = h * base + amp * sin(u * (1.1f + 0.5f * i) + phase + t * (1f + 0.3f * i)) +
                        amp * 0.4f * sin(u * 4.3f - t * 0.8f + phase)
                    path.lineTo(x, y)
                }
                drawPath(
                    path,
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.75f to color.copy(alpha = (0.35f * intensity).coerceAtMost(0.8f)),
                        1f to color.copy(alpha = (0.75f * intensity).coerceAtMost(1f)),
                        startY = h * base - amp - tail,
                        endY = h * base + amp,
                    ),
                )
            }
        }
        // Darken the lower part so the settings below stay readable (less so for stronger lights).
        val shade = (1.25f - 0.25f * intensity).coerceIn(0.75f, 1.1f)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.4f to AuroraNight.copy(alpha = (0.55f * shade).coerceAtMost(1f)),
                    0.62f to AuroraNight.copy(alpha = (0.92f * shade).coerceAtMost(1f)),
                    1f to AuroraNight,
                )
            )
        )
    }
}
