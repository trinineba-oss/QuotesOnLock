package com.trinineba.quotesonlock

import android.speech.tts.Voice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val SAMPLE = Quote(
    text = "The soul is dyed by the colour of its thoughts.",
    author = "Marcus Aurelius",
)

/** A readable name for a voice: its language and region, numbered when there are several, and how it sounds. */
private fun voiceLabel(voice: Voice, all: List<Voice>): String {
    val same = all.filter { it.locale == voice.locale }
    val base = voice.locale.displayName + if (same.size > 1) " · ${same.indexOf(voice) + 1}" else ""
    val notes = listOfNotNull(
        "natural".takeIf { voice.quality >= Voice.QUALITY_HIGH },
        "online".takeIf { voice.isNetworkConnectionRequired },
    )
    return if (notes.isEmpty()) base else "$base (${notes.joinToString(", ")})"
}

/** Appearance › Read aloud: speed, voice, the lock-screen button and a sample. */
@Composable
fun ReadAloudSettings(prefs: QuotePrefs, onLockScreenChanged: () -> Unit) {
    val context = LocalContext.current
    var rate by remember { mutableFloatStateOf(prefs.voiceRate) }
    var voiceName by remember { mutableStateOf(prefs.voiceName) }
    var notifRead by remember { mutableStateOf(prefs.notifRead) }
    var voices by remember { mutableStateOf(emptyList<Voice>()) }
    var menu by remember { mutableStateOf(false) }
    val speaking by QuoteVoice.speaking.collectAsState()
    LaunchedEffect(Unit) { QuoteVoice.warmUp(context) { voices = QuoteVoice.voices() } }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Tap the speaker beside a quote to hear it, using your phone's own voices. The words light up as they are read. " +
                "For the most natural sound, pick a voice marked natural; voices marked online sound best but need the internet. " +
                "More voices: Voice settings › Speech Services by Google › Install voice data.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Speed", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(78.dp))
            Slider(
                value = rate,
                onValueChange = { rate = it },
                onValueChangeFinished = { prefs.voiceRate = rate },
                valueRange = 0.5f..1.3f,
                modifier = Modifier.weight(1f),
            )
            Text("%.2f×".format(rate), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(52.dp), textAlign = TextAlign.End)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Voice", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(78.dp))
            Box(Modifier.weight(1f)) {
                val current = voices.firstOrNull { it.name == voiceName }
                TextButton(onClick = { menu = true }, enabled = voices.isNotEmpty()) {
                    Text(
                        when {
                            voices.isEmpty() -> "Best available"
                            current != null -> voiceLabel(current, voices)
                            else -> "Best available"
                        } + " ▾",
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Best available (automatic)") }, onClick = {
                        voiceName = ""; prefs.voiceName = ""; menu = false
                        QuoteVoice.speak(context, SAMPLE)
                    })
                    voices.forEach { v ->
                        DropdownMenuItem(text = { Text(voiceLabel(v, voices)) }, onClick = {
                            voiceName = v.name; prefs.voiceName = v.name; menu = false
                            QuoteVoice.speak(context, SAMPLE)
                        })
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Read aloud on the lock-screen card")
                Text("Puts a \"Read aloud\" button on the card in place of Copy.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = notifRead, onCheckedChange = {
                notifRead = it
                prefs.notifRead = it
                onLockScreenChanged()
            })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { QuoteVoice.toggle(context, SAMPLE) }) {
                Text(if (speaking == SAMPLE.text) "Stop" else "Try it")
            }
            TextButton(onClick = { QuoteVoice.openSettings(context) }) { Text("Voice settings") }
        }
    }
}
