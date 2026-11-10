package com.trinineba.quotesonlock

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** "X:YY left" for a sound's timer, or null when it has none. Paused sounds carry their time left as a negative endsAt. */
fun soundTimeLeft(state: SoundState, now: Long): String? {
    val left = when {
        state.endsAt > 0 -> state.endsAt - now
        state.endsAt < 0 -> -state.endsAt
        else -> return null
    }.coerceAtLeast(0L) / 1000
    return "%d:%02d left".format(left / 60, left % 60)
}

/**
 * Calming sounds: tap sounds to build a mix of up to three, each with its own volume; save mixes by name;
 * set the overall volume and when it should stop.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SoundsScreen(prefs: QuotePrefs, onBack: () -> Unit) {
    val context = LocalContext.current
    val state by SoundPlayer.state.collectAsState()
    var volume by remember { mutableFloatStateOf(prefs.soundVolume) }
    var timer by remember { mutableIntStateOf(prefs.soundTimer) }
    var saved by remember { mutableStateOf(prefs.savedMixes) }
    var naming by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.playing) {
        while (state.playing) {
            now = android.os.SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    val mix = state.layers.takeIf { !state.forBreathe }.orEmpty()
    ToolScreen("Sounds", onBack) {
        item(key = "s-intro") {
            Text(
                "Tap a sound to play it; tap others to layer up to three. Each has its own volume.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (mix.isNotEmpty()) item(key = "s-mix") {
            SoftCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                                .clickable(onClickLabel = if (state.playing) "Pause" else "Play") { SoundPlayer.togglePlaying(context) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(if (state.playing) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                                contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp),
                            )
                        }
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(if (mix.size == 1) "Playing" else "Your mix", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(
                                    if (state.playing) null else "Paused",
                                    soundTimeLeft(state, now),
                                    "keeps playing with the screen off".takeIf { state.playing },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = "Stop") { SoundPlayer.stop(context) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(painterResource(R.drawable.ic_stop), contentDescription = null) }
                    }
                    mix.forEach { layer ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                            Text(layer.sound.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(118.dp), maxLines = 2)
                            Slider(
                                value = layer.volume,
                                onValueChange = { SoundPlayer.setLayerVolume(context, layer.sound, it) },
                                valueRange = 0.05f..1f,
                                modifier = Modifier.weight(1f),
                            )
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).clickable(onClickLabel = "Take ${layer.sound.label} out of the mix") {
                                    SoundPlayer.toggleLayer(context, layer.sound)
                                },
                                contentAlignment = Alignment.Center,
                            ) { Text("✕", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { naming = true }) { Text("Save this mix") }
                    }
                }
            }
        }
        Soundscape.entries.groupBy { it.kind }.forEach { (kind, list) ->
            item(key = "h-${kind.name}") {
                Text(kind.heading, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            list.chunked(2).forEach { pair ->
                item(key = "s-" + pair.first().key) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { s ->
                            val inMix = mix.any { it.sound == s }
                            SoundCard(s, chosen = inMix, playing = inMix && state.playing, Modifier.weight(1f)) {
                                if (!SoundPlayer.toggleLayer(context, s)) {
                                    android.widget.Toast.makeText(context, "A mix can hold three sounds. Take one out first.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        if (pair.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }
        }
        if (saved.isNotEmpty()) item(key = "s-saved") {
            SoftCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Saved mixes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    saved.forEachIndexed { i, m ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClickLabel = "Play ${m.name}") { SoundPlayer.playMix(context, m.layers) }
                            .padding(vertical = 8.dp)) {
                            Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(m.name, style = MaterialTheme.typography.titleSmall)
                                Text(SoundMix.label(m.layers), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).clickable(onClickLabel = "Delete ${m.name}") {
                                    saved = saved.filterIndexed { j, _ -> j != i }; prefs.savedMixes = saved
                                },
                                contentAlignment = Alignment.Center,
                            ) { Text("✕", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
        item(key = "s-volume") {
            SoftCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Overall volume", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Slider(
                        value = volume,
                        onValueChange = { volume = it; prefs.soundVolume = it; SoundPlayer.settingsChanged(context) },
                        valueRange = 0.05f..1f,
                    )
                }
            }
        }
        item(key = "s-timer") {
            SoftCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Stop after", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15 to "15 min", 30 to "30 min", 45 to "45 min", 60 to "1 hour", 90 to "1½ hours", 0 to "Keep playing").forEach { (m, label) ->
                            FilterChip(
                                selected = timer == m,
                                onClick = { timer = m; prefs.soundTimer = m; SoundPlayer.settingsChanged(context) },
                                label = { Text(label) },
                            )
                        }
                    }
                    Text(
                        if (timer > 0) "Fades out slowly over the last minute." else "Plays until you stop it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item(key = "s-note") {
            Text(
                "Nature sounds are real recordings in the public domain (see About › Sources), made into long seamless loops. " +
                    "Brown noise and the singing bowls are made by the app. The music starts again after a short pause.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (naming) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("Save this mix") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, singleLine = true, label = { Text("Name, e.g. Reading by the fire") })
            },
            confirmButton = {
                TextButton(onClick = {
                    val layers = SoundPlayer.state.value.layers
                    if (layers.isNotEmpty()) {
                        saved = saved + SavedMix(name.trim().ifBlank { SoundMix.label(layers) }, layers)
                        prefs.savedMixes = saved
                    }
                    naming = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SoundCard(sound: Soundscape, chosen: Boolean, playing: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .height(104.dp)
            .clip(shape)
            .then(if (chosen) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClickLabel = if (playing) "Pause ${sound.label}" else "Play ${sound.label}", onClick = onClick),
    ) {
        Image(painterResource(sound.art), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.65f))))
        Box(
            Modifier.align(Alignment.TopEnd).padding(8.dp).size(32.dp).clip(CircleShape)
                .background(if (playing) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                contentDescription = null,
                tint = if (playing) MaterialTheme.colorScheme.onPrimary else Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            sound.label,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
        )
    }
}
