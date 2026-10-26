package com.example.data.model

import kotlin.math.roundToInt

enum class SpeedUnit(val label: String, val speedMultiplier: Double) {
    KMH("km/h", 1.0),
    MPH("mph", 0.621371)
}

enum class ScreenOrientationLock(val label: String) {
    AUTO("Auto"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

enum class AmoledSignStyle(val title: String, val description: String) {
    PIXEL_MESH(
        "Alternating Pixel Mesh (50% Off)",
        "Alternates adjacent pixels between pitch-black and active colors every 20s. Protects large white/red areas."
    ),
    STEALTH_STENCIL(
        "Ultra-Black Stencil (85% Off)",
        "Turns the large white sign area pitch-black (#000000) with a glowing red ring and white digits."
    ),
    SOLID_CLASSIC(
        "Solid Classic Fill",
        "Full solid white fill with coordinate orbit shift only."
    )
}

data class RoadInfo(
    val name: String,
    val speedLimitKmH: Int,
    val speedLimitMph: Int = (speedLimitKmH * 0.621371).roundToInt(),
    val roadType: String = "general",
    val isUnlimited: Boolean = false,
    val source: String = "OpenStreetMap",
    val directionName: String = "",
    val bearing: Float = 0f,
    val isOneway: Boolean = false,
    val confidence: String = "High"
)

data class HudSettings(
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val warningThresholdKmH: Int = 5,
    val silentMode: Boolean = false,
    val visualAlertsEnabled: Boolean = true,
    val hapticFeedbackEnabled: Boolean = false,
    val amoledProtectionEnabled: Boolean = true,
    val amoledShiftIntervalSeconds: Int = 20,
    val amoledSignStyle: AmoledSignStyle = AmoledSignStyle.PIXEL_MESH,
    val pixelAlternationIntervalSeconds: Int = 20,
    val nightDimLevel: Float = 1.0f,
    val hudMirrorMode: Boolean = false,
    val manualSpeedLimitOverride: Int? = null,
    val manualSpeedLimitUnit: SpeedUnit? = null,
    val orientationLock: ScreenOrientationLock = ScreenOrientationLock.AUTO
)

data class DrivingState(
    val currentSpeedDisplay: Double = 0.0,
    val currentSpeedKmH: Double = 0.0,
    val displayedSpeedLimit: Int = 50,
    val currentSpeedLimitKmH: Int = 50,
    val isUnlimited: Boolean = false,
    val roadName: String = "Locating Road...",
    val roadType: String = "Road",
    val isSpeeding: Boolean = false,
    val speedDeltaKmH: Double = 0.0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracyMeters: Float = 0f,
    val bearing: Float = 0f,
    val cardinalDirection: String = "N",
    val speedLimitSource: String = "GPS Auto",
    val tripDistanceMeters: Double = 0.0,
    val tripDurationSeconds: Long = 0,
    val maxSpeedKmH: Double = 0.0,
    val avgSpeedKmH: Double = 0.0,
    val isSimulated: Boolean = false,
    val amoledOffsetX: Float = 0f,
    val amoledOffsetY: Float = 0f,
    val isGpsActive: Boolean = false,
    val gpsStatusMessage: String = "Ready"
)
