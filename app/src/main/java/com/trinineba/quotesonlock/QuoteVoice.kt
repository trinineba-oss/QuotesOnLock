package com.trinineba.quotesonlock

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Reads quotes aloud with the phone's own text-to-speech voices (nothing is downloaded or bundled).
 * One engine for the whole app, started on first use; [speaking] holds the text being read, so a
 * button can show Stop for its own quote. Calming sounds dip under the voice and come back after.
 */
object QuoteVoice {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var focus: AudioFocusRequest? = null
    private lateinit var app: Context

    private val mutable = MutableStateFlow<String?>(null)
    val speaking: StateFlow<String?> = mutable

    private val attributes: AudioAttributes by lazy {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }

    /** Reads [quote], or stops if it is the one being read. */
    fun toggle(context: Context, quote: Quote) {
        if (mutable.value == quote.text) stop() else speak(context, quote)
    }

    /** The part of the quote's shown text being read right now (a word where the voice reports it, else a sentence). */
    private val readingRange = MutableStateFlow<IntRange?>(null)
    val reading: StateFlow<IntRange?> = readingRange

    /**
     * One piece of speech. [map] gives, for each character spoken, its place in the quote's shown text, so the
     * screen can highlight what is being said; it is null for the title, byline and explanations.
     */
    class Segment(val text: String, val map: IntArray?, val pauseAfterMs: Long)

    // Each reading gets a new generation, so callbacks from a reading that was stopped are ignored.
    private var generation = 0
    private var segments: List<Segment> = emptyList()
    private var lastId: String? = null
    private var pending: Pair<String, List<Segment>>? = null

    fun speak(context: Context, quote: Quote) {
        app = context.applicationContext
        val parts = segments(quote)
        val engine = tts
        if (engine == null || !ready) {
            pending = quote.text to parts
            mutable.value = quote.text
            if (engine == null) tts = TextToSpeech(app) { status -> onReady(status) }
            return
        }
        say(engine, quote.text, parts)
    }

    fun stop() {
        pending = null
        generation++
        tts?.stop()
        done()
    }

    /**
     * The voices for the phone's language (and English), most natural first, for the settings. Voices that need
     * the internet are included, as they are often the most natural, and marked as such in the list.
     */
    fun voices(): List<Voice> {
        val engine = tts ?: return emptyList()
        if (!ready) return emptyList()
        val lang = Locale.getDefault().language
        return runCatching { engine.voices.orEmpty() }.getOrDefault(emptySet())
            .filter { it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true }
            .filter { it.locale.language == lang || it.locale.language == "en" }
            .sortedWith(compareBy<Voice>({ it.locale.language != lang }, { -it.quality }, { it.isNetworkConnectionRequired }, { it.name }))
    }

    /** Starts the engine early (for the settings' voice list) without saying anything. */
    fun warmUp(context: Context, then: () -> Unit = {}) {
        app = context.applicationContext
        if (tts == null) tts = TextToSpeech(app) { status -> onReady(status); then() } else if (ready) then()
    }

    private fun onReady(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            tts = null
            ready = false
            done()
            Toast.makeText(app, "No text-to-speech voice found. Install one in Android settings.", Toast.LENGTH_LONG).show()
            openSettings(app)
            return
        }
        ready = true
        engine.setAudioAttributes(attributes)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                val seg = segmentOf(utteranceId) ?: return
                // A whole sentence lights up as it starts; engines that report words narrow it further.
                readingRange.value = seg.map?.takeIf { it.isNotEmpty() }?.let { it.first()..it.last() }
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                val map = segmentOf(utteranceId)?.map ?: return
                if (start < 0 || end <= start || start >= map.size) return
                readingRange.value = map[start]..map[(end - 1).coerceAtMost(map.size - 1)]
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId != null && utteranceId == lastId) done()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finishIfCurrent(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = finishIfCurrent(utteranceId)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finishIfCurrent(utteranceId)
        })
        pending?.let { (key, parts) -> say(engine, key, parts) }
        pending = null
    }

    private fun finishIfCurrent(utteranceId: String?) {
        if (utteranceId?.substringBefore(':') == generation.toString()) done()
    }

    private fun segmentOf(utteranceId: String?): Segment? {
        val id = utteranceId ?: return null
        if (id.substringBefore(':') != generation.toString()) return null
        return segments.getOrNull(id.substringAfter(':').toIntOrNull() ?: return null)
    }

    /**
     * The voice to use: the one chosen in the settings, or else the most natural voice installed on the
     * phone for its language (engines often default to a plainer one).
     */
    private fun chooseVoice(engine: TextToSpeech): Boolean {
        val all = runCatching { engine.voices.orEmpty() }.getOrDefault(emptySet())
        val wanted = QuotePrefs(app).voiceName
        all.firstOrNull { it.name == wanted && wanted.isNotEmpty() }?.let { engine.voice = it; return true }
        val here = Locale.getDefault()
        val best = all
            .filter { !it.isNetworkConnectionRequired && it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true }
            .filter { it.locale.language == here.language }
            .sortedWith(compareBy<Voice>({ it.locale.country != here.country }, { -it.quality }, { it.latency }))
            .firstOrNull()
        if (best != null) {
            engine.voice = best
            return true
        }
        val result = engine.setLanguage(here)
        return result >= TextToSpeech.LANG_AVAILABLE || engine.setLanguage(Locale.ENGLISH) >= TextToSpeech.LANG_AVAILABLE
    }

    private fun say(engine: TextToSpeech, key: String, parts: List<Segment>) {
        if (!chooseVoice(engine)) {
            done()
            Toast.makeText(app, "This phone has no voice installed for reading aloud yet.", Toast.LENGTH_LONG).show()
            runCatching {
                app.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return
        }
        engine.setSpeechRate(QuotePrefs(app).voiceRate)
        engine.setPitch(1f)
        requestFocus()
        val gen = ++generation
        segments = parts
        mutable.value = key
        readingRange.value = null
        // Sentence by sentence, with short natural pauses: after the title, between sentences, before the byline.
        var ok = true
        parts.forEachIndexed { i, seg ->
            val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            ok = ok && engine.speak(seg.text, mode, Bundle(), "$gen:$i") == TextToSpeech.SUCCESS
            lastId = "$gen:$i"
            if (seg.pauseAfterMs > 0 && i < parts.lastIndex) {
                engine.playSilentUtterance(seg.pauseAfterMs, TextToSpeech.QUEUE_ADD, "$gen:p$i")
            }
        }
        if (!ok || parts.isEmpty()) done()
    }

    private fun done() {
        mutable.value = null
        readingRange.value = null
        focus?.let { f -> runCatching { app.getSystemService(AudioManager::class.java).abandonAudioFocusRequest(f) } }
        focus = null
    }

    /** Asks other sound (the calming sounds included) to dip while the voice speaks. */
    private fun requestFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
        focus = request
        runCatching { app.getSystemService(AudioManager::class.java).requestAudioFocus(request) }
    }

    fun openSettings(context: Context) {
        runCatching {
            context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private const val PUNCTUATION = ".,;:!?—–-…"

    /**
     * The quote's shown text split into sentences, each turned into what is said (a line break in verse
     * becomes a pause, a comma) together with where each spoken character sits in the shown text.
     */
    fun bodySegments(shown: String): List<Segment> {
        val out = mutableListOf<Segment>()
        val sentenceEnd = Regex("""[.!?…]["”’)]*\s+""")
        var start = 0
        val bounds = sentenceEnd.findAll(shown).map { it.range.last + 1 }.toMutableList().apply { add(shown.length) }
        for (end in bounds) {
            if (end <= start) continue
            val spoken = StringBuilder()
            val map = ArrayList<Int>()
            var lastChar = ' '
            for (k in start until end) {
                val c = shown[k]
                if (c == '\n') {
                    if (spoken.isNotEmpty()) {
                        if (lastChar !in PUNCTUATION) { spoken.append(','); map.add(k) }
                        spoken.append(' '); map.add(k)
                        lastChar = ' '
                    }
                } else {
                    spoken.append(c); map.add(k)
                    if (!c.isWhitespace()) lastChar = c
                }
            }
            // Trim the spaces at either end, keeping the map in step.
            var a = 0
            var b = spoken.length
            while (a < b && spoken[a].isWhitespace()) a++
            while (b > a && spoken[b - 1].isWhitespace()) b--
            if (b > a) out += Segment(spoken.substring(a, b), map.subList(a, b).toIntArray(), 120)
            start = end
        }
        return out
    }

    /**
     * What is said, in order: for ideas and myths the title; the quote, sentence by sentence; who said it
     * (translator notes left out); and for ideas the brief and the fuller explanation.
     */
    fun segments(quote: Quote): List<Segment> {
        val byline = quote.byline?.replace(Regex("\\s*\\((tr\\.|trans\\.)[^)]*\\)"), "")?.trim()
        val out = mutableListOf<Segment>()
        quote.title?.let { out += Segment("$it.", null, 450) }
        val body = bodySegments(quote.shownText)
        body.forEachIndexed { i, seg -> out += if (i == body.lastIndex) Segment(seg.text, seg.map, 500) else seg }
        if (!byline.isNullOrBlank()) out += Segment("$byline.", null, 650)
        if (quote.isIdea) quote.brief?.takeIf { it.isNotBlank() && !quote.explained }?.let { out += Segment("In brief: $it", null, 650) }
        quote.more?.let { out += Segment(it, null, 0) }
        return out
    }

    /** All of it as one text (used by the tests). */
    fun spokenText(quote: Quote): String = segments(quote).joinToString(" ") { it.text }
}
