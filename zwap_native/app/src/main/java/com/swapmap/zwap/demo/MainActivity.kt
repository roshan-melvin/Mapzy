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

class MainActivity : AppCompatActivity(), OnMapReadyCallback, TextToSpeech.OnInitListener {

    private var mapView: MapView? = null
    var mapplsMap: MapplsMap? = null
    private var selectedELoc: String? = null
    
    // Firestore Database
    private val db = FirebaseFirestore.getInstance()
    
    private var locationEngine: LocationEngine? = null
    private val callback = LocationChangeCallback(this)
    
    private var isFollowMode = true
    private var tts: TextToSpeech? = null
    
    private var currentRoute: DirectionsResponse? = null
    private val routeHazards = mutableListOf<Pair<Double, Double>>()
    private var lastAlertTime: Long = 0
    private val ALERT_COOLDOWN = 10000L // 10 seconds
    
    private var symbolManager: SymbolManager? = null
    private var lineManager: LineManager? = null
    
    // OSM Overlay features
    private val osmFeatures = mutableListOf<OSMFeature>()
    private val osmMarkerIds = mutableListOf<Long>()
    private var osmOverlayEnabled = false
    private var lastOSMFetchLocation: Location? = null
    private var lastZoomLevel: Double = 0.0
    private lateinit var auth: FirebaseAuth
    private var currentUserId: String? = null
    private lateinit var communityManager: com.swapmap.zwap.demo.community.CommunityManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        communityManager = com.swapmap.zwap.demo.community.CommunityManager(this)
        
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
        
        // Setup Bottom Navigation
        setupBottomNavigation()
        
        setupSearchOverlay()
    }
    
    fun showReportDialog() {
        val reportFragment = com.swapmap.zwap.demo.community.ReportSubmissionFragment()
        reportFragment.setOnReportSelectedListener { reportType, imageUri ->
            submitReport(reportType, imageUri)
        }
        reportFragment.show(supportFragmentManager, "ReportSubmissionFragment")
    }
    
    fun showChannelReportDialog(channelName: String = "unknown") {
        val channelReportFragment = com.swapmap.zwap.demo.community.ChannelReportFragment()
        channelReportFragment.setChannelContext(channelName)
        channelReportFragment.setOnReportSubmittedListener { category, description, hashtags, imageUri ->
            submitChannelReport(category, description, hashtags, imageUri)
        }
        channelReportFragment.show(supportFragmentManager, "ChannelReportFragment")
    }
    
    fun submitChannelReport(category: String, description: String, hashtags: String, imageUri: android.net.Uri? = null) {
        val location = mapplsMap?.cameraPosition?.target
        if (location == null) {
            Toast.makeText(this, "Could not determine location", Toast.LENGTH_SHORT).show()
            return
        }
        
        Toast.makeText(this, "Channel Report: $category\n$description\n$hashtags", Toast.LENGTH_LONG).show()
        // TODO: Submit to backend with description, hashtags, and image
    }
    
    fun submitReport(reportType: String, imageUri: android.net.Uri? = null) {
        val location = mapplsMap?.cameraPosition?.target
        if (location == null) {
            Toast.makeText(this, "Could not determine location", Toast.LENGTH_SHORT).show()
            return
        }
        
        val point = com.mappls.sdk.geojson.Point.fromLngLat(location.longitude, location.latitude)
        
        // Map simplified string types to enum
        val (category, hazardType) = when (reportType) {
            "Hazard" -> Pair(com.swapmap.zwap.demo.community.models.ReportCategory.HAZARD, com.swapmap.zwap.demo.community.models.HazardType.ACCIDENT)
            "Speed Camera" -> Pair(com.swapmap.zwap.demo.community.models.ReportCategory.SPEED_CAMERA, null)
            "Police" -> Pair(com.swapmap.zwap.demo.community.models.ReportCategory.POLICE, null)
            "Traffic" -> Pair(com.swapmap.zwap.demo.community.models.ReportCategory.TRAFFIC, null)
            "Map Issue" -> Pair(com.swapmap.zwap.demo.community.models.ReportCategory.MAP_ISSUE, null)
            else -> Pair(com.swapmap.zwap.demo.community.models.ReportCategory.HAZARD, com.swapmap.zwap.demo.community.models.HazardType.OTHER)
        }
        
        lifecycleScope.launch {
            try {
                Toast.makeText(this@MainActivity, "Submitting report ${if (imageUri != null) "with photo" else ""}...", Toast.LENGTH_SHORT).show()
                val result = communityManager.submitReport(
                    category = category,
                    hazardType = hazardType,
                    location = point
                    // imageUri can be added to communityManager later
                )
                
                result.onSuccess {
                    Toast.makeText(this@MainActivity, "$reportType reported successfully!", Toast.LENGTH_LONG).show()
                }.onFailure { e ->
                    Log.e("Zwap", "Error submitting report", e)
                    Toast.makeText(this@MainActivity, "Failed to submit report: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("Zwap", "Error in submission", e)
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun setupBottomNavigation() {
        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)
        val mapView = findViewById<View>(R.id.map_view)
        val hudPanel = findViewById<View>(R.id.speed_limit_widget)
        val fabStack = findViewById<View>(R.id.fab_stack_container)
        val searchCard = findViewById<View>(R.id.search_card)
        val fragmentContainer = findViewById<View>(R.id.fragment_container)
        
        bottomNav?.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_explore -> {
                    // Show Map UI
                    mapView?.visibility = View.VISIBLE
                    hudPanel?.visibility = View.VISIBLE
                    fabStack?.visibility = View.VISIBLE
                    searchCard?.visibility = View.VISIBLE
                    fragmentContainer?.visibility = View.GONE
                    true
                }
                R.id.nav_profile -> {
                    // Show Profile Fragment
                    fragmentContainer?.visibility = View.VISIBLE
                    mapView?.visibility = View.GONE
                    hudPanel?.visibility = View.GONE
                    searchCard?.visibility = View.GONE
                    fabStack?.visibility = View.GONE

                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, com.swapmap.zwap.demo.profile.ProfileFragment())
                        .commit()
                    true
                }
                R.id.nav_contribute -> {
                    // Show Community Fragment
                    fragmentContainer?.visibility = View.VISIBLE
                    mapView?.visibility = View.GONE
                    hudPanel?.visibility = View.GONE
                    searchCard?.visibility = View.GONE
                    fabStack?.visibility = View.GONE
                    
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, com.swapmap.zwap.demo.community.CommunityFragment())
                        .commit()
                    true
                }
                R.id.nav_chat -> {
                    // Show Chat Fragment
                    fragmentContainer?.visibility = View.VISIBLE
                    mapView?.visibility = View.GONE
                    hudPanel?.visibility = View.GONE
                    searchCard?.visibility = View.GONE
                    fabStack?.visibility = View.GONE
                    
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, com.swapmap.zwap.demo.chat.ChatFragment())
                        .commit()
                    true
                }
                else -> false
            }
        }
        
        // Set Explore as default
        bottomNav?.selectedItemId = R.id.nav_explore
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
                        osmFeatures.clear()
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
            findViewById<TextView>(R.id.search_trigger).text = location.placeName
            findViewById<View>(R.id.btn_directions).visibility = View.VISIBLE
            
            location.mapplsPin?.let { pin ->
                isFollowMode = false
                mapplsMap?.animateCamera(CameraMapplsPinUpdateFactory.newMapplsPinZoom(pin, 16.0))
            }
            overlay.visibility = View.GONE
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(etInput.windowToken, 0)
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
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
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
        val lastLocation = mapplsMap?.locationComponent?.lastKnownLocation ?: return
        val origin = Point.fromLngLat(lastLocation.longitude, lastLocation.latitude)
        
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
                if (response != null && response.routes().size > 0) {
                    currentRoute = response
                    val route = response.routes()[0]
                    

                    
                    drawRoute(route.geometry())
                    extractHazardsFromRoute(route.legs()?.get(0)?.steps())
                    
                    findViewById<View>(R.id.route_info_layout).visibility = View.VISIBLE
                    findViewById<TextView>(R.id.tv_distance).text = "%.1f km".format(route.distance()!! / 1000.0)
                    findViewById<TextView>(R.id.tv_duration).text = "${(route.duration()!! / 60.0).toInt()} min"
                    
                    enableFollowMode()
                }
            }
            override fun onError(p0: Int, p1: String?) {
                Toast.makeText(this@MainActivity, "Directions Error: \$p1", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun extractHazardsFromRoute(steps: List<LegStep>?) {
        routeHazards.clear()
        symbolManager?.clearAll()
        steps?.forEach { step ->
            step.maneuver()?.location()?.let { location ->
                routeHazards.add(Pair(location.latitude(), location.longitude()))
                
                // Add visible marker for route guidance points
                val symbolOptions = SymbolOptions()
                    .geometry(Point.fromLngLat(location.longitude(), location.latitude()))
                    .textField(step.maneuver()?.instruction() ?: "")
                    .textSize(10f)
                symbolManager?.create(symbolOptions)
            }
        }
        Log.d("Zwap", "Extracted \${routeHazards.size} potential hazard points from route")
    }

    private fun drawRoute(geometry: String?) {
        if (geometry == null) return
        
        lineManager?.clearAll()
        
        val points = PolylineUtils.decode(geometry, Constants.PRECISION_6)
        val latLngs = points.map { com.mappls.sdk.maps.geometry.LatLng(it.latitude(), it.longitude()) }
        val lineOptions = LineOptions()
            .points(latLngs)
            .lineColor("#3498db")
            .lineWidth(7f)
        lineManager?.create(lineOptions)
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
