package com.swapmap.zwap.demo.network

import com.swapmap.zwap.demo.model.OverpassResponse
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface OSMOverpassAPI {
    @GET("interpreter")
    fun queryFeatures(@Query("data") query: String): Call<OverpassResponse>
}

object OSMOverpassService {
    private const val BASE_URL = "https://overpass-api.de/api/"
    
    val instance: OSMOverpassAPI by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OSMOverpassAPI::class.java)
    }
    
    fun buildQuery(south: Double, west: Double, north: Double, east: Double): String {
        val bbox = "$south,$west,$north,$east"
        return """
            [out:json][timeout:25];
            (
              node["highway"="speed_camera"]($bbox);
              node["traffic_calming"]($bbox);
              node["highway"="stop"]($bbox);
              node["highway"="give_way"]($bbox);
            );
            out body;
        """.trimIndent()
    }
}
