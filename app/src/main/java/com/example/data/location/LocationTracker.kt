package com.example.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class GpsUpdate(
    val latitude: Double,
    val longitude: Double,
    val speedKmH: Double,
    val accuracy: Float,
    val bearing: Float = 0f,
    val altitude: Double = 0.0,
    val isSimulated: Boolean = false,
    val simulatedRoadName: String? = null,
    val simulatedSpeedLimit: Int? = null
)

class LocationTracker(private val context: Context) {
    private var fusedClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var locationManager: LocationManager? = null
    private var legacyListener: LocationListener? = null

    private val _gpsUpdates = MutableSharedFlow<GpsUpdate>(extraBufferCapacity = 10)
    val gpsUpdates: SharedFlow<GpsUpdate> = _gpsUpdates.asSharedFlow()

    private var simulationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    var isSimulating: Boolean = false
        private set

    init {
        try {
            fusedClient = LocationServices.getFusedLocationProviderClient(context)
            locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        } catch (e: Exception) {
            Log.w("LocationTracker", "Error initializing location providers: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun startLiveTracking() {
        stopSimulation()
        stopTracking()
        isSimulating = false

        // 1. Google Play Services Fused Location
        try {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(500L)
                .setMinUpdateDistanceMeters(0.5f)
                .build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { handleNewLocation(it, false) }
                }
            }

            fusedClient?.requestLocationUpdates(
                request,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e("LocationTracker", "Location permission missing: ${e.message}")
        } catch (e: Exception) {
            Log.w("LocationTracker", "FusedLocation failed, falling back to LocationManager: ${e.message}")
        }

        // 2. Hardware GPS Fallback
        try {
            legacyListener = LocationListener { location ->
                handleNewLocation(location, false)
            }
            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0.5f,
                legacyListener!!,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            Log.w("LocationTracker", "Legacy GPS provider failed: ${e.message}")
        }
    }

    fun stopTracking() {
        locationCallback?.let { fusedClient?.removeLocationUpdates(it) }
        locationCallback = null
        legacyListener?.let { locationManager?.removeUpdates(it) }
        legacyListener = null
    }

    private var previousLocation: Location? = null
    private var previousTimeMs: Long = 0L

    private fun handleNewLocation(loc: Location, isSimulated: Boolean) {
        val now = System.currentTimeMillis()
        var speedKmH = if (loc.hasSpeed() && loc.speed > 0f) {
            loc.speed * 3.6 // m/s to km/h
        } else {
            // Compute from delta distance/time if GPS speed is unavailable
            previousLocation?.let { prev ->
                val dt = (now - previousTimeMs) / 1000.0
                if (dt > 0.3) {
                    val dist = prev.distanceTo(loc).toDouble()
                    (dist / dt) * 3.6
                } else null
            } ?: 0.0
        }

        if (speedKmH < 1.0) speedKmH = 0.0 // eliminate GPS drift when stationary

        // Compute reliable bearing: use hardware bearing if valid, else calculate from GPS trajectory
        val bearing = if (loc.hasBearing() && (loc.bearing != 0f || speedKmH > 3.0)) {
            loc.bearing
        } else if (previousLocation != null && previousLocation!!.distanceTo(loc) >= 2.0f) {
            calculateGpsBearing(
                previousLocation!!.latitude,
                previousLocation!!.longitude,
                loc.latitude,
                loc.longitude
            )
        } else {
            previousLocation?.bearing ?: 0f
        }

        previousLocation = loc
        previousTimeMs = now

        scope.launch {
            _gpsUpdates.emit(
                GpsUpdate(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    speedKmH = speedKmH,
                    accuracy = loc.accuracy,
                    bearing = bearing,
                    altitude = loc.altitude,
                    isSimulated = isSimulated
                )
            )
        }
    }

    private fun calculateGpsBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)
        val y = Math.sin(deltaLambda) * Math.cos(phi2)
        val x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(deltaLambda)
        val theta = Math.atan2(y, x)
        return ((Math.toDegrees(theta) + 360.0) % 360.0).toFloat()
    }

    enum class SimulationScenario(val displayName: String) {
        SPEEDING_DEMO("Speeding Alert Demo (+14 km/h Over)")
    }

    fun startSpeedingDemo() {
        stopTracking()
        simulationJob?.cancel()
        isSimulating = true

        simulationJob = scope.launch {
            var step = 0
            while (isActive) {
                step++
                // Alternates between compliant 47 km/h and overspeed 64 km/h (+14 km/h over 50 limit)
                val isOver = (step % 10) in 3..7
                val speed = if (isOver) 64.0 else 47.0

                val update = GpsUpdate(
                    latitude = 50.8503 + (step * 0.0001),
                    longitude = 4.3517 + (step * 0.0001),
                    speedKmH = speed,
                    accuracy = 2.0f,
                    isSimulated = true,
                    simulatedRoadName = "City Avenue (50 km/h Limit)",
                    simulatedSpeedLimit = 50
                )
                _gpsUpdates.emit(update)
                delay(1000)
            }
        }
    }

    fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
        isSimulating = false
    }
}
