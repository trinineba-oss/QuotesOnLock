package com.trinineba.quotesonlock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** How a sound is grouped on the Sounds screen. */
enum class SoundKind(val heading: String) { NATURE("Nature"), QUIET("Steady and still"), MUSIC("Music") }

/**
 * The calming sounds: each a seamless stereo loop in res/raw, shown with artwork and, in Calm, the matching
 * calm scene behind the app ([scene]). The [SoundKind.MUSIC] ones are whole pieces that start again after a pause.
 */
enum class Soundscape(
    val key: String,
    val label: String,
    @RawRes val raw: Int,
    @DrawableRes val art: Int,
    val scene: AppTheme,
    val kind: SoundKind = SoundKind.NATURE,
) {
    RAIN("rain", "Rain on the window", R.raw.snd_rain, R.drawable.rain_night_frame, AppTheme.RAIN_NIGHT),
    TENT("tent", "Rain on a tent", R.raw.snd_tent, R.drawable.rain_frame, AppTheme.RAIN),
    STORM("storm", "Distant storm", R.raw.snd_storm, R.drawable.space_frame, AppTheme.RAIN_NIGHT),
    WAVES("waves", "Waves on the shore", R.raw.snd_waves, R.drawable.waves_frame, AppTheme.WAVES),
    STREAM("stream", "Mountain stream", R.raw.snd_stream, R.drawable.water_frame, AppTheme.WATER),
    WIND("wind", "Wind in the trees", R.raw.snd_wind, R.drawable.bamboo_frame, AppTheme.BAMBOO),
    FIRE("fire", "Campfire", R.raw.snd_fire, R.drawable.candle_frame, AppTheme.CANDLE),
    FOREST("forest", "Night crickets", R.raw.snd_forest, R.drawable.fireflies_frame, AppTheme.FIREFLIES),
    FROGS("frogs", "Frogs on a summer night", R.raw.snd_frogs, R.drawable.lavender_frame, AppTheme.FIREFLIES),
    BIRDS("birds", "Dawn chorus", R.raw.snd_birds, R.drawable.clouds_frame, AppTheme.CLOUDS),
    BROWN("brown", "Brown noise", R.raw.snd_brown, R.drawable.aurora_frame, AppTheme.SPACE, SoundKind.QUIET),
    BOWL("bowl", "Singing bowls", R.raw.snd_bowl, R.drawable.butterfly_frame, AppTheme.SNOW, SoundKind.QUIET),
    SATIE("satie", "Satie: Gymnopédie No. 1", R.raw.mus_satie, R.drawable.lavender_frame, AppTheme.LAVENDER, SoundKind.MUSIC),
    BACH("bach", "Bach: Aria from the Goldberg Variations", R.raw.mus_bach, R.drawable.starry_frame, AppTheme.AURORA, SoundKind.MUSIC),
    NOCTURNE("nocturne", "Chopin: Nocturne Op. 9 No. 2", R.raw.mus_nocturne, R.drawable.starry_frame, AppTheme.STARRY, SoundKind.MUSIC),
    MOONLIGHT("moonlight", "Beethoven: Moonlight Sonata", R.raw.mus_moonlight, R.drawable.snow_frame, AppTheme.SNOW, SoundKind.MUSIC);

    val music: Boolean get() = kind == SoundKind.MUSIC

    companion object {
        fun fromKey(key: String?): Soundscape? = entries.firstOrNull { it.key == key }
    }
}

/** One sound in a mix, with its own volume (0–1) under the overall volume. */
data class SoundLayer(val sound: Soundscape, val volume: Float = 0.8f)

/** A mix: up to [MAX_LAYERS] sounds playing together. Stored as "key:volume,key:volume". */
object SoundMix {
    const val MAX_LAYERS = 3

    fun encode(layers: List<SoundLayer>): String = layers.joinToString(",") { "${it.sound.key}:${"%.2f".format(java.util.Locale.US, it.volume)}" }

    fun decode(raw: String?): List<SoundLayer> = raw.orEmpty().split(',').mapNotNull { part ->
        val sound = Soundscape.fromKey(part.substringBefore(':')) ?: return@mapNotNull null
        SoundLayer(sound, part.substringAfter(':', "0.8").toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.8f)
    }.distinctBy { it.sound }.take(MAX_LAYERS)

    /** A short name for a mix: "Rain on the window + Campfire". */
    fun label(layers: List<SoundLayer>): String = layers.joinToString(" + ") { it.sound.label }
}

/** A mix the user saved by name. */
data class SavedMix(val name: String, val layers: List<SoundLayer>)

/** What's playing, for the screens to show. [endsAt] is in elapsedRealtime, 0 when there's no timer. */
data class SoundState(
    val layers: List<SoundLayer> = emptyList(),
    val playing: Boolean = false,
    val endsAt: Long = 0L,
    val forBreathe: Boolean = false,
) {
    /** The first sound of the mix: it names the mix and chooses the calm scene. */
    val sound: Soundscape? get() = layers.firstOrNull()?.sound
}

/** The app's handle on the sound service: every call is a small intent, the service does the work. */
object SoundPlayer {
    internal val mutable = MutableStateFlow(SoundState())
    val state: StateFlow<SoundState> = mutable

    /** Plays [sound] on its own (replacing any mix) with the saved timer, or none for Breathe. */
    fun play(context: Context, sound: Soundscape, forBreathe: Boolean = false) =
        playMix(context, listOf(SoundLayer(sound, 1f)), forBreathe)

    /** Plays a whole mix, replacing what was playing; [minutes] sets this mix's own timer (else the saved one). */
    fun playMix(context: Context, layers: List<SoundLayer>, forBreathe: Boolean = false, minutes: Int? = null) {
        if (layers.isEmpty()) return stop(context)
        send(context, SoundService.ACTION_MIX) {
            putExtra(SoundService.EXTRA_MIX, SoundMix.encode(layers.take(SoundMix.MAX_LAYERS)))
            putExtra(SoundService.EXTRA_BREATHE, forBreathe)
            if (minutes != null) putExtra(SoundService.EXTRA_MINUTES, minutes)
        }
    }

    /** Fifteen more minutes before the sounds fade out. */
    fun extend(context: Context) {
        if (state.value.layers.isNotEmpty()) send(context, SoundService.ACTION_EXTEND)
    }

    /**
     * Tap on a sound card: adds it to what's playing (up to three), or takes it out if it's already there.
     * With nothing playing, it simply starts. Returns false when the mix is already full.
     */
    fun toggleLayer(context: Context, sound: Soundscape): Boolean {
        val s = state.value
        val layers = s.layers
        return when {
            layers.isEmpty() || s.forBreathe -> { play(context, sound); true }
            layers.any { it.sound == sound } -> {
                val rest = layers.filter { it.sound != sound }
                if (rest.isEmpty()) stop(context) else playMix(context, rest)
                true
            }
            layers.size >= SoundMix.MAX_LAYERS -> false
            else -> {
                // A new layer joins a little quieter than the first, so the mix stays soft.
                playMix(context, layers + SoundLayer(sound, 0.6f))
                true
            }
        }
    }

    /** Changes one layer's volume while it plays. */
    fun setLayerVolume(context: Context, sound: Soundscape, volume: Float) {
        val layers = state.value.layers.map { if (it.sound == sound) it.copy(volume = volume) else it }
        mutable.value = state.value.copy(layers = layers)
        send(context, SoundService.ACTION_VOLUMES) { putExtra(SoundService.EXTRA_MIX, SoundMix.encode(layers)) }
    }

    /** Pause or resume the whole mix. */
    fun togglePlaying(context: Context) {
        val s = state.value
        if (s.layers.isEmpty()) return
        if (s.playing) pause(context) else resume(context)
    }

    fun pause(context: Context) = send(context, SoundService.ACTION_PAUSE)
    fun resume(context: Context) = send(context, SoundService.ACTION_RESUME)
    fun stop(context: Context) {
        if (state.value.layers.isNotEmpty()) send(context, SoundService.ACTION_STOP)
    }

    /** Re-reads the volume and timer from the settings (after the user changes them). */
    fun settingsChanged(context: Context) {
        if (state.value.layers.isNotEmpty()) send(context, SoundService.ACTION_SETTINGS)
    }

    private fun send(context: Context, action: String, extras: Intent.() -> Unit = {}) {
        val intent = Intent(context, SoundService::class.java).setAction(action).apply(extras)
        runCatching { ContextCompat.startForegroundService(context, intent) }
    }
}

/**
 * Plays a mix of up to three looping sounds in the foreground (so it keeps going with the screen off), each
 * with its own volume, starting with a short fade-in, with a sleep timer that fades out over the last minute,
 * and a notification to pause, stop or add time.
 */
class SoundService : Service() {
    private val players = LinkedHashMap<Soundscape, MediaPlayer>()
    private var layers: List<SoundLayer> = emptyList()
    private var playing = false
    private var forBreathe = false
    private var endsAt = 0L
    private var pausedLeft = 0L
    private var fadeInFrom = 0L
    private var ducked = false
    private val handler = Handler(Looper.getMainLooper())
    private var focus: AudioFocusRequest? = null
    private var noisyRegistered = false

    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val prefs by lazy { QuotePrefs(this) }

    private val tick = object : Runnable {
        override fun run() {
            if (!playing) return
            if (endsAt > 0 && endsAt - SystemClock.elapsedRealtime() <= 0) {
                stopAll()
                return
            }
            applyVolume()
            handler.postDelayed(this, 200)
        }
    }

    private val noisy = object : BroadcastReceiver() {
        // Headphones unplugged: pause rather than play out loud.
        override fun onReceive(context: Context, intent: Intent) = pause()
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> pause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> { ducked = true; applyVolume() }
            AudioManager.AUDIOFOCUS_GAIN -> { ducked = false; applyVolume() }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every start must promise the foreground at once, even one that only stops.
        goForeground()
        when (intent?.action) {
            ACTION_MIX -> SoundMix.decode(intent.getStringExtra(EXTRA_MIX)).takeIf { it.isNotEmpty() }?.let {
                setMix(it, intent.getBooleanExtra(EXTRA_BREATHE, false), intent.getIntExtra(EXTRA_MINUTES, -1).takeIf { m -> m >= 0 })
            } ?: stopAll()
            ACTION_VOLUMES -> volumes(SoundMix.decode(intent.getStringExtra(EXTRA_MIX)))
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_EXTEND -> extend()
            ACTION_SETTINGS -> settings()
            else -> stopAll()
        }
        return START_NOT_STICKY
    }

    private fun newPlayer(s: Soundscape): MediaPlayer? = runCatching {
        MediaPlayer().apply {
            setAudioAttributes(ATTRIBUTES)
            resources.openRawResourceFd(s.raw).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            isLooping = true
            setVolume(0f, 0f)
            prepare()
        }
    }.getOrNull()

    /** Makes [wanted] the mix: keeps the players already running, starts new ones, releases the rest. */
    private fun setMix(wanted: List<SoundLayer>, breathe: Boolean, ownMinutes: Int? = null) {
        val wasEmpty = players.isEmpty()
        val keep = wanted.map { it.sound }.toSet()
        players.keys.filter { it !in keep }.forEach { s -> players.remove(s)?.let { release(it) } }
        val added = mutableListOf<MediaPlayer>()
        wanted.forEach { l ->
            if (l.sound !in players) newPlayer(l.sound)?.let { players[l.sound] = it; added += it }
        }
        layers = wanted.filter { it.sound in players }
        if (layers.isEmpty()) return stopAll()
        if (wasEmpty || breathe != forBreathe || ownMinutes != null) {
            forBreathe = breathe
            val minutes = ownMinutes ?: if (breathe) 0 else prefs.soundTimer
            endsAt = if (minutes > 0) SystemClock.elapsedRealtime() + minutes * 60_000L else 0L
            pausedLeft = 0L
        }
        if (!requestFocus()) return stopAll()
        fadeInFrom = SystemClock.elapsedRealtime()
        applyVolume()
        if (wasEmpty || playing || added.isNotEmpty()) {
            // Adding a sound to a paused mix resumes the whole mix.
            if (!playing && pausedLeft > 0 && endsAt == 0L) endsAt = SystemClock.elapsedRealtime() + pausedLeft
            players.values.forEach { mp -> if (!mp.isPlaying) runCatching { mp.start() } }
            playing = true
            listenNoisy(true)
            handler.removeCallbacks(tick)
            handler.post(tick)
        }
        publish()
    }

    private fun volumes(wanted: List<SoundLayer>) {
        val byKey = wanted.associateBy { it.sound }
        layers = layers.map { byKey[it.sound] ?: it }
        applyVolume()
        SoundPlayer.mutable.value = SoundPlayer.state.value.copy(layers = layers)
    }

    private fun pause() {
        if (players.isEmpty() || !playing) return
        players.values.forEach { runCatching { it.pause() } }
        playing = false
        pausedLeft = if (endsAt > 0) (endsAt - SystemClock.elapsedRealtime()).coerceAtLeast(FADE_MS) else 0L
        endsAt = 0L
        handler.removeCallbacks(tick)
        listenNoisy(false)
        publish()
    }

    private fun resume() {
        if (players.isEmpty() || playing) return
        if (!requestFocus()) return
        if (pausedLeft > 0) endsAt = SystemClock.elapsedRealtime() + pausedLeft
        pausedLeft = 0L
        fadeInFrom = SystemClock.elapsedRealtime()
        applyVolume()
        players.values.forEach { runCatching { it.start() } }
        playing = true
        listenNoisy(true)
        publish()
        handler.post(tick)
    }

    /** "+15 min" from the notification: more time, or a timer if there was none. */
    private fun extend() {
        val now = SystemClock.elapsedRealtime()
        if (playing) {
            endsAt = (if (endsAt > now) endsAt else now) + 15 * 60_000L
            applyVolume()
        } else {
            pausedLeft = (if (pausedLeft > 0) pausedLeft else 0L) + 15 * 60_000L
        }
        publish()
    }

    /** The volume or the timer changed in the app. */
    private fun settings() {
        if (players.isEmpty()) return stopAll()
        if (!forBreathe) {
            val minutes = prefs.soundTimer
            val left = if (minutes > 0) minutes * 60_000L else 0L
            if (playing) {
                endsAt = if (left > 0) SystemClock.elapsedRealtime() + left else 0L
            } else {
                pausedLeft = left
            }
        }
        if (playing) applyVolume()
        publish()
    }

    /** Overall volume × the layer's own × the fade-in × the timer's fade-out × ducking under speech. */
    private fun applyVolume() {
        val now = SystemClock.elapsedRealtime()
        val fadeIn = ((now - fadeInFrom).toFloat() / FADE_IN_MS).coerceIn(0f, 1f)
        val fadeOut = if (endsAt > 0) ((endsAt - now).toFloat() / FADE_MS).coerceIn(0f, 1f) else 1f
        val duck = if (ducked) 0.2f else 1f
        val master = prefs.soundVolume * fadeIn * fadeIn * fadeOut * duck
        layers.forEach { l ->
            val v = (master * l.volume).coerceIn(0f, 1f)
            runCatching { players[l.sound]?.setVolume(v, v) }
        }
    }

    private fun requestFocus(): Boolean {
        val request = focus ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(ATTRIBUTES)
            .setOnAudioFocusChangeListener(focusListener, handler)
            .build().also { focus = it }
        return audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_FAILED
    }

    private fun listenNoisy(on: Boolean) {
        if (on == noisyRegistered) return
        if (on) ContextCompat.registerReceiver(this, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        else runCatching { unregisterReceiver(noisy) }
        noisyRegistered = on
    }

    private fun release(mp: MediaPlayer) {
        runCatching { mp.stop() }
        runCatching { mp.release() }
    }

    private fun releasePlayers() {
        handler.removeCallbacks(tick)
        players.values.forEach { release(it) }
        players.clear()
    }

    private fun stopAll() {
        releasePlayers()
        layers = emptyList()
        playing = false
        endsAt = 0L
        pausedLeft = 0L
        listenNoisy(false)
        focus?.let { audio.abandonAudioFocusRequest(it) }
        SoundPlayer.mutable.value = SoundState()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        releasePlayers()
        listenNoisy(false)
        focus?.let { audio.abandonAudioFocusRequest(it) }
        SoundPlayer.mutable.value = SoundState()
        super.onDestroy()
    }

    /** Shares the state with the app and refreshes the notification. */
    private fun publish() {
        val shownEnd = if (playing) endsAt else if (pausedLeft > 0) -pausedLeft else 0L
        SoundPlayer.mutable.value = SoundState(layers, playing, shownEnd, forBreathe)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
    }

    private fun goForeground() {
        ensureChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = when {
            layers.isEmpty() -> ""
            !playing -> "Paused"
            endsAt > 0 -> "Stops at " + android.text.format.DateFormat.getTimeFormat(this)
                .format(java.util.Date(System.currentTimeMillis() + (endsAt - SystemClock.elapsedRealtime())))
            else -> "Playing"
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_graphic_eq)
            .setContentTitle(if (layers.isEmpty()) "Calming sounds" else SoundMix.label(layers))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(playing)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (layers.isNotEmpty()) {
            if (playing) builder.addAction(R.drawable.ic_pause, "Pause", action(ACTION_PAUSE, 1))
            else builder.addAction(R.drawable.ic_play_arrow, "Play", action(ACTION_RESUME, 2))
            builder.addAction(R.drawable.ic_stop, "Stop", action(ACTION_STOP, 3))
            builder.addAction(R.drawable.ic_schedule, "+15 min", action(ACTION_EXTEND, 4))
        }
        return builder.build()
    }

    private fun action(action: String, code: Int): PendingIntent = PendingIntent.getForegroundService(
        this, code, Intent(this, SoundService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Calming sounds", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while a calming sound is playing"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_MIX = "com.trinineba.quotesonlock.sound.MIX"
        const val ACTION_VOLUMES = "com.trinineba.quotesonlock.sound.VOLUMES"
        const val ACTION_PAUSE = "com.trinineba.quotesonlock.sound.PAUSE"
        const val ACTION_RESUME = "com.trinineba.quotesonlock.sound.RESUME"
        const val ACTION_STOP = "com.trinineba.quotesonlock.sound.STOP"
        const val ACTION_EXTEND = "com.trinineba.quotesonlock.sound.EXTEND"
        const val ACTION_SETTINGS = "com.trinineba.quotesonlock.sound.SETTINGS"
        const val EXTRA_MIX = "mix"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_BREATHE = "breathe"
        private const val CHANNEL_ID = "sounds"
        private const val NOTIFICATION_ID = 4207
        private const val FADE_MS = 60_000L
        private const val FADE_IN_MS = 2_500L
        private val ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }
}
