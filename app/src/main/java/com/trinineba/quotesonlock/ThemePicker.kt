package com.trinineba.quotesonlock

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** The themes grouped by mood, for the picker. A theme added later and not listed here appears under "More". */
object ThemeGroups {
    val groups: List<Pair<String, List<AppTheme>>> by lazy {
        val listed = listOf(
            "Painted" to listOf(AppTheme.P_TETONS, AppTheme.P_MAROON, AppTheme.P_BIGSUR, AppTheme.P_MONUMENT, AppTheme.P_LAKEDUSK, AppTheme.P_AUTUMN, AppTheme.P_SNOW, AppTheme.P_SUNFLOWERS, AppTheme.P_PLAINS),
            "Living paintings" to listOf(AppTheme.LAVENDER, AppTheme.SNOW, AppTheme.WAVES, AppTheme.L_BLOSSOM, AppTheme.L_LANTERNS, AppTheme.L_MEADOW, AppTheme.L_CANDLE, AppTheme.L_SPACE),
            "Night skies" to listOf(AppTheme.STARRY, AppTheme.AURORA, AppTheme.SPACE, AppTheme.LANTERNS),
            "Water" to listOf(AppTheme.WATER, AppTheme.RAIN, AppTheme.RAIN_NIGHT, AppTheme.DROP, AppTheme.JELLY, AppTheme.SEA),
            "Nature" to listOf(AppTheme.BUTTERFLY, AppTheme.FIREFLIES, AppTheme.BAMBOO, AppTheme.BLOSSOM, AppTheme.MOUNTAINS, AppTheme.LEAVES),
            "Art & quiet" to listOf(AppTheme.CLOUDS, AppTheme.ZEN, AppTheme.CANDLE, AppTheme.BOOK, AppTheme.VITRUVIAN),
            "Plain" to listOf(AppTheme.CLASSIC, AppTheme.BLACK),
        )
        val rest = AppTheme.entries.filter { t -> listed.none { t in it.second } }
        if (rest.isEmpty()) listed else listed + ("More" to rest)
    }

    /** Light themes show dark text. */
    fun light(t: AppTheme): Boolean = t.isLight || t == AppTheme.CLASSIC

    fun moving(t: AppTheme): Boolean = t.motionLabel != null

    /** The last few app themes chosen, newest first, kept in prefs. */
    fun recent(prefs: QuotePrefs): List<AppTheme> =
        (prefs.raw.getString("recent_themes", "") ?: "").split(',').mapNotNull { k -> AppTheme.entries.firstOrNull { it.key == k } }

    fun remember(prefs: QuotePrefs, t: AppTheme) {
        val list = (listOf(t) + recent(prefs).filter { it != t }).take(3)
        prefs.raw.edit().putString("recent_themes", list.joinToString(",") { it.key }).apply()
    }
}

/** One theme as a tile: its picture, a sample quote in its own colours, and its name. [option] null means "same as the app". */
@Composable
private fun ThemeTile(option: AppTheme?, selected: Boolean, sameAs: AppTheme?, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val shown = option ?: sameAs
    val light = shown?.let { ThemeGroups.light(it) } ?: false
    Box(
        modifier.height(96.dp).clip(shape)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClickLabel = "Use ${option?.label ?: "the same theme as the app"}", onClick = onClick),
    ) {
        val image = shown?.let { themePicture(it) }
        if (image != null) {
            Image(painterResource(image), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFE8E4F4), Color(0xFFCFD6EE)))))
        }
        if (option == null) Box(Modifier.fillMaxSize().background(Color(0x66000000)))
        // The picture speaks for itself; only "same as the app" needs words on it.
        if (option == null) Text(
            "Same as\nthe app",
            fontFamily = BookSerif, fontSize = 13.sp, lineHeight = 16.sp, textAlign = TextAlign.Center,
            color = Color.White,
            modifier = Modifier.align(Alignment.Center).padding(bottom = 14.dp),
        )
        Text(
            option?.shortLabel ?: (sameAs?.shortLabel ?: "Same"),
            style = MaterialTheme.typography.labelSmall.copy(shadow = null),
            color = if (option != null && light) Color(0xFF2E2012) else Color.White,
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(if (option != null && light) Color(0x88FFFFFF) else Color(0x99000000)).padding(vertical = 4.dp),
        )
    }
}

@Composable
private fun TileRows(items: List<AppTheme?>, selected: AppTheme?, sameAs: AppTheme?, onPick: (AppTheme?) -> Unit) {
    items.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { t -> ThemeTile(t, selected == t, sameAs, Modifier.weight(1f)) { onPick(t) } }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/**
 * The theme picker: filters (all, light, dark, moving, still), the themes grouped by mood with a sample quote on each,
 * recently used ones first, and an undo after each change. With [allowSame] (lock screen, widget) the first choice is
 * "same as the app", which is [selected] null. [appTheme] is what "same as the app" currently means.
 */
@Composable
fun ThemePicker(
    selected: AppTheme?,
    appTheme: AppTheme,
    recent: List<AppTheme>,
    allowSame: Boolean,
    onPick: (AppTheme?) -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf("all") }
    var undo by remember { mutableStateOf<Pair<AppTheme?, String>?>(null) }
    LaunchedEffect(undo) { if (undo != null) { delay(6000); undo = null } }
    fun pick(t: AppTheme?) {
        if (t == selected) return
        undo = selected to (t?.label ?: "the same theme as the app")
        onPick(t)
    }
    val keep: (AppTheme) -> Boolean = { t ->
        when (filter) {
            "light" -> ThemeGroups.light(t)
            "dark" -> !ThemeGroups.light(t)
            "moving" -> ThemeGroups.moving(t)
            "still" -> !ThemeGroups.moving(t)
            else -> true
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        undo?.let { (before, now) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.inverseSurface).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Now using $now", color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { onPick(before); undo = null }) { Text("Undo", color = MaterialTheme.colorScheme.inversePrimary) }
            }
        }
        if (allowSame) TileRows(listOf(null), selected, appTheme, ::pick)
        val recentShown = recent.filter(keep).take(3)
        if (!allowSame && recentShown.isNotEmpty() && filter == "all") {
            Text("RECENTLY USED", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.primary)
            TileRows(recentShown, selected, appTheme, ::pick)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("all" to "All", "light" to "Light", "dark" to "Dark", "moving" to "Moving", "still" to "Still").forEach { (k, label) ->
                FilterChip(selected = filter == k, onClick = { filter = k }, label = { Text(label) })
            }
        }
        ThemeGroups.groups.forEach { (name, themes) ->
            val shown = themes.filter(keep)
            if (shown.isNotEmpty()) {
                Text(name.uppercase(), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                TileRows(shown, selected, appTheme, ::pick)
            }
        }
    }
}

/** A line for the lock screen or widget: what it uses now; tapping opens the same picker below it. */
@Composable
fun ThemePlaceRow(title: String, current: AppTheme?, appTheme: AppTheme, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClickLabel = if (open) "Close" else "Choose a theme for $title", onClick = onToggle).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(current?.label ?: "Same as the app (${appTheme.label})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(if (open) "⌃" else "›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}
