package com.swapmap.zwap.demo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import com.mappls.sdk.maps.Mappls
import android.os.Bundle
import android.speech.tts.TextToSpeech
import com.mappls.sdk.services.account.MapplsAccountManager
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.mappls.sdk.geojson.Point
import com.mappls.sdk.geojson.utils.PolylineUtils
import com.mappls.sdk.maps.MapView
import com.mappls.sdk.maps.MapplsMap
import com.mappls.sdk.maps.OnMapReadyCallback
import com.mappls.sdk.maps.Style
import com.mappls.sdk.maps.annotations.MarkerOptions
import com.mappls.sdk.maps.annotations.PolylineOptions
import com.mappls.sdk.maps.camera.CameraMapplsPinUpdateFactory
import com.mappls.sdk.maps.camera.CameraUpdateFactory
import com.mappls.sdk.maps.location.LocationComponentActivationOptions
import com.mappls.sdk.maps.location.engine.LocationEngine
import com.mappls.sdk.maps.location.engine.LocationEngineCallback
import com.mappls.sdk.maps.location.engine.LocationEngineProvider
import com.mappls.sdk.maps.location.engine.LocationEngineRequest
import com.mappls.sdk.maps.location.engine.LocationEngineResult
import com.mappls.sdk.maps.location.modes.CameraMode
import com.mappls.sdk.maps.location.modes.RenderMode
import com.mappls.sdk.plugins.places.autocomplete.PlaceAutocomplete
import com.mappls.sdk.plugins.places.autocomplete.model.PlaceOptions
import com.mappls.sdk.services.api.OnResponseCallback
import com.mappls.sdk.services.api.autosuggest.model.ELocation
import com.mappls.sdk.services.api.autosuggest.MapplsAutoSuggest
import com.mappls.sdk.services.api.autosuggest.MapplsAutosuggestManager
import com.mappls.sdk.services.api.autosuggest.model.AutoSuggestAtlasResponse
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.speech.RecognizerIntent
import android.widget.EditText
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.auth.FirebaseAuth
import java.util.HashMap
import com.mappls.sdk.services.api.directions.DirectionsCriteria
import com.mappls.sdk.services.api.directions.MapplsDirectionManager
import com.mappls.sdk.services.api.directions.MapplsDirections
import com.mappls.sdk.services.api.directions.models.DirectionsResponse
import com.mappls.sdk.services.api.directions.models.LegStep
import com.mappls.sdk.plugin.annotation.LineManager
import com.mappls.sdk.plugin.annotation.LineOptions
import com.mappls.sdk.plugin.annotation.SymbolManager
import com.mappls.sdk.plugin.annotation.SymbolOptions
import com.mappls.sdk.services.utils.Constants
import com.swapmap.zwap.R
import com.swapmap.zwap.demo.model.FeatureType
import com.swapmap.zwap.demo.model.OSMFeature
import com.swapmap.zwap.demo.network.OSMOverpassService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.Button
import java.lang.ref.WeakReference
import java.util.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.swapmap.zwap.demo.profile.ProfileFragment
import com.swapmap.zwap.demo.community.CommunityFragment
import com.swapmap.zwap.demo.chat.ChatFragment
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class MainActivity : AppCompatActivity(), OnMapReadyCallback, TextToSpeech.OnInitListener {

    private var mapView: MapView? = null
    internal var mapplsMap: MapplsMap? = null  // internal for fragment access
    private var selectedELoc: String? = null
    private var selectedPlace: ELocation? = null  // Store full place details
    
    // Firestore Database
    private val db = FirebaseFirestore.getInstance()
    
    private var locationEngine: LocationEngine? = null
    private val callback = LocationChangeCallback(this)
    
    private var isFollowMode = true
    private var tts: TextToSpeech? = null
    
    private var currentRoute: DirectionsResponse? = null
    private val routeHazards = mutableListOf<Pair<Double, Double>>()
    private var lastAlertTime: Long = 0
    private val ALERT_COOLDOWN = 3000L  // 3 seconds
    private val HAZARD_ALERT_DISTANCE = 300.0  // 300 meters
    private val alertedHazardIds = mutableSetOf<Long>()
    private var hazardAlertAdapter: HazardAlertAdapter? = null
    private var isHazardPanelExpanded = true
    private val activeHazards = mutableListOf<HazardAlert>()
    private val HAZARD_CROSSED_DISTANCE = 30.0  // Consider crossed when within 30m // 10 seconds
    
    private var symbolManager: SymbolManager? = null
    private var lineManager: LineManager? = null
    
    // OSM Overlay features
    private val osmFeatures = java.util.concurrent.CopyOnWriteArrayList<OSMFeature>()
    private val testMarkerIds = mutableListOf<Long>()
    private val osmMarkerIds = mutableListOf<Long>()
    private var osmOverlayEnabled = false
    private var lastOSMFetchLocation: Location? = null
    private var lastZoomLevel: Double = 0.0
    
    // Route hazard markers (separate from regular OSM markers)
    private val routeHazardMarkerIds = mutableListOf<Long>()
    private var isNavigating = false
    private var currentRoutePoints: List<Point> = emptyList()  // Store route points for progressive fetching
    private var currentHazardFetchIndex = 0  // Track which chunk we're fetching
    private var hazardFetchJob: kotlinx.coroutines.Job? = null  // Job to cancel if route changes
    private var totalRouteDistanceMeters = 0.0  // Total route distance for progress calculation
    
    private lateinit var auth: FirebaseAuth
    private var currentUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        
        // Ensure keys are set before inflation
        Mappls.getInstance(this)
        
        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.map_view)
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync(this)

        tts = TextToSpeech(this, this)
        
        // Check if user is already logged in
        if (auth.currentUser != null) {
            currentUserId = auth.currentUser!!.uid
            Log.d("Zwap", "User already logged in: $currentUserId")
            setupUI()
        } else {
            // Show login/signup dialog
            showAuthDialog()
        }
    }
    
    private val nearbyMarkerIds = mutableListOf<Long>()
    private val osmHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var osmRunnable: Runnable? = null

    
    private fun showAuthDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Sign In / Sign Up")
        builder.setMessage("Enter your email and password")
        
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }
        
        val emailInput = EditText(this).apply {
            hint = "Email"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20 }
        }
        
        val passwordInput = EditText(this).apply {
            hint = "Password (min 6 chars)"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        layout.addView(emailInput)
        layout.addView(passwordInput)
        
        builder.setView(layout)
        builder.setPositiveButton("Sign Up / Sign In") { _, _ ->
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Email and password required", Toast.LENGTH_SHORT).show()
                showAuthDialog()
                return@setPositiveButton
            }
            
            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                showAuthDialog()
                return@setPositiveButton
            }
            
            // Try to sign in first, if fails then create new account
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    currentUserId = auth.currentUser!!.uid
                    Log.d("Zwap", "✅ Signed in: $currentUserId")
                    Toast.makeText(this@MainActivity, "Welcome back!", Toast.LENGTH_SHORT).show()
                    setupUI()
                }
                .addOnFailureListener { signInException ->
                    Log.d("Zwap", "Sign in failed, trying to create account...")
                    // If sign in fails, try to create new account
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener {
                            currentUserId = auth.currentUser!!.uid
                            Log.d("Zwap", "✅ Account created: $currentUserId")
                            Toast.makeText(this@MainActivity, "Account created successfully!", Toast.LENGTH_SHORT).show()
                            setupUI()
                        }
                        .addOnFailureListener { signUpException ->
                            Log.e("Zwap", "Sign up failed: ${signUpException.message}")
                            Toast.makeText(this@MainActivity, "Error: ${signUpException.message}", Toast.LENGTH_SHORT).show()
                            showAuthDialog()
                        }
                }
        }
        
        builder.setCancelable(false)
        builder.show()
    }
    
    private fun setupUI() {
        // Setup hazard alert panel
        setupHazardAlertPanel()
        findViewById<View>(R.id.search_trigger).setOnClickListener { showSearchOverlay(null) }
        findViewById<View>(R.id.btn_voice_search).setOnClickListener { performVoiceSearch() }
        findViewById<View>(R.id.btn_directions).setOnClickListener { getDirections() }
        findViewById<View>(R.id.fab_recenter).setOnClickListener { enableFollowMode() }
        findViewById<View>(R.id.btn_show_nearby).setOnClickListener { showNearbySearchDialog() }
        
        // Compass button - reset map orientation to north
        findViewById<View>(R.id.fab_compass)?.setOnClickListener {
            Log.d("Zwap", "Compass button clicked - resetting map bearing to north")
            mapplsMap?.animateCamera(
                com.mappls.sdk.maps.camera.CameraUpdateFactory.newCameraPosition(
                    com.mappls.sdk.maps.camera.CameraPosition.Builder()
                        .target(mapplsMap?.cameraPosition?.target)
                        .zoom(mapplsMap?.cameraPosition?.zoom ?: 14.0)
                        .bearing(0.0)  // Reset to north
                        .tilt(0.0)     // Reset tilt
                        .build()
                ),
                300  // Animation duration in ms
            )
        }
        
        // More from recent history button
        findViewById<View>(R.id.btn_more_history)?.setOnClickListener {
            Log.d("Zwap", "More history button clicked")
            fetchHistoryFromFirestore(limit = 20) // Show more items instead of just 5
        }
        
        try {
            findViewById<View>(R.id.btn_toggle_osm)?.setOnClickListener { 
                Log.d("Zwap", "Hazard toggle button clicked!")
                toggleOSMOverlay() 
            }
            Log.d("Zwap", "Hazard button setup complete")
        } catch (e: Exception) {
            Log.e("Zwap", "Error setting up hazard button", e)
        }
        
        setupBottomNavigation()
        setupSearchOverlay()
    }
    
    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        val fragmentContainer = findViewById<View>(R.id.fragment_container)
        
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_explore -> {
                    // Show map, hide fragment container
                    fragmentContainer.visibility = View.GONE
                    mapView?.visibility = View.VISIBLE
                    findViewById<View>(R.id.search_card)?.visibility = View.VISIBLE
                    findViewById<View>(R.id.fab_recenter)?.visibility = View.VISIBLE
                    findViewById<View>(R.id.fab_compass)?.visibility = View.VISIBLE
                    Log.d("Zwap", "Switched to Explore tab")
                    true
                }
                R.id.nav_profile -> {
                    // Show Profile fragment
                    showFragment(ProfileFragment(), "You")
                    true
                }
                R.id.nav_contribute -> {
                    // Show Community fragment (Discord-like contribute page)
                    showFragment(CommunityFragment(), "Contribute")
                    true
                }
                R.id.nav_chat -> {
                    // Show Chat fragment
                    showFragment(ChatFragment(), "Chat")
                    true
                }
                else -> false
            }
        }
    }
    
    private fun showFragment(fragment: androidx.fragment.app.Fragment, tag: String) {
        val fragmentContainer = findViewById<View>(R.id.fragment_container)
        
        // Hide map-related views
        mapView?.visibility = View.GONE
        findViewById<View>(R.id.search_card)?.visibility = View.GONE
        findViewById<View>(R.id.fab_recenter)?.visibility = View.GONE
        findViewById<View>(R.id.fab_compass)?.visibility = View.GONE
        findViewById<View>(R.id.search_overlay)?.visibility = View.GONE
        
        // Show fragment container
        fragmentContainer.visibility = View.VISIBLE
        
        // Replace fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment, tag)
            .commit()
        
        Log.d("Zwap", "Switched to $tag tab")
    }
    
    private fun performVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak destination...")
        }
        try {
            startActivityForResult(intent, 202)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice search not supported", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showBottomSheetList(title: String, items: Array<String>, onClick: (Int) -> Unit) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val context = this
        
        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        val titleView = TextView(context).apply {
            text = title
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 40)
            setTextColor(android.graphics.Color.BLACK)
        }
        layout.addView(titleView)
        
        val scroll = android.widget.ScrollView(context)
        val listLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }
        
        items.forEachIndexed { index, item ->
            val itemView = TextView(context).apply {
                text = item
                textSize = 16f
                setPadding(0, 30, 0, 30)
                setTextColor(android.graphics.Color.DKGRAY)
                setOnClickListener {
                    dialog.dismiss()
                    onClick(index)
                }
            }
            listLayout.addView(itemView)
            
            // Divider
            val divider = View(context).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, 1
                )
                setBackgroundColor(android.graphics.Color.LTGRAY)
            }
            listLayout.addView(divider)
        }
        
        scroll.addView(listLayout)
        val params = android.widget.LinearLayout.LayoutParams(
             android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
             800 // Max height for scrolling
        )
        scroll.layoutParams = params
        
        layout.addView(scroll)
        dialog.setContentView(layout)
        dialog.show()
    }

    private fun showNearbySearchDialog() {
        val location = mapplsMap?.locationComponent?.lastKnownLocation
        if (location == null) {
            Toast.makeText(this, "Getting your location...", Toast.LENGTH_SHORT).show()
            return
        }

        val categories = arrayOf(
            "Petrol Pump", "Restaurant", "ATM", "Hospital", "Pharmacy", 
            "Hotel", "Parking", "Garage", "Toilet"
        )

        showBottomSheetList("Search Nearby", categories) { which ->
            val cat = categories[which]
            searchNearbyPlaces(cat, cat, "📍", location)
        }
    }
    
    private fun searchNearbyPlaces(keyword: String, categoryName: String, icon: String, location: Location) {
        Toast.makeText(this, "Searching $categoryName...", Toast.LENGTH_SHORT).show()
        
        try {
            val nearbyRequest = com.mappls.sdk.services.api.nearby.MapplsNearby.builder()
                .setLocation(location.latitude, location.longitude)
                .keyword(keyword)
                .radius(5000)
                .build()
            
            com.mappls.sdk.services.api.nearby.MapplsNearbyManager.newInstance(nearbyRequest)
                .call(object : com.mappls.sdk.services.api.OnResponseCallback<com.mappls.sdk.services.api.nearby.model.NearbyAtlasResponse> {
                    override fun onSuccess(response: com.mappls.sdk.services.api.nearby.model.NearbyAtlasResponse?) {
                        val places = response?.suggestedLocations
                        
                        if (places.isNullOrEmpty()) {
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, "No $categoryName found nearby", Toast.LENGTH_SHORT).show()
                            }
                            return
                        }
                        
                        val displayList = places.take(20).map { 
                            "${it.placeName}\n${it.distance?.toInt() ?: 0}m • ${it.placeAddress ?: ""}" 
                        }.toTypedArray()
                        
                        runOnUiThread {
                            showBottomSheetList("Found ${places.size} $categoryName", displayList) { which ->
                                val selectedPlace = places[which]
                                val pin = selectedPlace.mapplsPin
                                
                                if (pin != null) {
                                    try {
                                        mapplsMap?.animateCamera(
                                            com.mappls.sdk.maps.camera.CameraMapplsPinUpdateFactory.newMapplsPinZoom(pin, 16.0)
                                        )
                                        Toast.makeText(this@MainActivity, "Moved to ${selectedPlace.placeName}", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Log.e("Zwap", "Error moving to pin", e)
                                        Toast.makeText(this@MainActivity, "Error moving to location", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(this@MainActivity, "Location ID missing", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    
                    override fun onError(code: Int, message: String?) {
                        runOnUiThread {
                            Log.e("Zwap", "Search Error: $code - $message")
                            Toast.makeText(this@MainActivity, "Search Error: $message", Toast.LENGTH_LONG).show()
                        }
                    }
                })
        } catch (e: Exception) {
            Log.e("Zwap", "Search Exception", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showMapStyleDialog() {
        if (mapplsMap == null) return
        
        // Comprehensive list of Mappls styles based on user's screenshot
        val styleDisplayNames = arrayOf(
            "Street - Day Mode",
            "Street - Night Mode",
            "Satellite Mode",
            "Hybrid Mode",
            "Street - Grey Mode",
            "Sublime Grey Mode",
            "Dark Classic Mode",
            "Hindi Day Mode",
            "India Day Mode",
            "Kogo Grey Mode",
            "Tracking Mode"
        )
        
        val styleNames = arrayOf(
            "standard_day",
            "standard_night",
            "satellite",
            "hybrid_day",
            "gray_day",
            "sublime_gray",
            "dark_classic",
            "hindi_day",
            "india_day",
            "kogo_gray",
            "tracking"
        )
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Map Layers")
            .setItems(styleDisplayNames) { _, which ->
                val styleName = styleNames[which]
                try {
                    val method = mapplsMap!!.javaClass.getMethod("setMapplsStyle", String::class.java)
                    method.invoke(mapplsMap, styleName)
                    Toast.makeText(this, "Switching to ${styleDisplayNames[which]}...", Toast.LENGTH_SHORT).show()
                    
                    // Re-enable location and hazards after style change
                    findViewById<View>(R.id.map_view).postDelayed({
                        mapplsMap?.getStyle { enableLocationComponent(it) }
                        if (osmOverlayEnabled) fetchAndDisplayOSMFeatures()
                    }, 1500)
                } catch (e: Exception) {
                    Log.e("Zwap", "Error setting style $styleName", e)
                    Toast.makeText(this, "Style not available", Toast.LENGTH_SHORT).show()
                }
            }.show()
    }
    
    private fun toggleOSMOverlay() {
        Log.d("Zwap", "toggleOSMOverlay called, current state: $osmOverlayEnabled")
        osmOverlayEnabled = !osmOverlayEnabled
        val button = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_toggle_osm)
        
        if (osmOverlayEnabled) {
            // Active state - orange/amber color with scale animation
            button.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF6B00"))
            button.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100).withEndAction {
                button.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
            }.start()
            Log.d("Zwap", "Hazards overlay enabled, fetching features...")
            Toast.makeText(this, "Hazards ON", Toast.LENGTH_SHORT).show()
            
            /*
            // TEST HAZARDS
            Log.d("Zwap", "ADDING_TEST_MARKERS")
            mapplsMap?.cameraPosition?.target?.let { ctr ->
                val lat = ctr.latitude
                val lon = ctr.longitude
                Log.d("Zwap", "Center: lat=$lat, lon=$lon")
                listOf(
                    Triple(lat + 0.001, lon, "TestN"),
                    Triple(lat - 0.001, lon, "TestS"),
                    Triple(lat, lon + 0.001, "TestE"),
                    Triple(lat, lon - 0.001, "TestW")
                ).forEachIndexed { i, (mlat, mlon, t) ->
                    mapplsMap?.addMarker(MarkerOptions().position(com.mappls.sdk.maps.geometry.LatLng(mlat, mlon)).title(t))?.let { m ->
                        testMarkerIds.add(m.id)
                        Log.d("Zwap", "Marker $i added")
                        val ft = when(i) { 0 -> FeatureType.SPEED_CAMERA; 1 -> FeatureType.TRAFFIC_CALMING; 2 -> FeatureType.STOP_SIGN; else -> FeatureType.GIVE_WAY }
                        osmFeatures.add(OSMFeature(id = -1001L - i, lat = mlat, lon = mlon, type = ft, name = t))
                    }
                }
                Toast.makeText(this, "4 TEST hazards added!", Toast.LENGTH_LONG).show()
                // Immediate proximity check
                checkHazardProximity(lat, lon)
            }
            */
            fetchAndDisplayOSMFeatures()
        } else {
            // Inactive state - white background
            button.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            Log.d("Zwap", "Hazards overlay disabled, clearing markers")
            Toast.makeText(this, "Hazards OFF", Toast.LENGTH_SHORT).show()
            clearOSMMarkers()
        }
    }
    
    private fun fetchAndDisplayOSMFeatures() {
        val center = mapplsMap?.cameraPosition?.target
        if (center == null) {
            Log.e("Zwap", "Map center is null!")
            return
        }
        
        // Check zoom level - show cameras at country view (zoom 3+), hazards at state view (zoom 5+)
        val zoom = mapplsMap?.cameraPosition?.zoom ?: 0.0
        val lat = center.latitude
        val lon = center.longitude
        Log.d("Zwap", "fetchAndDisplayOSMFeatures called: zoom=$zoom, center=($lat,$lon)")
        
        if (zoom < 1.0) {
            clearOSMMarkers()
            Log.d("Zwap", "Zoom level $zoom too low (need 1+), cleared markers")
            return
        }
        
        // Larger search radius based on zoom level
        val delta = when {
            zoom < 4.0 -> 0.50  // Country view: ~55km radius
            zoom < 6.0 -> 0.10  // State view: ~11km radius
            else -> 0.02        // City view: ~2.2km radius
        }
        Log.d("Zwap", "Fetching OSM data: lat=$lat, lon=$lon, zoom=$zoom, radius=${delta*111}km")
        val south = lat - delta
        val west = lon - delta
        val north = lat + delta
        val east = lon + delta
        
        Thread {
            try {
                val query = OSMOverpassService.buildQuery(south, west, north, east)
                Log.d("Zwap", "📍 OSM Query Parameters: bbox=($south,$west,$north,$east), zoom=$zoom, radius=${delta*111}km")
                Log.d("Zwap", "📤 Sending OSM Query (first 250 chars): ${query.take(250)}...")
                val response = OSMOverpassService.instance.queryFeatures(query).execute()
                
                Log.d("Zwap", "📥 OSM Response: code=${response.code()}, successful=${response.isSuccessful}, hasBody=${response.body() != null}")
                
                if (response.isSuccessful && response.body() != null) {
                    val elements = response.body()!!.elements
                    Log.d("Zwap", "📡 OSM API RESPONSE: ${elements.size} elements returned, zoom=$zoom")
                    if (elements.isEmpty()) {
                        Log.w("Zwap", "⚠️ NO ELEMENTS in response - Check if OSM has data for this region/zoom")
                    }
                    
                    // Clear existing markers first
                    runOnUiThread {
                        osmFeatures.removeIf { it.id >= 0 }  // Keep test markers (negative IDs)
                        clearOSMMarkers()
                    }
                    
                    var processedCount = 0
                    var cameraCount = 0
                    var hazardCount = 0
                    
                    elements.forEachIndexed { index, element ->
                        // Skip invalid coordinates
                        if (element.lat == 0.0 && element.lon == 0.0) {
                            Log.d("Zwap", "Skipping element ${element.id} with invalid coords")
                            return@forEachIndexed
                        }
                        
                        element.tags?.forEach { (key, value) ->
                            FeatureType.fromOSMTag(key, value)?.let { featureType ->
                                // Show cameras at lower zoom (country level), hazards at higher zoom (state level)
                                val shouldShow = if (featureType == FeatureType.SPEED_CAMERA) {
                                    zoom >= 1.0  // Cameras visible at very low zoom (country/continent level)
                                } else {
                                    zoom >= 5.0  // Hazards visible at state level only
                                }
                                
                                // DETAILED LOGGING FOR DEBUGGING
                                if (featureType == FeatureType.SPEED_CAMERA) {
                                    Log.d("Zwap", "🎥 CAMERA DETECTED: id=${element.id}, lat=${element.lat}, lon=${element.lon}, zoom=$zoom, shouldShow=$shouldShow")
                                } else {
                                    Log.d("Zwap", "⚠️ HAZARD DETECTED: ${featureType.name}, zoom=$zoom, shouldShow=$shouldShow")
                                }
                                
                                if (shouldShow) {
                                    val feature = OSMFeature(
                                        id = element.id,
                                        lat = element.lat,
                                        lon = element.lon,
                                        type = featureType,
                                        name = element.tags["name"],
                                        tags = element.tags
                                    )
                                    osmFeatures.add(feature)
                                    
                                    if (featureType == FeatureType.SPEED_CAMERA) {
                                        cameraCount++
                                        Log.d("Zwap", "✅ CAMERA ADDED: total cameras now = $cameraCount")
                                    } else {
                                        hazardCount++
                                        Log.d("Zwap", "✅ HAZARD ADDED: ${featureType.name}, total hazards now = $hazardCount")
                                    }
                                    
                                    // Add marker on UI thread with small delay for smooth loading animation
                                    runOnUiThread {
                                        addOSMMarker(feature)
                                        processedCount++
                                        Log.d("Zwap", "🎨 MARKER RENDERED: ${featureType.name} at (${element.lat}, ${element.lon}) - ${osmFeatures.size} total")
                                    }
                                    
                                    // Small delay between markers to show loading animation
                                    Thread.sleep(20)
                                }
                            }
                        }
                    }
                    
                    // Show final count after all markers loaded
                    runOnUiThread {
                        Log.d("Zwap", "Loaded ${osmFeatures.size} total: $cameraCount cameras, $hazardCount hazards")
                        Toast.makeText(this@MainActivity, "Cameras: $cameraCount | Hazards: $hazardCount", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val errorMsg = "OSM API error: ${response.code()} - ${response.message()}"
                    Log.e("Zwap", errorMsg)
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Failed to load hazards", Toast.LENGTH_SHORT).show()
                    }
                }

                // --- FETCH COMMUNITY HAZARDS ---
                try {
                    Log.d("Zwap", "🚀 Fetching Community Hazards from Backend...")
                    kotlinx.coroutines.runBlocking {
                        val commResponse = com.swapmap.zwap.demo.network.ApiClient.hazardApiService.getHazards(
                            lat, lon, delta * 111.0  // Convert degrees to approx km
                        )
                        
                        if (commResponse.isSuccessful && commResponse.body() != null) {
                            val clusterList = commResponse.body()!!.hazards
                            Log.d("Zwap", "✅ Received ${clusterList.size} community hazards from Backend")
                            
                            var communityCount = 0
                            clusterList.forEach { cluster ->
                                try {
                                    val type = if (cluster.status == "NEEDS_REVALIDATION") 
                                        com.swapmap.zwap.demo.model.FeatureType.COMMUNITY_NEEDS_REVALIDATION 
                                    else 
                                        com.swapmap.zwap.demo.model.FeatureType.COMMUNITY_VERIFIED
                                    
                                    // Generate a stable ID from UUID hash
                                    val featId = cluster.hazard_id.hashCode().toLong()
                                    
                                    val feat = com.swapmap.zwap.demo.model.OSMFeature(
                                        id = featId,
                                        lat = cluster.latitude,
                                        lon = cluster.longitude,
                                        type = type,
                                        name = "${cluster.hazard_type.replace("_", " ")} (${cluster.verified_image_count} reports)",
                                        tags = mapOf(
                                            "community" to "true",
                                            "status" to cluster.status,
                                            "confidence" to cluster.confidence_score.toString()
                                        )
                                    )
                                    
                                    osmFeatures.add(feat)
                                    communityCount++
                                    
                                    runOnUiThread {
                                        addOSMMarker(feat)
                                    }
                                } catch (e: Exception) {
                                    Log.e("Zwap", "Error processing community hazard: ${cluster.hazard_id}", e)
                                }
                            }
                            
                            if (communityCount > 0) {
                                runOnUiThread {
                                    Toast.makeText(this@MainActivity, "Added $communityCount Community Hazards", Toast.LENGTH_SHORT).show()
                                    
                                    // Trigger immediate proximity check for new hazards
                                    val loc = mapplsMap?.locationComponent?.lastKnownLocation
                                    if (loc != null) {
                                        checkHazardProximity(loc.latitude, loc.longitude)
                                    } else {
                                        checkHazardProximity(lat, lon)
                                    }
                                }
                            }
                        } else {
                            Log.e("Zwap", "Backend fetch failed: ${commResponse.code()}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Zwap", "Community fetch exception", e)
                }

            } catch (e: Exception) {
                Log.e("Zwap", "OSM fetch error", e)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Network error loading hazards", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
    
    private fun addOSMMarker(feature: OSMFeature) {
        try {
            // Create larger, more visible marker with colored circle background
            val markerOptions = MarkerOptions()
                .position(com.mappls.sdk.maps.geometry.LatLng(feature.lat, feature.lon))
                .title(feature.type.name.replace("_", " "))
                .snippet(feature.name ?: "")
                .icon(com.mappls.sdk.maps.annotations.IconFactory.getInstance(this)
                    .fromBitmap(createColoredMarkerBitmap(feature)))
            
            val marker = mapplsMap?.addMarker(markerOptions)
            if (marker != null) {
                osmMarkerIds.add(marker.id)
                Log.d("Zwap", "Added OSM marker: ${feature.type.name} at ${feature.lat},${feature.lon}")
            } else {
                Log.e("Zwap", "Failed to add marker for ${feature.type.name}")
            }
        } catch (e: Exception) {
            Log.e("Zwap", "Error adding OSM marker for ${feature.type.name}", e)
        }
    }
    
    private fun createColoredMarkerBitmap(feature: OSMFeature): android.graphics.Bitmap {
        // Dynamic size based on zoom level
        val zoom = mapplsMap?.cameraPosition?.zoom ?: 12.0
        val baseSize = when {
            zoom >= 16 -> 140
            zoom >= 14 -> 120
            zoom >= 10 -> 100
            else -> 80
        }
        
        val bitmap = android.graphics.Bitmap.createBitmap(baseSize, baseSize, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        // Use different colors for cameras vs hazards
        val isCamera = feature.type == FeatureType.SPEED_CAMERA
        val circleColor = if (isCamera) android.graphics.Color.parseColor("#2196F3") else android.graphics.Color.parseColor("#FF9800")  // Blue for camera, Orange for hazard
        
        // Draw outer white border circle
        val borderPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = true
            strokeWidth = 2f
        }
        canvas.drawCircle(baseSize / 2f, baseSize / 2f, baseSize / 2f - 1, borderPaint)
        
        // Draw inner colored circle
        val paint = android.graphics.Paint().apply {
            color = circleColor
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(baseSize / 2f, baseSize / 2f, baseSize / 2f - 8, paint)
        
        // Draw icon - camera for speed cameras, exclamation for hazards
        val icon = if (isCamera) "📷" else "!"
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = baseSize * 0.5f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isFakeBoldText = true
        }
        val yPos = (baseSize / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2)
        canvas.drawText(icon, baseSize / 2f, yPos, textPaint)
        
        return bitmap
    }
    
    private fun clearOSMMarkers() {
        mapplsMap?.markers?.filter { osmMarkerIds.contains(it.id) }?.forEach { it.remove() }
        osmMarkerIds.clear()
    }

    private lateinit var searchAdapter: SearchAdapter
    private lateinit var historyAdapter: SearchAdapter
    
    private fun setupSearchOverlay() {
        val overlay = findViewById<View>(R.id.search_overlay)
        val etInput = findViewById<EditText>(R.id.et_search_input)
        val btnBack = findViewById<View>(R.id.btn_search_back)
        val btnClear = findViewById<android.widget.ImageView>(R.id.btn_search_clear)
        val rvResults = findViewById<RecyclerView>(R.id.rv_search_results)
        val rvHistory = findViewById<RecyclerView>(R.id.rv_history)
        val dashboard = findViewById<View>(R.id.search_dashboard)

        val onLocationSelected: (ELocation) -> Unit = { location ->
            saveToHistory(location)
            selectedELoc = location.mapplsPin
            selectedPlace = location  // Store full place details
            findViewById<TextView>(R.id.search_trigger).text = location.placeName
            findViewById<View>(R.id.btn_directions).visibility = View.VISIBLE
            
            location.mapplsPin?.let { pin ->
                isFollowMode = false
                mapplsMap?.animateCamera(CameraMapplsPinUpdateFactory.newMapplsPinZoom(pin, 16.0))
            }
            overlay.visibility = View.GONE
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(etInput.windowToken, 0)
            
            // Show place details bottom sheet like Google Maps
            showPlaceDetailsBottomSheet(location)
        }

        searchAdapter = SearchAdapter(onLocationSelected)
        historyAdapter = SearchAdapter(onLocationSelected)

        rvResults.layoutManager = LinearLayoutManager(this)
        rvResults.adapter = searchAdapter
        
        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = historyAdapter

        // Category Listeners
        val hideKb = {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(etInput.windowToken, 0)
        }
        
        val currentLocation = mapplsMap?.locationComponent?.lastKnownLocation
        
        findViewById<View>(R.id.btn_cat_rest)?.setOnClickListener { 
            hideKb()
            overlay.visibility = View.GONE
            currentLocation?.let { searchNearbyPlaces("Restaurant", "Restaurant", "🍴", it) }
                ?: Toast.makeText(this, "Location not found", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btn_cat_petrol)?.setOnClickListener {
             hideKb()
             overlay.visibility = View.GONE
             currentLocation?.let { searchNearbyPlaces("Petrol Pump", "Petrol Pump", "⛽", it) }
                ?: Toast.makeText(this, "Location not found", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btn_cat_ev)?.setOnClickListener {
             hideKb()
             overlay.visibility = View.GONE
             currentLocation?.let { searchNearbyPlaces("Charging Station", "EV Charging", "⚡", it) }
                ?: Toast.makeText(this, "Location not found", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btn_cat_more)?.setOnClickListener {
             hideKb()
             showNearbySearchDialog()
        }

        findViewById<View>(R.id.layout_home)?.setOnClickListener {
             Toast.makeText(this, "Home Shortcut: Search for address and select it.", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.layout_work)?.setOnClickListener {
             Toast.makeText(this, "Work Shortcut: Search for address and select it.", Toast.LENGTH_SHORT).show()
        }

        etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                if (query.isEmpty()) {
                    // Show history and dashboard, hide results
                    dashboard.visibility = View.VISIBLE
                    rvHistory.visibility = View.VISIBLE
                    rvResults.visibility = View.GONE
                    Log.d("Zwap", "Query cleared, showing history")
                    fetchHistory()
                    btnClear.setImageResource(android.R.drawable.ic_btn_speak_now)
                } else {
                    // Hide history and dashboard, show results
                    dashboard.visibility = View.GONE
                    rvHistory.visibility = View.GONE
                    rvResults.visibility = View.VISIBLE
                    Log.d("Zwap", "Query entered: '$query', searching")
                    performLiveSearch(query)
                    btnClear.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                }
            }
        })

        btnBack.setOnClickListener { 
            overlay.visibility = View.GONE
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(etInput.windowToken, 0)
        }
        
        btnClear.setOnClickListener { 
            if (etInput.text.toString().isEmpty()) {
                performVoiceSearch()
            } else {
                etInput.setText("")
            }
        }
    }

    private fun showSearchOverlay(query: String?) {
        val overlay = findViewById<View>(R.id.search_overlay)
        val etInput = findViewById<EditText>(R.id.et_search_input)
        val dashboard = findViewById<View>(R.id.search_dashboard)
        val rvResults = findViewById<RecyclerView>(R.id.rv_search_results)
        val rvHistory = findViewById<RecyclerView>(R.id.rv_history)
        
        overlay.visibility = View.VISIBLE
        etInput.requestFocus()
        
        if (query.isNullOrEmpty()) {
            // Show history on open
            rvHistory.visibility = View.VISIBLE
            dashboard.visibility = View.VISIBLE
            rvResults.visibility = View.GONE
            Log.d("Zwap", "Showing search overlay with history")
            fetchHistory()
        } else {
            // Show search results for non-empty query
            rvHistory.visibility = View.GONE
            dashboard.visibility = View.GONE
            rvResults.visibility = View.VISIBLE
            etInput.setText(query)
            etInput.setSelection(query.length)
            Log.d("Zwap", "Showing search overlay with results for query: $query")
            performLiveSearch(query)
        }
        
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(etInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private fun saveToHistory(location: ELocation) {
        saveHistoryToFirestore(location)
    }
    
    private fun saveHistoryToFirestore(location: ELocation) {
        if (currentUserId == null) {
            Log.e("Zwap", "❌ User not authenticated, cannot save history")
            Toast.makeText(this@MainActivity, "Please log in to save history", Toast.LENGTH_SHORT).show()
            return
        }
        
        val mapplsPin = location.mapplsPin ?: ""
        
        // Check if this place already exists in history (prevent duplicates)
        db.collection("users")
            .document(currentUserId!!)
            .collection("searchHistory")
            .whereEqualTo("mapplsPin", mapplsPin)
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    // Place doesn't exist, add it
                    addNewHistoryItem(location)
                } else {
                    // Place exists, update timestamp to move it to top
                    val existingDoc = documents.documents[0]
                    existingDoc.reference.update("timestamp", System.currentTimeMillis())
                        .addOnSuccessListener {
                            Log.d("Zwap", "✅ Updated existing history item: ${location.placeName}")
                        }
                        .addOnFailureListener { e ->
                            Log.e("Zwap", "❌ Error updating history: ${e.message}", e)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("Zwap", "❌ Error checking duplicates: ${e.message}", e)
                // If check fails, just add the item anyway
                addNewHistoryItem(location)
            }
    }
    
    private fun addNewHistoryItem(location: ELocation) {
        try {
            val historyItem = hashMapOf(
                "placeName" to (location.placeName ?: "Unknown"),
                "placeAddress" to (location.placeAddress ?: ""),
                "mapplsPin" to (location.mapplsPin ?: ""),
                "timestamp" to System.currentTimeMillis()
            )
            
            Log.d("Zwap", "💾 Saving to Firebase for user: $currentUserId - ${location.placeName}")
            
            // Save to user-specific path: users/{userId}/searchHistory/{docId}
            db.collection("users")
                .document(currentUserId!!)
                .collection("searchHistory")
                .add(historyItem)
                .addOnSuccessListener { documentReference ->
                    Log.d("Zwap", "✅ Firebase saved successfully: ${documentReference.id}")
                }
                .addOnFailureListener { e ->
                    Log.e("Zwap", "❌ Firebase save error: ${e.message}", e)
                    Toast.makeText(this@MainActivity, "❌ Firebase error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Log.e("Zwap", "❌ Error saving history: ${e.message}", e)
            Toast.makeText(this@MainActivity, "❌ Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    


    private fun fetchHistory() {
        fetchHistoryFromFirestore()
    }
    
    private fun fetchHistoryFromFirestore(limit: Long = 5) {
        if (currentUserId == null) {
            Log.e("Zwap", "❌ User not authenticated, cannot fetch history")
            if (::historyAdapter.isInitialized) {
                historyAdapter.submitList(emptyList())
            }
            return
        }
        
        try {
            Log.d("Zwap", "📱 Fetching history from Firebase for user: $currentUserId (limit: $limit)")
            
            // Query user-specific path: users/{userId}/searchHistory
            db.collection("users")
                .document(currentUserId!!)
                .collection("searchHistory")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .addOnSuccessListener { documents ->
                    Log.d("Zwap", "✅ Firebase: Fetched ${documents.size()} history items for user: $currentUserId")
                    val historyList = mutableListOf<ELocation>()
                    
                    for (document in documents) {
                        try {
                            val placeName = document.getString("placeName") ?: "Unknown"
                            val placeAddress = document.getString("placeAddress") ?: ""
                            val mapplsPin = document.getString("mapplsPin") ?: ""
                            
                            val loc = ELocation()
                            loc.placeName = placeName
                            loc.placeAddress = placeAddress
                            loc.mapplsPin = mapplsPin
                            historyList.add(loc)
                            
                            Log.d("Zwap", "📍 History item: $placeName")
                        } catch (e: Exception) {
                            Log.e("Zwap", "Parse error for document ${document.id}", e)
                        }
                    }
                    
                    // Update adapter on main thread
                    runOnUiThread {
                        if (::historyAdapter.isInitialized) {
                            historyAdapter.submitList(historyList)
                            Log.d("Zwap", "✅ Adapter updated with ${historyList.size} Firebase items")
                        } else {
                            Log.e("Zwap", "❌ History adapter not initialized!")
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("Zwap", "❌ Firebase fetch failed: ${e.message}", e)
                    
                    // Show error to user
                    runOnUiThread {
                        Log.d("Zwap", "Firebase error: ${e.message}")
                        Toast.makeText(this@MainActivity, "❌ Firebase error: ${e.message}", Toast.LENGTH_SHORT).show()
                        
                        // Clear the list and show error
                        if (::historyAdapter.isInitialized) {
                            historyAdapter.submitList(emptyList())
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e("Zwap", "❌ Firebase fetch error: ${e.message}", e)
            runOnUiThread {
                Toast.makeText(this@MainActivity, "❌ Error: ${e.message}", Toast.LENGTH_SHORT).show()
                if (::historyAdapter.isInitialized) {
                    historyAdapter.submitList(emptyList())
                }
            }
        }
    }
    

    


    private fun performLiveSearch(query: String) {
        if (query.isEmpty()) {
            searchAdapter.submitList(emptyList())
            return
        }
        
        val lastLoc = mapplsMap?.locationComponent?.lastKnownLocation
        val builder = MapplsAutoSuggest.builder().query(query)
        if (lastLoc != null) builder.setLocation(lastLoc.latitude, lastLoc.longitude)
        val autoSuggest = builder.build()

        MapplsAutosuggestManager.newInstance(autoSuggest).call(object : OnResponseCallback<AutoSuggestAtlasResponse> {
            override fun onSuccess(response: AutoSuggestAtlasResponse?) {
                searchAdapter.submitList(response?.suggestedLocations ?: emptyList())
            }
            override fun onError(p0: Int, p1: String?) {}
        })
    }

    private inner class SearchAdapter(private val onItemClick: (ELocation) -> Unit) : 
        RecyclerView.Adapter<SearchAdapter.ViewHolder>() {
        
        private var items = listOf<ELocation>()

        fun submitList(newItems: List<ELocation>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.layout_search_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.text1.text = item.placeName
            holder.text2.text = item.placeAddress
            holder.itemView.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val text1: TextView = view.findViewById(R.id.tv_place_name)
            val text2: TextView = view.findViewById(R.id.tv_place_address)
        }
    }

    override fun onMapReady(map: MapplsMap) {
        Log.i("ZwapTest", "onMapReady called")
        mapplsMap = map
        
        // Enable all built-in Mappls traffic and safety layers (Cameras, Hazards, etc.)
        try {
            Log.i("ZwapTest", "Enabling built-in layers...")
            map.enableTraffic(true)
            map.enableTrafficStopIcon(true)
            map.enableTrafficFreeFlow(true)
            map.enableTrafficNonFreeFlow(true)
            map.enableTrafficClosure(true)
            map.enableTrafficOther1(true) // Usually Speed Cameras
            map.enableTrafficOther2(true) // Usually Road Hazards/Incidents
            map.enableTrafficOther3(true)
            map.enableTrafficOther4(true)
            map.enableTrafficOther5(true)
            Log.i("ZwapTest", "All built-in Mappls traffic/safety layers enabled")
        } catch (e: Exception) {
            Log.e("ZwapTest", "Error enabling built-in layers: ${e.message}")
        }
        
        map.uiSettings?.setCompassEnabled(false)
        map.uiSettings?.setLogoMargins(0, 0, 0, -200) // Hide logo by moving it off-screen
        
        map.getStyle { style -> 
            Log.i("ZwapTest", "Style loaded. Scanning for layers...")
            // Log layers to confirm built-in safety layers are present
            style.layers.forEach { layer ->
                if (layer.id.contains("traffic", true) || layer.id.contains("safety", true)) {
                    Log.i("ZwapTest", "Found Built-in Layer: ${layer.id}")
                }
            }
            
            symbolManager = SymbolManager(mapView!!, map, style)
            lineManager = LineManager(mapView!!, map, style)
            
            enableLocationComponent(style)
        }
        
        // Add camera change listener to refresh OSM overlay on zoom changes
        // Add camera change listener to refresh OSM overlay
        map.addOnCameraIdleListener {
            val currentZoom = map.cameraPosition.zoom
            lastZoomLevel = currentZoom
            
            if (osmOverlayEnabled) {
                // Debounce fetch to update on Pan/Zoom but avoid 429 API errors
                osmRunnable?.let { osmHandler.removeCallbacks(it) }
                osmRunnable = Runnable { 
                    fetchAndDisplayOSMFeatures() 
                }
                osmHandler.postDelayed(osmRunnable!!, 1000)
            }
        }
    }
    


    override fun onMapError(p0: Int, p1: String?) {
         Toast.makeText(this, "Map Error: \$p1", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("MissingPermission")
    private fun enableLocationComponent(style: Style) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 201)
            return
        }

        val locationComponent = mapplsMap?.locationComponent
        val options = LocationComponentActivationOptions.builder(this, style).build()
        locationComponent?.activateLocationComponent(options)
        locationComponent?.isLocationComponentEnabled = true
        locationComponent?.renderMode = RenderMode.COMPASS
        locationComponent?.cameraMode = CameraMode.TRACKING_GPS_NORTH

        initLocationEngine()
    }

    @SuppressLint("MissingPermission")
    private fun initLocationEngine() {
        locationEngine = LocationEngineProvider.getBestLocationEngine(this)
        val request = LocationEngineRequest.Builder(1000L)
            .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
            .setMaxWaitTime(5000L)
            .build()
        
        locationEngine?.requestLocationUpdates(request, callback, mainLooper)
        locationEngine?.getLastLocation(callback)
    }

    private fun enableFollowMode() {
        isFollowMode = true
        mapplsMap?.locationComponent?.cameraMode = CameraMode.TRACKING_GPS
        mapplsMap?.animateCamera(CameraUpdateFactory.zoomTo(18.0))
    }

    fun handleLocationUpdate(location: Location) {
        val speedKmh = (location.speed * 3.6).toInt()
        findViewById<TextView>(R.id.tv_speed).text = "$speedKmh"
        findViewById<TextView>(R.id.tv_speed).setTextColor(if (speedKmh > 50) Color.RED else Color.WHITE)
        
        // Check hazard proximity (during overlay mode OR navigation)
        if (osmOverlayEnabled || isNavigating) {
            checkHazardProximity(location.latitude, location.longitude)
        }

        // Refresh OSM data if enabled and moved > 1km
        if (osmOverlayEnabled) {
            if (lastOSMFetchLocation == null || location.distanceTo(lastOSMFetchLocation!!) > 1000) {
                lastOSMFetchLocation = location
                fetchAndDisplayOSMFeatures()
            }
        }
    }



    private fun alertUser(message: String) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAlertTime < ALERT_COOLDOWN) return
        
        lastAlertTime = currentTime
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        tts?.speak(message, TextToSpeech.QUEUE_ADD, null, null)
    }

    private fun checkHazardProximity(userLat: Double, userLon: Double) {
        osmFeatures.forEach { feature ->
            if (alertedHazardIds.contains(feature.id)) return@forEach
            val results = FloatArray(1)
            android.location.Location.distanceBetween(userLat, userLon, feature.lat, feature.lon, results)
            val distance = results[0].toDouble()
            Log.d("Zwap", "Distance to ${feature.type}: ${distance.toInt()}m")
            if (distance < HAZARD_ALERT_DISTANCE) {
                alertedHazardIds.add(feature.id)
                val msg = when (feature.type) {
                    FeatureType.SPEED_CAMERA -> "Speed camera ahead in ${distance.toInt()} meters"
                    FeatureType.TRAFFIC_CALMING -> "Speed bump ahead in ${distance.toInt()} meters"
                    FeatureType.STOP_SIGN -> "Stop sign ahead in ${distance.toInt()} meters"
                    FeatureType.GIVE_WAY -> "Give way sign ahead in ${distance.toInt()} meters"
                    FeatureType.COMMUNITY_VERIFIED -> "Community verified hazard ahead in ${distance.toInt()} meters"
                    FeatureType.COMMUNITY_NEEDS_REVALIDATION -> "Reported hazard ahead in ${distance.toInt()} meters, needs verification"
                    else -> "Hazard ahead in ${distance.toInt()} meters"
                }
                Log.d("Zwap", "HAZARD ALERT: $msg")
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); tts?.speak(msg, TextToSpeech.QUEUE_ADD, null, null)
                // Add to visual panel
                addHazardToPanel(feature, distance.toInt())
            }
        }
        // Update panel with current distances and remove crossed hazards
        updateHazardPanel(userLat, userLon)
    }

    private fun setupHazardAlertPanel() {
        val recyclerView = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_hazard_alerts)
        recyclerView?.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        hazardAlertAdapter = HazardAlertAdapter(activeHazards) { hazard ->
            Log.d("Zwap", "Hazard crossed: \${hazard.type}")
        }
        recyclerView?.adapter = hazardAlertAdapter
        
        // Setup collapse/expand header click
        findViewById<View>(R.id.hazard_panel_header)?.setOnClickListener {
            toggleHazardPanel()
        }
    }

    private fun toggleHazardPanel() {
        isHazardPanelExpanded = !isHazardPanelExpanded
        val recyclerView = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_hazard_alerts)
        val expandArrow = findViewById<android.widget.ImageView>(R.id.iv_hazard_expand)
        
        if (isHazardPanelExpanded) {
            recyclerView?.visibility = View.VISIBLE
            expandArrow?.animate()?.rotation(180f)?.setDuration(200)?.start()
        } else {
            recyclerView?.visibility = View.GONE
            expandArrow?.animate()?.rotation(0f)?.setDuration(200)?.start()
        }
    }

    private fun updateHazardPanel(userLat: Double, userLon: Double) {
        val panel = findViewById<View>(R.id.hazard_alert_panel)
        val countView = findViewById<TextView>(R.id.tv_hazard_count)
        val toRemove = mutableListOf<Long>()
        activeHazards.forEach { hazard ->
            val feature = osmFeatures.find { it.id == hazard.id }
            if (feature != null) {
                val results = FloatArray(1)
                android.location.Location.distanceBetween(userLat, userLon, feature.lat, feature.lon, results)
                val newDistance = results[0].toInt()
                if (newDistance < HAZARD_CROSSED_DISTANCE) {
                    toRemove.add(hazard.id)
                    Log.d("Zwap", "CROSSED hazard: \${hazard.type} at \${newDistance}m")
                } else {
                    hazardAlertAdapter?.updateHazard(hazard.id, newDistance)
                }
            }
        }
        toRemove.forEach { id ->
            hazardAlertAdapter?.removeHazard(id)
            activeHazards.removeIf { it.id == id }
        }
        countView?.text = activeHazards.size.toString()
        panel?.visibility = if (activeHazards.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun addHazardToPanel(feature: OSMFeature, distance: Int) {
        val hazard = HazardAlert(
            id = feature.id,
            type = feature.type,
            name = feature.name ?: feature.type.name,
            distance = distance
        )
        if (activeHazards.none { it.id == hazard.id }) {
            activeHazards.add(hazard)
            activeHazards.sortBy { it.distance }
            hazardAlertAdapter?.notifyDataSetChanged()
            val panel = findViewById<View>(R.id.hazard_alert_panel)
            val countView = findViewById<TextView>(R.id.tv_hazard_count)
            panel?.visibility = View.VISIBLE
            countView?.text = activeHazards.size.toString()
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == RESULT_OK) {
            val place: ELocation? = PlaceAutocomplete.getPlace(data)
            if (place != null) {
                selectedELoc = place.mapplsPin
                findViewById<TextView>(R.id.search_trigger).text = place.placeName
                findViewById<View>(R.id.btn_directions).visibility = View.VISIBLE
                
                place.mapplsPin?.let { pin ->
                    isFollowMode = false
                    mapplsMap?.animateCamera(CameraMapplsPinUpdateFactory.newMapplsPinZoom(pin, 16.0))
                }
            }
        } else if (requestCode == 202 && resultCode == RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val query = results[0]
                showSearchOverlay(query)
            }
        }
    }

    private fun getDirections() {
        // Check if destination is selected
        if (selectedELoc.isNullOrEmpty()) {
            Toast.makeText(this, "Please select a destination first", Toast.LENGTH_SHORT).show()
            return
        }
        
        val lastLocation = mapplsMap?.locationComponent?.lastKnownLocation
        if (lastLocation == null) {
            Toast.makeText(this, "Getting your location...", Toast.LENGTH_SHORT).show()
            return
        }
        
        Toast.makeText(this, "Calculating route...", Toast.LENGTH_SHORT).show()
        
        val origin = Point.fromLngLat(lastLocation.longitude, lastLocation.latitude)
        
        Log.d("Zwap", "Getting directions from (${lastLocation.latitude}, ${lastLocation.longitude}) to $selectedELoc")
        
        val builder = MapplsDirections.builder()
            .origin(origin)
            .destination(selectedELoc!!)
            .profile(DirectionsCriteria.PROFILE_DRIVING)
            .resource(DirectionsCriteria.RESOURCE_ROUTE_ETA)
            .steps(true)
            .overview(DirectionsCriteria.OVERVIEW_FULL)
            .annotations(DirectionsCriteria.ANNOTATION_CONGESTION, DirectionsCriteria.ANNOTATION_DISTANCE)
            .build()
            
        MapplsDirectionManager.newInstance(builder).call(object : OnResponseCallback<DirectionsResponse> {
            override fun onSuccess(response: DirectionsResponse?) {
                if (response != null && response.routes().isNotEmpty()) {
                    currentRoute = response
                    val route = response.routes()[0]
                    
                    Log.d("Zwap", "Route found: ${route.distance()}m, ${route.duration()}s")
                    
                    drawRoute(route.geometry())
                    extractHazardsFromRoute(route.legs()?.get(0)?.steps())
                    
                    // Show directions UI (Google Maps style)
                    showDirectionsUI(route.distance()!!, route.duration()!!)
                    
                    enableFollowMode()
                    
                    Toast.makeText(this@MainActivity, "Route ready!", Toast.LENGTH_SHORT).show()
                } else {
                    Log.e("Zwap", "No routes found in response")
                    Toast.makeText(this@MainActivity, "No route found", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onError(code: Int, message: String?) {
                Log.e("Zwap", "Directions Error: $code - $message")
                Toast.makeText(this@MainActivity, "Directions Error: $message", Toast.LENGTH_SHORT).show()
            }
        })
    }
    
    private fun showDirectionsUI(distance: Double, duration: Double) {
        // Hide search bar
        findViewById<View>(R.id.search_card).visibility = View.GONE
        
        // Show directions header card
        findViewById<View>(R.id.directions_header_card).visibility = View.VISIBLE
        findViewById<TextView>(R.id.tv_origin_name).text = "Your location"
        findViewById<TextView>(R.id.tv_destination_name).text = selectedPlace?.placeName ?: "Destination"
        
        // Setup swap locations button
        findViewById<View>(R.id.btn_swap_locations).setOnClickListener {
            Toast.makeText(this, "Swap not available - route starts from your location", Toast.LENGTH_SHORT).show()
        }
        
        // Setup menu button
        findViewById<View>(R.id.btn_directions_menu).setOnClickListener {
            val popup = android.widget.PopupMenu(this, it)
            popup.menu.add("Route options")
            popup.menu.add("Add stop")
            popup.menu.add("Set departure time")
            popup.setOnMenuItemClickListener { item ->
                Toast.makeText(this, "${item.title} - Coming soon", Toast.LENGTH_SHORT).show()
                true
            }
            popup.show()
        }
        
        // Show directions bottom panel
        findViewById<View>(R.id.directions_bottom_panel).visibility = View.VISIBLE
        
        val durationMin = (duration / 60.0).toInt()
        val durationText = if (durationMin >= 60) {
            "${durationMin / 60} hr ${durationMin % 60} min"
        } else {
            "$durationMin min"
        }
        findViewById<TextView>(R.id.tv_route_duration).text = durationText
        findViewById<TextView>(R.id.tv_route_distance).text = "%.1f km".format(distance / 1000.0)
        
        // Setup close button
        findViewById<View>(R.id.btn_close_directions).setOnClickListener {
            closeDirectionsUI()
        }
        
        // Setup start navigation button
        findViewById<View>(R.id.btn_start_navigation).setOnClickListener {
            Toast.makeText(this, "Starting navigation...", Toast.LENGTH_SHORT).show()
            // TODO: Start turn-by-turn navigation
        }
        
        // Setup share button
        findViewById<View>(R.id.btn_share_route)?.setOnClickListener {
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, "Check out this route to ${selectedPlace?.placeName ?: "destination"}")
            }
            startActivity(android.content.Intent.createChooser(shareIntent, "Share route"))
        }
        
        // Setup options button
        findViewById<View>(R.id.btn_route_options)?.setOnClickListener {
            Toast.makeText(this, "Route options - Coming soon", Toast.LENGTH_SHORT).show()
        }
        
        // Hide old route info layout
        findViewById<View>(R.id.route_info_layout).visibility = View.GONE
    }
    
    private fun closeDirectionsUI() {
        // Show search bar
        findViewById<View>(R.id.search_card).visibility = View.VISIBLE
        
        // Hide directions panels
        findViewById<View>(R.id.directions_header_card).visibility = View.GONE
        findViewById<View>(R.id.directions_bottom_panel).visibility = View.GONE
        
        // Clear route
        lineManager?.clearAll()
        mapplsMap?.clear()
        clearRouteHazards()
        
        // Reset state
        currentRoute = null
        isNavigating = false
    }
    
    private fun showPlaceDetailsBottomSheet(location: ELocation) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 48)
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.parseColor("#1E1E1E"))
        }
        
        // Drag handle
        val dragHandle = View(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(80, 10).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                bottomMargin = 32
            }
            setBackgroundColor(Color.parseColor("#666666"))
        }
        layout.addView(dragHandle)
        
        // Place name (large title)
        val titleView = TextView(this).apply {
            text = location.placeName ?: "Unknown Place"
            textSize = 24f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
        }
        layout.addView(titleView)
        
        // Place type/category
        val typeView = TextView(this).apply {
            text = location.orderIndex?.toString() ?: location.type ?: "Location"
            textSize = 14f
            setTextColor(Color.parseColor("#AAAAAA"))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }
        layout.addView(typeView)
        
        // Address
        val addressView = TextView(this).apply {
            text = "📍 ${location.placeAddress ?: "Address not available"}"
            textSize = 14f
            setTextColor(Color.parseColor("#CCCCCC"))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
        }
        layout.addView(addressView)
        
        // Distance (if available)
        location.distance?.let { dist ->
            val distanceView = TextView(this).apply {
                text = "📏 ${dist.toInt()}m away"
                textSize = 14f
                setTextColor(Color.parseColor("#CCCCCC"))
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 24 }
            }
            layout.addView(distanceView)
        }
        
        // Action buttons row
        val buttonsLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        }
        
        // Directions button
        val directionsBtn = com.google.android.material.button.MaterialButton(this).apply {
            text = "Directions"
            setIconResource(android.R.drawable.ic_menu_directions)
            setBackgroundColor(Color.parseColor("#00BFA5"))
            setTextColor(Color.WHITE)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginEnd = 8 }
            setOnClickListener {
                dialog.dismiss()
                getDirections()
            }
        }
        buttonsLayout.addView(directionsBtn)
        
        // Start button
        val startBtn = com.google.android.material.button.MaterialButton(this).apply {
            text = "Start"
            setIconResource(android.R.drawable.ic_media_play)
            setBackgroundColor(Color.parseColor("#4FC3F7"))
            setTextColor(Color.WHITE)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginStart = 8; marginEnd = 8 }
            setOnClickListener {
                dialog.dismiss()
                getDirections()
                // Start navigation after getting directions
                Toast.makeText(this@MainActivity, "Starting navigation...", Toast.LENGTH_SHORT).show()
            }
        }
        buttonsLayout.addView(startBtn)
        
        // Save button
        val saveBtn = com.google.android.material.button.MaterialButton(this).apply {
            text = "Save"
            setIconResource(android.R.drawable.ic_menu_save)
            setBackgroundColor(Color.parseColor("#333333"))
            setTextColor(Color.WHITE)
            strokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#666666"))
            strokeWidth = 2
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginStart = 8 }
            setOnClickListener {
                Toast.makeText(this@MainActivity, "Place saved!", Toast.LENGTH_SHORT).show()
            }
        }
        buttonsLayout.addView(saveBtn)
        
        layout.addView(buttonsLayout)
        
        dialog.setContentView(layout)
        dialog.show()
    }

    private fun extractHazardsFromRoute(steps: List<LegStep>?) {
        routeHazards.clear()
        symbolManager?.clearAll()
        // Only store hazard points for alerts, don't add markers for every step
        steps?.forEach { step ->
            step.maneuver()?.location()?.let { location ->
                routeHazards.add(Pair(location.latitude(), location.longitude()))
            }
        }
        Log.d("Zwap", "Extracted ${routeHazards.size} potential hazard points from route")
    }

    private fun drawRoute(geometry: String?) {
        if (geometry == null) return
        
        lineManager?.clearAll()
        symbolManager?.clearAll()
        clearRouteHazards()  // Clear previous route hazards
        isNavigating = true  // Mark navigation as active
        
        val points = PolylineUtils.decode(geometry, Constants.PRECISION_6)
        val latLngs = points.map { com.mappls.sdk.maps.geometry.LatLng(it.latitude(), it.longitude()) }
        
        // Draw smooth route line with rounded caps
        val lineOptions = LineOptions()
            .points(latLngs)
            .lineColor("#2196F3")  // Google Maps blue
            .lineWidth(8f)
            .lineOpacity(0.9f)
        lineManager?.create(lineOptions)
        
        // Add only DESTINATION marker (red pin) - no start marker since current location is already shown
        if (latLngs.size > 1) {
            val endPoint = latLngs.last()
            val endMarker = MarkerOptions()
                .position(endPoint)
                .title("Destination")
                .icon(com.mappls.sdk.maps.annotations.IconFactory.getInstance(this)
                    .fromBitmap(createRouteMarkerBitmap(false)))
            mapplsMap?.addMarker(endMarker)
        }
        
        // Fetch hazards along the route progressively (50km chunks)
        fetchHazardsAlongRouteProgressive(points)
    }
    
    private fun fetchHazardsAlongRouteProgressive(routePoints: List<Point>) {
        if (routePoints.isEmpty()) {
            Log.d("Zwap", "No route points to fetch hazards for")
            return
        }
        
        // Cancel any existing fetch job
        hazardFetchJob?.cancel()
        currentRoutePoints = routePoints
        currentHazardFetchIndex = 0
        
        // Calculate total route distance
        totalRouteDistanceMeters = 0.0
        for (i in 0 until routePoints.size - 1) {
            totalRouteDistanceMeters += calculateDistance(
                routePoints[i].latitude(), routePoints[i].longitude(),
                routePoints[i + 1].latitude(), routePoints[i + 1].longitude()
            )
        }
        
        Log.d("Zwap", "Total route distance: ${(totalRouteDistanceMeters / 1000).toInt()} km, starting progressive fetch...")
        
        // Show progress indicator
        runOnUiThread {
            findViewById<View>(R.id.hazard_scan_progress_container)?.visibility = View.VISIBLE
            updateHazardScanProgress(0)
        }
        
        // Start fetching in 50km chunks
        hazardFetchJob = lifecycleScope.launch(Dispatchers.IO) {
            fetchNextHazardChunk()
        }
    }
    
    private fun updateHazardScanProgress(percent: Int) {
        findViewById<android.widget.ProgressBar>(R.id.hazard_scan_progress)?.progress = percent
        findViewById<TextView>(R.id.tv_hazard_scan_percent)?.text = "$percent%"
    }
    
    private fun hideHazardScanProgress() {
        findViewById<View>(R.id.hazard_scan_progress_container)?.visibility = View.GONE
    }
    
    private suspend fun fetchNextHazardChunk() {
        if (!isNavigating || currentRoutePoints.isEmpty()) {
            Log.d("Zwap", "Navigation stopped or no route points, stopping hazard fetch")
            withContext(Dispatchers.Main) { hideHazardScanProgress() }
            return
        }
        
        val CHUNK_DISTANCE_METERS = 50000.0  // 50 km per chunk
        val startIndex = currentHazardFetchIndex
        var accumulatedDistance = 0.0
        var endIndex = startIndex
        
        // Find the end index for this 50km chunk
        for (i in startIndex until currentRoutePoints.size - 1) {
            val dist = calculateDistance(
                currentRoutePoints[i].latitude(), currentRoutePoints[i].longitude(),
                currentRoutePoints[i + 1].latitude(), currentRoutePoints[i + 1].longitude()
            )
            accumulatedDistance += dist
            endIndex = i + 1
            
            if (accumulatedDistance >= CHUNK_DISTANCE_METERS) {
                break
            }
        }
        
        // If we've reached the end
        if (startIndex >= currentRoutePoints.size - 1) {
            Log.d("Zwap", "All hazard chunks fetched!")
            withContext(Dispatchers.Main) {
                updateHazardScanProgress(100)
                // Hide after a brief delay to show 100%
                kotlinx.coroutines.MainScope().launch {
                    kotlinx.coroutines.delay(1500)
                    hideHazardScanProgress()
                }
            }
            return
        }
        
        // Get the chunk of points
        val chunkPoints = currentRoutePoints.subList(startIndex, minOf(endIndex + 1, currentRoutePoints.size))
        val chunkEndKm = getDistanceToIndex(endIndex)
        
        // Calculate and update progress percentage
        val progressPercent = if (totalRouteDistanceMeters > 0) {
            ((chunkEndKm / totalRouteDistanceMeters) * 100).toInt().coerceIn(0, 100)
        } else 0
        
        Log.d("Zwap", "Fetching hazards: ${progressPercent}% complete")
        
        withContext(Dispatchers.Main) {
            updateHazardScanProgress(progressPercent)
        }
        
        // Retry logic for failed requests
        var retryCount = 0
        val maxRetries = 2
        var success = false
        
        while (!success && retryCount <= maxRetries && isNavigating) {
            try {
                // Build bounding box for this chunk
                var minLat = Double.MAX_VALUE
                var maxLat = Double.MIN_VALUE
                var minLon = Double.MAX_VALUE
                var maxLon = Double.MIN_VALUE
                
                chunkPoints.forEach { point ->
                    minLat = minOf(minLat, point.latitude())
                    maxLat = maxOf(maxLat, point.latitude())
                    minLon = minOf(minLon, point.longitude())
                    maxLon = maxOf(maxLon, point.longitude())
                }
                
                // Add padding (500m buffer)
                val padding = 0.005
                minLat -= padding
                maxLat += padding
                minLon -= padding
                maxLon += padding
                
                // OSM Overpass query - full hazard types with longer timeout
                val query = """
                    [out:json][timeout:30];
                    (
                      node["highway"="speed_camera"]($minLat,$minLon,$maxLat,$maxLon);
                      node["enforcement"="speed"]($minLat,$minLon,$maxLat,$maxLon);
                      node["hazard"]($minLat,$minLon,$maxLat,$maxLon);
                      node["traffic_calming"]($minLat,$minLon,$maxLat,$maxLon);
                      node["barrier"="toll_booth"]($minLat,$minLon,$maxLat,$maxLon);
                    );
                    out body;
                """.trimIndent()
                
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                
                val requestBody = query.toRequestBody("text/plain".toMediaTypeOrNull())
                
                val request = okhttp3.Request.Builder()
                    .url("https://overpass-api.de/api/interpreter")
                    .post(requestBody)
                    .build()
                
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                
                if (response.isSuccessful && responseBody != null) {
                    val hazards = parseOSMHazards(responseBody, chunkPoints).toMutableList()
                    Log.d("Zwap", "Found ${hazards.size} OSM hazards in chunk (${progressPercent}%)")
                    
                    // Fetch Community Hazards for this chunk
                    try {
                        val centerLat = (minLat + maxLat) / 2
                        val centerLon = (minLon + maxLon) / 2
                        // Calculate approximate radius (diagonal of bounding box / 2)
                        val latDiff = maxLat - minLat
                        val lonDiff = maxLon - minLon
                        val radiusKm = (Math.sqrt(latDiff * latDiff + lonDiff * lonDiff) * 111.0) / 2
                        
                        Log.d("Zwap", "Fetching community hazards: center=($centerLat,$centerLon), radius=${radiusKm}km")
                        
                        val commResponse = com.swapmap.zwap.demo.network.ApiClient.hazardApiService.getHazards(
                            centerLat, centerLon, radiusKm
                        )
                        
                        if (commResponse.isSuccessful && commResponse.body() != null) {
                            val communityHazards = commResponse.body()!!.hazards.map { cluster ->
                                RouteHazard(
                                    lat = cluster.latitude,
                                    lon = cluster.longitude,
                                    type = if (cluster.status == "NEEDS_REVALIDATION") 
                                        HazardType.COMMUNITY_REVALIDATE 
                                    else 
                                        HazardType.COMMUNITY_VERIFIED_HAZARD,
                                    name = "${cluster.hazard_type.replace("_", " ")} (${cluster.verified_image_count} reports)"
                                )
                            }
                            hazards.addAll(communityHazards)
                            Log.d("Zwap", "Added ${communityHazards.size} community hazards to chunk")
                        }
                    } catch (e: Exception) {
                        Log.e("Zwap", "Failed to fetch community hazards for chunk: ${e.message}")
                    }
                    
                    withContext(Dispatchers.Main) {
                        if (isNavigating) {
                            displayRouteHazards(hazards)
                        }
                    }
                    success = true
                } else if (response.code == 504 || response.code == 429) {
                    // Server timeout or rate limit - wait and retry
                    Log.w("Zwap", "OSM server busy (${response.code}), retry ${retryCount + 1}/$maxRetries")
                    retryCount++
                    if (retryCount <= maxRetries) {
                        kotlinx.coroutines.delay(2000L * retryCount) // Exponential backoff
                    }
                } else {
                    Log.e("Zwap", "OSM request failed: code=${response.code}")
                    success = true // Don't retry for other errors
                }
            } catch (e: Exception) {
                Log.e("Zwap", "Error fetching chunk hazards: ${e.message}")
                retryCount++
                if (retryCount <= maxRetries) {
                    kotlinx.coroutines.delay(2000L * retryCount)
                }
            }
        }
        
        // Update index for next chunk and continue
        currentHazardFetchIndex = endIndex
        
        // Delay between chunks to avoid API rate limiting
        kotlinx.coroutines.delay(500)
        
        // Fetch next chunk if still navigating
        if (isNavigating && currentHazardFetchIndex < currentRoutePoints.size - 1) {
            fetchNextHazardChunk()
        } else if (isNavigating) {
            // Completed scanning
            withContext(Dispatchers.Main) {
                updateHazardScanProgress(100)
                kotlinx.coroutines.MainScope().launch {
                    kotlinx.coroutines.delay(1500)
                    hideHazardScanProgress()
                }
            }
        }
    }
    
    private fun getDistanceToIndex(index: Int): Double {
        var distance = 0.0
        for (i in 0 until minOf(index, currentRoutePoints.size - 1)) {
            distance += calculateDistance(
                currentRoutePoints[i].latitude(), currentRoutePoints[i].longitude(),
                currentRoutePoints[i + 1].latitude(), currentRoutePoints[i + 1].longitude()
            )
        }
        return distance
    }
    
    // Keep old function for compatibility but redirect to progressive
    private fun fetchHazardsAlongRoute(routePoints: List<Point>) {
        fetchHazardsAlongRouteProgressive(routePoints)
    }
    
    private fun parseOSMHazards(json: String, routePoints: List<Point>): List<RouteHazard> {
        val hazards = mutableListOf<RouteHazard>()
        try {
            val jsonObject = org.json.JSONObject(json)
            val elements = jsonObject.getJSONArray("elements")
            
            for (i in 0 until elements.length()) {
                val element = elements.getJSONObject(i)
                val lat = element.getDouble("lat")
                val lon = element.getDouble("lon")
                
                // Check if hazard is within 500m of the route (increased for highway routes)
                val isNearRoute = isPointNearRoute(lat, lon, routePoints, 500.0)
                
                if (isNearRoute) {
                    val tags = element.optJSONObject("tags")
                    val type = when {
                        tags?.has("highway") == true && tags.getString("highway") == "speed_camera" -> HazardType.SPEED_CAMERA
                        tags?.has("enforcement") == true -> HazardType.SPEED_CAMERA
                        tags?.has("barrier") == true && tags.getString("barrier") == "toll_booth" -> HazardType.TOLL
                        tags?.has("hazard") == true -> HazardType.HAZARD
                        tags?.has("traffic_calming") == true -> HazardType.TRAFFIC_CALMING
                        else -> HazardType.OTHER
                    }
                    
                    hazards.add(RouteHazard(lat, lon, type, tags?.optString("name") ?: ""))
                }
            }
            
            Log.d("Zwap", "Found ${hazards.size} hazards near route")
        } catch (e: Exception) {
            Log.e("Zwap", "Error parsing OSM hazards: ${e.message}")
        }
        return hazards
    }
    
    private fun isPointNearRoute(lat: Double, lon: Double, routePoints: List<Point>, maxDistanceMeters: Double): Boolean {
        for (point in routePoints) {
            val distance = calculateDistance(lat, lon, point.latitude(), point.longitude())
            if (distance <= maxDistanceMeters) {
                return true
            }
        }
        return false
    }
    
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // Earth's radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }
    
    private fun displayRouteHazards(hazards: List<RouteHazard>) {
        if (!isNavigating) return
        
        hazards.forEach { hazard ->
            try {
                // Use MapplsMap markers for route hazards
                val marker = mapplsMap?.addMarker(
                    MarkerOptions()
                        .position(com.mappls.sdk.maps.geometry.LatLng(hazard.lat, hazard.lon))
                        .title(hazard.name.ifEmpty { hazard.type.name })
                        .icon(com.mappls.sdk.maps.annotations.IconFactory.getInstance(this@MainActivity)
                            .fromBitmap(createHazardMarkerBitmap(hazard.type)))
                )
                
                marker?.let { routeHazardMarkerIds.add(it.id) }
                
                // CRITICAL: Also add to osmFeatures for proximity alerts
                val featureType = when (hazard.type) {
                    HazardType.SPEED_CAMERA -> com.swapmap.zwap.demo.model.FeatureType.SPEED_CAMERA
                    HazardType.TRAFFIC_CALMING -> com.swapmap.zwap.demo.model.FeatureType.TRAFFIC_CALMING
                    HazardType.TOLL -> com.swapmap.zwap.demo.model.FeatureType.TOLL
                    HazardType.COMMUNITY_VERIFIED_HAZARD -> com.swapmap.zwap.demo.model.FeatureType.COMMUNITY_VERIFIED
                    HazardType.COMMUNITY_REVALIDATE -> com.swapmap.zwap.demo.model.FeatureType.COMMUNITY_NEEDS_REVALIDATION
                    else -> com.swapmap.zwap.demo.model.FeatureType.TRAFFIC_CALMING
                }
                
                val osmFeature = com.swapmap.zwap.demo.model.OSMFeature(
                    id = hazard.hashCode().toLong(),
                    lat = hazard.lat,
                    lon = hazard.lon,
                    type = featureType,
                    name = hazard.name,
                    tags = mapOf("route_hazard" to "true")
                )
                
                osmFeatures.add(osmFeature)
                
                Log.d("Zwap", "Added ${hazard.type} marker + alert at (${hazard.lat}, ${hazard.lon})")
            } catch (e: Exception) {
                Log.e("Zwap", "Error adding hazard marker: ${e.message}")
            }
        }
        
        // Immediately check proximity for newly added hazards
        if (hazards.isNotEmpty()) {
            val currentLocation = mapplsMap?.locationComponent?.lastKnownLocation
            if (currentLocation != null) {
                checkHazardProximity(currentLocation.latitude, currentLocation.longitude)
                Log.d("Zwap", "Triggered immediate proximity check for ${hazards.size} new hazards")
            }
        }
    }
    
    private fun createHazardMarkerBitmap(type: HazardType): Bitmap {
        val size = 64
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        val bgColor = when (type) {
            HazardType.SPEED_CAMERA -> Color.parseColor("#2196F3")  // Blue
            HazardType.HAZARD -> Color.parseColor("#FF9800")        // Orange
            HazardType.TOLL -> Color.parseColor("#9C27B0")          // Purple
            HazardType.TRAFFIC_CALMING -> Color.parseColor("#FFC107") // Yellow
            HazardType.COMMUNITY_VERIFIED_HAZARD -> Color.parseColor("#4CAF50") // Green
            HazardType.COMMUNITY_REVALIDATE -> Color.parseColor("#FFC107") // Yellow
            else -> Color.parseColor("#F44336")                      // Red
        }
        
        // Draw background circle
        val bgPaint = Paint().apply {
            color = bgColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4, bgPaint)
        
        // Draw white border
        val borderPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4, borderPaint)
        
        // Draw icon
        val iconPaint = Paint().apply {
            color = Color.WHITE
            textSize = size * 0.45f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val icon = when (type) {
            HazardType.SPEED_CAMERA -> "📷"
            HazardType.HAZARD -> "⚠"
            HazardType.TOLL -> "💰"
            HazardType.TRAFFIC_CALMING -> "⏬"
            HazardType.COMMUNITY_VERIFIED_HAZARD -> "✅"
            HazardType.COMMUNITY_REVALIDATE -> "⏳"
            else -> "!"
        }
        val yPos = (size / 2f) - ((iconPaint.descent() + iconPaint.ascent()) / 2)
        canvas.drawText(icon, size / 2f, yPos, iconPaint)
        
        return bitmap
    }
    
    private fun clearRouteHazards() {
        // Cancel ongoing hazard fetch
        hazardFetchJob?.cancel()
        hazardFetchJob = null
        currentHazardFetchIndex = 0
        currentRoutePoints = emptyList()
        totalRouteDistanceMeters = 0.0
        
        // Hide progress indicator
        hideHazardScanProgress()
        
        mapplsMap?.let { map ->
            map.markers.filter { routeHazardMarkerIds.contains(it.id) }.forEach { 
                map.removeMarker(it) 
            }
        }
        routeHazardMarkerIds.clear()
        isNavigating = false
    }
    
    // Data classes for route hazards
    data class RouteHazard(val lat: Double, val lon: Double, val type: HazardType, val name: String)
    
    enum class HazardType {
        SPEED_CAMERA, HAZARD, TOLL, TRAFFIC_CALMING, OTHER, COMMUNITY_VERIFIED_HAZARD, COMMUNITY_REVALIDATE
    }
    
    private fun createRouteMarkerBitmap(isStart: Boolean): Bitmap {
        val width = 60
        val height = 80
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        val mainColor = if (isStart) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
        
        // Draw pin shadow
        val shadowPaint = Paint().apply {
            color = Color.parseColor("#40000000")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawOval(10f, height - 12f, width - 10f, height.toFloat(), shadowPaint)
        
        // Draw pin body (teardrop shape)
        val pinPaint = Paint().apply {
            color = mainColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        
        // Draw the pin using path for teardrop shape
        val path = android.graphics.Path()
        val centerX = width / 2f
        val radius = width / 2f - 6
        
        // Top circle part
        path.addCircle(centerX, radius + 4, radius, android.graphics.Path.Direction.CW)
        
        // Bottom point
        path.moveTo(centerX - radius, radius + 4)
        path.lineTo(centerX, height - 15f)
        path.lineTo(centerX + radius, radius + 4)
        path.close()
        
        canvas.drawPath(path, pinPaint)
        
        // Draw white border
        val borderPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        canvas.drawPath(path, borderPaint)
        
        // Draw inner white circle
        val innerPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(centerX, radius + 4, radius * 0.4f, innerPaint)
        
        return bitmap
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.setLanguage(Locale.US)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 201 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            mapplsMap?.getStyle { enableLocationComponent(it) }
        }
    }

    private class LocationChangeCallback(activity: MainActivity) : LocationEngineCallback<LocationEngineResult> {
        private val activityWeakReference = WeakReference(activity)
        override fun onSuccess(result: LocationEngineResult?) {
            activityWeakReference.get()?.let { it.handleLocationUpdate(result?.lastLocation ?: return) }
        }
        override fun onFailure(exception: Exception) {}
    }

    override fun onStart() { super.onStart(); mapView?.onStart() }
    override fun onResume() { super.onResume(); mapView?.onResume() }
    override fun onPause() { super.onPause(); mapView?.onPause() }
    override fun onStop() { super.onStop(); mapView?.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView?.onSaveInstanceState(outState) }
    override fun onDestroy() { 
        locationEngine?.removeLocationUpdates(callback)
        tts?.stop(); tts?.shutdown()
        super.onDestroy(); mapView?.onDestroy() 
    }
}
