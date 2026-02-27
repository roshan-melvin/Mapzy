package com.swapmap.zwap.demo.network

import com.swapmap.zwap.demo.config.AppConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val DEFAULT_BASE_URL = "http://10.0.2.2:8000/"

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)  // Connection timeout
            .readTimeout(60, TimeUnit.SECONDS)     // AI processing can take time
            .writeTimeout(60, TimeUnit.SECONDS)    // Image upload can take time
            .build()
    }

    private val retrofit: Retrofit by lazy {
        val baseUrl = AppConfig.get("BACKEND_BASE_URL", DEFAULT_BASE_URL)
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val hazardApiService: HazardApiService by lazy {
        retrofit.create(HazardApiService::class.java)
    }
}
