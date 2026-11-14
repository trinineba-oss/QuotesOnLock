package com.trinineba.quotesonlock

import android.Manifest
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Typography
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/* Building blocks shared by the app's pages: cards, tiles, sliders, theme previews and the sources reader. */

internal const val FEEDBACK_URL = "https://github.com/trinineba-oss/QuotesOnLock/issues/new"

/** One step of the first-launch welcome: an icon, a title, a line of explanation and its controls. */
@Composable
internal fun WelcomeStep(@DrawableRes icon: Int, title: String, body: String, content: @Composable ColumnScope.() -> Unit) {
    SoftCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
            Column(Modifier.padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                content()
            }
        }
    }
}

internal class Tile(@DrawableRes val icon: Int, val title: String, val subtitle: String, val onClick: () -> Unit)

/** A round icon button with a small label underneath, for the actions under the home quote. */
@Composable
internal fun QuoteAction(
    @DrawableRes icon: Int,
    label: String,
    enabled: Boolean = true,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val color = (tint ?: MaterialTheme.colorScheme.primary).copy(alpha = if (enabled) 1f else 0.38f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(painterResource(icon), contentDescription = label, tint = color, modifier = Modifier.size(22.dp)) }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 4.dp).widthIn(max = 64.dp),
        )
    }
}

@Composable
internal fun TileCard(tile: Tile, modifier: Modifier, wide: Boolean = false) {
    SoftCard(onClick = tile.onClick, modifier = modifier.heightIn(min = if (wide) 64.dp else 104.dp)) {
        if (wide) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(tile.icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                Column(Modifier.padding(start = 14.dp)) {
                    Text(tile.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(tile.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Column(Modifier.padding(16.dp)) {
                Icon(painterResource(tile.icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                Spacer(Modifier.height(10.dp))
                Text(tile.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    tile.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

/** The picture used for a theme's preview tile and lock-screen frame (null: Classic, drawn as a gradient). */
@DrawableRes
internal fun themePicture(option: AppTheme): Int? = when (option) {
    AppTheme.STARRY -> R.drawable.starry_frame
    AppTheme.AURORA -> R.drawable.aurora_frame
    AppTheme.BOOK -> R.drawable.paper
    AppTheme.BUTTERFLY -> R.drawable.butterfly_frame
    AppTheme.SPACE -> R.drawable.space_frame
    AppTheme.WATER -> R.drawable.water_frame
    AppTheme.RAIN -> R.drawable.rain_frame
    AppTheme.RAIN_NIGHT -> R.drawable.rain_night_frame
    AppTheme.SNOW -> R.drawable.snow_frame
    AppTheme.CANDLE -> R.drawable.candle_frame
    AppTheme.FIREFLIES -> R.drawable.fireflies_frame
    AppTheme.LAVENDER -> R.drawable.lavender_frame
    AppTheme.CLOUDS -> R.drawable.clouds_frame
    AppTheme.WAVES -> R.drawable.waves_frame
    AppTheme.BAMBOO -> R.drawable.bamboo_frame
    AppTheme.ZEN -> R.drawable.zen_frame
    AppTheme.DROP -> R.drawable.drop_frame
    AppTheme.BLOSSOM -> R.drawable.blossom_frame
    AppTheme.MOUNTAINS -> R.drawable.mountains_frame
    AppTheme.LANTERNS -> R.drawable.lanterns_frame
    AppTheme.LEAVES -> R.drawable.leaves_frame
    AppTheme.SEA -> R.drawable.sea_frame
    AppTheme.BLACK -> R.drawable.black_frame
    AppTheme.CLASSIC -> null
}

/** A small lock-screen theme tile; [option] null means "same as the app". */
@Composable
internal fun LockThemeTile(option: AppTheme?, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClickLabel = "Lock screen: ${option?.label ?: "same as app"}", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val image = option?.let { themePicture(it) }
        when {
            image != null -> Image(painterResource(image), contentDescription = option.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            option == AppTheme.CLASSIC -> Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFE8E4F4), Color(0xFFCFD6EE)))))
            else -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Text("Same", style = MaterialTheme.typography.labelSmall.copy(shadow = null), color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** A slider with its label on the left and the current value on the right. */
@Composable
internal fun LabeledSlider(
    label: String,
    value: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(78.dp))
        Slider(value = current, onValueChange = onChange, onValueChangeFinished = onDone, valueRange = range, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(48.dp), textAlign = TextAlign.End)
    }
}

/** A small picture of each theme to tap. */
@Composable
internal fun ThemePreview(option: AppTheme, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .height(76.dp)
            .clip(shape)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClickLabel = "Use ${option.label}", onClick = onClick),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val image = themePicture(option)
        if (image != null) {
            Image(painterResource(image), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFE8E4F4), Color(0xFFCFD6EE)))))
        }
        Text(
            option.label,
            style = MaterialTheme.typography.labelSmall.copy(shadow = null),
            color = if (option == AppTheme.CLASSIC || option.isLight) Color(0xFF2E2012) else Color.White,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (option == AppTheme.CLASSIC || option.isLight) Color(0x66FFFFFF) else Color(0x88000000)
                )
                .padding(vertical = 4.dp),
            textAlign = TextAlign.Center,
        )
    }
}

/** Opens Android's live-wallpaper preview with ours selected; the user confirms with "Set wallpaper". */
internal fun openWallpaperPicker(context: Context) {
    val component = ComponentName(context, QuoteWallpaperService::class.java)
    val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
    try {
        context.startActivity(direct)
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "This phone doesn't support live wallpapers.", Toast.LENGTH_LONG).show()
        }
    }
}

/** SOURCES.md, flattened for the About page: 0 = heading, 1 = text, 2 = list entry. */
internal fun readSources(context: Context): List<Pair<Int, String>> {
    fun clean(s: String) = s.replace("**", "").replace("`", "").replace("*", "").replace("_", " ").trim()
    val out = mutableListOf<Pair<Int, String>>()
    context.resources.openRawResource(R.raw.sources).bufferedReader().useLines { lines ->
        lines.map { it.trim() }.forEach { l ->
            when {
                l.isEmpty() -> out.add(-1 to "")
                l.matches(Regex("^\\|[\\s:|-]+\\|$")) -> Unit
                l.startsWith("#") -> out.add(0 to clean(l.trimStart('#')))
                l.startsWith("|") -> {
                    val cells = l.trim('|').split("|").map { clean(it) }.filter { it.isNotEmpty() }
                    if (cells.firstOrNull() != "Author") out.add(2 to cells.joinToString(" · "))
                }
                else -> {
                    // Join wrapped paragraph lines.
                    val last = out.lastOrNull()
                    if (last != null && last.first == 1) out[out.size - 1] = 1 to (last.second + " " + clean(l))
                    else out.add(1 to clean(l))
                }
            }
        }
    }
    return out.filter { it.first >= 0 }
}

internal val HEX_COLOR = Regex("[0-9A-Fa-f]{6}")

internal fun fontFamilyOf(font: QuoteFont) = FontFamily(Typeface.create(font.family, Typeface.NORMAL))

@Composable
internal fun ColorSwatch(label: String, color: Color?, selected: Boolean, onClick: () -> Unit) {
    val ring = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color ?: MaterialTheme.colorScheme.surfaceVariant)
            .border(if (selected) 3.dp else 1.dp, ring, CircleShape)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (color == null) Text("A", style = MaterialTheme.typography.labelLarge)
    }
}

/** A large card for one of Discover's journeys, tinted with its own colour. */
@Composable
internal fun JourneyCard(title: String, subtitle: String, tint: Color, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        modifier = modifier,
    ) {
        Box(
            Modifier
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(tint.copy(alpha = 0.55f), MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f)),
                    ),
                )
                .heightIn(min = 76.dp)
                .padding(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** The round back arrow at the top of inner pages. */
@Composable
internal fun BackArrow(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f))
            .clickable(onClickLabel = "Back", onClick = onBack),
        contentAlignment = Alignment.Center,
    ) { androidx.compose.material3.Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
}
