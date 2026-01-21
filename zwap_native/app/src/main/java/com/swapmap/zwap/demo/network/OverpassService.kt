package com.swapmap.zwap.demo.network

import com.swapmap.zwap.demo.model.SpeedCamera
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface OverpassService {
    @FormUrlEncoded
    @POST("interpreter")
    suspend fun getCameras(@Field("data") query: String): OverpassResponse
}

data class OverpassResponse(val elements: List<OverpassElement>)
data class OverpassElement(val id: Long, val lat: Double, val lon: Double)

object OverpassClient {
    private const val BASE_URL = "https://overpass-api.de/api/"

    val instance: OverpassService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(OverpassService::class.java)
    }
}
