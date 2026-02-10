package com.swapmap.zwap.demo.network

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseManager {
    // TODO: Replace with your actual Supabase Project URL and Anon Key
    private const val SUPABASE_URL = "https://mjsylrqiyjupixopbdez.supabase.co"
    private const val SUPABASE_KEY = "sb_publishable_pPnGYcgWm5HlRZFARW2kHA_QsrXLtv8"

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY
    ) {
        install(Postgrest)
    }
}
