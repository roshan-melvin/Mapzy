package com.swapmap.zwap.demo.config

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

object AppConfig {
    private const val ENV_ASSET_NAME = "zwap.env"
    private val values = ConcurrentHashMap<String, String>()
    @Volatile private var loaded = false

    fun load(context: Context) {
        if (loaded) {
            return
        }
        synchronized(this) {
            if (loaded) {
                return
            }
            try {
                context.assets.open(ENV_ASSET_NAME).bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                            return@forEach
                        }
                        val idx = trimmed.indexOf('=')
                        if (idx <= 0) {
                            return@forEach
                        }
                        val key = trimmed.substring(0, idx).trim()
                        val value = trimmed.substring(idx + 1).trim().trim('"')
                        if (key.isNotEmpty()) {
                            values[key] = value
                        }
                    }
                }
            } catch (_: Exception) {
                // Keep defaults when asset is missing or unreadable.
            }
            loaded = true
        }
    }

    fun get(key: String, defaultValue: String): String {
        return values[key] ?: defaultValue
    }
}
