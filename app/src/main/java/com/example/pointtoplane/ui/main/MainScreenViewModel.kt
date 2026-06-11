package com.example.pointtoplane.ui.main

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pointtoplane.calibration.CalibrationManager
import com.example.pointtoplane.model.FlightInfo
import com.example.pointtoplane.model.OrientationState
import com.example.pointtoplane.repository.FlightMatcher
import com.example.pointtoplane.repository.FlightRepository
import com.example.pointtoplane.repository.RouteInfo
import com.example.pointtoplane.sensor.SensorOrientationManager
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "MainViewModel"
    private val LPF_ALPHA = 0.12f

    // ── Sensor ────────────────────────────────────────────────────────────────
    private val sensorManager = SensorOrientationManager(application)

    private val _orientation = MutableStateFlow(OrientationState())
    val orientation: StateFlow<OrientationState> = _orientation.asStateFlow()

    // ── Calibration ───────────────────────────────────────────────────────────
    val calibrationManager = CalibrationManager(application)

    private val _calibratedAzimuth = MutableStateFlow(0f)
    val correctedAzimuth: StateFlow<Float> = _calibratedAzimuth.asStateFlow()

    private val _calibratedElevation = MutableStateFlow(0f)
    val correctedElevation: StateFlow<Float> = _calibratedElevation.asStateFlow()

    // ── Radar View ────────────────────────────────────────────────────────────
    private val _radarAircraft = MutableStateFlow<List<RadarPlane>>(emptyList())
    val radarAircraft: StateFlow<List<RadarPlane>> = _radarAircraft.asStateFlow()

    private val _radarRangeKm = MutableStateFlow(30f)
    val radarRangeKm: StateFlow<Float> = _radarRangeKm.asStateFlow()

    fun toggleRadarRange() {
        _radarRangeKm.value = when (_radarRangeKm.value) {
            10f -> 30f
            30f -> 50f
            else -> 10f
        }
        Log.d(TAG, "Radar range toggled to: ${_radarRangeKm.value}km")
    }

    // ── Location ──────────────────────────────────────────────────────────────
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)
    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null
    private var hasFirstFix = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            currentLatitude = loc.latitude
            currentLongitude = loc.longitude
            Log.d(TAG, "Location fix: ${loc.latitude}, ${loc.longitude}")

            if (!hasFirstFix) {
                hasFirstFix = true
                onFirstLocationFix()
            }
        }
    }

    // ── Flight data ───────────────────────────────────────────────────────────
    private val flightRepository = FlightRepository()

    // ── UI State ──────────────────────────────────────────────────────────────
    private val _uiState = MutableStateFlow<PlaneFinderUiState>(PlaneFinderUiState.GpsAcquiring)
    val uiState: StateFlow<PlaneFinderUiState> = _uiState.asStateFlow()

    // ── Scan debounce ─────────────────────────────────────────────────────────
    private var debounceJob: Job? = null
    private var isScanning = false
    private val ELEVATION_TRIGGER_DEG = 10f
    private val DEBOUNCE_MS = 2000L
    private val SCAN_COOLDOWN_MS = 8000L
    private var lastScanMs = 0L

    init {
        // Init empty, started via onResume/onPause lifecycle
    }

    private var sensorJob: Job? = null
    private var radarPollJob: Job? = null

    fun onResume() {
        startSensorCollection()
        startLocationUpdates()
        startRadarPolling()
    }

    fun onPause() {
        sensorJob?.cancel()
        sensorJob = null
        radarPollJob?.cancel()
        radarPollJob = null
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing location updates: ${e.message}")
        }
    }

    private fun startRadarPolling() {
        radarPollJob?.cancel()
        radarPollJob = viewModelScope.launch {
            while (true) {
                val lat = currentLatitude
                val lon = currentLongitude
                if (lat != null && lon != null) {
                    try {
                        val list = withContext(Dispatchers.IO) {
                            flightRepository.getNearbyAircraft(lat, lon)
                        }
                        val mapped = list.mapNotNull { ac ->
                            val distM = FlightMatcher.haversineDistance(lat, lon, ac.latitude, ac.longitude)
                            if (distM <= 50000.0) {
                                val bearing = FlightMatcher.computeBearing(lat, lon, ac.latitude, ac.longitude)
                                RadarPlane(
                                    icao24 = ac.icao24,
                                    callsign = ac.callsign,
                                    distanceKm = (distM / 1000.0).toFloat(),
                                    bearingDeg = bearing,
                                    trackDeg = ac.trackDeg
                                )
                            } else null
                        }
                        _radarAircraft.value = mapped
                        Log.d(TAG, "Radar polling: found ${mapped.size} aircraft within 50km")
                    } catch (e: Exception) {
                        Log.e(TAG, "Radar polling failed: ${e.message}")
                    }
                } else {
                    Log.v(TAG, "Radar polling: waiting for GPS fix...")
                }
                delay(10_000L) // Poll every 10 seconds
            }
        }
    }

    // ── Location ──────────────────────────────────────────────────────────────

    fun retryLocationUpdates() {
        if (!hasFirstFix) startLocationUpdates()
    }

    private fun startLocationUpdates() {
        val hasPermission = ContextCompat.checkSelfPermission(
            getApplication(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.w(TAG, "Location permission not granted yet")
            return
        }

        try {
            val req = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10_000L)
                .setMinUpdateIntervalMillis(5_000L)
                .build()
            fusedLocationClient.requestLocationUpdates(req, locationCallback, null)
            Log.d(TAG, "Location updates requested")

            // Get last known location for an immediate lock if available
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null && !hasFirstFix) {
                    currentLatitude = loc.latitude
                    currentLongitude = loc.longitude
                    Log.d(TAG, "Last known location fix: ${loc.latitude}, ${loc.longitude}")
                    hasFirstFix = true
                    onFirstLocationFix()
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException requesting location: ${e.message}")
        }
    }

    private fun onFirstLocationFix() {
        Log.d(TAG, "First GPS fix! calibrated=${calibrationManager.isCalibrated}")
        startRadarPolling() // Trigger immediately upon location lock
        if (!calibrationManager.isCalibrated) {
            _uiState.value = PlaneFinderUiState.NeedCalibration
        } else {
            _uiState.value = PlaneFinderUiState.Idle
        }
    }

    // ── Calibration ───────────────────────────────────────────────────────────

    private var calibrationJob: Job? = null

    fun startCalibration() {
        calibrationJob?.cancel()
        calibrationJob = viewModelScope.launch {
            var seconds = 5
            while (seconds > 0) {
                _uiState.value = PlaneFinderUiState.Calibrating(
                    secondsLeft = seconds,
                    rawElevation = _orientation.value.elevationDeg
                )
                delay(1000L)
                seconds--
            }
            confirmCalibration()
        }
    }

    fun cancelCalibration() {
        calibrationJob?.cancel()
        calibrationJob = null
        _uiState.value = PlaneFinderUiState.NeedCalibration
    }

    private fun confirmCalibration() {
        calibrationJob = null
        val rawMatrix = _orientation.value.rotationMatrix
        calibrationManager.calibrateWithRotationMatrix(rawMatrix)

        val calibPair = calibrationManager.calculateOrientation(rawMatrix)
        _calibratedAzimuth.value = calibPair.first
        _calibratedElevation.value = calibPair.second

        // Vibrate to notify user of calibration completion
        try {
            val vibrator = getApplication<Application>().getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(300, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(300)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed: ${e.message}")
        }

        Log.d(TAG, "Calibrated! azimuth=${calibPair.first} elevation=${calibPair.second}")
        _uiState.value = PlaneFinderUiState.Idle
    }

    fun recalibrate() {
        calibrationManager.reset()
        startCalibration()
    }

    // ── Sensor collection ─────────────────────────────────────────────────────

    private fun startSensorCollection() {
        sensorJob?.cancel()
        sensorJob = viewModelScope.launch {
            sensorManager.orientationFlow.collectLatest { orientation ->
                _orientation.value = orientation
                handleOrientationChange(orientation)
            }
        }
    }

    private fun handleOrientationChange(orientation: OrientationState) {
        val state = _uiState.value

        // Always compute and update calibrated values
        val calibPair = calibrationManager.calculateOrientation(orientation.rotationMatrix)
        val rawCalibAz = calibPair.first
        val rawCalibElev = calibPair.second

        val deltaAz = azimuthDelta(rawCalibAz, _calibratedAzimuth.value)
        _calibratedAzimuth.value = ((_calibratedAzimuth.value + LPF_ALPHA * deltaAz) + 360f) % 360f
        _calibratedElevation.value += LPF_ALPHA * (rawCalibElev - _calibratedElevation.value)

        // While calibrating, continuously update raw elevation in the state
        if (state is PlaneFinderUiState.Calibrating) {
            _uiState.value = PlaneFinderUiState.Calibrating(
                secondsLeft = state.secondsLeft,
                rawElevation = orientation.elevationDeg
            )
            return
        }

        // Only auto-trigger when in Idle
        if (state != PlaneFinderUiState.Idle) return

        val calibrated = _calibratedElevation.value
        val now = System.currentTimeMillis()

        if (calibrated > ELEVATION_TRIGGER_DEG && !isScanning && (now - lastScanMs) > SCAN_COOLDOWN_MS) {
            if (debounceJob?.isActive != true) {
                debounceJob = viewModelScope.launch {
                    delay(DEBOUNCE_MS)
                    val stillUp = _calibratedElevation.value > ELEVATION_TRIGGER_DEG
                    if (stillUp && !isScanning && _uiState.value == PlaneFinderUiState.Idle) {
                        triggerScan()
                    }
                }
            }
        } else if (calibrated <= ELEVATION_TRIGGER_DEG) {
            debounceJob?.cancel()
            debounceJob = null
        }
    }

    private fun azimuthDelta(target: Float, current: Float): Float {
        var diff = target - current
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f
        return diff
    }

    // ── Scan ──────────────────────────────────────────────────────────────────

    fun triggerManualScan() {
        if (!isScanning && _uiState.value == PlaneFinderUiState.Idle) {
            viewModelScope.launch { triggerScan() }
        }
    }

    private suspend fun triggerScan() {
        isScanning = true
        lastScanMs = System.currentTimeMillis()
        _uiState.value = PlaneFinderUiState.Scanning

        val lat = currentLatitude
        val lon = currentLongitude
        if (lat == null || lon == null) {
            _uiState.value = PlaneFinderUiState.Error("GPS not available yet")
            isScanning = false
            return
        }

        val correctedOrientation = OrientationState(
            azimuthDeg = _calibratedAzimuth.value,
            elevationDeg = _calibratedElevation.value,
            rotationMatrix = _orientation.value.rotationMatrix
        )

        Log.d(TAG, "Scan: az=${correctedOrientation.azimuthDeg.toInt()}° correctedElev=${correctedOrientation.elevationDeg.toInt()}°")

        try {
            val aircraft = withContext<List<com.example.pointtoplane.model.RawAircraft>>(Dispatchers.IO) {
                flightRepository.getNearbyAircraft(lat, lon)
            }

            if (aircraft.isEmpty()) {
                _uiState.value = PlaneFinderUiState.NotFound
                isScanning = false
                return
            }

            val match = FlightMatcher.findBestMatch(lat, lon, correctedOrientation, aircraft)

            if (match == null) {
                _uiState.value = PlaneFinderUiState.NotFound
            } else {
                val (rawAircraft, angularDiff) = match
                val routeInfo = withContext<RouteInfo?>(Dispatchers.IO) {
                    flightRepository.getRouteInfo(rawAircraft.callsign)
                }
                val flightInfo = FlightMatcher.buildFlightInfo(rawAircraft, routeInfo, angularDiff)
                _uiState.value = PlaneFinderUiState.Found(flightInfo)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Scan failed: ${e.message}", e)
            _uiState.value = PlaneFinderUiState.Error("Network error. Check connection.")
        } finally {
            isScanning = false
        }
    }

    fun reset() {
        lastScanMs = 0L
        _uiState.value = PlaneFinderUiState.Idle
    }

    fun forceRefresh() {
        flightRepository.clearCache()
        triggerManualScan()
    }

    override fun onCleared() {
        super.onCleared()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        debounceJob?.cancel()
    }
}

sealed interface PlaneFinderUiState {
    data object GpsAcquiring : PlaneFinderUiState
    data object NeedCalibration : PlaneFinderUiState
    data class Calibrating(val secondsLeft: Int, val rawElevation: Float) : PlaneFinderUiState
    data object Idle : PlaneFinderUiState
    data object Scanning : PlaneFinderUiState
    data class Found(val flight: FlightInfo) : PlaneFinderUiState
    data object NotFound : PlaneFinderUiState
    data class Error(val message: String) : PlaneFinderUiState
}

data class RadarPlane(
    val icao24: String,
    val callsign: String,
    val distanceKm: Float,
    val bearingDeg: Float,
    val trackDeg: Float
)
