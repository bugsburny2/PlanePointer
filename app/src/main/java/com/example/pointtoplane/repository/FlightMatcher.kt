package com.example.pointtoplane.repository

import android.util.Log
import com.example.pointtoplane.model.FlightInfo
import com.example.pointtoplane.model.OrientationState
import com.example.pointtoplane.model.RawAircraft
import com.example.pointtoplane.repository.RouteInfo
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Matches the user's pointing direction to the closest aircraft in the sky.
 *
 * Algorithm:
 * 1. For each aircraft, compute its azimuth and elevation as seen from the user's GPS location
 * 2. Compute the angular separation between the "look ray" and the aircraft direction
 * 3. Return the aircraft with the smallest angular separation (if within threshold)
 */
object FlightMatcher {

    private const val TAG = "FlightMatcher"

    /**
     * Maximum angular tolerance in degrees.
     * A plane within 15° of the pointing direction is considered a match.
     * Commercial planes at cruise altitude (10 km) subtend only ~0.05° of arc,
     * but pointing accuracy is limited by sensor noise, so we use a generous window.
     */
    const val MATCH_THRESHOLD_DEG = 15f

    /**
     * Finds the best-matching aircraft for the given pointing direction.
     *
     * @param userLat User latitude in decimal degrees
     * @param userLon User longitude in decimal degrees
     * @param orientation The current watch pointing direction (azimuth + elevation)
     * @param aircraft List of nearby aircraft from the ADS-B API
     * @return The best-matching [RawAircraft] and its computed angular difference, or null
     */
    fun findBestMatch(
        userLat: Double,
        userLon: Double,
        orientation: OrientationState,
        aircraft: List<RawAircraft>
    ): Pair<RawAircraft, Float>? {
        if (aircraft.isEmpty()) return null

        var bestAircraft: RawAircraft? = null
        var bestAngularDiff = Float.MAX_VALUE

        for (ac in aircraft) {
            val bearing = computeBearing(userLat, userLon, ac.latitude, ac.longitude)
            val horizontalDistM = haversineDistance(userLat, userLon, ac.latitude, ac.longitude)
            val elevationDeg = computeElevation(ac.altitudeMeters, horizontalDistM)

            val angularDiff = sphericalAngularDiff(
                orientation.azimuthDeg, orientation.elevationDeg,
                bearing, elevationDeg
            )

            Log.v(TAG, "Aircraft ${ac.callsign.ifEmpty { ac.icao24 }}: bearing=${bearing.toInt()}° elev=${elevationDeg.toInt()}° diff=${angularDiff.toInt()}°")

            if (angularDiff < bestAngularDiff) {
                bestAngularDiff = angularDiff
                bestAircraft = ac
            }
        }

        return if (bestAngularDiff <= MATCH_THRESHOLD_DEG && bestAircraft != null) {
            Log.d(TAG, "Best match: ${bestAircraft.callsign} (diff=${bestAngularDiff}°)")
            Pair(bestAircraft, bestAngularDiff)
        } else {
            Log.d(TAG, "No match found. Best was ${bestAngularDiff}° (threshold=${MATCH_THRESHOLD_DEG}°)")
            null
        }
    }

    /**
     * Builds a [FlightInfo] from a matched [RawAircraft] and optional route data.
     */
    fun buildFlightInfo(aircraft: RawAircraft, routeInfo: RouteInfo?, angularDiff: Float): FlightInfo {
        return FlightInfo(
            icao24 = aircraft.icao24,
            callsign = aircraft.callsign,
            airline = routeInfo?.airline ?: "",
            aircraftTypeIcao = aircraft.aircraftTypeIcao,
            aircraftTypeName = resolveAircraftTypeName(aircraft.aircraftTypeIcao),
            originIata = routeInfo?.originIata ?: "",
            originName = routeInfo?.originName ?: "",
            destinationIata = routeInfo?.destinationIata ?: "",
            destinationName = routeInfo?.destinationName ?: "",
            altitudeMeters = aircraft.altitudeMeters,
            speedKnots = aircraft.groundSpeedKnots,
            angularDiffDeg = angularDiff
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Geometric computations
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Computes the forward azimuth (bearing) from point 1 to point 2.
     * @return Bearing in degrees, 0–360°, clockwise from North
     */
    fun computeBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val dLonRad = Math.toRadians(lon2 - lon1)

        val y = sin(dLonRad) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLonRad)

        val bearingRad = atan2(y, x)
        return ((Math.toDegrees(bearingRad) + 360.0) % 360.0).toFloat()
    }

    /**
     * Computes the elevation angle from the user's position to the aircraft.
     * @param altitudeM Aircraft altitude in meters above sea level (approximation: above user)
     * @param horizontalDistM Horizontal distance to aircraft in meters
     * @return Elevation angle in degrees (0° = horizon, 90° = directly overhead)
     */
    fun computeElevation(altitudeM: Double, horizontalDistM: Double): Float {
        if (horizontalDistM < 1.0) return 90f
        return Math.toDegrees(atan2(altitudeM, horizontalDistM)).toFloat()
    }

    /**
     * Computes the great-circle distance between two GPS points using the Haversine formula.
     * @return Distance in meters
     */
    fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6_371_000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        return R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * Computes the angular separation between two directions in spherical space.
     * Uses the dot-product formula for robustness near the poles (zenith/nadir).
     *
     * Both inputs are (azimuth, elevation) pairs in degrees.
     * @return Angular separation in degrees (0 = same direction, 180 = opposite)
     */
    fun sphericalAngularDiff(
        az1: Float, el1: Float,
        az2: Float, el2: Float
    ): Float {
        val az1Rad = Math.toRadians(az1.toDouble())
        val el1Rad = Math.toRadians(el1.toDouble())
        val az2Rad = Math.toRadians(az2.toDouble())
        val el2Rad = Math.toRadians(el2.toDouble())

        // Convert spherical to Cartesian unit vectors
        val x1 = cos(el1Rad) * cos(az1Rad)
        val y1 = cos(el1Rad) * sin(az1Rad)
        val z1 = sin(el1Rad)
        val x2 = cos(el2Rad) * cos(az2Rad)
        val y2 = cos(el2Rad) * sin(az2Rad)
        val z2 = sin(el2Rad)

        // Dot product → angle
        val dot = (x1 * x2 + y1 * y2 + z1 * z2).coerceIn(-1.0, 1.0)
        return Math.toDegrees(kotlin.math.acos(dot)).toFloat()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Aircraft type name lookup (common ICAO type codes)
    // ──────────────────────────────────────────────────────────────────────────

    private val aircraftTypeNames = mapOf(
        // Boeing narrow-body
        "B737" to "Boeing 737", "B738" to "Boeing 737-800", "B739" to "Boeing 737-900",
        "B735" to "Boeing 737-500", "B736" to "Boeing 737-600", "B737" to "Boeing 737-700",
        "B38M" to "Boeing 737 MAX 8", "B39M" to "Boeing 737 MAX 9",
        // Boeing wide-body
        "B744" to "Boeing 747-400", "B748" to "Boeing 747-8",
        "B752" to "Boeing 757-200", "B753" to "Boeing 757-300",
        "B762" to "Boeing 767-200", "B763" to "Boeing 767-300", "B764" to "Boeing 767-400",
        "B772" to "Boeing 777-200", "B77W" to "Boeing 777-300ER", "B779" to "Boeing 777X",
        "B788" to "Boeing 787-8", "B789" to "Boeing 787-9", "B78X" to "Boeing 787-10",
        // Airbus narrow-body
        "A318" to "Airbus A318", "A319" to "Airbus A319", "A320" to "Airbus A320",
        "A321" to "Airbus A321", "A20N" to "Airbus A320neo", "A21N" to "Airbus A321neo",
        // Airbus wide-body
        "A332" to "Airbus A330-200", "A333" to "Airbus A330-300",
        "A338" to "Airbus A330-800neo", "A339" to "Airbus A330-900neo",
        "A342" to "Airbus A340-200", "A343" to "Airbus A340-300",
        "A345" to "Airbus A340-500", "A346" to "Airbus A340-600",
        "A359" to "Airbus A350-900", "A35K" to "Airbus A350-1000",
        "A388" to "Airbus A380-800",
        // Embraer
        "E170" to "Embraer 170", "E175" to "Embraer 175",
        "E190" to "Embraer 190", "E195" to "Embraer 195",
        "E290" to "Embraer E190-E2", "E295" to "Embraer E195-E2",
        // Bombardier
        "CRJ2" to "Bombardier CRJ-200", "CRJ7" to "Bombardier CRJ-700",
        "CRJ9" to "Bombardier CRJ-900", "CRJX" to "Bombardier CRJ-1000",
        "DH8D" to "Bombardier Q400",
        // ATR
        "AT72" to "ATR 72", "AT76" to "ATR 72-600", "AT43" to "ATR 42",
        // Business jets
        "C56X" to "Cessna Citation Excel", "CL60" to "Bombardier Challenger 600",
        "GL7T" to "Gulfstream G700", "GLEX" to "Bombardier Global Express",
        // Military / Other
        "C130" to "Lockheed C-130 Hercules", "C17" to "Boeing C-17",
        "F16" to "Lockheed F-16", "F18" to "Boeing F/A-18",
    )

    private fun resolveAircraftTypeName(icaoCode: String): String {
        if (icaoCode.isBlank()) return ""
        return aircraftTypeNames[icaoCode.uppercase()] ?: ""
    }
}
