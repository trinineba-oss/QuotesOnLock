package com.trinineba.quotesonlock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A short practice from the app's own thinkers: a real line (word for word from its source), a few words on
 * how to use it, optional steps read aloud, and questions whose answers go to the Journal.
 */
data class Practice(
    val key: String,
    val title: String,
    val subtitle: String,
    val quote: String?,
    val author: String?,
    val intro: String,
    val spoken: List<String> = emptyList(),
    val questions: List<String>,
    val minutes: Int,
)

object Practices {
    val all = listOf(
        Practice(
            "evening", "Evening review", "Seneca's three questions",
            "I make use of this privilege, and daily plead my cause before myself: when the lamp is taken out of my sight, and my wife, " +
                "who knows my habit, has ceased to talk, I pass the whole day in review before myself, and repeat all that I have said and done: " +
                "I conceal nothing from myself, and omit nothing",
            "Seneca, On Anger (tr. Aubrey Stewart)",
            "A Stoic practice for the end of the day. Look back over it kindly, as a fair judge, not a harsh one. Short answers are fine, and you can skip any question.",
            questions = listOf("What did I do well today?", "Where did I fall short, and why?", "What will I do differently tomorrow?"),
            minutes = 5,
        ),
        Practice(
            "above", "The view from above", "Marcus Aurelius",
            "Look down, as from some eminence, upon the innumerable herds, the countless solemn festivals, the voyaging of every sort, in tempests and in calms; " +
                "the different states of those who come into life, enter upon life's associations, and leave it in the end.",
            "Marcus Aurelius, Meditations",
            "Marcus Aurelius often imagined looking down on the world from a great height, to see his own troubles at their true size. Listen, and let your imagination rise.",
            spoken = listOf(
                "Close your eyes, and take a slow breath.",
                "Picture yourself where you are now, as if from the ceiling, looking down.",
                "Now rise higher, above the building. See the roofs, the streets, the people going about their day.",
                "Higher still. The whole town, then the country, rivers and roads like threads.",
                "Now the whole earth, turning slowly in the dark. Every life on it, all at once.",
                "From here, look at whatever has been troubling you. See how small it is, beside all of this.",
                "It still matters. But it is one small part of a very large, very old world, that has held countless troubles and carried on.",
                "Now come slowly back down, bringing a little of this wider view with you.",
            ),
            questions = listOf("From up there, what seemed to matter most?", "What can I let go of today?"),
            minutes = 5,
        ),
        Practice(
            "power", "What is in my power", "Epictetus",
            "There are things which are within our power, and there are things which are beyond our power. Within our power are opinion, aim, desire, aversion, " +
                "and, in one word, whatever affairs are our own. Beyond our power are body, property, reputation, office, and, in one word, whatever are not properly our own affairs.",
            "Epictetus, Enchiridion (tr. Thomas Wentworth Higginson)",
            "The first lesson of Epictetus, who was born a slave: sort a worry into what you can do something about, and what you can't. Put your effort into the first, and loosen your grip on the second.",
            questions = listOf("What is worrying me?", "Which part of it is in my power? What could I do?", "Which part is not, and can I let that part be?"),
            minutes = 5,
        ),
        Practice(
            "goodthings", "Three good things", "Daniel Defoe",
            "All our discontents about what we want appeared to me to spring from the want of thankfulness for what we have.",
            "Daniel Defoe, Robinson Crusoe",
            "Alone on his island, Crusoe learned to count what he had. Write down three things that went well today, however small, and if you like, why they happened.",
            questions = listOf("One good thing today", "Another", "And one more", "Why did they happen, or what was my part in them?"),
            minutes = 3,
        ),
        Practice(
            "arrow", "The second arrow", "The Buddha",
            null, null,
            "The Buddha compared pain to being struck by an arrow. Most of us then shoot a second arrow into ourselves: the worry, blame and what-ifs we add to it. The first arrow can't always be avoided; the second often can.",
            questions = listOf("What happened? (the first arrow)", "What am I adding to it? Stories, blame, what-ifs (the second arrows)", "What would it be like to feel only the first?"),
            minutes = 4,
        ),
        Practice(
            "imagination", "Worry, at its true size", "Seneca on fear",
            "We suffer more often in imagination than in reality.",
            "Seneca, Moral Letters (tr. Richard M. Gummere)",
            "Seneca wrote this to a friend kept awake by fears of what might happen. His advice: look closely at the fear, and ask what is actually here now, and what is only imagined. Many things we dread never come; the ones that do are seldom as we pictured them.",
            questions = listOf("What am I afraid might happen?", "What is actually happening, right now?", "If it did happen, what would I do? Who or what would help me?"),
            minutes = 4,
        ),
        Practice(
            "today", "What matters today", "Marcus Aurelius",
            "Order not your life as though you had ten thousand years to live. Fate hangs over you. While you live, while yet you may, be good.",
            "Marcus Aurelius, Meditations",
            "Marcus reminded himself that time is not endless, not to frighten himself but to wake himself up: to spend today on what matters, rather than putting the good things off.",
            questions = listOf("What matters most to me, today?", "What have I been putting off that I would like to do?", "What small good thing can I do before the day ends?"),
            minutes = 4,
        ),
        Practice(
            "useless", "The use of the useless", "Zhuangzi",
            "Hills suffer from the trees they produce. Fat burns by its own combustibility. Cinnamon trees furnish food: therefore they are cut down. The lacquer tree is felled for use. All men know the use of useful things; but they do not know the use of useless things.",
            "Zhuangzi",
            "Zhuangzi loved the gnarled old tree that no carpenter wanted, and so lived for centuries. Not everything has to be useful. This practice is a few minutes of doing nothing at all, on purpose. Listen, and then let yourself be useless for a while.",
            spoken = listOf(
                "For the next few minutes, there is nothing to achieve.",
                "Put down whatever you were doing. Let your hands rest.",
                "Look out of a window, if there is one, or at anything at all. Let your eyes wander where they like.",
                "If you feel you ought to be doing something, notice that feeling, and let it pass.",
                "Like the old tree that no one wanted to cut down, simply stand here, and grow.",
                "Stay as long as you like. When you are ready, come back, and write a few words if you wish.",
            ),
            questions = listOf("What do I love doing that isn't useful at all?", "When could I make room for some of it this week?"),
            minutes = 5,
        ),
        Practice(
            "simplify", "Simplify", "Henry David Thoreau",
            "Our life is frittered away by detail. Simplify, simplify.",
            "Henry David Thoreau, Walden",
            "Thoreau went to live by Walden Pond to see what life needed, and what it didn't. You don't need a cabin in the woods: just look at one corner of your life, and see what could be lighter.",
            questions = listOf("What is cluttering my days, my mind or my home?", "What is one thing I could let go of, or say no to, this week?", "What would a simpler day look like?"),
            minutes = 4,
        ),
        Practice(
            "view", "The view I take", "Epictetus",
            "Men are disturbed not by things, but by the views which they take of things.",
            "Epictetus, Enchiridion (tr. Thomas Wentworth Higginson)",
            "Epictetus noticed that two people can meet the same event and feel quite differently, because of what they tell themselves about it. You can't always change what happens, but you can look again at the story you are telling.",
            questions = listOf("What is upsetting me?", "What am I telling myself about it?", "Is there another way to see it, one a kind and fair friend might suggest?"),
            minutes = 4,
        ),
        Practice(
            "slowread", "Slow reading", "One line, read three times",
            null, null,
            "An old way of reading, used by monks for centuries: one short passage, read slowly three times, listening for the word that speaks to you. The line is one of your saved favourites, or today's quote.",
            questions = listOf("Which word or phrase stays with you?", "What does it say to you today?"),
            minutes = 5,
        ),
    )

    fun byKey(key: String): Practice? = all.firstOrNull { it.key == key }
}

/** The practices, as a row of cards on the Calm page. */
@Composable
fun PracticeRow(onOpen: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(Practices.all.size, key = { Practices.all[it].key }) { i ->
            val p = Practices.all[i]
            SoftCard(onClick = { onOpen("practice:" + p.key) }, modifier = Modifier.width(150.dp).height(108.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(p.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(p.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    Spacer(Modifier.weight(1f))
                    Text("About ${p.minutes} min", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun PracticeScreen(practice: Practice, repo: QuoteRepository, today: Quote?, onBack: () -> Unit, onFinished: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { QuotePrefs(context) }
    val speaking by QuoteVoice.speaking.collectAsState()
    // Slow reading uses a favourite (or today's quote); the others carry their own line.
    val line: Quote? = remember {
        when {
            practice.quote != null -> Quote(text = practice.quote, author = practice.author)
            practice.key == "slowread" -> repo.favorites().filter { it.text.length in 40..400 }.randomOrNull() ?: today
            else -> null
        }
    }
    val answers = remember { mutableStateListOf(*Array(practice.questions.size) { "" }) }
    val key = "practice:" + practice.key
    DisposableEffect(Unit) { onDispose { if (QuoteVoice.speaking.value == key) QuoteVoice.stop() } }
    BackHandler { QuoteVoice.stop(); onBack() }

    fun listen() {
        if (speaking == key) { QuoteVoice.stop(); return }
        val parts = mutableListOf<QuoteVoice.Segment>()
        fun add(text: String, pauseMs: Long) { parts += QuoteVoice.Segment(text, null, pauseMs) }
        if (practice.key == "slowread" && line != null) {
            repeat(3) { add(line.text.replace("\n", " "), 9_000L) }
        } else {
            line?.let { add(it.text, 3_000L) }
            practice.spoken.forEach { add(it, 7_000L) }
        }
        if (parts.isNotEmpty()) QuoteVoice.speakParts(context, key, parts, slower = 0.85f, duckSounds = false)
    }

    ToolScreen(practice.title, { QuoteVoice.stop(); onBack() }) {
        item { Text(practice.intro, style = MaterialTheme.typography.bodyMedium) }
        line?.let { q ->
            item {
                SoftCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("“" + q.text.replace("\\n", "\n") + "”", fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 25.sp)
                        q.author?.let { Text("— $it", fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
        }
        if (line != null || practice.spoken.isNotEmpty()) item {
            OutlinedButton(onClick = { listen() }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when {
                        speaking == key -> "Stop"
                        practice.key == "slowread" -> "Read it to me three times, slowly"
                        practice.spoken.isNotEmpty() -> "Guide me (read aloud)"
                        else -> "Read it to me"
                    },
                )
            }
        }
        practice.questions.forEachIndexed { i, question ->
            item(key = "q$i") {
                OutlinedTextField(
                    value = answers[i],
                    onValueChange = { answers[i] = it },
                    label = { Text(question) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        }
        item {
            Text("Saved to your Journal, on this phone only. You can leave any question blank.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Button(
                onClick = {
                    val text = practice.questions.indices.filter { answers[it].isNotBlank() }
                        .joinToString("\n\n") { practice.questions[it] + "\n" + answers[it].trim() }
                    if (text.isNotBlank()) {
                        Journal.add(prefs, line ?: Quote(text = practice.intro, author = null), practice.title, text)
                    }
                    QuoteVoice.stop()
                    onFinished()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save and finish") }
        }
    }
}
