package com.trinineba.quotesonlock

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Soft vibrations for the breathing exercises. These use the vibrator directly, not the view's touch feedback,
 * which many phones ignore when the screen is not being touched or when touch vibration is turned off.
 */
object Haptics {
    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    /** A single soft pulse: [strong] for breathing in, lighter for breathing out or holding. */
    fun breath(context: Context, strong: Boolean = true) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        val ms = if (strong) 90L else 45L
        val amplitude = if (v.hasAmplitudeControl()) (if (strong) 140 else 80) else VibrationEffect.DEFAULT_AMPLITUDE
        val effect = VibrationEffect.createOneShot(ms, amplitude)
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                v.vibrate(effect, android.os.VibrationAttributes.createForUsage(android.os.VibrationAttributes.USAGE_MEDIA))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            }
        }
    }
}
