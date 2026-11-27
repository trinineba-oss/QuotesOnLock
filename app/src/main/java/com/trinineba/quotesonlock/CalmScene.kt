package com.trinineba.quotesonlock

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Calm's own wallpaper. While Calm (or one of its tools) is on screen, the app's background becomes a calm
 * scene: the one matching the sound playing, or the user's chosen scene when nothing plays. Outside Calm the
 * app keeps its own theme.
 */
object CalmScene {
    /** Whether Calm is on screen; set by the Calm page and its tools. */
    val active = MutableStateFlow(false)

    /** The user's wallpaper settings, mirrored here so the theme can react when they change. */
    data class Settings(val mode: String = "follow", val theme: AppTheme = AppTheme.WATER, val still: Boolean = false)

    val settings = MutableStateFlow(Settings())

    fun load(context: Context) {
        val prefs = QuotePrefs(context)
        settings.value = Settings(prefs.calmWallpaper, prefs.calmTheme, prefs.calmStill)
    }

    fun save(context: Context, s: Settings) {
        val prefs = QuotePrefs(context)
        prefs.calmWallpaper = s.mode
        prefs.calmTheme = s.theme
        prefs.calmStill = s.still
        settings.value = s
    }

    /** The scenes Calm can show when nothing is playing. */
    val choices = listOf(
        AppTheme.P_TETONS, AppTheme.P_LAKEDUSK, AppTheme.P_SNOW, AppTheme.P_AUTUMN, AppTheme.P_BIGSUR, AppTheme.L_LANTERNS, AppTheme.L_BLOSSOM, AppTheme.L_MEADOW, AppTheme.L_CANDLE, AppTheme.L_SPACE,
        AppTheme.WATER, AppTheme.DROP, AppTheme.JELLY, AppTheme.SEA, AppTheme.LANTERNS, AppTheme.MOUNTAINS, AppTheme.BLOSSOM, AppTheme.LEAVES, AppTheme.ZEN, AppTheme.WAVES, AppTheme.RAIN, AppTheme.RAIN_NIGHT, AppTheme.SNOW, AppTheme.CANDLE,
        AppTheme.FIREFLIES, AppTheme.LAVENDER, AppTheme.CLOUDS, AppTheme.BAMBOO, AppTheme.AURORA, AppTheme.STARRY, AppTheme.SPACE,
    )

    /**
     * The theme to draw now and whether it moves: the app's own ([base], [baseAnimate]) outside Calm or with
     * the wallpaper off; otherwise the calm scene.
     */
    @Composable
    fun shown(base: AppTheme, baseAnimate: Boolean): Pair<AppTheme, Boolean> {
        val on by active.collectAsState()
        val s by settings.collectAsState()
        val sound by SoundPlayer.state.collectAsState()
        // Pure black is the battery saver: Calm keeps it black and still too.
        if (!on || s.mode == "off" || base == AppTheme.BLACK) return base to baseAnimate
        val scene = if (s.mode == "follow") sound.sound?.takeIf { !sound.forBreathe || sound.playing }?.scene ?: s.theme else s.theme
        return scene to !s.still
    }
}

/** The calm wallpaper's settings: follow the sound, one fixed scene, or off; and whether it moves. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CalmWallpaperSettings() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val s by CalmScene.settings.collectAsState()
    androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
        listOf(
            "follow" to "Follows the sound playing",
            "fixed" to "Always the same scene",
            "off" to "Off: keep my app theme",
        ).forEach { (mode, label) ->
            androidx.compose.foundation.layout.Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                    .clickable(onClickLabel = label) { CalmScene.save(context, s.copy(mode = mode)) },
            ) {
                androidx.compose.material3.RadioButton(selected = s.mode == mode, onClick = { CalmScene.save(context, s.copy(mode = mode)) })
                androidx.compose.material3.Text(label, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            }
        }
        if (s.mode != "off") {
            androidx.compose.material3.Text(
                if (s.mode == "follow") "When nothing is playing:" else "Scene:",
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                modifier = androidx.compose.ui.Modifier.padding(top = 6.dp),
            )
            // The scenes as pictures, three to a row.
            CalmScene.choices.chunked(3).forEach { row ->
                androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    row.forEach { t -> SceneTile(t, s.theme == t, androidx.compose.ui.Modifier.weight(1f)) { CalmScene.save(context, s.copy(theme = t)) } }
                    repeat(3 - row.size) { androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.weight(1f)) }
                }
            }
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.material3.Text(
                    "Keep it still",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    modifier = androidx.compose.ui.Modifier.weight(1f),
                )
                androidx.compose.material3.Switch(checked = s.still, onCheckedChange = { CalmScene.save(context, s.copy(still = it)) })
            }
        }
    }
}

/** One calm scene as a small picture with its name; outlined when chosen. */
@Composable
private fun SceneTile(t: AppTheme, selected: Boolean, modifier: androidx.compose.ui.Modifier, onClick: () -> Unit) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    androidx.compose.foundation.layout.Box(
        modifier.height(80.dp).clip(shape)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClickLabel = "Use ${t.label}", onClick = onClick),
    ) {
        themePicture(t)?.let {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(it), contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = androidx.compose.ui.Modifier.fillMaxSize(),
            )
        }
        Text(
            t.shortLabel,
            style = MaterialTheme.typography.labelSmall.copy(shadow = null),
            color = androidx.compose.ui.graphics.Color.White,
            maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter).fillMaxWidth()
                .background(androidx.compose.ui.graphics.Color(0x99000000)).padding(vertical = 3.dp),
        )
    }
}

/** Solace's own appearance, from the palette button on the Solace tab: which scene sits behind it, and how. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SolaceSceneSheet(onDismiss: () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        androidx.compose.foundation.layout.Column(
            androidx.compose.ui.Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            Text("Solace's scene", style = MaterialTheme.typography.headlineSmall)
            Text(
                "The calm picture behind Solace, separate from the app's own theme.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CalmWallpaperSettings()
        }
    }
}
