package com.javargasp.tiominutermvi.data.feedback

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.javargasp.tiominutermvi.domain.feedback.WorkoutFeedback

/**
 * Android implementation of [WorkoutFeedback].
 *
 * ToneGenerator: one instance per tone, created and released on a background
 * thread (it is expensive to build and must not block the UI thread). It is
 * always released in a `finally` block, so it is never leaked — the previous
 * code created one per call and never released it.
 *
 * All feedback failures are caught and silently ignored.
 */
class AndroidWorkoutFeedback(private val context: Context) : WorkoutFeedback {

    override fun playCompletionSound() {
        // ToneGenerator construction can block; keep it off the main thread.
        Thread {
            try {
                val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                try {
                    generator.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
                    // Let the tone finish before releasing the generator.
                    Thread.sleep(250)
                } finally {
                    try {
                        generator.release()
                    } catch (_: Exception) {
                        // Release failures are also non-fatal.
                    }
                }
            } catch (e: Exception) {
                // Silently fail if sound cannot be played.
            }
        }.start()
    }

    override fun vibrate() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        200, // 200ms vibration
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(200)
            }
        } catch (e: Exception) {
            // Silently fail if vibration is not available.
        }
    }
}
