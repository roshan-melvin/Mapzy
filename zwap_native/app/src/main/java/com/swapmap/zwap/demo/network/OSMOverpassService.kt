package com.swapmap.zwap.demo.network

import com.swapmap.zwap.demo.model.OverpassResponse
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface OSMOverpassAPI {
    @GET("interpreter")
    fun queryFeatures(@Query("data") query: String): Call<OverpassResponse>
}

object OSMOverpassService {
    // kumi.systems is a distributed Overpass CDN with mirrors globally — much faster than overpass-api.de for non-EU users
    private const val BASE_URL = "https://overpass.kumi.systems/api/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(14, TimeUnit.SECONDS)  // Overpass [timeout:10] + 4s buffer
        .writeTimeout(6, TimeUnit.SECONDS)
        .build()

    val instance: OSMOverpassAPI by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)           // Apply the custom client with correct timeouts
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OSMOverpassAPI::class.java)
    }
    
    /**
     * Fetches hazard POIs within [radiusM] metres of [lat],[lon].
     * Uses `around` (radial) queries which are significantly faster than
     * bbox scans because Overpass spatially indexes nodes by proximity.
     * Timeout capped at 10s to match OkHttp readTimeout (14s).
     */
    fun buildQuery(lat: Double, lon: Double, radiusM: Int = 2500): String {
        val around = "around:$radiusM,$lat,$lon"
        return """
            [out:json][timeout:10][maxsize:262144];
            (
              node["highway"="speed_camera"]($around);
              node["traffic_calming"]($around);
              node["highway"="stop"]($around);
              node["highway"="give_way"]($around);
            );
            out body;
        """.trimIndent()
    }

    @Deprecated("Use buildQuery(lat, lon, radiusM) instead", ReplaceWith("buildQuery(lat, lon)"))
    fun buildQuery(south: Double, west: Double, north: Double, east: Double): String =
        buildQuery((south + north) / 2, (west + east) / 2)

    fun buildSpeedLimitQuery(lat: Double, lon: Double, radius: Int = 50): String {
        // Fetch ALL highway ways near the driver (no maxspeed filter).
        // This lets us read both the "maxspeed" tag (preferred) and the
        // "highway" type tag (used as an India-standard fallback when maxspeed
        // is absent).
        return """
            [out:json][timeout:10];
            way["highway"](around:${'$'}radius,${'$'}lat,${'$'}lon);
            out tags center;
        """.trimIndent()
    }
}
