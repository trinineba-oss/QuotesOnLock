package com.trinineba.quotesonlock

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Talk to someone, by text if you prefer.
 *
 * Free, confidential crisis services that people can reach by text or online chat as well as by phone, so nobody
 * has to make a call to get help. Texting comes first, then chat, then calling. The country is taken from the
 * phone's SIM (no permission needed) and can be changed; every other country is pointed to findahelpline.com.
 *
 * The app never sends or connects anything itself: texting opens the phone's own messages app with the number
 * (and any first word) filled in, chat opens the browser, and calling opens the dialler with the number ready.
 *
 * Each service was checked against its official website on 28 November 2026 (see SOURCES.md, "Talk to someone").
 */

/** One way to reach a service. [body] is the first message to fill in, if the service asks for a word. */
sealed class Reach {
    class Text(val name: String, val number: String, val shown: String, val body: String?, val note: String) : Reach()
    class Chat(val name: String, val url: String, val note: String) : Reach()
    class Call(val name: String, val number: String, val shown: String) : Reach()
}

class HelpCountry(val code: String, val name: String, val ways: List<Reach>)

object Helplines {
    val countries = listOf(
        HelpCountry(
            "US", "United States",
            listOf(
                Reach.Text("988 Lifeline", "988", "988", null, "Opens your messages with 988 filled in. You can just send “hi”, and someone will write back. Free, private, any time; in English or Spanish."),
                Reach.Chat("988 Lifeline", "https://chat.988lifeline.org/", "988 Lifeline chat, in your browser."),
                Reach.Text("Crisis Text Line", "741741", "741741", "HOME", "Another free, private text service, any time. Opens your messages with HOME ready to send."),
                Reach.Call("988 Lifeline", "988", "988"),
            ),
        ),
        HelpCountry(
            "CA", "Canada",
            listOf(
                Reach.Text("9-8-8", "988", "988", null, "Opens your messages with 988 filled in. You can just send “hi”, and someone will write back. Free, private, any time."),
                Reach.Call("9-8-8", "988", "988"),
            ),
        ),
        HelpCountry(
            "GB", "United Kingdom",
            listOf(
                Reach.Text("Shout", "85258", "85258", "SHOUT", "Shout: free, private, any time. Opens your messages with SHOUT ready to send."),
                Reach.Call("Samaritans", "116123", "116 123"),
            ),
        ),
        HelpCountry(
            "IE", "Ireland",
            listOf(
                Reach.Text("50808", "50808", "50808", null, "Free, private, any time. Opens your messages with 50808 filled in: send any message, like “hello”, and someone will write back."),
                Reach.Call("Samaritans", "116123", "116 123"),
            ),
        ),
        HelpCountry(
            "AU", "Australia",
            listOf(
                Reach.Text("Lifeline", "0477131114", "0477 13 11 14", null, "Lifeline: free, private, any time. Opens your messages with the number filled in. You can just send “hi”."),
                Reach.Chat("Lifeline", "https://www.lifeline.org.au/crisis-chat/", "Lifeline's crisis chat, in your browser."),
                Reach.Call("Lifeline", "131114", "13 11 14"),
            ),
        ),
        HelpCountry(
            "NZ", "New Zealand",
            listOf(
                Reach.Text("1737", "1737", "1737", null, "Free, private, any time. Opens your messages with 1737 filled in. You can just send “hi”."),
                Reach.Call("1737", "1737", "1737"),
            ),
        ),
    )

    const val ELSEWHERE_URL = "https://findahelpline.com/"

    fun byCode(code: String?): HelpCountry? = countries.firstOrNull { it.code.equals(code, ignoreCase = true) || (it.code == "GB" && code.equals("UK", true)) }

    /** The country to show: the one chosen before, else the SIM's or network's country, else the phone's region. */
    fun detect(context: Context): String? {
        val chosen = context.getSharedPreferences("quotesonlock", Context.MODE_PRIVATE).getString("help_country", null)
        if (chosen != null) return chosen
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val sim = runCatching { tm?.simCountryIso }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: runCatching { tm?.networkCountryIso }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: java.util.Locale.getDefault().country.takeIf { it.isNotBlank() }
        return sim?.uppercase()?.let { if (byCode(it) != null) byCode(it)!!.code else "OTHER" }
    }

    fun choose(context: Context, code: String) {
        context.getSharedPreferences("quotesonlock", Context.MODE_PRIVATE).edit().putString("help_country", code).apply()
    }

    fun text(context: Context, r: Reach.Text) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + r.number)).apply { r.body?.let { putExtra("sms_body", it) } }
        open(context, intent, "No messages app found. Text ${r.body?.let { "$it to " } ?: ""}${r.shown}.")
    }

    fun call(context: Context, r: Reach.Call) =
        open(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + r.number)), "No phone app found. The number is ${r.shown}.")

    fun browse(context: Context, url: String) = open(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)), "No browser found. The address is $url")

    private fun open(context: Context, intent: Intent, fallback: String) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, fallback, Toast.LENGTH_LONG).show()
        }
    }
}

/** The quiet way in: a small card, never a pop-up. [onDark] for the night-blue of "It's too much right now". */
@Composable
fun TalkCard(onDark: Boolean = false, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    val ink = if (onDark) Color.White else MaterialTheme.colorScheme.onSurface
    val soft = if (onDark) Color(0xFFAEB8CC) else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier.fillMaxWidth().clip(shape)
            .background(if (onDark) Color.White.copy(alpha = 0.07f) else MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(1.dp, if (onDark) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), shape)
            .clickable(onClickLabel = "Talk to someone") { open = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("Talk to someone, by text", color = ink, style = MaterialTheme.typography.titleSmall)
        Text("Kind, trained people, free and private. No need to call.", color = soft, style = MaterialTheme.typography.bodySmall)
    }
    if (open) TalkSheet { open = false }
}

/** The sheet: for the chosen country, texting first, then chat, then calling; or findahelpline.com elsewhere. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TalkSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var code by remember { mutableStateOf(Helplines.detect(context) ?: "OTHER") }
    var choosing by remember { mutableStateOf(false) }
    val country = Helplines.byCode(code)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Talk to someone", fontFamily = BookSerif, fontSize = 26.sp)
            Text(
                "Kind, trained people are there day and night. It's free and private, and you can text: there's no need to call.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (choosing || country == null && code != "OTHER") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (Helplines.countries.map { it.code to it.name } + ("OTHER" to "Somewhere else")).forEach { (c, name) ->
                        FilterChip(selected = code == c, onClick = { code = c; Helplines.choose(context, c); choosing = false }, label = { Text(name) })
                    }
                }
            }
            if (country != null) {
                val texts = country.ways.filterIsInstance<Reach.Text>()
                texts.forEachIndexed { i, t ->
                    WayCard(
                        title = "Text " + (t.body?.let { "$it to " } ?: "") + t.shown + if (i > 0) " · ${t.name}" else "",
                        note = t.note,
                        action = "Text now",
                        main = i == 0,
                    ) { Helplines.text(context, t) }
                    // Chat sits right after the first way to text.
                    if (i == 0) country.ways.filterIsInstance<Reach.Chat>().forEach { ch ->
                        WayCard("Chat online", ch.note, "Open chat", main = false) { Helplines.browse(context, ch.url) }
                    }
                }
                country.ways.filterIsInstance<Reach.Call>().forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Prefer to talk?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { Helplines.call(context, c) }) {
                            Text(if (c.name.contains(c.shown) || c.name.first().isDigit()) "Call ${c.shown}" else "Call ${c.name}, ${c.shown}")
                        }
                    }
                }
            }
            if (country == null || choosing) {
                WayCard(
                    "Somewhere else?",
                    "findahelpline.com lists free services in over 130 countries, and shows which ones you can text or chat with.",
                    "Find one",
                    main = country == null,
                ) { Helplines.browse(context, Helplines.ELSEWHERE_URL) }
            }
            if (!choosing) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(country?.name ?: "Somewhere else", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { choosing = true }) { Text("Change country") }
            }
            Text(
                "If you might hurt yourself or someone else, call your local emergency number now.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WayCard(title: String, note: String, action: String, main: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (main) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, if (main) accent.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (main) Button(onClick = onClick) { Text(action) } else OutlinedButton(onClick = onClick) { Text(action) }
    }
}
