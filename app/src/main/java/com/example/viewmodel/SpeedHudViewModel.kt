package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.audio.AlertChimePlayer
import com.example.data.db.AppDatabase
import com.example.data.db.SpeedingIncidentEntity
import com.example.data.location.GpsUpdate
import com.example.data.location.LocationTracker
import com.example.data.model.DrivingState
import com.example.data.model.HudSettings
import com.example.data.model.ScreenOrientationLock
import com.example.data.model.SpeedUnit
import com.example.data.network.OsmSpeedService
import com.example.data.preferences.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

class SpeedHudViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val speedLogDao = db.speedLogDao()
    private val osmService = OsmSpeedService()
    private val chimePlayer = AlertChimePlayer(application)
    private val locationTracker = LocationTracker(application)
    private val settingsRepo = SettingsRepository(application)

    private val _settings = MutableStateFlow(settingsRepo.loadSettings())
    val settings: StateFlow<HudSettings> = _settings.asStateFlow()

    private val _drivingState = MutableStateFlow(DrivingState())
    val drivingState: StateFlow<DrivingState> = _drivingState.asStateFlow()

    val speedingIncidents: StateFlow<List<SpeedingIncidentEntity>> = speedLogDao.getAllIncidents()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private var amoledBurnInJob: Job? = null
    private var lastSpeedingLogTime: Long = 0
    private var lastQueriedLat: Double = 0.0
    private var lastQueriedLon: Double = 0.0
    private var tripStartTime: Long = System.currentTimeMillis()
    private var speedReadingsCount: Long = 0
    private var speedSumKmH: Double = 0.0

    init {
        startAmoledBurnInProtection()
        observeGps()
        locationTracker.startLiveTracking()
    }

    private fun observeGps() {
        viewModelScope.launch {
            locationTracker.gpsUpdates.collect { update ->
                handleGpsUpdate(update)
            }
        }
    }

    private fun handleGpsUpdate(update: GpsUpdate) {
        viewModelScope.launch {
            val currentSettings = _settings.value
            val isMph = currentSettings.speedUnit == SpeedUnit.MPH
            var rawSpeedLimitKmH = _drivingState.value.currentSpeedLimitKmH
            var isUnlimited = _drivingState.value.isUnlimited
            var roadName = _drivingState.value.roadName
            var roadType = _drivingState.value.roadType
            var limitSource = _drivingState.value.speedLimitSource
            val cardinalDir = osmService.getCardinalDirection(update.bearing)

            if (currentSettings.manualSpeedLimitOverride != null) {
                val manualVal = currentSettings.manualSpeedLimitOverride
                val manualUnit = currentSettings.manualSpeedLimitUnit ?: currentSettings.speedUnit
                rawSpeedLimitKmH = if (manualUnit == SpeedUnit.MPH) {
                    (manualVal / 0.621371).roundToInt()
                } else {
                    manualVal
                }
                roadName = "Manual Limit"
                limitSource = "Manual Override"
            } else if (update.latitude != 0.0 && update.longitude != 0.0) {
                try {
                    val osmInfo = osmService.getRoadInfo(
                        latitude = update.latitude,
                        longitude = update.longitude,
                        bearing = update.bearing,
                        speedKmH = update.speedKmH,
                        isMph = isMph
                    )
                    rawSpeedLimitKmH = osmInfo.speedLimitKmH
                    isUnlimited = osmInfo.isUnlimited
                    roadName = osmInfo.name
                    roadType = osmInfo.roadType
                    limitSource = osmInfo.source
                    lastQueriedLat = update.latitude
                    lastQueriedLon = update.longitude
                } catch (e: Exception) {
                    Log.w("SpeedHudViewModel", "Road lookup error: ${e.message}")
                }
            }

            // Calculate displayed speed limit in user's active unit
            val displayedLimit: Int = if (currentSettings.manualSpeedLimitOverride != null) {
                val manualVal = currentSettings.manualSpeedLimitOverride
                val manualUnit = currentSettings.manualSpeedLimitUnit ?: currentSettings.speedUnit
                if (manualUnit == currentSettings.speedUnit) {
                    manualVal
                } else if (currentSettings.speedUnit == SpeedUnit.MPH) {
                    (manualVal * 0.621371).roundToInt()
                } else {
                    (manualVal / 0.621371).roundToInt()
                }
            } else {
                if (currentSettings.speedUnit == SpeedUnit.KMH) {
                    rawSpeedLimitKmH
                } else {
                    (rawSpeedLimitKmH * 0.621371).roundToInt()
                }
            }

            val currentSpeedDisplay = if (currentSettings.speedUnit == SpeedUnit.KMH) {
                update.speedKmH
            } else {
                update.speedKmH * 0.621371
            }

            // Exceeding alert threshold check in current active unit
            val warningThreshold = currentSettings.warningThresholdKmH
            val deltaInUnit = currentSpeedDisplay - displayedLimit
            val isExceeding = !isUnlimited && (deltaInUnit > warningThreshold)

            // Audio & Haptic alert
            if (isExceeding) {
                chimePlayer.playSpeedAlertChime(
                    force = false,
                    silentMode = currentSettings.silentMode,
                    haptic = currentSettings.hapticFeedbackEnabled
                )

                // Log incident to Room DB throttled to once per 25 seconds
                val now = System.currentTimeMillis()
                if (now - lastSpeedingLogTime > 25_000) {
                    lastSpeedingLogTime = now
                    speedLogDao.insertIncident(
                        SpeedingIncidentEntity(
                            roadName = roadName,
                            speedLimitKmH = rawSpeedLimitKmH,
                            recordedSpeedKmH = update.speedKmH.toInt(),
                            deltaKmH = (update.speedKmH - rawSpeedLimitKmH).toInt(),
                            latitude = update.latitude,
                            longitude = update.longitude
                        )
                    )
                }
            }

            // Trip calculation
            speedReadingsCount++
            speedSumKmH += update.speedKmH
            val avgSpeed = if (speedReadingsCount > 0) speedSumKmH / speedReadingsCount else 0.0
            val maxSpeed = maxOf(_drivingState.value.maxSpeedKmH, update.speedKmH)
            val addedDist = (update.speedKmH / 3.6) * 1.0 // approx distance in 1 sec
            val totalDist = _drivingState.value.tripDistanceMeters + addedDist
            val tripDuration = (System.currentTimeMillis() - tripStartTime) / 1000

            val headingText = if (update.speedKmH >= 3.0 || update.bearing != 0f) {
                "$cardinalDir (${update.bearing.roundToInt()}°)"
            } else {
                cardinalDir
            }

            _drivingState.value = _drivingState.value.copy(
                currentSpeedKmH = update.speedKmH,
                currentSpeedDisplay = currentSpeedDisplay,
                displayedSpeedLimit = displayedLimit,
                currentSpeedLimitKmH = rawSpeedLimitKmH,
                isUnlimited = isUnlimited,
                roadName = roadName,
                roadType = roadType,
                isSpeeding = isExceeding,
                speedDeltaKmH = deltaInUnit,
                latitude = update.latitude,
                longitude = update.longitude,
                accuracyMeters = update.accuracy,
                bearing = update.bearing,
                cardinalDirection = cardinalDir,
                speedLimitSource = limitSource,
                tripDistanceMeters = totalDist,
                tripDurationSeconds = tripDuration,
                maxSpeedKmH = maxSpeed,
                avgSpeedKmH = avgSpeed,
                isSimulated = false,
                isGpsActive = true,
                gpsStatusMessage = "GPS Lock"
            )
        }
    }

    private fun recomputeDrivingStateDisplay() {
        val currentSettings = _settings.value
        val state = _drivingState.value
        val displayedLimit: Int = if (currentSettings.manualSpeedLimitOverride != null) {
            val manualVal = currentSettings.manualSpeedLimitOverride
            val manualUnit = currentSettings.manualSpeedLimitUnit ?: currentSettings.speedUnit
            if (manualUnit == currentSettings.speedUnit) {
                manualVal
            } else if (currentSettings.speedUnit == SpeedUnit.MPH) {
                (manualVal * 0.621371).roundToInt()
            } else {
                (manualVal / 0.621371).roundToInt()
            }
        } else {
            if (currentSettings.speedUnit == SpeedUnit.KMH) {
                state.currentSpeedLimitKmH
            } else {
                (state.currentSpeedLimitKmH * 0.621371).roundToInt()
            }
        }

        val currentSpeedDisplay = if (currentSettings.speedUnit == SpeedUnit.KMH) {
            state.currentSpeedKmH
        } else {
            state.currentSpeedKmH * 0.621371
        }

        val deltaInUnit = currentSpeedDisplay - displayedLimit
        val isExceeding = !state.isUnlimited && (deltaInUnit > currentSettings.warningThresholdKmH)

        val updatedRoadName = if (currentSettings.manualSpeedLimitOverride != null) {
            "Manual Limit"
        } else {
            state.roadName
        }

        _drivingState.value = state.copy(
            displayedSpeedLimit = displayedLimit,
            currentSpeedDisplay = currentSpeedDisplay,
            isSpeeding = isExceeding,
            speedDeltaKmH = deltaInUnit,
            roadName = updatedRoadName
        )
    }

    fun setManualSpeedLimit(limit: Int?) {
        val unit = if (limit != null) _settings.value.speedUnit else null
        _settings.value = _settings.value.copy(
            manualSpeedLimitOverride = limit,
            manualSpeedLimitUnit = unit
        )
        settingsRepo.saveSettings(_settings.value)

        if (limit == null) {
            // Reset to automatic GPS limit: invalidate cache and fetch immediate real street name
            osmService.clearCache()
            lastQueriedLat = 0.0
            lastQueriedLon = 0.0
            _drivingState.value = _drivingState.value.copy(
                roadName = "Locating Road..."
            )
            refreshCurrentRoadFromGps()
        } else {
            _drivingState.value = _drivingState.value.copy(
                roadName = "Manual Limit"
            )
            recomputeDrivingStateDisplay()
        }
    }

    private fun refreshCurrentRoadFromGps() {
        val lat = _drivingState.value.latitude
        val lon = _drivingState.value.longitude
        val bearing = _drivingState.value.bearing
        val speedKmH = _drivingState.value.currentSpeedKmH
        val currentSettings = _settings.value
        val isMph = currentSettings.speedUnit == SpeedUnit.MPH

        if (lat != 0.0 && lon != 0.0) {
            viewModelScope.launch {
                try {
                    val roadInfo = osmService.getRoadInfo(
                        latitude = lat,
                        longitude = lon,
                        bearing = bearing,
                        speedKmH = speedKmH,
                        isMph = isMph
                    )
                    lastQueriedLat = lat
                    lastQueriedLon = lon

                    val displayedLimit = if (isMph) roadInfo.speedLimitMph else roadInfo.speedLimitKmH
                    val currentSpeedDisplay = if (isMph) _drivingState.value.currentSpeedKmH * 0.621371 else _drivingState.value.currentSpeedKmH
                    val delta = currentSpeedDisplay - displayedLimit
                    val isExceeding = !roadInfo.isUnlimited && (delta > currentSettings.warningThresholdKmH)

                    val cardinal = roadInfo.directionName.ifEmpty { osmService.getCardinalDirection(bearing) }
                    val headingText = if (speedKmH >= 3.0 || bearing != 0f) {
                        "$cardinal (${bearing.roundToInt()}°)"
                    } else {
                        cardinal
                    }

                    _drivingState.value = _drivingState.value.copy(
                        currentSpeedLimitKmH = roadInfo.speedLimitKmH,
                        displayedSpeedLimit = displayedLimit,
                        isUnlimited = roadInfo.isUnlimited,
                        roadName = roadInfo.name,
                        roadType = roadInfo.roadType,
                        isSpeeding = isExceeding,
                        speedDeltaKmH = delta,
                        speedLimitSource = roadInfo.source,
                        cardinalDirection = cardinal,
                        gpsStatusMessage = "GPS Lock"
                    )
                } catch (e: Exception) {
                    Log.w("SpeedHudViewModel", "Failed to refresh road: ${e.message}")
                    recomputeDrivingStateDisplay()
                }
            }
        } else {
            recomputeDrivingStateDisplay()
        }
    }

    fun setAmoledSignStyle(style: com.example.data.model.AmoledSignStyle) {
        _settings.value = _settings.value.copy(amoledSignStyle = style)
        settingsRepo.saveSettings(_settings.value)
    }

    fun setPixelAlternationInterval(seconds: Int) {
        _settings.value = _settings.value.copy(pixelAlternationIntervalSeconds = seconds)
        settingsRepo.saveSettings(_settings.value)
    }

    private fun startAmoledBurnInProtection() {
        amoledBurnInJob?.cancel()
        amoledBurnInJob = viewModelScope.launch {
            while (isActive) {
                val settingsVal = _settings.value
                val intervalSec = settingsVal.amoledShiftIntervalSeconds.coerceAtLeast(10)
                delay(intervalSec * 1000L)

                if (settingsVal.amoledProtectionEnabled) {
                    val targetX = (Random.nextFloat() * 48f) - 24f
                    val targetY = (Random.nextFloat() * 40f) - 20f

                    _drivingState.value = _drivingState.value.copy(
                        amoledOffsetX = targetX,
                        amoledOffsetY = targetY
                    )
                } else {
                    _drivingState.value = _drivingState.value.copy(
                        amoledOffsetX = 0f,
                        amoledOffsetY = 0f
                    )
                }
            }
        }
    }

    fun setAmoledInterval(seconds: Int) {
        _settings.value = _settings.value.copy(amoledShiftIntervalSeconds = seconds)
        settingsRepo.saveSettings(_settings.value)
        startAmoledBurnInProtection()
    }

    fun toggleAmoledProtection() {
        _settings.value = _settings.value.copy(amoledProtectionEnabled = !_settings.value.amoledProtectionEnabled)
        settingsRepo.saveSettings(_settings.value)
    }

    fun toggleSilentMode() {
        _settings.value = _settings.value.copy(silentMode = !_settings.value.silentMode)
        settingsRepo.saveSettings(_settings.value)
    }

    fun toggleSpeedUnit() {
        val newUnit = if (_settings.value.speedUnit == SpeedUnit.KMH) SpeedUnit.MPH else SpeedUnit.KMH
        _settings.value = _settings.value.copy(speedUnit = newUnit)
        settingsRepo.saveSettings(_settings.value)
        // Refresh with unit-specific speed laws
        osmService.clearCache()
        lastQueriedLat = 0.0
        lastQueriedLon = 0.0
        refreshCurrentRoadFromGps()
    }

    fun setWarningThreshold(thresholdKmH: Int) {
        _settings.value = _settings.value.copy(warningThresholdKmH = thresholdKmH)
        settingsRepo.saveSettings(_settings.value)
        recomputeDrivingStateDisplay()
    }

    fun toggleHudMirrorMode() {
        _settings.value = _settings.value.copy(hudMirrorMode = !_settings.value.hudMirrorMode)
        settingsRepo.saveSettings(_settings.value)
    }

    fun setNightDimLevel(dim: Float) {
        _settings.value = _settings.value.copy(nightDimLevel = dim)
        settingsRepo.saveSettings(_settings.value)
    }

    fun setOrientationLock(lock: ScreenOrientationLock) {
        _settings.value = _settings.value.copy(orientationLock = lock)
        settingsRepo.saveSettings(_settings.value)
    }

    fun setHapticFeedback(enabled: Boolean) {
        _settings.value = _settings.value.copy(hapticFeedbackEnabled = enabled)
        settingsRepo.saveSettings(_settings.value)
    }

    fun switchToLiveGps() {
        locationTracker.startLiveTracking()
        _drivingState.value = _drivingState.value.copy(
            isSimulated = false,
            gpsStatusMessage = "Connecting to GPS..."
        )
    }

    fun resetTrip() {
        tripStartTime = System.currentTimeMillis()
        speedReadingsCount = 0
        speedSumKmH = 0.0
        _drivingState.value = _drivingState.value.copy(
            tripDistanceMeters = 0.0,
            tripDurationSeconds = 0,
            maxSpeedKmH = 0.0,
            avgSpeedKmH = 0.0
        )
    }

    fun clearSpeedingLogs() {
        viewModelScope.launch {
            speedLogDao.clearIncidents()
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        if (lat1 == 0.0 || lat2 == 0.0) return 9999.0
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    override fun onCleared() {
        super.onCleared()
        locationTracker.stopTracking()
        chimePlayer.release()
        amoledBurnInJob?.cancel()
    }
}
