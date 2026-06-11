package com.example.pointtoplane.network

import com.google.gson.annotations.SerializedName

// ──────────────────────────────────────────────────────────────────────────────
// adsb.fi API response models
// Endpoint: GET https://opendata.adsb.fi/api/v2/lat/{lat}/lon/{lon}/dist/{dist}
// ──────────────────────────────────────────────────────────────────────────────

data class AdsbFiResponse(
    @SerializedName("ac") val aircraft: List<AdsbFiAircraft>? = null,
    @SerializedName("total") val total: Int = 0,
    @SerializedName("now") val now: Double = 0.0
)

data class AdsbFiAircraft(
    /** ICAO 24-bit hex address */
    @SerializedName("hex") val hex: String = "",
    /** Flight callsign / IATA flight number */
    @SerializedName("flight") val flight: String? = null,
    /** Registration (tail number) */
    @SerializedName("r") val registration: String? = null,
    /** ICAO aircraft type code (e.g. "B738", "A320") */
    @SerializedName("t") val aircraftType: String? = null,
    /** Latitude in decimal degrees */
    @SerializedName("lat") val lat: Double? = null,
    /** Longitude in decimal degrees */
    @SerializedName("lon") val lon: Double? = null,
    /** Barometric altitude in feet */
    @SerializedName("alt_baro") val altBaroFt: Any? = null,
    /** Geometric altitude in feet */
    @SerializedName("alt_geom") val altGeomFt: Int? = null,
    /** Ground speed in knots */
    @SerializedName("gs") val groundSpeedKts: Double? = null,
    /** True track (heading) in degrees */
    @SerializedName("track") val track: Double? = null,
    /** Vertical rate in ft/min */
    @SerializedName("baro_rate") val baroRate: Int? = null,
    /** Aircraft category (A1=light, A3=large, A5=heavy) */
    @SerializedName("category") val category: String? = null,
    /** Emergency squawk flag */
    @SerializedName("emergency") val emergency: String? = null
) {
    /** Altitude in meters, handling both numeric and "ground" string values */
    val altitudeMeters: Double get() {
        return when (val raw = altBaroFt) {
            is Number -> raw.toDouble() * 0.3048
            is String -> if (raw == "ground") 0.0 else 0.0
            else -> (altGeomFt?.toDouble() ?: 0.0) * 0.3048
        }
    }

    val callsign: String get() = flight?.trim() ?: ""
}

// ──────────────────────────────────────────────────────────────────────────────
// adsbdb.com API response models
// Endpoint: GET https://api.adsbdb.com/v0/callsign/{callsign}
// ──────────────────────────────────────────────────────────────────────────────

data class AdsbDbResponse(
    @SerializedName("response") val response: AdsbDbFlightResponse? = null
)

data class AdsbDbFlightResponse(
    @SerializedName("flightroute") val flightRoute: AdsbDbFlightRoute? = null
)

data class AdsbDbFlightRoute(
    @SerializedName("callsign") val callsign: String? = null,
    @SerializedName("callsign_icao") val callsignIcao: String? = null,
    @SerializedName("callsign_iata") val callsignIata: String? = null,
    @SerializedName("airline") val airline: AdsbDbAirline? = null,
    @SerializedName("origin") val origin: AdsbDbAirport? = null,
    @SerializedName("destination") val destination: AdsbDbAirport? = null
)

data class AdsbDbAirline(
    @SerializedName("name") val name: String? = null,
    @SerializedName("icao") val icao: String? = null,
    @SerializedName("iata") val iata: String? = null,
    @SerializedName("country") val country: String? = null
)

data class AdsbDbAirport(
    @SerializedName("country_iso_name") val countryIso: String? = null,
    @SerializedName("country_name") val countryName: String? = null,
    @SerializedName("elevation") val elevation: Int? = null,
    @SerializedName("iata_code") val iataCode: String? = null,
    @SerializedName("icao_code") val icaoCode: String? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("municipality") val municipality: String? = null,
    @SerializedName("name") val name: String? = null
)

// ──────────────────────────────────────────────────────────────────────────────
// OpenSky Network fallback API response models
// Endpoint: GET https://opensky-network.org/api/states/all
// ──────────────────────────────────────────────────────────────────────────────

data class OpenSkyResponse(
    @SerializedName("time") val time: Long? = null,
    @SerializedName("states") val states: List<List<Any?>>? = null
)
