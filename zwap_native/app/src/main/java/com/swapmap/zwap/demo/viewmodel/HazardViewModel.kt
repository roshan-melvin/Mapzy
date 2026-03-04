package com.swapmap.zwap.demo.viewmodel

import android.location.Location
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swapmap.zwap.demo.network.OSMOverpassService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * HazardViewModel — owns all speed-limit state and fetching logic.
 *
 * Previously this lived inside MainActivity as raw thread calls and private vars.
 * Moving it here gives us:
 *  - Lifecycle safety (no memory leaks when the screen rotates / user leaves)
 *  - LiveData observers (MainActivity just reacts, never calculates)
 *  - Coroutines via viewModelScope (automatically cancelled on ViewModel clear)
 *
 * NO existing feature has changed — same 40m radius, same 300m poll distance,
 * same maxspeed parsing, same TTS logic. Only the *location* of the code changed.
 */
class HazardViewModel : ViewModel() {

    // ── Speed Limit State ──────────────────────────────────────────────────────

    /** The road speed limit at the driver's current position. 0 = unknown / no data. */
    private val _speedLimitKmh = MutableLiveData<Int>(0)
    val speedLimitKmh: LiveData<Int> = _speedLimitKmh

    /** Set to true once "Over speed limit" TTS fires; reset when driver slows down. */
    private var isOverSpeedWarned = false

    /** Last location where we called the Overpass API for speed limit. */
    private var lastSpeedLimitFetchLocation: Location? = null

    // ── Public API called by MainActivity ─────────────────────────────────────

    /**
     * Called on every location update from the LocationEngine.
     * Triggers a fresh Overpass fetch only when the vehicle has moved > 300m
     * since the last fetch — identical to the previous polling strategy.
     */
    fun onLocationChanged(location: Location) {
        val shouldFetch = lastSpeedLimitFetchLocation == null ||
                location.distanceTo(lastSpeedLimitFetchLocation!!) > 300
        if (shouldFetch) {
            lastSpeedLimitFetchLocation = location
            fetchSpeedLimitNearby(location.latitude, location.longitude)
        }
    }

    /**
     * Evaluates whether the driver is over the speed limit.
     * Triggers TTS once when they go over; resets when they slow back down.
     * Returns true if currently over limit (so MainActivity can colour the HUD).
     *
     * @param speedKmh  Current vehicle speed in km/h
     * @param tts       TextToSpeech instance owned by MainActivity
     */
    fun checkOverSpeed(speedKmh: Int, tts: TextToSpeech?): Boolean {
        val limit = _speedLimitKmh.value ?: 0
        if (limit <= 0) return false   // Unknown limit — no alarm

        val isOver = speedKmh > limit
        if (isOver && !isOverSpeedWarned) {
            isOverSpeedWarned = true
            tts?.speak("Over speed limit", TextToSpeech.QUEUE_FLUSH, null, null)
        } else if (!isOver) {
            isOverSpeedWarned = false
        }
        return isOver
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    /**
     * Queries OSM Overpass for the maxspeed tag of the road within 40m of the driver.
     * Runs entirely on [Dispatchers.IO] — never blocks the UI thread.
     */
    private fun fetchSpeedLimitNearby(lat: Double, lon: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val query = OSMOverpassService.buildSpeedLimitQuery(lat, lon, radius = 40)
                val response = OSMOverpassService.instance.queryFeatures(query).execute()
                if (response.isSuccessful) {
                    val elements = response.body()?.elements ?: return@launch
                    val rawLimit = elements
                        .mapNotNull { it.tags?.get("maxspeed") }
                        .firstOrNull() ?: return@launch

                    val limitKmh: Int? = parseMaxspeed(rawLimit)
                    if (limitKmh != null) {
                        withContext(Dispatchers.Main) {
                            _speedLimitKmh.value = limitKmh
                            Log.d("HazardVM", "Speed limit updated: $limitKmh km/h (raw='$rawLimit')")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("HazardVM", "Speed limit fetch failed: ${e.message}")
            }
        }
    }

    /**
     * Parses OSM maxspeed strings into km/h integers.
     *
     * Handles:
     *  "50"            → 50 km/h
     *  "60 mph"        → 96 km/h  (converted)
     *  "IN:urban"      → 50 km/h  (India advisory)
     *  "IN:rural"      → 70 km/h
     *  "IN:motorway"   → 120 km/h
     *  "none"          → 999 (no limit)
     */
    private fun parseMaxspeed(raw: String): Int? = when {
        raw.equals("none", ignoreCase = true) -> 999
        raw.contains("mph", ignoreCase = true) -> {
            val mph = raw.removeSuffix(" mph").trim().toIntOrNull()
            mph?.let { (it * 1.60934).toInt() }
        }
        raw.contains(":", ignoreCase = true) -> when {
            raw.contains("urban",    ignoreCase = true) -> 50
            raw.contains("rural",    ignoreCase = true) -> 70
            raw.contains("motorway", ignoreCase = true) -> 120
            else -> null
        }
        else -> raw.trim().toIntOrNull()
    }
}
