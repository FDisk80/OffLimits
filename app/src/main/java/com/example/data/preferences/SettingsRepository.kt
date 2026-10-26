package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.HudSettings
import com.example.data.model.ScreenOrientationLock
import com.example.data.model.SpeedUnit

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("speed_hud_preferences", Context.MODE_PRIVATE)

    fun loadSettings(): HudSettings {
        val unitStr = prefs.getString("speed_unit", SpeedUnit.KMH.name) ?: SpeedUnit.KMH.name
        val speedUnit = try {
            SpeedUnit.valueOf(unitStr)
        } catch (e: Exception) {
            SpeedUnit.KMH
        }

        val orientStr = prefs.getString("orientation_lock", ScreenOrientationLock.AUTO.name)
            ?: ScreenOrientationLock.AUTO.name
        val orientationLock = try {
            ScreenOrientationLock.valueOf(orientStr)
        } catch (e: Exception) {
            ScreenOrientationLock.AUTO
        }

        val manualLimit = if (prefs.contains("manual_speed_limit")) {
            prefs.getInt("manual_speed_limit", 50)
        } else null

        val manualUnitStr = prefs.getString("manual_speed_limit_unit", null)
        val manualUnit = if (manualUnitStr != null) {
            try { SpeedUnit.valueOf(manualUnitStr) } catch (e: Exception) { null }
        } else null

        val amoledStyleStr = prefs.getString("amoled_sign_style", com.example.data.model.AmoledSignStyle.PIXEL_MESH.name)
            ?: com.example.data.model.AmoledSignStyle.PIXEL_MESH.name
        val amoledSignStyle = try {
            com.example.data.model.AmoledSignStyle.valueOf(amoledStyleStr)
        } catch (e: Exception) {
            com.example.data.model.AmoledSignStyle.PIXEL_MESH
        }

        return HudSettings(
            speedUnit = speedUnit,
            warningThresholdKmH = prefs.getInt("warning_threshold", 5),
            silentMode = prefs.getBoolean("silent_mode", false),
            visualAlertsEnabled = prefs.getBoolean("visual_alerts", true),
            hapticFeedbackEnabled = prefs.getBoolean("haptic_feedback", false),
            amoledProtectionEnabled = prefs.getBoolean("amoled_protection", true),
            amoledShiftIntervalSeconds = prefs.getInt("amoled_shift_interval", 20),
            amoledSignStyle = amoledSignStyle,
            pixelAlternationIntervalSeconds = prefs.getInt("pixel_alternation_interval", 20),
            nightDimLevel = prefs.getFloat("night_dim_level", 1.0f),
            hudMirrorMode = prefs.getBoolean("hud_mirror_mode", false),
            manualSpeedLimitOverride = manualLimit,
            manualSpeedLimitUnit = manualUnit,
            orientationLock = orientationLock
        )
    }

    fun saveSettings(settings: HudSettings) {
        prefs.edit().apply {
            putString("speed_unit", settings.speedUnit.name)
            putString("orientation_lock", settings.orientationLock.name)
            putString("amoled_sign_style", settings.amoledSignStyle.name)
            putInt("pixel_alternation_interval", settings.pixelAlternationIntervalSeconds)
            putInt("warning_threshold", settings.warningThresholdKmH)
            putBoolean("silent_mode", settings.silentMode)
            putBoolean("visual_alerts", settings.visualAlertsEnabled)
            putBoolean("haptic_feedback", settings.hapticFeedbackEnabled)
            putBoolean("amoled_protection", settings.amoledProtectionEnabled)
            putInt("amoled_shift_interval", settings.amoledShiftIntervalSeconds)
            putFloat("night_dim_level", settings.nightDimLevel)
            putBoolean("hud_mirror_mode", settings.hudMirrorMode)
            if (settings.manualSpeedLimitOverride != null) {
                putInt("manual_speed_limit", settings.manualSpeedLimitOverride)
                if (settings.manualSpeedLimitUnit != null) {
                    putString("manual_speed_limit_unit", settings.manualSpeedLimitUnit.name)
                }
            } else {
                remove("manual_speed_limit")
                remove("manual_speed_limit_unit")
            }
            apply()
        }
    }
}
