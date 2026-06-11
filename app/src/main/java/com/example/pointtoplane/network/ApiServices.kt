package com.example.pointtoplane.network

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Retrofit service interface for the adsb.fi open data API */
interface AdsbFiService {
    /**
     * Fetches all aircraft within [distanceNm] nautical miles of the given coordinates.
     * The v3 endpoint uses nautical miles for the distance parameter.
     */
    @GET("v3/lat/{lat}/lon/{lon}/dist/{dist}")
    suspend fun getAircraftNear(
        @Path("lat") lat: Double,
        @Path("lon") lon: Double,
        @Path("dist") distanceNm: Int = 100
    ): AdsbFiResponse
}

/** Retrofit service interface for the adsbdb.com callsign database */
interface AdsbDbService {
    /**
     * Looks up route information for a given [callsign] (e.g. "BAW249", "EZY1234").
     * Returns airline name, origin, and destination airports.
     */
    @GET("v0/callsign/{callsign}")
    suspend fun getCallsignInfo(@Path("callsign") callsign: String): AdsbDbResponse
}

/** Retrofit service interface for the OpenSky Network API (fallback) */
interface OpenSkyService {
    /**
     * Fetches all aircraft states within a geographic bounding box.
     */
    @GET("states/all")
    suspend fun getStates(
        @Query("lamin") lamin: Double,
        @Query("lomin") lomin: Double,
        @Query("lamax") lamax: Double,
        @Query("lomax") lomax: Double
    ): OpenSkyResponse
}
