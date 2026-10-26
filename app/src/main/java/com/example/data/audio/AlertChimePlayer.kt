package com.example.data.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AlertChimePlayer(private val context: Context) {
    private var toneGenerator: ToneGenerator? = null
    private var lastPlayTime: Long = 0
    private val scope = CoroutineScope(Dispatchers.Default)

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        } catch (e: Exception) {
            Log.w("AlertChimePlayer", "Failed to init ToneGenerator: ${e.message}")
        }
    }

    fun playSpeedAlertChime(force: Boolean = false, silentMode: Boolean = false, haptic: Boolean = true) {
        val now = System.currentTimeMillis()
        // Minimum 5 seconds between chimes unless forced
        if (!force && (now - lastPlayTime < 5000)) {
            return
        }
        lastPlayTime = now

        // Vibrate if enabled
        if (haptic) {
            triggerHaptic()
        }

        // If silent mode is on, do not play audible sound
        if (silentMode) return

        scope.launch {
            try {
                // Subtle pleasant double chime: short high beep, pause, short higher beep
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                delay(160)
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 140)
            } catch (e: Exception) {
                Log.w("AlertChimePlayer", "Error playing chime: ${e.message}")
            }
        }
    }

    private fun triggerHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 100, 80, 150),
                        -1
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(250)
            }
        } catch (e: Exception) {
            Log.w("AlertChimePlayer", "Haptic error: ${e.message}")
        }
    }

    fun release() {
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (_: Exception) {}
    }
}
