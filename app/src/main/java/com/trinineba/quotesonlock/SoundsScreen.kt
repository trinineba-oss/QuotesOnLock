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

/** Calming sounds: tap a sound to play it, set the volume and when it should stop. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SoundsScreen(prefs: QuotePrefs, onBack: () -> Unit) {
    val context = LocalContext.current
    val state by SoundPlayer.state.collectAsState()
    var volume by remember { mutableFloatStateOf(prefs.soundVolume) }
    var timer by remember { mutableIntStateOf(prefs.soundTimer) }
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.playing) {
        while (state.playing) {
            now = android.os.SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    ToolScreen("Calming sounds", onBack) {
        Soundscape.entries.chunked(2).forEach { pair ->
            item(key = "s-" + pair.first().key) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { s ->
                        SoundCard(s, chosen = state.sound == s, playing = state.sound == s && state.playing, Modifier.weight(1f)) {
                            SoundPlayer.toggle(context, s)
                        }
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
        item(key = "s-volume") {
            SoftCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Volume", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
                        listOf(15 to "15 min", 30 to "30 min", 60 to "1 hour", 0 to "Keep playing").forEach { (m, label) ->
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
        state.sound?.let { s ->
            item(key = "s-now") {
                SoftCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                                .clickable(onClickLabel = if (state.playing) "Pause" else "Play") { SoundPlayer.toggle(context, s) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(if (state.playing) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                                contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp),
                            )
                        }
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(s.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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
                }
            }
        }
        item(key = "s-note") {
            Text(
                "Recordings are public domain or CC0 (see About › Sources); the wind is made by the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
