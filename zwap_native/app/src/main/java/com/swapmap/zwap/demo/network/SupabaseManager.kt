package com.swapmap.zwap.demo.network

import com.swapmap.zwap.demo.config.AppConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseManager {
    private val supabaseUrl = AppConfig.get("SUPABASE_URL", "")
    private val supabaseKey = AppConfig.get("SUPABASE_KEY", "")

    init {
        if (supabaseUrl.isBlank() || supabaseKey.isBlank()) {
            android.util.Log.e("SupabaseManager", "Supabase config missing. Check zwap.env")
        }
    }

    val client = createSupabaseClient(
        supabaseUrl = supabaseUrl,
        supabaseKey = supabaseKey
    ) {
        install(Postgrest)
    }
}
