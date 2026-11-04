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
    private var pending: Pair<String, String>? = null
    private var focus: AudioFocusRequest? = null
    private lateinit var app: Context

    private val mutable = MutableStateFlow<String?>(null)
    val speaking: StateFlow<String?> = mutable

    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /** Reads [quote], or stops if it is the one being read. */
    fun toggle(context: Context, quote: Quote) {
        if (mutable.value == quote.text) stop() else speak(context, quote)
    }

    fun speak(context: Context, quote: Quote) {
        app = context.applicationContext
        val words = spokenText(quote)
        val engine = tts
        if (engine == null) {
            pending = quote.text to words
            mutable.value = quote.text
            tts = TextToSpeech(app) { status -> onReady(status) }
            return
        }
        if (!ready) {
            pending = quote.text to words
            mutable.value = quote.text
            return
        }
        say(engine, quote.text, words)
    }

    fun stop() {
        pending = null
        tts?.stop()
        done()
    }

    /** The installed voices for the phone's language (and English), best first, for the settings. */
    fun voices(): List<Voice> {
        val engine = tts ?: return emptyList()
        if (!ready) return emptyList()
        val lang = Locale.getDefault().language
        return runCatching { engine.voices.orEmpty() }.getOrDefault(emptySet())
            .filter { !it.isNetworkConnectionRequired && it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true }
            .filter { it.locale.language == lang || it.locale.language == "en" }
            .sortedWith(compareBy<Voice>({ it.locale.language != lang }, { -it.quality }, { it.name }))
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
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = done(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = done(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = done(utteranceId)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = done(utteranceId)
        })
        pending?.let { (key, words) -> say(engine, key, words) }
        pending = null
    }

    private fun say(engine: TextToSpeech, key: String, words: String) {
        val prefs = QuotePrefs(app)
        val wanted = prefs.voiceName
        val voice = if (wanted.isNotEmpty()) runCatching { engine.voices?.firstOrNull { it.name == wanted } }.getOrNull() else null
        if (voice != null) {
            engine.voice = voice
        } else {
            val result = engine.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                if (engine.setLanguage(Locale.ENGLISH) < TextToSpeech.LANG_AVAILABLE) {
                    done()
                    Toast.makeText(app, "This phone has no voice installed for reading aloud yet.", Toast.LENGTH_LONG).show()
                    runCatching {
                        app.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    return
                }
            }
        }
        engine.setSpeechRate(prefs.voiceRate)
        engine.setPitch(1f)
        requestFocus()
        mutable.value = key
        if (engine.speak(words, TextToSpeech.QUEUE_FLUSH, Bundle(), key.hashCode().toString()) != TextToSpeech.SUCCESS) done()
    }

    private fun done(utteranceId: String? = null) {
        // A finished earlier utterance must not clear the state of the one that replaced it.
        if (utteranceId != null && utteranceId != mutable.value?.hashCode()?.toString()) return
        mutable.value = null
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

    /**
     * What is said: the quote and who said it; for ideas and myths the title, the passage and the short
     * explanation. Line breaks in verse become pauses, and translator notes are left out of the byline.
     */
    fun spokenText(quote: Quote): String {
        fun lines(s: String) = s.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            .joinToString(" ") { if (it.last() in ".,;:!?—–-…") it else "$it," }
        val byline = quote.byline?.replace(Regex("\\s*\\((tr\\.|trans\\.)[^)]*\\)"), "")?.trim()
        return buildString {
            quote.title?.let { append(it).append(". ") }
            append(lines(quote.shownText))
            if (!byline.isNullOrBlank()) append("\n\n").append(byline).append('.')
            if (quote.isIdea) quote.brief?.takeIf { it.isNotBlank() && !quote.explained }?.let { append("\n\nIn brief: ").append(it) }
            quote.more?.let { append("\n\n").append(it) }
        }
    }
}
