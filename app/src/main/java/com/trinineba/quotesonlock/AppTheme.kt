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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.compositionLocalOf
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
    /** True black and nothing moving: on OLED screens black pixels are off, so this uses the least battery. */
    BLACK(
        "black", "Pure black", null, R.drawable.notif_frame_black, 0xFF000000.toInt(),
        0xFFE2DFD9.toInt(), 0xFFC9A66B.toInt(),
    ),
    STARRY(
        "starry", "Starry Night", "Moving swirls", R.drawable.notif_frame_starry, 0xFF0B1A3D.toInt(),
        0xFFFFFFFF.toInt(), 0xFFF4C94A.toInt(),
    ),
    AURORA(
        "aurora", "Northern lights", "Moving lights", R.drawable.notif_frame_aurora, 0xFF040915.toInt(),
        0xFFFFFFFF.toInt(), 0xFF6CF5C8.toInt(),
    ),
    BOOK(
        "book", "Old book", "Window light, dust and a lifting page", R.drawable.notif_frame_book, 0xFFF1E4C6.toInt(),
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
    ),
    RAIN(
        "rain", "Rainy day", "Rain on the glass", R.drawable.notif_frame_rain, 0xFF263038.toInt(),
        0xFFFFFFFF.toInt(), 0xFFC9DCEB.toInt(),
    ),
    RAIN_NIGHT(
        "rainnight", "Rainy night", "Rain on the glass", R.drawable.notif_frame_rain_night, 0xFF141A2A.toInt(),
        0xFFFFFFFF.toInt(), 0xFFFFC27A.toInt(),
    ),
    SNOW(
        "snow", "Snowy night", "Falling snow", R.drawable.notif_frame_snow, 0xFF101C30.toInt(),
        0xFFFFFFFF.toInt(), 0xFFCFE0F5.toInt(),
    ),
    CANDLE(
        "candle", "Candlelight", "Flickering flame", R.drawable.notif_frame_candle, 0xFF140C06.toInt(),
        0xFFFFF4E6.toInt(), 0xFFFFC27A.toInt(),
    ),
    FIREFLIES(
        "fireflies", "Enchanted forest", "Drifting fireflies", R.drawable.notif_frame_fireflies, 0xFF0C1A24.toInt(),
        0xFFFFFFFF.toInt(), 0xFFE4F59A.toInt(),
    ),
    LAVENDER(
        "lavender", "Lavender field", "Swaying lavender", R.drawable.notif_frame_lavender, 0xFFF6EEF8.toInt(),
        0xFF3A2547.toInt(), 0xFF7B3FA0.toInt(),
    ),
    CLOUDS(
        "clouds", "Van Gogh's clouds", "Clouds drifting over van Gogh's wheat field", R.drawable.notif_frame_clouds, 0xFFF4F8FC.toInt(),
        0xFF24364A.toInt(), 0xFF3E6E9E.toInt(),
    ),
    WAVES(
        "waves", "Waves at dusk", "Rolling waves", R.drawable.notif_frame_waves, 0xFF10203A.toInt(),
        0xFFFFFFFF.toInt(), 0xFFFFC9A8.toInt(),
    ),
    BAMBOO(
        "bamboo", "Bamboo grove", "Bamboo in the wind", R.drawable.notif_frame_bamboo, 0xFFF2F7EE.toInt(),
        0xFF1F3324.toInt(), 0xFF3F6E33.toInt(),
    ),
    ZEN(
        "zen", "Inside the temple", "Candlelight and incense smoke", R.drawable.notif_frame_zen, 0xFF2A1E14.toInt(),
        0xFFF3EEE4.toInt(), 0xFFF0B98C.toInt(),
    ),
    DROP(
        "drop", "A single drop", "A drop falling into still water", R.drawable.notif_frame_drop, 0xFF0A1A24.toInt(),
        0xFFF2F4F2.toInt(), 0xFFF2B66B.toInt(),
    ),
    BLOSSOM(
        "blossom", "Falling blossoms", "Petals drifting down", R.drawable.notif_frame_blossom, 0xFFFBF1F5.toInt(),
        0xFF4A2A38.toInt(), 0xFFB0446A.toInt(),
    ),
    MOUNTAINS(
        "mountains", "Misty mountains", "Mist drifting through the valleys", R.drawable.notif_frame_mountains, 0xFF2B2A38.toInt(),
        0xFFF4EEF2.toInt(), 0xFFF3C9A4.toInt(),
    ),
    LANTERNS(
        "lanterns", "Floating lanterns", "Lanterns rising over the lake", R.drawable.notif_frame_lanterns, 0xFF141A3A.toInt(),
        0xFFFFF4E6.toInt(), 0xFFFFC27A.toInt(),
    ),
    LEAVES(
        "leaves", "Autumn leaves", "Leaves twirling down", R.drawable.notif_frame_leaves, 0xFF3A2414.toInt(),
        0xFFFFF4E6.toInt(), 0xFFF2B266.toInt(),
    ),
    SEA(
        "sea", "Light under the sea", "Sunbeams and swaying kelp", R.drawable.notif_frame_sea, 0xFF082A40.toInt(),
        0xFFEFFAFF.toInt(), 0xFF9FE3F2.toInt(),
    );

    /** The calm themes drawn by [CalmArt]. */
    val isCalm: Boolean get() = this == SNOW || this == CANDLE || this == FIREFLIES || this == LAVENDER || this == CLOUDS || this == WAVES || this == BAMBOO || this == ZEN

    /** Light themes use dark text. */
    val isLight: Boolean get() = this == BLOSSOM || this == BOOK || this == BUTTERFLY || this == LAVENDER || this == CLOUDS || this == BAMBOO

    companion object {
        fun fromKey(key: String?): AppTheme = entries.firstOrNull { it.key == key } ?: CLASSIC
    }
}

private val Night = Color(0xFF0B1A3D)
private val AuroraNight = Color(0xFF040915)

/** [cardOpacity] (0–1) is how solid the cards behind the text are over the theme's artwork. */
fun AppTheme.colorScheme(cardOpacity: Float): ColorScheme? = when (this) {
    AppTheme.CLASSIC -> null
    AppTheme.BLACK -> darkColorScheme(
        primary = Color(0xFFC9A66B),
        onPrimary = Color.Black,
        primaryContainer = Color(0xFF1A1714),
        onPrimaryContainer = Color(0xFFE2DFD9),
        secondary = Color(0xFFBDB9B2),
        onSecondary = Color.Black,
        secondaryContainer = Color(0xFF141311),
        onSecondaryContainer = Color(0xFFE2DFD9),
        background = Color.Black,
        onBackground = Color(0xFFE2DFD9),
        surface = Color.Black,
        onSurface = Color(0xFFE2DFD9),
        onSurfaceVariant = Color(0xFFBDB9B2),
        surfaceVariant = Color(0xFF141311),
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color.Black,
        surfaceContainer = Color.Black,
        surfaceContainerHigh = Color.Black,
        surfaceContainerHighest = Color.Black,
        outline = Color(0xFF3A3733),
        outlineVariant = Color(0xFF2A2825),
    )
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
    AppTheme.RAIN -> darkColorScheme(
        primary = Color(0xFFC9DCEB),
        onPrimary = Color(0xFF14202A),
        primaryContainer = Color(0xFF3E5566),
        onPrimaryContainer = Color.White,
        secondary = Color(0xFFB7C4CC),
        onSecondary = Color(0xFF1A2228),
        secondaryContainer = Color(0xFF3A4650),
        onSecondaryContainer = Color.White,
        background = Color.Transparent,
        onBackground = Color(0xFFF1F4F6),
        surface = Color(0xFF263038),
        onSurface = Color(0xFFF1F4F6),
        onSurfaceVariant = Color(0xFFD4DCE1),
        surfaceContainerHighest = Color(0xFF263038).copy(alpha = cardOpacity),
        surfaceVariant = Color(0xFF34404A),
        outline = Color(0xFFC9DCEB),
        outlineVariant = Color(0x663E5566),
    )
    AppTheme.RAIN_NIGHT -> darkColorScheme(
        primary = Color(0xFFFFC27A),
        onPrimary = Color(0xFF2B1A05),
        primaryContainer = Color(0xFF5A4030),
        onPrimaryContainer = Color.White,
        secondary = Color(0xFF9CC8EC),
        onSecondary = Color(0xFF0C1D2C),
        secondaryContainer = Color(0xFF2A3A55),
        onSecondaryContainer = Color.White,
        background = Color.Transparent,
        onBackground = Color(0xFFF2EEF0),
        surface = Color(0xFF141A2A),
        onSurface = Color(0xFFF2EEF0),
        onSurfaceVariant = Color(0xFFD2CCD6),
        surfaceContainerHighest = Color(0xFF141A2A).copy(alpha = cardOpacity),
        surfaceVariant = Color(0xFF242C40),
        outline = Color(0xFFFFC27A),
        outlineVariant = Color(0x665A4030),
    )
    AppTheme.SNOW -> calmScheme(true, 0xFFCFE0F5, 0xFF101C30, 0xFFF1F5FB, 0xFFC9D6E6, cardOpacity)
    AppTheme.CANDLE -> calmScheme(true, 0xFFFFC27A, 0xFF140C06, 0xFFFFF4E6, 0xFFE3CDB4, cardOpacity)
    AppTheme.FIREFLIES -> calmScheme(true, 0xFFE4F59A, 0xFF0C1A24, 0xFFEFF6EC, 0xFFC4D6C6, cardOpacity)
    AppTheme.LAVENDER -> calmScheme(false, 0xFF7B3FA0, 0xFFF6EEF8, 0xFF3A2547, 0xFF5E4470, cardOpacity)
    AppTheme.CLOUDS -> calmScheme(false, 0xFF3E6E9E, 0xFFF4F8FC, 0xFF24364A, 0xFF4A5E74, cardOpacity)
    AppTheme.WAVES -> calmScheme(true, 0xFFFFC9A8, 0xFF10203A, 0xFFF4EEF2, 0xFFD6CCD8, cardOpacity)
    AppTheme.BAMBOO -> calmScheme(false, 0xFF3F6E33, 0xFFF2F7EE, 0xFF1F3324, 0xFF45604A, cardOpacity)
    AppTheme.BLOSSOM -> calmScheme(false, 0xFFB0446A, 0xFFFBF1F5, 0xFF4A2A38, 0xFF6E4A5A, cardOpacity)
    AppTheme.MOUNTAINS -> calmScheme(true, 0xFFF3C9A4, 0xFF2B2A38, 0xFFF4EEF2, 0xFFD6CCD6, cardOpacity)
    AppTheme.LANTERNS -> calmScheme(true, 0xFFFFC27A, 0xFF141A3A, 0xFFFFF4E6, 0xFFDCD2C8, cardOpacity)
    AppTheme.LEAVES -> calmScheme(true, 0xFFF2B266, 0xFF3A2414, 0xFFFFF4E6, 0xFFE6D2BC, cardOpacity)
    AppTheme.SEA -> calmScheme(true, 0xFF9FE3F2, 0xFF082A40, 0xFFEFFAFF, 0xFFC4DCE6, cardOpacity)
    AppTheme.DROP -> calmScheme(true, 0xFFF2B66B, 0xFF0A1A24, 0xFFF0F2F0, 0xFFC9D3D6, cardOpacity)
    AppTheme.ZEN -> calmScheme(true, 0xFFF0B98C, 0xFF2A1E14, 0xFFF3EEE4, 0xFFD8C9B2, cardOpacity)
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

/** A colour scheme for a calm theme from its accent, card colour and text colours. */
private fun calmScheme(dark: Boolean, accent: Long, card: Long, text: Long, textVariant: Long, cardOpacity: Float): ColorScheme {
    val a = Color(accent)
    val c = Color(card)
    val on = Color(text)
    val onVariant = Color(textVariant)
    return if (dark) darkColorScheme(
        primary = a, onPrimary = c, primaryContainer = a.copy(alpha = 0.35f), onPrimaryContainer = on,
        secondary = onVariant, onSecondary = c, secondaryContainer = a.copy(alpha = 0.25f), onSecondaryContainer = on,
        background = Color.Transparent, onBackground = on, surface = c, onSurface = on, onSurfaceVariant = onVariant,
        surfaceContainerHighest = c.copy(alpha = cardOpacity), surfaceVariant = c.copy(alpha = 0.8f),
        outline = a, outlineVariant = a.copy(alpha = 0.4f),
    ) else lightColorScheme(
        primary = a, onPrimary = Color.White, primaryContainer = a.copy(alpha = 0.25f), onPrimaryContainer = on,
        secondary = onVariant, onSecondary = Color.White, secondaryContainer = a.copy(alpha = 0.2f), onSecondaryContainer = on,
        background = Color.Transparent, onBackground = on, surface = c, onSurface = on, onSurfaceVariant = onVariant,
        surfaceContainerHighest = c.copy(alpha = cardOpacity), surfaceVariant = c.copy(alpha = 0.8f),
        outline = a, outlineVariant = a.copy(alpha = 0.4f),
    )
}

/** Night themes get a soft shadow under all text so it reads over the painting; the book page gets serif type. */
fun AppTheme.typography(base: Typography): Typography = when (this) {
    AppTheme.CLASSIC -> base
    AppTheme.BLACK -> base.map(headings = { it.copy(fontFamily = FontFamily.Serif) }, all = { it })
    AppTheme.STARRY, AppTheme.AURORA, AppTheme.SPACE, AppTheme.WATER, AppTheme.RAIN, AppTheme.RAIN_NIGHT,
    AppTheme.SNOW, AppTheme.CANDLE, AppTheme.FIREFLIES, AppTheme.WAVES, AppTheme.DROP, AppTheme.MOUNTAINS, AppTheme.LANTERNS, AppTheme.LEAVES, AppTheme.SEA -> base.map(
        headings = { it.copy(fontFamily = FontFamily.Serif) },
        all = { it.copy(shadow = Shadow(Color(0xE6000000), Offset(0f, 2f), 8f)) },
    )
    AppTheme.BOOK -> base.map(headings = { it }, all = { it.copy(fontFamily = FontFamily.Serif) })
    AppTheme.BUTTERFLY, AppTheme.LAVENDER, AppTheme.CLOUDS, AppTheme.BAMBOO, AppTheme.ZEN, AppTheme.BLOSSOM -> base.map(headings = { it.copy(fontFamily = FontFamily.Serif) }, all = { it })
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
        AppTheme.BLACK -> Box(Modifier.fillMaxSize().background(Color.Black))
        AppTheme.STARRY -> StarryBackground(animate)
        AppTheme.AURORA -> AuroraBackground(animate, auroraIntensity)
        AppTheme.BOOK -> CalmBackground(theme, animate)
        AppTheme.BUTTERFLY -> ButterflyBackground(animate)
        AppTheme.SPACE -> SpaceBackground(animate)
        AppTheme.WATER -> WaterBackground(animate)
        AppTheme.DROP -> DropBackground(animate)
        AppTheme.RAIN -> RainBackground(animate, night = false)
        AppTheme.RAIN_NIGHT -> RainBackground(animate, night = true)
        AppTheme.SNOW, AppTheme.CANDLE, AppTheme.FIREFLIES, AppTheme.LAVENDER, AppTheme.CLOUDS, AppTheme.WAVES, AppTheme.BAMBOO, AppTheme.ZEN, AppTheme.BLOSSOM, AppTheme.MOUNTAINS, AppTheme.LANTERNS, AppTheme.LEAVES, AppTheme.SEA -> CalmBackground(theme, animate)
    }
}

@Composable
private fun RainBackground(animate: Boolean, night: Boolean) {
    // If the GPU can't build the shader, show the still rain rather than fail.
    val shader = if (animate && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        remember { runCatching { RuntimeShader(RainArt.SHADER) }.getOrNull() }
    } else {
        null
    }
    if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        RainOnGlass(shader, night)
    } else {
        Image(
            painter = painterResource(if (night) R.drawable.rain_night_still else R.drawable.rain_still),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun RainOnGlass(shader: RuntimeShader, night: Boolean) {
    val scene = ImageBitmap.imageResource(if (night) R.drawable.rain_night_scene else R.drawable.rain_scene)
    val fog = ImageBitmap.imageResource(if (night) R.drawable.rain_night_fog else R.drawable.rain_fog)
    val sceneShader = remember(scene) { BitmapShader(scene.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    val fogShader = remember(fog) { BitmapShader(fog.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    val brush = remember(shader) { ShaderBrush(shader) }
    // A loop of 2π·1000 ms makes the value plain seconds.
    val time by rememberLoopAngle(2000f * PI.toFloat())
    Canvas(Modifier.fillMaxSize()) {
        RainArt.bind(
            shader, sceneShader, fogShader, scene.width, scene.height, size.width, size.height, time,
            streaks = if (night) 0f else 1f, lensLift = if (night) 0.6f else 0f,
        )
        drawRect(brush)
    }
}

@Composable
private fun DropBackground(animate: Boolean) {
    // If the GPU can't build the shader, show the still picture rather than fail.
    val shader = if (animate && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        remember { runCatching { RuntimeShader(DropArt.SHADER) }.getOrNull() }
    } else {
        null
    }
    if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        FallingDrop(shader)
    } else {
        Image(
            painter = painterResource(R.drawable.drop_still),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun FallingDrop(shader: RuntimeShader) {
    val brush = remember(shader) { ShaderBrush(shader) }
    // A loop of 2π·1000 ms makes the value plain seconds.
    val time by rememberLoopAngle(2000f * PI.toFloat())
    Canvas(Modifier.fillMaxSize()) {
        shader.setFloatUniform("res", size.width, size.height)
        shader.setFloatUniform("time", time % 3600f)
        drawRect(brush)
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
private fun CalmBackground(theme: AppTheme, animate: Boolean) {
    // A loop of 2π·1000 ms makes the value plain seconds.
    val time: State<Float> = if (animate) rememberLoopAngle(2000f * PI.toFloat()) else remember(theme) { mutableFloatStateOf(CalmArt.stillTime(theme)) }
    val context = androidx.compose.ui.platform.LocalContext.current
    remember { CalmArt.attach(context) }
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        // CalmArt never leaves the frame blank: a failed frame is retried, then shown as a plain sky.
        drawIntoCanvas { CalmArt.draw(theme, it.nativeCanvas, size.width, size.height, t, density) }
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
    // Read the speed each frame and add up small steps, so changing it never makes the scene jump.
    val speed by rememberUpdatedState(LocalMotionSpeed.current)
    LaunchedEffect(periodMs) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val ms = (now - last) / 1_000_000f
                // At most 30 frames a second: the scene only changes (and redraws) every 33 ms or so, which
                // looks the same for slow, calm motion and roughly halves the drawing work and battery use.
                if (ms >= FRAME_MS) {
                    last = now
                    angle.floatValue += ms.coerceAtMost(100f) * speed / periodMs * 2f * PI.toFloat()
                }
            }
        }
    }
    return angle
}

/** Moving backgrounds advance at most this often (30 frames a second, a little under 33.3 ms for display jitter). */
private const val FRAME_MS = 32f

/** The "Motion speed" setting (0.25–2), read by every moving background. */
val LocalMotionSpeed = compositionLocalOf { 1f }

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
