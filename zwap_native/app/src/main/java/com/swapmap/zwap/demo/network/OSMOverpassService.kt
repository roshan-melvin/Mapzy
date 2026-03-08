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
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)  // Overpass [timeout:25] + buffer; default OkHttp 10s was killing calls silently
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    val instance: OSMOverpassAPI by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)           // Apply the custom client with correct timeouts
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OSMOverpassAPI::class.java)
    }
    
    fun buildQuery(south: Double, west: Double, north: Double, east: Double): String {
        val bbox = "$south,$west,$north,$east"
        return """
            [out:json][timeout:25][maxsize:524288];
            (
              node["highway"="speed_camera"]($bbox);
              node["traffic_calming"]($bbox);
              node["highway"="stop"]($bbox);
              node["highway"="give_way"]($bbox);
            );
            out body;
        """.trimIndent()
    }

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
