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

/** The calming sounds: each a seamless loop in res/raw, shown with the matching calm theme's artwork. */
enum class Soundscape(val key: String, val label: String, @RawRes val raw: Int, @DrawableRes val art: Int) {
    RAIN("rain", "Rain on the window", R.raw.snd_rain, R.drawable.rain_night_frame),
    WAVES("waves", "Waves at dusk", R.raw.snd_waves, R.drawable.waves_frame),
    WIND("wind", "Wind in bamboo", R.raw.snd_wind, R.drawable.bamboo_frame),
    FOREST("forest", "Night forest", R.raw.snd_forest, R.drawable.fireflies_frame),
    FIRE("fire", "Crackling fire", R.raw.snd_fire, R.drawable.candle_frame),
    STREAM("stream", "Gentle stream", R.raw.snd_stream, R.drawable.water_frame),
    BIRDS("birds", "Birdsong", R.raw.snd_birds, R.drawable.clouds_frame);

    companion object {
        fun fromKey(key: String?): Soundscape? = entries.firstOrNull { it.key == key }
    }
}

/** What's playing, for the screens to show. [endsAt] is in elapsedRealtime, 0 when there's no timer. */
data class SoundState(
    val sound: Soundscape? = null,
    val playing: Boolean = false,
    val endsAt: Long = 0L,
    val forBreathe: Boolean = false,
)

/** The app's handle on the sound service: every call is a small intent, the service does the work. */
object SoundPlayer {
    internal val mutable = MutableStateFlow(SoundState())
    val state: StateFlow<SoundState> = mutable

    /** Plays [sound] (replacing any other) with the saved timer, or none for Breathe. */
    fun play(context: Context, sound: Soundscape, forBreathe: Boolean = false) =
        send(context, SoundService.ACTION_PLAY) {
            putExtra(SoundService.EXTRA_SOUND, sound.key)
            putExtra(SoundService.EXTRA_BREATHE, forBreathe)
        }

    /** Tap on a sound card: start it, or pause/resume it if it is the one already chosen. */
    fun toggle(context: Context, sound: Soundscape) {
        val s = state.value
        when {
            s.sound != sound -> play(context, sound)
            s.playing -> pause(context)
            else -> resume(context)
        }
    }

    fun pause(context: Context) = send(context, SoundService.ACTION_PAUSE)
    fun resume(context: Context) = send(context, SoundService.ACTION_RESUME)
    fun stop(context: Context) {
        if (state.value.sound != null) send(context, SoundService.ACTION_STOP)
    }

    /** Re-reads the volume and timer from the settings (after the user changes them). */
    fun settingsChanged(context: Context) {
        if (state.value.sound != null) send(context, SoundService.ACTION_SETTINGS)
    }

    private fun send(context: Context, action: String, extras: Intent.() -> Unit = {}) {
        val intent = Intent(context, SoundService::class.java).setAction(action).apply(extras)
        runCatching { ContextCompat.startForegroundService(context, intent) }
    }
}

/**
 * Plays one looping sound in the foreground (so it keeps going with the screen off), with a sleep timer
 * that fades out over the last minute, and a notification to pause, stop or add time.
 */
class SoundService : Service() {
    private var player: MediaPlayer? = null
    private var sound: Soundscape? = null
    private var playing = false
    private var forBreathe = false
    private var endsAt = 0L
    private var pausedLeft = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var focus: AudioFocusRequest? = null
    private var noisyRegistered = false

    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val prefs by lazy { QuotePrefs(this) }

    private val tick = object : Runnable {
        override fun run() {
            if (!playing) return
            if (endsAt > 0) {
                val left = endsAt - SystemClock.elapsedRealtime()
                if (left <= 0) {
                    stopAll()
                    return
                }
                // The last minute fades out gently.
                applyVolume(if (left < FADE_MS) left.toFloat() / FADE_MS else 1f)
            }
            handler.postDelayed(this, 250)
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
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.setVolume(0.2f * prefs.soundVolume, 0.2f * prefs.soundVolume)
            AudioManager.AUDIOFOCUS_GAIN -> applyVolume(1f)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every start must promise the foreground at once, even one that only stops.
        goForeground()
        when (intent?.action) {
            ACTION_PLAY -> Soundscape.fromKey(intent.getStringExtra(EXTRA_SOUND))?.let {
                start(it, intent.getBooleanExtra(EXTRA_BREATHE, false))
            } ?: stopAll()
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_EXTEND -> extend()
            ACTION_SETTINGS -> settings()
            else -> stopAll()
        }
        return START_NOT_STICKY
    }

    private fun start(s: Soundscape, breathe: Boolean) {
        releasePlayer()
        val mp = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(ATTRIBUTES)
                resources.openRawResourceFd(s.raw).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                isLooping = true
                prepare()
            }
        }.getOrNull() ?: return stopAll()
        player = mp
        sound = s
        forBreathe = breathe
        val minutes = if (breathe) 0 else prefs.soundTimer
        endsAt = if (minutes > 0) SystemClock.elapsedRealtime() + minutes * 60_000L else 0L
        if (!requestFocus()) return stopAll()
        applyVolume(1f)
        mp.start()
        playing = true
        listenNoisy(true)
        publish()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    private fun pause() {
        val mp = player ?: return
        if (!playing) return
        runCatching { mp.pause() }
        playing = false
        pausedLeft = if (endsAt > 0) (endsAt - SystemClock.elapsedRealtime()).coerceAtLeast(FADE_MS) else 0L
        handler.removeCallbacks(tick)
        listenNoisy(false)
        publish()
    }

    private fun resume() {
        val mp = player ?: return
        if (playing) return
        if (!requestFocus()) return
        if (pausedLeft > 0) endsAt = SystemClock.elapsedRealtime() + pausedLeft
        runCatching { mp.start() }
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
            applyVolume(1f)
        } else {
            pausedLeft = (if (pausedLeft > 0) pausedLeft else 0L) + 15 * 60_000L
        }
        publish()
    }

    /** The volume or the timer changed in the app. */
    private fun settings() {
        if (sound == null) return stopAll()
        if (!forBreathe) {
            val minutes = prefs.soundTimer
            val left = if (minutes > 0) minutes * 60_000L else 0L
            if (playing) {
                endsAt = if (left > 0) SystemClock.elapsedRealtime() + left else 0L
            } else {
                pausedLeft = left
            }
        }
        if (playing) applyVolume(1f)
        publish()
    }

    private fun applyVolume(fade: Float) {
        val v = (prefs.soundVolume * fade).coerceIn(0f, 1f)
        runCatching { player?.setVolume(v, v) }
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

    private fun releasePlayer() {
        handler.removeCallbacks(tick)
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }

    private fun stopAll() {
        releasePlayer()
        playing = false
        sound = null
        endsAt = 0L
        pausedLeft = 0L
        listenNoisy(false)
        focus?.let { audio.abandonAudioFocusRequest(it) }
        SoundPlayer.mutable.value = SoundState()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        releasePlayer()
        listenNoisy(false)
        focus?.let { audio.abandonAudioFocusRequest(it) }
        SoundPlayer.mutable.value = SoundState()
        super.onDestroy()
    }

    /** Shares the state with the app and refreshes the notification. */
    private fun publish() {
        val shownEnd = if (playing) endsAt else if (pausedLeft > 0) -pausedLeft else 0L
        SoundPlayer.mutable.value = SoundState(sound, playing, shownEnd, forBreathe)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
    }

    private fun goForeground() {
        ensureChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
    }

    private fun notification(): Notification {
        val s = sound
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = when {
            s == null -> ""
            !playing -> "Paused"
            endsAt > 0 -> "Stops at " + android.text.format.DateFormat.getTimeFormat(this)
                .format(java.util.Date(System.currentTimeMillis() + (endsAt - SystemClock.elapsedRealtime())))
            else -> "Playing"
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_graphic_eq)
            .setContentTitle(s?.label ?: "Calming sounds")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(playing)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (s != null) {
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
        const val ACTION_PLAY = "com.trinineba.quotesonlock.sound.PLAY"
        const val ACTION_PAUSE = "com.trinineba.quotesonlock.sound.PAUSE"
        const val ACTION_RESUME = "com.trinineba.quotesonlock.sound.RESUME"
        const val ACTION_STOP = "com.trinineba.quotesonlock.sound.STOP"
        const val ACTION_EXTEND = "com.trinineba.quotesonlock.sound.EXTEND"
        const val ACTION_SETTINGS = "com.trinineba.quotesonlock.sound.SETTINGS"
        const val EXTRA_SOUND = "sound"
        const val EXTRA_BREATHE = "breathe"
        private const val CHANNEL_ID = "sounds"
        private const val NOTIFICATION_ID = 4207
        private const val FADE_MS = 60_000L
        private val ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }
}
