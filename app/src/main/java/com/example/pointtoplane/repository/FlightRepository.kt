package com.example.pointtoplane.repository

import android.util.Log
import com.example.pointtoplane.model.RawAircraft
import com.example.pointtoplane.network.AdsbDbService
import com.example.pointtoplane.network.AdsbFiService
import com.example.pointtoplane.network.OpenSkyService
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Handles all flight data network operations.
 *
 * Strategy:
 * 1. Query adsb.fi for real-time aircraft positions near the user
 * 2. For the matched aircraft, query adsbdb.com for route info (origin/destination/airline)
 * 3. Falls back to OpenSky Network if adsb.fi is unavailable
 *
 * All responses are cached for 30 seconds to preserve battery and respect rate limits.
 */
class FlightRepository {

    private val TAG = "FlightRepository"

    // Cache: stores the last raw aircraft list fetch
    private var cachedAircraft: List<RawAircraft>? = null
    private var cacheTimestampMs: Long = 0L
    private val CACHE_TTL_MS = 30_000L // 30 seconds

    // Shared OkHttp client with reasonable timeouts for a watch
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val gson = GsonBuilder().setLenient().create()

    private val adsbFiRetrofit = Retrofit.Builder()
        .baseUrl("https://opendata.adsb.fi/api/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    private val adsbDbRetrofit = Retrofit.Builder()
        .baseUrl("https://api.adsbdb.com/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    private val openSkyRetrofit = Retrofit.Builder()
        .baseUrl("https://opensky-network.org/api/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    private val adsbFiService = adsbFiRetrofit.create(AdsbFiService::class.java)
    private val adsbDbService = adsbDbRetrofit.create(AdsbDbService::class.java)
    private val openSkyService = openSkyRetrofit.create(OpenSkyService::class.java)

    /**
     * Fetches nearby aircraft from adsb.fi, with OpenSky as fallback.
     * Returns cached results if the cache is fresh (< 30s old).
     *
     * @param lat User's latitude
     * @param lon User's longitude
     * @param radiusNm Search radius in nautical miles (default 100 nm ≈ 185 km)
     */
    suspend fun getNearbyAircraft(lat: Double, lon: Double, radiusNm: Int = 100): List<RawAircraft> {
        val now = System.currentTimeMillis()
        if (cachedAircraft != null && (now - cacheTimestampMs) < CACHE_TTL_MS) {
            Log.d(TAG, "Returning cached aircraft list (${cachedAircraft!!.size} aircraft)")
            return cachedAircraft!!
        }

        val aircraft = try {
            Log.d(TAG, "Fetching from adsb.fi: lat=$lat lon=$lon radius=${radiusNm}nm")
            val response = adsbFiService.getAircraftNear(lat, lon, radiusNm)
            val acList = response.aircraft ?: emptyList()
            Log.d(TAG, "adsb.fi returned ${acList.size} aircraft")
            acList.mapNotNull { ac ->
                val acLat = ac.lat ?: return@mapNotNull null
                val acLon = ac.lon ?: return@mapNotNull null
                if (ac.altitudeMeters < 100) return@mapNotNull null // skip ground vehicles
                RawAircraft(
                    icao24 = ac.hex,
                    callsign = ac.callsign,
                    latitude = acLat,
                    longitude = acLon,
                    altitudeMeters = ac.altitudeMeters,
                    groundSpeedKnots = ac.groundSpeedKts ?: 0.0,
                    trackDeg = ac.track?.toFloat() ?: 0f,
                    aircraftTypeIcao = ac.aircraftType ?: ""
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "adsb.fi failed: ${e.message}. Trying OpenSky fallback...")
            try {
                fetchFromOpenSky(lat, lon)
            } catch (e2: Exception) {
                Log.e(TAG, "OpenSky fallback also failed: ${e2.message}")
                emptyList()
            }
        }

        cachedAircraft = aircraft
        cacheTimestampMs = now
        return aircraft
    }

    /**
     * Fetches route information (origin, destination, airline) for a specific [callsign].
     * Returns null if the callsign is not found in the database.
     */
    suspend fun getRouteInfo(callsign: String): RouteInfo? {
        if (callsign.isBlank()) return null
        return try {
            val response = adsbDbService.getCallsignInfo(callsign.trim())
            val route = response.response?.flightRoute
            if (route != null) {
                RouteInfo(
                    airline = route.airline?.name ?: "",
                    originIata = route.origin?.iataCode ?: "",
                    originName = route.origin?.name ?: "",
                    destinationIata = route.destination?.iataCode ?: "",
                    destinationName = route.destination?.name ?: ""
                )
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "adsbdb lookup failed for callsign=$callsign: ${e.message}")
            null
        }
    }

    /** Clears the aircraft cache, forcing a fresh API call on next request */
    fun clearCache() {
        cachedAircraft = null
        cacheTimestampMs = 0L
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    private suspend fun fetchFromOpenSky(lat: Double, lon: Double): List<RawAircraft> {
        val delta = 1.5 // ~165 km bounding box
        val response = openSkyService.getStates(
            lamin = lat - delta, lomin = lon - delta,
            lamax = lat + delta, lomax = lon + delta
        )
        Log.d(TAG, "OpenSky returned ${response.states?.size ?: 0} states")
        return response.states?.mapNotNull { state ->
            try {
                // OpenSky state vector: [icao24, callsign, origin_country, time_position,
                //   last_contact, longitude, latitude, baro_altitude, on_ground, velocity,
                //   true_track, vertical_rate, sensors, geo_altitude, squawk, spi, position_source]
                val icao24 = state[0] as? String ?: return@mapNotNull null
                val callsign = (state[1] as? String)?.trim() ?: ""
                val lon2 = (state[5] as? Number)?.toDouble() ?: return@mapNotNull null
                val lat2 = (state[6] as? Number)?.toDouble() ?: return@mapNotNull null
                val altBaro = (state[7] as? Number)?.toDouble() ?: return@mapNotNull null
                val onGround = state[8] as? Boolean ?: false
                val velocity = (state[9] as? Number)?.toDouble() ?: 0.0
                val track = (state[10] as? Number)?.toFloat() ?: 0f

                if (onGround || altBaro < 100) return@mapNotNull null

                RawAircraft(
                    icao24 = icao24,
                    callsign = callsign,
                    latitude = lat2,
                    longitude = lon2,
                    altitudeMeters = altBaro,
                    groundSpeedKnots = velocity * 1.94384, // m/s to knots
                    trackDeg = track
                )
            } catch (e: Exception) {
                null
            }
        } ?: emptyList()
    }
}

/** Route enrichment data from adsbdb.com */
data class RouteInfo(
    val airline: String,
    val originIata: String,
    val originName: String,
    val destinationIata: String,
    val destinationName: String
)
