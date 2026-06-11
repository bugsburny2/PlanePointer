package com.example.pointtoplane.model

/**
 * Full flight information displayed to the user after a match is found.
 */
data class FlightInfo(
    /** ICAO24 transponder address (hex) */
    val icao24: String,
    /** Flight callsign / number (e.g. "BAW249", "EZY123") */
    val callsign: String,
    /** Human-readable airline name (e.g. "British Airways") */
    val airline: String = "",
    /** ICAO aircraft type code (e.g. "B738", "A320") */
    val aircraftTypeIcao: String = "",
    /** Human-readable aircraft name derived from ICAO type code */
    val aircraftTypeName: String = "",
    /** Origin airport IATA code (e.g. "LHR") */
    val originIata: String = "",
    /** Origin airport name */
    val originName: String = "",
    /** Destination airport IATA code (e.g. "JFK") */
    val destinationIata: String = "",
    /** Destination airport name */
    val destinationName: String = "",
    /** Current barometric altitude in meters */
    val altitudeMeters: Double = 0.0,
    /** Ground speed in knots */
    val speedKnots: Double = 0.0,
    /** Angular separation between pointing direction and aircraft (degrees) — lower is better */
    val angularDiffDeg: Float = 0f
) {
    val altitudeFeet: Int get() = (altitudeMeters * 3.28084).toInt()
    val displayCallsign: String get() = callsign.trim().ifEmpty { icao24.uppercase() }
    val hasRoute: Boolean get() = originIata.isNotEmpty() && destinationIata.isNotEmpty()
    val displayAircraftType: String get() = aircraftTypeName.ifEmpty { aircraftTypeIcao.ifEmpty { "Unknown aircraft" } }
}

/** Raw aircraft state from the ADS-B network (before route enrichment) */
data class RawAircraft(
    val icao24: String,
    val callsign: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val groundSpeedKnots: Double,
    val trackDeg: Float,
    val aircraftTypeIcao: String = ""
)
