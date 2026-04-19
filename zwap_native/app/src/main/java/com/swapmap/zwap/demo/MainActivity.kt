package com.swapmap.zwap.demo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mappls.sdk.geojson.Point
import com.mappls.sdk.geojson.utils.PolylineUtils
import com.mappls.sdk.maps.MapView
import com.mappls.sdk.maps.MapplsMap
import com.mappls.sdk.maps.OnMapReadyCallback
import com.mappls.sdk.maps.Style
import com.mappls.sdk.maps.annotations.MarkerOptions
import com.mappls.sdk.maps.annotations.Marker
import com.mappls.sdk.maps.annotations.PolylineOptions
import com.mappls.sdk.maps.annotations.IconFactory
import com.mappls.sdk.maps.camera.CameraMapplsPinUpdateFactory
import com.mappls.sdk.maps.camera.CameraUpdateFactory
import com.mappls.sdk.maps.location.LocationComponentActivationOptions
import com.mappls.sdk.maps.location.LocationComponentOptions
import androidx.core.content.ContextCompat
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
import android.widget.RelativeLayout
import android.speech.RecognizerIntent
import android.widget.EditText
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.auth.FirebaseAuth
import java.util.HashMap
import com.mappls.sdk.services.api.directions.DirectionsCriteria
import com.mappls.sdk.services.api.directions.MapplsDirectionManager
import com.mappls.sdk.services.api.directions.MapplsDirections
import com.mappls.sdk.services.api.directions.models.DirectionsResponse
import com.mappls.sdk.services.api.directions.models.DirectionsRoute
import com.mappls.sdk.services.api.directions.models.LegStep
import com.mappls.sdk.plugin.annotation.Line
import com.mappls.sdk.plugin.annotation.LineManager
import com.mappls.sdk.plugin.annotation.LineOptions
import com.mappls.sdk.plugin.annotation.OnLineClickListener
import com.mappls.sdk.plugin.annotation.OnSymbolClickListener
import com.mappls.sdk.plugin.annotation.Symbol
import com.mappls.sdk.plugin.annotation.SymbolManager
import com.mappls.sdk.plugin.annotation.SymbolOptions
import com.mappls.sdk.services.utils.Constants
import com.swapmap.zwap.R
import com.swapmap.zwap.demo.model.FeatureType
import com.swapmap.zwap.demo.model.OSMFeature
import com.swapmap.zwap.demo.network.OSMOverpassService
import android.animation.ObjectAnimator
import android.view.animation.LinearInterpolator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Observer
import com.swapmap.zwap.demo.viewmodel.HazardViewModel
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.widget.Button
import java.lang.ref.WeakReference
import java.util.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.swapmap.zwap.demo.profile.ProfileFragment
import com.swapmap.zwap.demo.community.CommunityFragment
import com.swapmap.zwap.demo.chat.ChatFragment
import com.swapmap.zwap.demo.repository.ReportRepository
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import java.util.concurrent.Executors
import com.swapmap.zwap.demo.db.AppDatabase
import com.swapmap.zwap.demo.navigation.DriverTasksManager
import com.swapmap.zwap.demo.navigation.TaskCategory
import com.swapmap.zwap.demo.navigation.TaskIndicatorView
import com.google.firebase.firestore.ListenerRegistration

class MainActivity : AppCompatActivity(), OnMapReadyCallback, TextToSpeech.OnInitListener, SensorEventListener {

    private var mapView: MapView? = null
    internal var mapplsMap: MapplsMap? = null  // internal for fragment access
    private var selectedELoc: String? = null
    private var selectedPlace: ELocation? = null  // Acts as destinationPlace
    private var originPlace: ELocation? = null    // null means "Your location"
    private var isOriginCurrentLocation = true
    enum class SearchSource { EXPLORE, ORIGIN, DESTINATION }
    private var originArrowMarker: Marker? = null  // Arrow marker for origin point
    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null
    private var currentDeviceAzimuth: Float = 0f
    private var currentSearchSource = SearchSource.EXPLORE
    
    // Firestore Database
    private val db = FirebaseFirestore.getInstance()
    
    private var locationEngine: LocationEngine? = null
    private val callback = LocationChangeCallback(this)
    
    private var isFollowMode = true
    // FAB Layout Manager for per-page FAB state management
    private lateinit var fabLayoutManager: FabLayoutManager
    private var tts: TextToSpeech? = null
    
    private var currentRoute: DirectionsResponse? = null
    private val routeHazards = mutableListOf<Pair<Double, Double>>()
    private var lastAlertTime: Long = 0
    private val ALERT_COOLDOWN = 3000L  // 3 seconds
    private val HAZARD_ALERT_DISTANCE = 300.0  // 300 meters
    private val alertedHazardIds = mutableSetOf<Long>()
    private val hazardPillSpokenIds = mutableSetOf<Long>()  // tracks 500m early-warn TTS for pill
    private val taskPillSpokenIds = mutableSetOf<Long>()       // tracks TTS for task place alerts
    private var driverTasksManager: DriverTasksManager? = null
    private var taskIndicatorView: TaskIndicatorView? = null
    private var cachedTasks: List<com.swapmap.zwap.demo.db.DriverTask> = emptyList()
    private var musicTrayVisible = false

    // ── ViewModel: owns speed limit state, fetch logic, over-speed TTS ────────
    private lateinit var hazardViewModel: HazardViewModel
    private var hazardAlertAdapter: HazardAlertAdapter? = null
    private var isHazardPanelExpanded = true
    private val activeHazards = mutableListOf<HazardAlert>()
    private val HAZARD_CROSSED_DISTANCE = 10.0  // Consider crossed when within 10m
    
    private var symbolManager: SymbolManager? = null
    private var lineManager: LineManager? = null
    
    // OSM Overlay features
    private val osmFeatures = java.util.concurrent.CopyOnWriteArrayList<OSMFeature>()
    private val toggledOsmFeatureIds = mutableSetOf<Long>()
    private val testMarkerIds = mutableListOf<Long>()
    private val osmMarkerIds = mutableListOf<Long>()
    private var osmOverlayEnabled = false
    private var lastOSMFetchLocation: Location? = null
    private var lastZoomLevel: Double = 0.0
    private var lastFetchedMapCenter: com.mappls.sdk.maps.geometry.LatLng? = null

    // Bounding-box cache: tracks the geographic area already loaded so we don't
    // wipe and refetch when the user is still looking at the same area.
    private data class BBox(val south: Double, val west: Double, val north: Double, val east: Double) {
        fun contains(lat: Double, lon: Double) = lat in south..north && lon in west..east
        // Shrink the "still valid" zone to 70% of the bbox so we prefetch a
        // little before the user actually reaches the edge.
        fun innerContains(lat: Double, lon: Double): Boolean {
            val latPad = (north - south) * 0.15
            val lonPad = (east - west) * 0.15
            return lat in (south + latPad)..(north - latPad) &&
                   lon in (west + lonPad)..(east - lonPad)
        }
    }
    private var lastFetchedBBox: BBox? = null
    // Guard: prevents a second parallel fetch from starting while one is already in flight.
    private var isFetchInProgress = false
    private var osmLoadingAnimator: ObjectAnimator? = null
    private val routeHazardMarkers = mutableListOf<com.mappls.sdk.maps.annotations.Marker>()
    private var isNavigating = false
    private var isPreviewMode = false
    private var isMapTilted = false  // Tracks 2D (false) vs 3D (true) tilt state
    private var currentRoutePoints: List<Point> = emptyList()  // Store route points for progressive fetching
    private var currentHazardFetchIndex = 0  // Track which chunk we're fetching
    private var hazardFetchJob: kotlinx.coroutines.Job? = null  // Job to cancel if route changes
    private var totalRouteDistanceMeters = 0.0  // Total route distance for progress calculation
    private var isScanningHazards = false // Flag to prevent battery updates from overwriting scanning progress
    
    // Interactive Route selection maps
    private val markerToRouteMap = mutableMapOf<Long, DirectionsRoute>()
    private val routeMetaMarkerIds = mutableListOf<Long>()
    private val taskRouteMarkerIds = mutableListOf<Long>()
    // Store all route geometries for tap-detection on gray lines
    private val allRouteGeometries = mutableMapOf<DirectionsRoute, List<com.mappls.sdk.maps.geometry.LatLng>>()
    // The currently displayed primary (blue) route
    private var currentPrimaryRoute: DirectionsRoute? = null
    // Tracks which route step the user is currently on — updated every GPS tick during navigation
    private var currentStepIndex = 0
    
    // Firebase live sync state
    private val liveConfidenceMap = mutableMapOf<Long, Double>()
    private val liveStatusMap = mutableMapOf<Long, String>()
    private val featureToMarkerMap = mutableMapOf<Long, com.mappls.sdk.maps.annotations.Marker>()
    
    private lateinit var auth: FirebaseAuth
    private var currentUserId: String? = null
    private var userNotifListener: ListenerRegistration? = null
    private val seenNotifIds = mutableSetOf<String>()

    // CameraX for hands-free auto-capture
    private var imageCapture: ImageCapture? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var isCameraXStarted = false

    // Hoisted here so launchVoiceReporter() (called from wake-word OR FAB) can use it
    private lateinit var speechLauncherMain: androidx.activity.result.ActivityResultLauncher<android.content.Intent>
    private lateinit var takeAIPicture: androidx.activity.result.ActivityResultLauncher<android.net.Uri>

    // State holders for the voice + camera flow
    private var aiVoiceTranscript = ""
    private var aiImageFileMain: java.io.File? = null
    private var aiCameraUriMain: android.net.Uri? = null
    
    // Custom SpeechRecognizer
    private var customSpeechRecognizer: android.speech.SpeechRecognizer? = null
    private var isVoiceAnimating = false

    // ── Battery Receiver ──────────────────────────────────────────────────────
    private val batteryReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level != -1 && scale != -1) {
                val batteryPct = (level * 100 / scale.toFloat()).toInt()
                if (!isScanningHazards) {
                    updateBatteryDisplay(batteryPct)
                }
            }
        }
    }

    private fun updateBatteryDisplay(percent: Int) {
        findViewById<android.widget.ProgressBar>(R.id.progress_battery)?.progress = percent
        findViewById<android.widget.TextView>(R.id.tv_active_battery_pct)?.text = "$percent%"
    }

    // ── Wake-Word Receiver ────────────────────────────────────────────────────
    // Receives a local broadcast from WakeWordService when "Hey Mapzy" is heard,
    // then triggers the same voice pipeline as tapping the mic FAB.
    private val wakeWordReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == WakeWordService.ACTION_WAKE_WORD_DETECTED) {
                android.util.Log.i("WakeWord", "🔔 'Hey Mapzy' detected — launching voice reporter")
                launchVoiceReporter()
            }
        }
    }

    /** Bind CameraX with the chosen lens (back or front) for silent auto-capture. */
    private fun startCameraX(lens: String) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            val selector = if (lens == "front")
                CameraSelector.DEFAULT_FRONT_CAMERA
            else
                CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, selector, imageCapture!!)
                isCameraXStarted = true
                Log.d("CameraX", "✅ CameraX bound with $lens lens")
            } catch (e: Exception) {
                Log.e("CameraX", "Failed to bind CameraX", e)
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(this))
    }

    /**
     * Shared entry point for the AI voice reporter.
     * Called by: (1) Mic FAB tap, (2) Wake-word 'Hey Mapzy' broadcast.
     * Checks permissions first, then launches Android speech recognition.
     */
    internal fun launchVoiceReporter() {
        val neededPerms = mutableListOf<String>()
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            neededPerms.add(android.Manifest.permission.CAMERA)
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            neededPerms.add(android.Manifest.permission.RECORD_AUDIO)
        }
        if (neededPerms.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, neededPerms.toTypedArray(), 999)
            Toast.makeText(this, "Please grant camera & mic permissions.", Toast.LENGTH_LONG).show()
            return
        }

        // ── CRITICAL FIX: Stop WakeWordService FIRST to free the microphone ────
        // Porcupine holds an AudioRecord open. If both run simultaneously, Android
        // silences the SpeechRecognizer stream → NO_SPEECH_DETECTED (-1) every time.
        stopService(android.content.Intent(this, WakeWordService::class.java))
        android.util.Log.d("Zwap", "WakeWordService stopped — mic is now free for SpeechRecognizer")

        // Destroy and recreate recognizer every call for a fresh, clean session
        customSpeechRecognizer?.destroy()
        customSpeechRecognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(this)

        customSpeechRecognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                startVoiceAnimation()
                Toast.makeText(this@MainActivity, "🎙 Listening...", Toast.LENGTH_SHORT).show()
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { stopVoiceAnimation() }

            override fun onError(error: Int) {
                stopVoiceAnimation()
                restartWakeWordServiceIfEnabled()
                // Only surface meaningful user-facing errors; silently swallow the rest
                val msg = when (error) {
                    android.speech.SpeechRecognizer.ERROR_NO_MATCH,
                    android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected — try again"
                    android.speech.SpeechRecognizer.ERROR_AUDIO          -> "Microphone error — try again"
                    android.speech.SpeechRecognizer.ERROR_NETWORK,
                    android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error — check connection"
                    android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice recognizer busy — try again"
                    android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission needed"
                    else -> null  // Silently ignore ERROR_CLIENT (-5) and other internal errors
                }
                msg?.let { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show() }
            }

            override fun onResults(results: Bundle?) {
                stopVoiceAnimation()
                restartWakeWordServiceIfEnabled()
                val matches = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    processVoiceTranscript(matches[0])
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val recognizerIntent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        try {
            customSpeechRecognizer?.startListening(recognizerIntent)
        } catch (e: Exception) {
            android.util.Log.e("Zwap", "SpeechRecognizer failed to start: ${e.message}", e)
            stopVoiceAnimation()
            restartWakeWordServiceIfEnabled()
        }
    }

    /** Restarts WakeWordService after the SpeechRecognizer has released the mic,
     *  but only if the user has the "Hey Mapzy" feature enabled in preferences. */
    private fun restartWakeWordServiceIfEnabled() {
        val prefs = getSharedPreferences("zwap_prefs", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean("wake_word_enabled", false)) {
            val audioPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.RECORD_AUDIO
            )
            if (audioPerm == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                startForegroundService(android.content.Intent(this, WakeWordService::class.java))
                android.util.Log.d("Zwap", "WakeWordService restarted — mic returned to Porcupine")
            }
        }
    }

    private fun processVoiceTranscript(transcript: String) {
        aiVoiceTranscript = transcript
        // ── Read camera preference ─────────────────────────────
        val prefs = getSharedPreferences("zwap_prefs", android.content.Context.MODE_PRIVATE)
        val cameraMode = prefs.getString("voice_camera_mode", "manual")
        val cameraLens = prefs.getString("voice_camera_lens", "back") ?: "back"

        if (cameraMode == "auto") {
            // ── AUTO: silently capture with CameraX ────────────
            Toast.makeText(this, "⏳ Scanning road for hazards...", Toast.LENGTH_SHORT).show()
            val captureLat = mapplsMap?.cameraPosition?.target?.latitude  ?: 0.0
            val captureLng = mapplsMap?.cameraPosition?.target?.longitude ?: 0.0
            if (!isCameraXStarted) startCameraX(cameraLens)
            val captureHandler = android.os.Handler(mainLooper)
            captureHandler.postDelayed({
                val ic = imageCapture
                if (ic == null) {
                    Toast.makeText(this, "⚠️ Camera not ready, retrying...", Toast.LENGTH_SHORT).show()
                    return@postDelayed
                }
                aiImageFileMain = java.io.File(cacheDir, "ai_auto_${System.currentTimeMillis()}.jpg")
                val outputOptions = ImageCapture.OutputFileOptions.Builder(aiImageFileMain!!).build()
                ic.takePicture(
                    outputOptions,
                    cameraExecutor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            sendToAiAndSubmit(aiImageFileMain!!, aiVoiceTranscript, captureLat, captureLng)
                        }
                        override fun onError(exc: ImageCaptureException) {
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, "📷 Auto-capture failed: ${exc.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }, 800)
        } else {
            // ── MANUAL: open system camera ─────────────────────
            Toast.makeText(this, "🎙 \"$aiVoiceTranscript\" — Taking photo...", Toast.LENGTH_SHORT).show()
            try {
                aiImageFileMain = java.io.File(cacheDir, "ai_main_${System.currentTimeMillis()}.jpg")
                aiCameraUriMain = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    "$packageName.fileprovider",
                    aiImageFileMain!!
                )
                takeAIPicture.launch(aiCameraUriMain!!)
            } catch (e: Exception) {
                Toast.makeText(this, "Camera failed to start", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Ensure keys are set before inflation
        Mappls.getInstance(this)

        setContentView(R.layout.activity_main)

        // Create notification channels immediately (must run before any notify() call)
        createAppNotificationChannels()

        // Request ALL runtime permissions the app needs in one upfront dialog
        requestAllAppPermissions()

        // Initialize FAB layout manager for handling FAB positions per page state
        fabLayoutManager = FabLayoutManager(this)
        fabLayoutManager.setPageState(PageState.HOME_EXPLORE, immediate = true)
        // Initialize sensor for arrow marker rotation
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        // ── Initialize HazardViewModel ────────────────────────────────────────
        hazardViewModel = ViewModelProvider(this)[HazardViewModel::class.java]

        // ── Initialize DriverTasksManager ─────────────────────────────────────
        driverTasksManager = DriverTasksManager(
            context = this,
            db = AppDatabase.getDatabase(this),
            scope = lifecycleScope,
            lifecycleOwner = this
        )

        // Observe speed limit changes and update tv_limit automatically.
        // This runs on the Main thread, is lifecycle-safe, and replaces the
        // old runOnUiThread { currentSpeedLimitKmh = ... } pattern.
        hazardViewModel.speedLimitKmh.observe(this, Observer { limitKmh ->
            val tvLimit = findViewById<TextView>(R.id.tv_limit)
            tvLimit?.text = if (limitKmh > 0) "$limitKmh" else "--"
        })

        // Register launchers strictly here while in CREATED state
        initLaunchers()

        mapView = findViewById(R.id.map_view)
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync(this)

        tts = TextToSpeech(this, this)

        // Check if user is already logged in
        if (auth.currentUser != null) {
            currentUserId = auth.currentUser!!.uid
            Log.d("Zwap", "User already logged in: $currentUserId")
            setupUI()
            startUserNotificationListener(currentUserId!!)
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
                    startUserNotificationListener(currentUserId!!)
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
                            startUserNotificationListener(currentUserId!!)
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
    
    private fun listenToFirebaseMapHazards() {
        Log.d("Zwap", "Starting Firebase map_hazards live listener")
        db.collection("map_hazards").addSnapshotListener { snapshots, e ->
            if (e != null) {
                Log.w("Zwap", "Listen to map_hazards failed.", e)
                return@addSnapshotListener
            }

            if (snapshots != null) {
                Log.d("Zwap", "Firebase snapshot updated! Documents found: ${snapshots.documents.size}")
                for (doc in snapshots.documents) {
                    val hazardIdStr = doc.id
                    val status = doc.getString("status") ?: "CONFIRMED"
                    val confidence = doc.getDouble("confidence") ?: 100.0
                    val type = doc.getString("incident_type") ?: "HAZARD"
                    
                    val hazardId = hazardIdStr.hashCode().toLong()
                    Log.d("Zwap", "Checking Firebase Doc: $hazardIdStr (Hash ID: $hazardId) -> Conf: $confidence")
                    
                    // SAVE LIVE DATA FOR BACKEND LOOKUP
                    liveConfidenceMap[hazardId] = confidence
                    liveStatusMap[hazardId] = status
                    
                    val feat = osmFeatures.find { it.id == hazardId }
                    if (feat != null) {
                        Log.d("Zwap", "Updating hazard $hazardIdStr from Firebase live sync. New conf: $confidence")
                        
                        // Extract reports count from existing name if present
                        val currentName = feat.name ?: ""
                        val reportsMatch = Regex("(\\d+)\\s+reports").find(currentName)
                        val reportsStr = if (reportsMatch != null) "${reportsMatch.groupValues[1]} reports" else "1 reports"
                        
                        val updatedName = "${type.replace("_", " ")} ($confidence% conf, $reportsStr)"
                        feat.name = updatedName
                        
                        val featType = if (status == "NEEDS_REVALIDATION") 
                                        com.swapmap.zwap.demo.model.FeatureType.COMMUNITY_NEEDS_REVALIDATION 
                                    else 
                                        com.swapmap.zwap.demo.model.FeatureType.COMMUNITY_VERIFIED
                        feat.type = featType
                        
                        runOnUiThread {
                            // Update active visual alert panel if present
                            val activeHazard = activeHazards.find { it.id == hazardId }
                            if (activeHazard != null) {
                                activeHazard.name = updatedName
                                activeHazard.type = featType
                                hazardAlertAdapter?.notifyDataSetChanged()
                            }
                            
                            // CRITICAL: Only re-render the map marker if the hazard overlay
                            // is currently enabled. Without this guard, a Firebase snapshot
                            // arriving after the user toggles OFF would silently re-add
                            // the marker even though the toggle is off.
                            if (osmOverlayEnabled) {
                                val oldMarker = featureToMarkerMap[hazardId]
                                if (oldMarker != null) {
                                    oldMarker.remove()
                                    osmMarkerIds.remove(oldMarker.id)
                                    featureToMarkerMap.remove(hazardId)
                                    addOSMMarker(feat)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupUI() {
        listenToFirebaseMapHazards()
        // Setup hazard alert panel
        setupHazardAlertPanel()
        
        // Ensure Explore FABs are visible at start
        findViewById<View>(R.id.btn_toggle_osm)?.visibility = View.VISIBLE
        findViewById<View>(R.id.fab_compass)?.visibility = View.VISIBLE
        findViewById<View>(R.id.fab_recenter)?.visibility = View.VISIBLE
        findViewById<View>(R.id.speed_limit_widget)?.visibility = View.VISIBLE
        findViewById<View>(R.id.fab_stack_container)?.visibility = View.VISIBLE
        findViewById<View>(R.id.search_trigger).setOnClickListener { showSearchOverlay(null) }
        findViewById<View>(R.id.btn_voice_search).setOnClickListener { performVoiceSearch() }
        findViewById<View>(R.id.btn_directions).setOnClickListener { 
            // If already previewing a route, just start it
            if (currentRoute != null && currentPrimaryRoute != null) {
                startNavigation()
            } else {
                originPlace = null // Reset to default current location for new flow
                isOriginCurrentLocation = true
                getDirections() 
            }
        }
        findViewById<View>(R.id.fab_recenter).setOnClickListener { enableFollowMode() }
        findViewById<View>(R.id.btn_show_nearby).setOnClickListener { showNearbySearchDialog() }
        
        // Compass button - toggle 2D / 3D tilt
        findViewById<View>(R.id.fab_compass)?.setOnClickListener {
            isMapTilted = !isMapTilted
            val targetTilt = if (isMapTilted) 60.0 else 0.0
            val label = if (isMapTilted) "3D" else "2D"
            Log.d("Zwap", "Compass tilt toggle -> $label (tilt=$targetTilt)")

            val currentPosition = mapplsMap?.cameraPosition
            mapplsMap?.animateCamera(
                com.mappls.sdk.maps.camera.CameraUpdateFactory.newCameraPosition(
                    com.mappls.sdk.maps.camera.CameraPosition.Builder()
                        .target(currentPosition?.target)          // keep same map centre — no drift
                        .zoom(currentPosition?.zoom ?: 14.0)      // keep same zoom level
                        .bearing(currentPosition?.bearing ?: 0.0) // keep current bearing
                        .tilt(targetTilt)
                        .build()
                ),
                350  // Smooth animation duration in ms
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
        // Mic FAB click → shared method used by both FAB and wake-word
        try {
            val aiVoiceBtn = findViewById<View>(R.id.fab_ai_voice)
            aiVoiceBtn?.setOnClickListener {
                launchVoiceReporter()
            }
        } catch (e: Exception) {
            Log.e("Zwap", "Error setting up AI voice button", e)
        }
        
        // Native Gesture Handling: Modern OnBackPressedDispatcher for Edge-Swipe Support
        // Modern OnBackPressedDispatcher: Consolidates search, navigation, tab switching, and exit confirmation
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 1. Close search overlay if open
                val overlay = findViewById<View>(R.id.search_overlay)
                if (overlay?.visibility == View.VISIBLE) {
                    overlay.visibility = View.GONE
                    return
                }

                // 2. Navigation Flow: If navigating OR in preview mode, close and return to place details
                val panel = findViewById<View>(R.id.directions_bottom_panel)
                if (isNavigating || panel?.visibility == View.VISIBLE) {
                    closeDirectionsUI()
                    return
                }

                // 3. Pop fragment back stack if any (e.g. CreateReport -> Community)
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                    return
                }

                // 4. Tab Flow: If not on Explore tab, return to Explore
                val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)
                if (bottomNav != null && bottomNav.selectedItemId != R.id.nav_explore) {
                    bottomNav.selectedItemId = R.id.nav_explore
                    return
                }

                // 5. Exit Confirmation: If on Explore tab, show one-press exit check
                androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("Exit Mapzy")
                    .setMessage("Are you sure you want to close the app?")
                    .setPositiveButton("Exit") { _, _ ->
                        finishAffinity()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        })

        setupBottomNavigation()
        setupSearchOverlay()
        setupTaskIndicator()
        setupMusicTray()
        setupNavBottomBarCurvedBg()
    }

    private fun startVoiceAnimation() {
        val aiVoiceBtn = findViewById<View>(R.id.fab_ai_voice) ?: return
        if (isVoiceAnimating) return
        isVoiceAnimating = true
        pulseAnim(aiVoiceBtn)
    }

    private fun pulseAnim(btn: View) {
        if (!isVoiceAnimating) return
        btn.animate().scaleX(1.15f).scaleY(1.15f).setDuration(600).withEndAction {
            if (!isVoiceAnimating) return@withEndAction
            btn.animate().scaleX(1.0f).scaleY(1.0f).setDuration(600).withEndAction {
                if (isVoiceAnimating) pulseAnim(btn)
            }.start()
        }.start()
    }

    private fun stopVoiceAnimation() {
        isVoiceAnimating = false
        val aiVoiceBtn = findViewById<View>(R.id.fab_ai_voice)
        aiVoiceBtn?.animate()?.cancel()
        aiVoiceBtn?.animate()?.scaleX(1.0f)?.scaleY(1.0f)?.setDuration(200)?.start()
    }
    
    /**
     * Shared AI pipeline: upload image + transcript to the assistant backend,
     * then silently submit the detected hazard to the main backend.
     * Called by both Manual (TakePicture) and Auto (CameraX) capture paths.
     */
    /**
     * One-Shot Hybrid AI Voice Report.
     * The Assistant Backend handles everything: AI, Cloudinary, Supabase insert,
     * and for Path C: Firebase rejection notification.
     * This function just fires the POST and shows a smart toast.
     */
    private fun sendToAiAndSubmit(imageFile: java.io.File, transcript: String, lat: Double, lng: Double) {
        val userId = auth.currentUser?.uid ?: "anonymous"
        lifecycleScope.launch {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val requestBody = okhttp3.MultipartBody.Builder()
                    .setType(okhttp3.MultipartBody.FORM)
                    .addFormDataPart("transcript", transcript)
                    .addFormDataPart("user_id", userId)
                    .addFormDataPart("latitude", lat.toString())
                    .addFormDataPart("longitude", lng.toString())
                    .addFormDataPart(
                        "image", imageFile.name,
                        imageFile.readBytes().toRequestBody("image/jpeg".toMediaTypeOrNull())
                    )
                    .build()

                val assistantUrl = com.swapmap.zwap.demo.config.AppConfig.get(
                    "ASSISTANT_BACKEND_URL", "http://192.168.0.102:8001"
                )
                val request = okhttp3.Request.Builder()
                    .url("$assistantUrl/api/v1/reports/ai-draft")
                    .post(requestBody)
                    .build()

                val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
                val body = response.body?.string()

                if (response.isSuccessful && body != null) {
                    val json = org.json.JSONObject(body)
                    val success    = json.optBoolean("success", false)
                    val hazardType = json.optString("hazard_type", "")
                    val status     = json.optString("status", "")
                    val path       = json.optString("path", "")

                    withContext(Dispatchers.Main) {
                        when {
                            success && path == "A" ->
                                Toast.makeText(this@MainActivity,
                                    "✅ $hazardType detected visually — report submitted!",
                                    Toast.LENGTH_LONG).show()

                            success && path == "B" ->
                                Toast.makeText(this@MainActivity,
                                    "🎙️ $hazardType captured from voice — report submitted!",
                                    Toast.LENGTH_LONG).show()

                            success && path == "C" ->
                                Toast.makeText(this@MainActivity,
                                    "🎙️ No hazard found — logged for your history.",
                                    Toast.LENGTH_LONG).show()

                            else -> {
                                val error = json.optString("error", "Unknown error")
                                Toast.makeText(this@MainActivity, "🚫 $error", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity,
                            "AI Server error: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
                Log.e("AI_Main", "Exception in sendToAiAndSubmit", e)
            }
        }
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        val fragmentContainer = findViewById<View>(R.id.fragment_container)
        
        bottomNav.setOnItemSelectedListener { item ->
            hideMusicTray()
            when (item.itemId) {
                R.id.nav_explore -> {
                    // Show map, hide fragment container
                    fragmentContainer.visibility = View.GONE
                    mapView?.visibility = View.VISIBLE
                    findViewById<View>(R.id.search_card)?.visibility = View.VISIBLE
                    // FabLayoutManager controls FAB visibility — no manual sets to avoid all-FABs flash
                    fabLayoutManager.setPageState(PageState.HOME_EXPLORE)
                    fabLayoutManager.forceUpdateLayout()
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
        findViewById<View>(R.id.speed_limit_widget)?.visibility = View.GONE
        findViewById<View>(R.id.fab_ai_voice)?.visibility = View.GONE
        findViewById<View>(R.id.fab_stack_container)?.visibility = View.GONE
        
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
            setBackgroundColor(android.graphics.Color.parseColor("#1E1E1E"))
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
            setTextColor(android.graphics.Color.WHITE)
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
                setTextColor(android.graphics.Color.WHITE)
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
                setBackgroundColor(android.graphics.Color.parseColor("#333333"))
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
        val button = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.btn_toggle_osm)
        
        if (osmOverlayEnabled) {
            // Active state - orange/amber background with white icon and scale animation
            button.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF6B00"))
            button.imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            button.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100).withEndAction {
                button.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
            }.start()
            Log.d("Zwap", "Hazards overlay enabled, fetching features...")
            
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
            // Inactive state - dark theme background with grey icon
            button.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#40444B"))
            button.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#B9BBBE"))
            Log.d("Zwap", "Hazards overlay disabled, clearing markers (maintaining cache)")
            stopOSMLoadingAnimation()  // Stop spinner immediately if fetch is still in-flight
            // Reset hazard panel state so re-toggling ON gives a fresh result
            alertedHazardIds.clear()
            activeHazards.clear()
            hazardAlertAdapter?.notifyDataSetChanged()
            clearOSMMarkers(fullWipe = false)
        }
    }
    
    private fun fetchAndDisplayOSMFeatures() {
        val center = mapplsMap?.cameraPosition?.target
        if (center == null) {
            Log.e("Mapzy", "fetchAndDisplayOSMFeatures: map center is null, aborting")
            return
        }

        val zoom = mapplsMap?.cameraPosition?.zoom ?: 0.0
        val lat = center.latitude
        val lon = center.longitude

        if (zoom < 1.0) {
            clearOSMMarkers()
            Log.d("Mapzy", "Zoom $zoom too low (need 1+), skipping fetch")
            return
        }

        // ── BBOX CACHE: skip fetch if the viewport is still inside the loaded area ──
        // This is the primary fix for the glitch: if the user hasn't moved out of the
        // bounding box we already fetched for, the existing markers are still valid.
        val cachedBBox = lastFetchedBBox
        if (cachedBBox != null && cachedBBox.innerContains(lat, lon)) {
            // CASE 1: Markers are already on the map
            if (osmMarkerIds.isNotEmpty()) {
                Log.d("Mapzy", "Camera still inside cached bbox — markers live, skipping refetch")
                return
            }
            
            // CASE 2: Markers were cleared (toggle off) but we still have the features in memory
            if (osmFeatures.isNotEmpty()) {
                Log.d("Mapzy", "Restoring ${osmFeatures.size} markers from memory cache (INSTANT)")
                osmFeatures.forEach { feature ->
                    addOSMMarker(feature)
                }
                // Always run proximity check after restoring cached features
                val loc = mapplsMap?.locationComponent?.lastKnownLocation
                checkHazardProximity(loc?.latitude ?: lat, loc?.longitude ?: lon)
                return
            }
        }

        // ── IN-PROGRESS GUARD: prevent duplicate parallel fetches ────────────────
        if (isFetchInProgress) {
            Log.d("Mapzy", "Fetch already in progress, ignoring duplicate request")
            return
        }
        isFetchInProgress = true

        val delta = when {
            zoom < 4.0 -> 0.50
            zoom < 6.0 -> 0.10
            else -> 0.02
        }
        val south = lat - delta; val west = lon - delta
        val north = lat + delta; val east = lon + delta
        Log.d("Mapzy", "Fetch radius: ${delta * 111}km, bbox=($south,$west,$north,$east)")

        // NOTE: We do NOT call clearOSMMarkers() here.
        // Old markers stay visible on the map while the network request is in flight.
        // The atomic swap happens inside withContext(Main) only after both responses
        // arrive — so the map is NEVER blank.

        startOSMLoadingAnimation()
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val fetchStartMs = System.currentTimeMillis()
            try {
                // ── Launch both network calls IN PARALLEL ─────────────────────
                val osmDeferred = async {
                    try {
                        val query = OSMOverpassService.buildQuery(lat, lon)
                        Log.d("Mapzy", "📤 OSM query dispatched (parallel)")
                        OSMOverpassService.instance.queryFeatures(query).execute()
                    } catch (e: Exception) {
                        Log.e("Mapzy", "OSM network error", e)
                        null
                    }
                }

                val communityDeferred = async {
                    try {
                        Log.d("Mapzy", "🚀 Community hazards dispatched (parallel)")
                        // Cap at 8s so a down/unreachable backend never delays OSM results
                        kotlinx.coroutines.withTimeoutOrNull(6_000) {
                            com.swapmap.zwap.demo.network.ApiClient.hazardApiService.getHazards(
                                lat, lon, delta * 111.0
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("Mapzy", "Community network error", e)
                        null
                    }
                }

                // ── Await both; they ran concurrently ─────────────────────────
                val osmResponse       = osmDeferred.await()
                val communityResponse = communityDeferred.await()

                // ── Process OSM results on the IO thread ──────────────────────
                val osmFeatureBatch = mutableListOf<OSMFeature>()
                var cameraCount = 0; var hazardCount = 0

                if (osmResponse != null && osmResponse.isSuccessful && osmResponse.body() != null) {
                    val elements = osmResponse.body()!!.elements
                    Log.d("Mapzy", "📡 OSM: ${elements.size} elements returned at zoom=$zoom")
                    if (elements.isEmpty()) Log.w("Mapzy", "⚠️ No OSM elements for this region/zoom")

                    elements.forEach { element ->
                        val eLat = element.lat ?: element.center?.lat ?: return@forEach
                        val eLon = element.lon ?: element.center?.lon ?: return@forEach
                        if (eLat == 0.0 && eLon == 0.0) return@forEach

                        element.tags?.forEach { (key, value) ->
                            FeatureType.fromOSMTag(key, value)?.let { featureType ->
                                val shouldShow = if (featureType == FeatureType.SPEED_CAMERA) zoom >= 1.0 else zoom >= 5.0
                                if (shouldShow) {
                                    val feature = OSMFeature(
                                        id = element.id, lat = eLat, lon = eLon,
                                        type = featureType,
                                        name = element.tags["name"],
                                        tags = element.tags
                                    )
                                    osmFeatureBatch.add(feature)
                                    if (featureType == FeatureType.SPEED_CAMERA) cameraCount++ else hazardCount++
                                }
                            }
                        }
                    }
                    Log.d("Mapzy", "✅ OSM batch ready: $cameraCount cameras, $hazardCount hazards")
                } else {
                    Log.e("Mapzy", "OSM API error: ${osmResponse?.code()} - ${osmResponse?.message()}")
                }

                // ── Process Community results on the IO thread ────────────────
                val communityFeatureBatch = mutableListOf<OSMFeature>()
                var communityCount = 0

                if (communityResponse != null && communityResponse.isSuccessful && communityResponse.body() != null) {
                    val clusterList = communityResponse.body()!!.hazards
                    Log.d("Mapzy", "✅ Community: ${clusterList.size} hazards from backend")

                    clusterList.forEach { cluster ->
                        try {
                            val featId = cluster.hazard_id.hashCode().toLong()
                            val overrideConfidence = liveConfidenceMap[featId] ?: cluster.confidence_score
                            val overrideStatus     = liveStatusMap[featId]     ?: cluster.status
                            val type = if (overrideStatus == "NEEDS_REVALIDATION")
                                com.swapmap.zwap.demo.model.FeatureType.COMMUNITY_NEEDS_REVALIDATION
                            else
                                com.swapmap.zwap.demo.model.FeatureType.COMMUNITY_VERIFIED

                            val feat = com.swapmap.zwap.demo.model.OSMFeature(
                                id   = featId,
                                lat  = cluster.latitude,
                                lon  = cluster.longitude,
                                type = type,
                                name = "${cluster.hazard_type.replace("_", " ")} ($overrideConfidence% conf, ${cluster.verified_image_count} reports)",
                                tags = mapOf(
                                    "community"  to "true",
                                    "status"     to overrideStatus,
                                    "confidence" to overrideConfidence.toString()
                                )
                            )
                            communityFeatureBatch.add(feat)
                            communityCount++
                        } catch (e: Exception) {
                            Log.e("Mapzy", "Error processing community hazard: ${cluster.hazard_id}", e)
                        }
                    }
                } else {
                    Log.e("Mapzy", "Community backend error: ${communityResponse?.code()}")
                }

                // ── ATOMIC SWAP: clear old markers and add new ones in one Main block ──
                // This is the key to zero-flicker: old markers are only removed
                // HERE, immediately before new ones are painted in the same frame.
                // The map is never in an empty state visible to the user.
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (!osmOverlayEnabled) {
                        Log.d("Mapzy", "Toggle turned off during fetch — discarding results")
                        stopOSMLoadingAnimation()
                        isFetchInProgress = false
                        return@withContext
                    }

                    // Remove old markers atomically — right before new ones appear
                    clearOSMMarkers()

                    // Record the new bounding box BEFORE painting so subsequent
                    // GPS/camera events immediately see the updated cache.
                    lastFetchedBBox = BBox(south, west, north, east)

                    // Paint OSM features
                    osmFeatureBatch.forEach { feature ->
                        osmFeatures.add(feature)
                        toggledOsmFeatureIds.add(feature.id)
                        addOSMMarker(feature)
                    }

                    // Paint Community features
                    communityFeatureBatch.forEach { feat ->
                        osmFeatures.add(feat)
                        toggledOsmFeatureIds.add(feat.id)
                        addOSMMarker(feat)
                    }

                    val fetchMs = System.currentTimeMillis() - fetchStartMs
                    Log.d("Mapzy", "⏱️ Fetch+parse took ${fetchMs}ms | OSM: ${osmFeatureBatch.size} features | Community: ${communityFeatureBatch.size} features")

                    // Always trigger proximity check so the panel shows even if OSM timed out but community data exists
                    val loc = mapplsMap?.locationComponent?.lastKnownLocation
                    checkHazardProximity(loc?.latitude ?: lat, loc?.longitude ?: lon)

                    stopOSMLoadingAnimation()
                    isFetchInProgress = false
                }

            } catch (e: Exception) {
                Log.e("Mapzy", "fetchAndDisplayOSMFeatures: unexpected error", e)
                withContext(kotlinx.coroutines.Dispatchers.Main) { stopOSMLoadingAnimation(); isFetchInProgress = false }
            }
        }
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
                featureToMarkerMap[feature.id] = marker
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
    

    private fun createTaskMarkerBitmap(task: com.swapmap.zwap.demo.db.DriverTask): android.graphics.Bitmap {
        val baseSize = 100
        val bitmap = android.graphics.Bitmap.createBitmap(baseSize, baseSize, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        val category = com.swapmap.zwap.demo.navigation.TaskCategory.fromName(task.category)
        val circleColor = category?.color ?: android.graphics.Color.parseColor("#9C27B0")
        val emoji = category?.emoji ?: "📍"

        // Outer white border
        val borderPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(baseSize / 2f, baseSize / 2f, baseSize / 2f - 1, borderPaint)

        // Inner colored circle
        val paint = android.graphics.Paint().apply {
            color = circleColor
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(baseSize / 2f, baseSize / 2f, baseSize / 2f - 7, paint)

        // Draw emoji icon
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = baseSize * 0.42f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        val yPos = (baseSize / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2)
        canvas.drawText(emoji, baseSize / 2f, yPos, textPaint)

        return bitmap
    }

    private fun addTaskMarkersOnRoute() {
        val map = mapplsMap ?: return
        val tasks = cachedTasks.filter { !it.isCompleted && it.nearestPlaceLat != null && it.nearestPlaceLon != null }
        if (tasks.isEmpty()) return

        for (task in tasks) {
            try {
                val lat = task.nearestPlaceLat ?: continue
                val lon = task.nearestPlaceLon ?: continue
                val label = com.swapmap.zwap.demo.navigation.TaskCategory.fromName(task.category)?.label
                    ?: task.nearestPlaceName ?: "Task"
                val markerOptions = com.mappls.sdk.maps.annotations.MarkerOptions()
                    .position(com.mappls.sdk.maps.geometry.LatLng(lat, lon))
                    .title(label)
                    .snippet(task.nearestPlaceName ?: "")
                    .icon(com.mappls.sdk.maps.annotations.IconFactory.getInstance(this)
                        .fromBitmap(createTaskMarkerBitmap(task)))
                val marker = map.addMarker(markerOptions)
                if (marker != null) {
                    taskRouteMarkerIds.add(marker.id)
                    Log.d("Zwap", "Task marker added: $label at $lat,$lon")
                }
            } catch (e: Exception) {
                Log.e("Zwap", "Error adding task marker", e)
            }
        }
    }

    private fun clearTaskRouteMarkers() {
        mapplsMap?.let { map ->
            map.markers.filter { taskRouteMarkerIds.contains(it.id) }.forEach { map.removeMarker(it) }
        }
        taskRouteMarkerIds.clear()
    }

        /** Swap icon to spinner and start infinite rotation — call from Main thread */
    private fun startOSMLoadingAnimation() {
        val btn = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.btn_toggle_osm) ?: return
        btn.setImageResource(R.drawable.ic_osm_spinner)
        osmLoadingAnimator?.cancel()
        osmLoadingAnimator = ObjectAnimator.ofFloat(btn, "rotation", 0f, 360f).apply {
            duration = 800
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            interpolator = LinearInterpolator()
            start()
        }
    }

    /** Stop rotation and restore the hazard triangle icon — call from Main thread */
    private fun stopOSMLoadingAnimation() {
        osmLoadingAnimator?.cancel()
        osmLoadingAnimator = null
        val btn = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.btn_toggle_osm) ?: return
        btn.rotation = 0f
        btn.setImageResource(R.drawable.ic_hazard_waze)
    }

    private fun clearOSMMarkers(fullWipe: Boolean = true) {
        try {
            mapplsMap?.let { map ->
                val markersToRemove = map.markers.filter { osmMarkerIds.contains(it.id) }
                markersToRemove.forEach { map.removeMarker(it) }
            }
        } catch (e: Exception) {
            Log.e("Mapzy", "Error removing OSM markers from map", e)
        }
        osmMarkerIds.clear()
        featureToMarkerMap.clear()
        
        if (fullWipe) {
            // Wipe the full state (used for true refresh or low zoom)
            osmFeatures.clear()
            toggledOsmFeatureIds.clear()
            lastFetchedBBox = null
            Log.d("Mapzy", "clearOSMMarkers: full state wiped (markers + cache)")
        } else {
            // Only hide markers (used for toggle OFF)
            // We keep osmFeatures so they can be restored instantly if toggled back ON.
            Log.d("Mapzy", "clearOSMMarkers: markers removed but cache RETAINED")
        }
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
            
            when (currentSearchSource) {
                SearchSource.ORIGIN -> {
                    originPlace = location
                    isOriginCurrentLocation = (location.placeName == "Current Location")
                    findViewById<TextView>(R.id.et_origin_input)?.text = location.placeName
                    overlay.visibility = View.GONE
                    
                    // Immediate camera move for responsiveness
                    if (!isOriginCurrentLocation && location.latitude != null && location.longitude != null) {
                        isFollowMode = false
                        mapplsMap?.locationComponent?.cameraMode = com.mappls.sdk.maps.location.modes.CameraMode.NONE
                        mapplsMap?.animateCamera(
                            com.mappls.sdk.maps.camera.CameraUpdateFactory.newCameraPosition(
                                com.mappls.sdk.maps.camera.CameraPosition.Builder()
                                    .target(com.mappls.sdk.maps.geometry.LatLng(location.latitude!!, location.longitude!!))
                                    .tilt(0.0)
                                    .zoom(15.0)
                                    .build()
                            ), 800
                        )
                    }
                    
                    getDirections() // Redraw route immediately
                }
                SearchSource.DESTINATION -> {
                    selectedPlace = location
                    selectedELoc = location.mapplsPin
                    findViewById<TextView>(R.id.et_destination_input)?.text = location.placeName
                    overlay.visibility = View.GONE
                    getDirections() // Redraw route immediately
                }
                SearchSource.EXPLORE -> {
                    selectedELoc = location.mapplsPin
                    selectedPlace = location  // Store full place details
                    findViewById<TextView>(R.id.search_trigger).text = location.placeName
                    findViewById<View>(R.id.btn_directions).visibility = View.VISIBLE
                    
                    location.mapplsPin?.let { pin ->
                        isFollowMode = false
                        mapplsMap?.animateCamera(CameraMapplsPinUpdateFactory.newMapplsPinZoom(pin, 16.0))
                    }
                    overlay.visibility = View.GONE
                    
                    // Show place details bottom sheet like Google Maps
                    showPlaceDetailsBottomSheet(location)
                }
            }
            
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(etInput.windowToken, 0)
        }

        searchAdapter = SearchAdapter { selection ->
            if (selection.mapplsPin == "current_location_selection") {
                resolveCurrentLocationForSearch(onLocationSelected)
            } else {
                onLocationSelected(selection)
            }
        }
        historyAdapter = SearchAdapter { selection ->
            if (selection.mapplsPin == "current_location_selection") {
                resolveCurrentLocationForSearch(onLocationSelected)
            } else {
                onLocationSelected(selection)
            }
        }

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
        
        // Removed references to deleted category buttons (btn_cat_rest, etc.)

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

    private fun showSearchOverlay(query: String?, source: SearchSource = SearchSource.EXPLORE) {
        currentSearchSource = source
        val showCurrent = source != SearchSource.EXPLORE
        searchAdapter.showCurrentLocation = showCurrent
        historyAdapter.showCurrentLocation = showCurrent
        searchAdapter.notifyDataSetChanged()
        historyAdapter.notifyDataSetChanged()
        
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

    private fun resolveCurrentLocationForSearch(callback: (ELocation) -> Unit) {
        val lastLocation = mapplsMap?.locationComponent?.lastKnownLocation
        if (lastLocation == null) {
            Toast.makeText(this, "Location not available", Toast.LENGTH_SHORT).show()
            return
        }

        val currentLoc = ELocation()
        currentLoc.placeName = "Current Location"
        currentLoc.placeAddress = "My current GPS position"
        currentLoc.latitude = lastLocation.latitude
        currentLoc.longitude = lastLocation.longitude
        
        callback(currentLoc)
    }

    private fun saveToHistory(location: ELocation) {
        if (location.placeName == "Current Location" || location.mapplsPin == "current_location_selection") {
            Log.d("Zwap", "Skipping history save for Current Location")
            return
        }
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
                            
                            // FILTER: Don't show "Current Location" in history if we are in EXPLORE mode
                            if (currentSearchSource == SearchSource.EXPLORE && placeName == "Current Location") {
                                continue
                            }

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
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        
        var showCurrentLocation = false
        private var items = listOf<ELocation>()

        private val TYPE_CURRENT_LOCATION = 0
        private val TYPE_RESULT = 1

        fun submitList(newItems: List<ELocation>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int {
            return if (showCurrentLocation && position == 0) TYPE_CURRENT_LOCATION else TYPE_RESULT
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val iconView: android.widget.ImageView = view.findViewById(R.id.iv_icon)
            val text1: TextView = view.findViewById(R.id.tv_place_name)
            val text2: TextView = view.findViewById(R.id.tv_place_address)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.layout_search_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val vh = holder as ViewHolder
            if (getItemViewType(position) == TYPE_CURRENT_LOCATION) {
                vh.text1.text = "Current Location"
                vh.text1.setTypeface(null, android.graphics.Typeface.BOLD)
                vh.text2.text = "Use your current GPS position"
                vh.iconView.setImageResource(android.R.drawable.ic_menu_mylocation)
                vh.iconView.setColorFilter(Color.parseColor("#00B0FF"))
                vh.itemView.setOnClickListener { 
                    val specialLoc = ELocation()
                    specialLoc.mapplsPin = "current_location_selection"
                    onItemClick(specialLoc)
                }
            } else {
                val actualPos = if (showCurrentLocation) position - 1 else position
                val item = items[actualPos]
                vh.text1.text = item.placeName
                vh.text1.setTypeface(null, android.graphics.Typeface.BOLD)
                vh.text2.text = item.placeAddress
                vh.iconView.setImageResource(android.R.drawable.ic_dialog_map)
                vh.iconView.clearColorFilter()
                vh.itemView.setOnClickListener { onItemClick(item) }
            }
        }

        override fun getItemCount(): Int = items.size + (if (showCurrentLocation) 1 else 0)
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
        // Default explore view: top-down (0 tilt)
        isMapTilted = false
        map.animateCamera(com.mappls.sdk.maps.camera.CameraUpdateFactory.tiltTo(0.0))
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
            
            // Click listener for interactive route selection via duration label markers
            map.setOnMarkerClickListener { marker ->
                markerToRouteMap[marker.id]?.let { selectedRoute ->
                    Log.d("Zwap", "User tapped a route label — switching...")
                    switchToRoute(selectedRoute)
                    return@setOnMarkerClickListener true
                }
                false
            }
            
            // Map click listener to detect taps on gray route LINES
            map.addOnMapClickListener { tapLatLng ->
                val routes = currentRoute?.routes()
                if (routes != null && routes.size > 1) {
                    // Find the route whose line is closest to the tap point
                    var closestRoute: DirectionsRoute? = null
                    var closestDistanceM = Double.MAX_VALUE
                    
                    allRouteGeometries.forEach { (route, points) ->
                        // Check distance from tap to each segment of this route
                        for (i in 0 until points.size - 1) {
                            val dist = distanceToSegmentMeters(
                                tapLatLng,
                                points[i],
                                points[i + 1]
                            )
                            if (dist < closestDistanceM) {
                                closestDistanceM = dist
                                closestRoute = route
                            }
                        }
                    }
                    
                    // Only switch if tap is within 30m of a route line AND it's not already the active one
                    if (closestDistanceM < 30.0 && closestRoute != null && closestRoute != currentPrimaryRoute) {
                        Log.d("Zwap", "Tap detected ${closestDistanceM.toInt()}m from a route — switching to it")
                        switchToRoute(closestRoute!!)
                        return@addOnMapClickListener true
                    }
                }
                false
            }
            
            enableLocationComponent(style)
        }
        
        // Add camera change listener to refresh OSM overlay
        map.addOnCameraIdleListener {
            val currentZoom = map.cameraPosition.zoom
            val currentTarget = map.cameraPosition.target
            
            if (osmOverlayEnabled && currentTarget != null) {
                var distanceMoved = 0f
                if (lastFetchedMapCenter != null) {
                    val distArray = FloatArray(1)
                    android.location.Location.distanceBetween(
                        lastFetchedMapCenter!!.latitude, lastFetchedMapCenter!!.longitude,
                        currentTarget.latitude, currentTarget.longitude, distArray
                    )
                    distanceMoved = distArray[0]
                }
                
                val zoomDiff = Math.abs(currentZoom - lastZoomLevel)
                
                // Only refetch if camera moved > 500m or zoom changed by > 0.5
                if (lastFetchedMapCenter == null || distanceMoved > 500 || zoomDiff > 0.5) {
                    lastZoomLevel = currentZoom
                    lastFetchedMapCenter = currentTarget
                    
                    // Debounce fetch to update on Pan/Zoom but avoid 429 API errors
                    osmRunnable?.let { osmHandler.removeCallbacks(it) }
                    osmRunnable = Runnable { 
                        fetchAndDisplayOSMFeatures() 
                    }
                    osmHandler.postDelayed(osmRunnable!!, 1000)
                }
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
        try {
            isFollowMode = true
            val loc = mapplsMap?.locationComponent?.lastKnownLocation
            if (loc != null) {
                val targetTilt = if (isMapTilted) 60.0 else 0.0
                mapplsMap?.animateCamera(
                    com.mappls.sdk.maps.camera.CameraUpdateFactory.newCameraPosition(
                        com.mappls.sdk.maps.camera.CameraPosition.Builder()
                            .target(com.mappls.sdk.maps.geometry.LatLng(loc.latitude, loc.longitude))
                            .zoom(18.0)
                            .tilt(targetTilt)
                            .build()
                    ),
                    300,
                    object : com.mappls.sdk.maps.MapplsMap.CancelableCallback {
                        override fun onCancel() {}
                        override fun onFinish() {
                            mapplsMap?.locationComponent?.cameraMode =
                                com.mappls.sdk.maps.location.modes.CameraMode.TRACKING_GPS
                        }
                    }
                )
            } else {
                mapplsMap?.locationComponent?.cameraMode = com.mappls.sdk.maps.location.modes.CameraMode.TRACKING_GPS
            }
        } catch (e: Exception) {
            Log.e("Zwap", "Recenter error", e)
        }
    }

    /**
     * Apply custom arrow marker for navigation/start screen.
     * The arrow rotates based on device compass heading.
     * Only affects navigation screens - not home/search screens.
     */
    @SuppressLint("MissingPermission")
    private fun applyNavigationLocationMarker() {
        val style = mapplsMap?.style ?: return
        val locationComponent = mapplsMap?.locationComponent ?: return
        
        Log.d("Zwap", "Applying navigation arrow with COMPASS rotation")
        
        // Temporarily disable to prevent glitch
        locationComponent.isLocationComponentEnabled = false
        
        // CRITICAL: In COMPASS mode:
        // - foregroundDrawable does NOT rotate (stays fixed)
        // - bearingDrawable ROTATES with device compass
        // So we make foreground transparent and use bearing for the arrow
        val locationOptions = LocationComponentOptions.builder(this)
            // Foreground - make transparent so only bearing drawable shows
            .foregroundDrawable(R.drawable.ic_transparent_marker)
            .foregroundDrawableStale(R.drawable.ic_transparent_marker)
            // Bearing drawable - THIS ROTATES with device compass in COMPASS mode
            .bearingDrawable(R.drawable.ic_navigation_arrow_marker)
            // GPS drawable - rotates with movement direction in GPS mode
            .gpsDrawable(R.drawable.ic_navigation_arrow_marker)
            // Transparent background
            .backgroundDrawable(R.drawable.ic_transparent_marker_bg)
            .backgroundDrawableStale(R.drawable.ic_transparent_marker_bg)
            // Hide accuracy circle
            .accuracyColor(ContextCompat.getColor(this, android.R.color.transparent))
            .accuracyAlpha(0.0f)
            .build()
        
        val activationOptions = LocationComponentActivationOptions.builder(this, style)
            .locationComponentOptions(locationOptions)
            .build()
        
        locationComponent.activateLocationComponent(activationOptions)
        locationComponent.isLocationComponentEnabled = true
        
        // Use COMPASS mode so bearingDrawable rotates with device orientation
        locationComponent.renderMode = RenderMode.COMPASS
        Log.d("Zwap", "Arrow marker applied with RenderMode.COMPASS")
    }
    
    /**
     * Reset location marker to default blue dot (for non-navigation screens).
     */
    @SuppressLint("MissingPermission")
    private fun resetToDefaultLocationMarker() {
        val style = mapplsMap?.style ?: return
        val locationComponent = mapplsMap?.locationComponent ?: return
        
        // Create default LocationComponentOptions (no custom drawables)
        val defaultOptions = LocationComponentOptions.builder(this)
            // Restore default accuracy circle
            .accuracyColor(ContextCompat.getColor(this, R.color.mappls_blue))
            .accuracyAlpha(0.15f)
            .build()
        
        // Reactivate with default options
        val activationOptions = LocationComponentActivationOptions.builder(this, style)
            .locationComponentOptions(defaultOptions)
            .build()
        
        locationComponent.activateLocationComponent(activationOptions)
        locationComponent.isLocationComponentEnabled = true
        locationComponent.renderMode = RenderMode.COMPASS
    }
    /**
     * Updates the origin arrow marker based on whether origin is current location or a custom place.
     * - If origin is current location: Uses LocationComponent with rotation
     * - If origin is a custom place: Creates a fixed Marker at that position
     */
    private fun updateOriginArrowMarker() {
        // Remove existing origin arrow marker first
        originArrowMarker?.let {
            mapplsMap?.removeMarker(it)
            originArrowMarker = null
        }
        
        if (isOriginCurrentLocation) {
            // Origin is current GPS location - use LocationComponent for rotation
            applyNavigationLocationMarker()
        } else {
            // Origin is a custom place - reset LocationComponent to default and add fixed marker
            resetToDefaultLocationMarker()
            
            originPlace?.let { place ->
                val lat = place.latitude
                val lng = place.longitude
                if (lat != null && lng != null) {
                    val arrowDrawable = ContextCompat.getDrawable(this, R.drawable.ic_navigation_arrow_marker)
                    arrowDrawable?.let { drawable ->
                        val bitmap = Bitmap.createBitmap(
                            drawable.intrinsicWidth,
                            drawable.intrinsicHeight,
                            Bitmap.Config.ARGB_8888
                        )
                        val canvas = Canvas(bitmap)
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                        
                        originArrowMarker = mapplsMap?.addMarker(
                            MarkerOptions()
                                .position(com.mappls.sdk.maps.geometry.LatLng(lat, lng))
                                .icon(IconFactory.getInstance(this).fromBitmap(bitmap))
                        )
                    }
                }
            }
        }
    }
    
    /**
     * Removes origin arrow marker and resets location marker to default
     */
    private fun clearOriginArrowMarker() {
        originArrowMarker?.let {
            mapplsMap?.removeMarker(it)
            originArrowMarker = null
        }
        resetToDefaultLocationMarker()
    }
    
    /**
     * Updates start button visibility - only show if origin is current location
     */
    private fun updateStartButtonVisibility() {
        val startBtn = findViewById<View>(R.id.btn_start_navigation)
        startBtn?.visibility = if (isOriginCurrentLocation) View.VISIBLE else View.GONE
    }

    // ── Music Tray ───────────────────────────────────────────────────────────

    // ── Live Navigation Header Updates ──────────────────────────────────────────

    /** Returns the appropriate turn icon drawable for the given maneuver type + modifier */
    private fun maneuverIconRes(type: String?, modifier: String?): Int {
        val t = type?.lowercase() ?: ""
        val m = modifier?.lowercase() ?: ""
        return when {
            t.contains("arrive")                              -> R.drawable.ic_turn_arrive
            t.contains("roundabout") || t.contains("rotary") -> R.drawable.ic_turn_roundabout
            m.contains("uturn") || t.contains("uturn")        -> R.drawable.ic_turn_uturn
            m.contains("right")                               -> R.drawable.ic_turn_right_arrow
            m.contains("left")                                -> R.drawable.ic_turn_left_arrow
            else                                              -> R.drawable.ic_turn_straight
        }
    }

    /**
     * Called on every GPS update while isNavigating == true.
     * Advances the step index when the user passes a maneuver point,
     * then refreshes all header fields (icon, instruction, distance,
     * remaining time, remaining distance, ETA).
     */
    private fun updateNavActiveHeader(location: android.location.Location) {
        val steps = currentPrimaryRoute?.legs()?.firstOrNull()?.steps() ?: return
        if (steps.size < 2) return

        // ── step[i].maneuver().location() = START of step i (behind driver).
        // ── The UPCOMING maneuver is at step[currentStepIndex + 1].maneuver().location().
        // ── Advance index when the user passes within 40 m of the next turn point.
        while (currentStepIndex + 1 < steps.size - 1) {
            val nextManeuverLoc = steps[currentStepIndex + 1].maneuver()?.location() ?: break
            val d = FloatArray(1)
            android.location.Location.distanceBetween(
                location.latitude, location.longitude,
                nextManeuverLoc.latitude(), nextManeuverLoc.longitude(), d
            )
            if (d[0] < 40f) currentStepIndex++ else break
        }

        // ── NEXT maneuver = step[currentStepIndex + 1] (upcoming, ahead of driver) ──
        val nextIdx  = (currentStepIndex + 1).coerceAtMost(steps.size - 1)
        val nextStep = steps[nextIdx]
        val nextLoc  = nextStep.maneuver()?.location()

        // ── Distance to that upcoming maneuver point ──────────────────────────
        val distToManeuver: Double = if (nextLoc != null) {
            val d = FloatArray(1)
            android.location.Location.distanceBetween(
                location.latitude, location.longitude,
                nextLoc.latitude(), nextLoc.longitude(), d
            )
            d[0].toDouble()
        } else {
            // Fallback: use the step's declared distance minus distance already walked
            steps[currentStepIndex].distance() ?: 0.0
        }
        val distText = if (distToManeuver < 1000)
            "in ${distToManeuver.toInt()}m"
        else
            "in %.1f km".format(distToManeuver / 1000.0)

        // ── Remaining distance + duration ────────────────────────────────────────
        // distToManeuver = remaining distance on the CURRENT step (to the next turn).
        // From nextIdx onward = full distances of all subsequent steps.
        var remDist = distToManeuver
        var remDur  = 0.0
        for (i in nextIdx until steps.size) {
            remDist += steps[i].distance() ?: 0.0
            remDur  += steps[i].duration() ?: 0.0
        }
        // Estimate time for remaining portion of current step using the step's avg speed
        val curDist = steps[currentStepIndex].distance() ?: 0.0
        val curDur  = steps[currentStepIndex].duration() ?: 0.0
        val curStepSpeed = if (curDur > 0 && curDist > 0) curDist / curDur else 8.33
        remDur += distToManeuver / curStepSpeed

        // ── Icon + instruction from NEXT maneuver ─────────────────────────────
        val maneuver    = nextStep.maneuver()
        val iconRes     = maneuverIconRes(maneuver?.type(), maneuver?.modifier())
        val instruction = maneuver?.instruction()
            ?.takeIf { it.isNotBlank() }
            ?: "Continue on route"

        // ── Push to UI ────────────────────────────────────────────────────────
        val dMin      = (remDur / 60).toInt()
        val timeText  = if (dMin >= 60) "${dMin / 60} hr ${dMin % 60} min" else "$dMin min"
        val distKmText = "%.1f km".format(remDist / 1000.0)

        runOnUiThread {
            findViewById<android.widget.ImageView>(R.id.iv_active_turn_icon)?.setImageResource(iconRes)
            findViewById<android.widget.TextView>(R.id.tv_active_turn_instruction)?.text = instruction
            findViewById<android.widget.TextView>(R.id.tv_active_turn_distance)?.text = distText
            findViewById<android.widget.TextView>(R.id.tv_active_time)?.text = timeText
            findViewById<android.widget.TextView>(R.id.tv_active_distance)?.text = distKmText
            updateETADisplay(remDur)
        }
    }

    private fun setupNavBottomBarCurvedBg() {
        val navBar = findViewById<android.view.View>(R.id.nav_active_bottom_bar) ?: return
        val radiusPx = (20f * resources.displayMetrics.density)
        val bg = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(android.graphics.Color.BLACK)
            cornerRadii = floatArrayOf(radiusPx, radiusPx, radiusPx, radiusPx, 0f, 0f, 0f, 0f)
        }
        navBar.background = bg
        navBar.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                val r = (20f * view.resources.displayMetrics.density)
                outline.setRoundRect(0, 0, view.width, (view.height + r).toInt(), r)
            }
        }
        navBar.clipToOutline = true
        navBar.elevation = 10f * resources.displayMetrics.density
    }

    private fun setupMusicTray() {
        val spotifyIv = findViewById<android.widget.ImageView>(R.id.iv_spotify_icon)
        val gaanaIv   = findViewById<android.widget.ImageView>(R.id.iv_gaana_icon)
        // Use real app icon when installed; fall back to brand-accurate drawables
        if (spotifyIv != null) {
            try {
                spotifyIv.setImageDrawable(packageManager.getApplicationIcon("com.spotify.music"))
            } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                spotifyIv.setImageResource(R.drawable.ic_spotify_logo)
                spotifyIv.background = null
            }
        }
        if (gaanaIv != null) {
            try {
                gaanaIv.setImageDrawable(packageManager.getApplicationIcon("com.gaana"))
            } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                gaanaIv.setImageResource(R.drawable.ic_gaana_logo)
                gaanaIv.background = null
            }
        }

        findViewById<View>(R.id.btn_open_spotify)?.setOnClickListener {
            hideMusicTray()
            openAppOrStore("com.spotify.music",
                "spotify://",
                "https://play.google.com/store/apps/details?id=com.spotify.music")
        }
        findViewById<View>(R.id.btn_open_gaana)?.setOnClickListener {
            hideMusicTray()
            openAppOrStore("com.gaana",
                "gaana://",
                "https://play.google.com/store/apps/details?id=com.gaana")
        }
        findViewById<View>(R.id.music_tray_backdrop)?.setOnClickListener {
            hideMusicTray()
        }
    }

    private fun openAppOrStore(pkg: String, deepLink: String, storeUrl: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(storeUrl)))
        }
    }

    private fun toggleMusicTray() {
        if (musicTrayVisible) hideMusicTray() else showMusicTray()
    }

    private fun showMusicTray() {
        val tray     = findViewById<View>(R.id.music_tray)          ?: return
        val backdrop = findViewById<View>(R.id.music_tray_backdrop)  ?: return
        musicTrayVisible = true
        backdrop.visibility = View.VISIBLE
        tray.visibility     = View.VISIBLE
        tray.post {
            tray.translationY = tray.height.toFloat()
            tray.animate()
                .translationY(0f)
                .setDuration(260)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
    }

    private fun hideMusicTray() {
        val tray     = findViewById<View>(R.id.music_tray)          ?: return
        val backdrop = findViewById<View>(R.id.music_tray_backdrop)  ?: return
        musicTrayVisible = false
        backdrop.visibility = View.GONE
        tray.animate()
            .translationY(tray.height.toFloat())
            .setDuration(220)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction { tray.visibility = View.GONE }
            .start()
    }

    // ─────────────────────────────────────────────────────────────────────────

        private fun setupTaskIndicator() {
        taskIndicatorView = findViewById(R.id.task_indicator_view)
        if (taskIndicatorView == null) return

        // Click opens driver tasks panel
        taskIndicatorView?.setOnClickListener { showNoteBottomSheet() }

        // Observe task changes; cache them so location updates can refresh without DB query
        AppDatabase.getDatabase(this).driverTaskDao().getAllLive().observe(this) { tasks ->
            cachedTasks = tasks
            refreshTaskIndicator()
            // Refresh task map markers live if a route is already displayed
            if (currentPrimaryRoute != null) {
                clearTaskRouteMarkers()
                addTaskMarkersOnRoute()
            }
        }
    }

    private fun refreshTaskIndicator() {
        val tasks = cachedTasks
        val indicator = taskIndicatorView ?: return
        val incompleteTasks = tasks.filter { !it.isCompleted }
        if (incompleteTasks.isEmpty()) {
            indicator.updateTask(null, null)
            return
        }
        val loc = mapplsMap?.locationComponent?.lastKnownLocation
        if (loc == null) {
            indicator.updateTask(incompleteTasks.first(), null)
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val nearestPair = driverTasksManager?.nearestActiveTaskPlace(loc.latitude, loc.longitude)
                withContext(Dispatchers.Main) {
                    if (nearestPair != null) {
                        indicator.updateTask(nearestPair.first, nearestPair.second)
                    } else {
                        val nearest = incompleteTasks.minByOrNull { task ->
                            if (task.nearestPlaceLat != null && task.nearestPlaceLon != null) {
                                val r = FloatArray(1)
                                android.location.Location.distanceBetween(
                                    loc.latitude, loc.longitude,
                                    task.nearestPlaceLat, task.nearestPlaceLon, r)
                                r[0].toDouble()
                            } else Double.MAX_VALUE
                        }
                        if (nearest?.nearestPlaceLat != null && nearest.nearestPlaceLon != null) {
                            val r = FloatArray(1)
                            android.location.Location.distanceBetween(
                                loc.latitude, loc.longitude,
                                nearest.nearestPlaceLat, nearest.nearestPlaceLon, r)
                            indicator.updateTask(nearest, r[0] / 1000.0)
                        } else {
                            indicator.updateTask(incompleteTasks.first(), null)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Zwap", "Error updating task indicator", e)
            }
        }
    }

    fun handleLocationUpdate(location: Location) {
        // Refresh task indicator distance on every GPS location change (uses cached tasks - no DB hit)
        if (cachedTasks.any { !it.isCompleted }) refreshTaskIndicator()

        val speedKmh = (location.speed * 3.6).toInt()

        // ── Update current speed display ──────────────────────────────────────
        val tvSpeed = findViewById<TextView>(R.id.tv_speed)
        tvSpeed.text = "$speedKmh"

        // ── Speed limit HUD coloring — driven by HazardViewModel LiveData ─────
        // tv_limit text is updated by the LiveData observer in onCreate().
        // Here we just read the latest observed value to decide colours.
        val tvLimit = findViewById<TextView>(R.id.tv_limit)
        val isOver = hazardViewModel.checkOverSpeed(speedKmh, tts)
        val limitKnown = (hazardViewModel.speedLimitKmh.value ?: 0) > 0
        tvSpeed.setTextColor(if (isOver) Color.RED else Color.WHITE)
        tvLimit.setTextColor(
            if (isOver) Color.RED
            else android.graphics.Color.parseColor("#FFA726")
        )
        if (!limitKnown) tvLimit.text = "--"

        // ── Delegate 300m speed limit polling to HazardViewModel ─────────────
        hazardViewModel.onLocationChanged(location)

        // ── Check hazard proximity (during overlay mode OR navigation) ─────────
        if (osmOverlayEnabled || isNavigating) {
            checkHazardProximity(location.latitude, location.longitude)
        updateHazardAlertPill(location.latitude, location.longitude)
        }

        // ── Update live navigation header (turn icon, instruction, distance, time) ─
        if (isNavigating) updateNavActiveHeader(location)

        // ── Refresh OSM overlay data every 1km ────────────────────────────────
        if (osmOverlayEnabled) {
            if (lastOSMFetchLocation == null || location.distanceTo(lastOSMFetchLocation!!) > 1000) {
                lastOSMFetchLocation = location
                fetchAndDisplayOSMFeatures()
            }
        }
    }

    // fetchSpeedLimitNearby() has been moved to HazardViewModel.kt
    // MainActivity no longer owns speed limit logic — it only observes LiveData.



    private fun alertUser(message: String) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAlertTime < ALERT_COOLDOWN) return
        
        lastAlertTime = currentTime
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        tts?.speak(message, TextToSpeech.QUEUE_ADD, null, null)
    }

    private fun updateHazardAlertPill(userLat: Double, userLon: Double) {
        if (!isNavigating && !isPreviewMode) return

        lifecycleScope.launch {
            // ── Priority 1: task-linked place within 500 m ──────────────────────
            val taskAlert = withContext(Dispatchers.IO) {
                driverTasksManager?.nearestActiveTaskPlace(userLat, userLon)
            }

            // ── Priority 2: nearest OSM route hazard within 10 km ───────────────
            val nearest = osmFeatures
                .filter { it.type in listOf(
                    FeatureType.SPEED_CAMERA, FeatureType.TOLL,
                    FeatureType.TRAFFIC_CALMING,
                    FeatureType.COMMUNITY_VERIFIED,
                    FeatureType.COMMUNITY_NEEDS_REVALIDATION
                )}
                .mapNotNull { feature ->
                    val r = FloatArray(1)
                    android.location.Location.distanceBetween(userLat, userLon, feature.lat, feature.lon, r)
                    if (r[0] < 10000f) Pair(feature, r[0].toDouble()) else null
                }
                .minByOrNull { it.second }

            // ── Update UI (already on Main via lifecycleScope) ───────────────────
            val pill = findViewById<android.view.ViewGroup>(R.id.hazard_alert_pill)
                ?: return@launch
            val pillIcon = findViewById<android.widget.ImageView>(R.id.iv_hazard_pill_icon)
            val pillType = findViewById<android.widget.TextView>(R.id.tv_hazard_pill_type)
            val pillDist = findViewById<android.widget.TextView>(R.id.tv_hazard_pill_dist)
            pill.visibility = android.view.View.VISIBLE

            val bg = android.graphics.drawable.GradientDrawable()
            bg.cornerRadius = 100f

            when {
                taskAlert != null -> {
                    // Task-linked place (highest priority after SAFE)
                    val (task, distKm) = taskAlert
                    val cat = TaskCategory.fromName(task.category)
                    val distStr = if (distKm < 1.0) "${(distKm * 1000).toInt()}m"
                                  else "${"%.1f".format(distKm)}km"
                    val pillColor = cat?.color ?: android.graphics.Color.parseColor("#26A69A")
                    bg.setColor(pillColor)
                    bg.setStroke(3, android.graphics.Color.BLACK)
                    pill.background = bg
                    pillIcon?.setImageResource(R.drawable.ic_note_document)
                    pillIcon?.imageTintList = android.content.res.ColorStateList
                        .valueOf(android.graphics.Color.BLACK)
                    pillType?.text = cat?.label?.uppercase() ?: "TASK"
                    pillType?.setTextColor(android.graphics.Color.BLACK)
                    pillDist?.text = (task.nearestPlaceName?.take(12) ?: "") + "  " + distStr
                    pillDist?.setTextColor(android.graphics.Color.parseColor("#1A1A1A"))
                    // TTS handled by checkHazardProximity only (no duplicate pill speech)
                }
                nearest != null -> {
                    // Nearest hazard
                    val (feature, dist) = nearest
                    val distStr = if (dist < 1000) "${dist.toInt()}m"
                                  else "${"%.1f".format(dist / 1000)}km"
                    val (bgColor, iconRes, label) = when (feature.type) {
                        FeatureType.SPEED_CAMERA ->
                            Triple(android.graphics.Color.parseColor("#FFD600"),
                                R.drawable.ic_camera_outlined, "CAMERA")
                        FeatureType.TOLL ->
                            Triple(android.graphics.Color.parseColor("#00E5FF"),
                                R.drawable.ic_hazard_waze, "TOLL")
                        FeatureType.TRAFFIC_CALMING ->
                            Triple(android.graphics.Color.parseColor("#FF6D00"),
                                R.drawable.ic_hazard_waze, "BUMP")
                        else ->
                            Triple(android.graphics.Color.parseColor("#FF6D00"),
                                R.drawable.ic_hazard_waze, "HAZARD")
                    }
                    bg.setColor(bgColor)
                    bg.setStroke(3, android.graphics.Color.BLACK)
                    pill.background = bg
                    pillIcon?.setImageResource(iconRes)
                    pillIcon?.imageTintList = android.content.res.ColorStateList
                        .valueOf(android.graphics.Color.BLACK)
                    pillType?.text = label
                    pillType?.setTextColor(android.graphics.Color.BLACK)
                    pillDist?.text = distStr
                    pillDist?.setTextColor(android.graphics.Color.parseColor("#1A1A1A"))
                    // TTS handled by checkHazardProximity only (no duplicate pill speech)
                }
                else -> {
                    // SAFE — nothing nearby
                    bg.setColor(android.graphics.Color.parseColor("#00C853"))
                    bg.setStroke(3, android.graphics.Color.BLACK)
                    pill.background = bg
                    pillIcon?.setImageResource(R.drawable.ic_hazard_waze)
                    pillIcon?.imageTintList = android.content.res.ColorStateList
                        .valueOf(android.graphics.Color.BLACK)
                    pillType?.text = "SAFE"
                    pillType?.setTextColor(android.graphics.Color.BLACK)
                    pillDist?.text = ""
                }
            }
        }
    }

        private fun checkHazardProximity(userLat: Double, userLon: Double) {
        // Note: we no longer block the panel during preview mode;
        // only audio (TTS) is suppressed below if isPreviewMode is true.
        
        osmFeatures.forEach { feature ->
            val results = FloatArray(1)
            android.location.Location.distanceBetween(userLat, userLon, feature.lat, feature.lon, results)
            val distance = results[0].toDouble()
            
            // Only trigger NEW alerts if not alerted yet and within distance
            if (!alertedHazardIds.contains(feature.id) && distance < HAZARD_ALERT_DISTANCE) {
                alertedHazardIds.add(feature.id)
                var title = ""
                var subtitle = ""
                val msg = when (feature.type) {
                    FeatureType.SPEED_CAMERA -> { title="SPEED CAMERA AHEAD"; subtitle="in ${distance.toInt()} meters"; "Speed camera ahead in ${distance.toInt()} meters" }
                    FeatureType.TRAFFIC_CALMING -> { title="SPEED BUMP AHEAD"; subtitle="in ${distance.toInt()} meters"; "Speed bump ahead in ${distance.toInt()} meters" }
                    FeatureType.STOP_SIGN -> { title="STOP SIGN AHEAD"; subtitle="in ${distance.toInt()} meters"; "Stop sign ahead in ${distance.toInt()} meters" }
                    FeatureType.GIVE_WAY -> { title="GIVE WAY AHEAD"; subtitle="in ${distance.toInt()} meters"; "Give way sign ahead in ${distance.toInt()} meters" }
                    FeatureType.COMMUNITY_VERIFIED -> { title="HAZARD AHEAD"; subtitle="Community verified, in ${distance.toInt()} meters"; "Community verified hazard ahead in ${distance.toInt()} meters" }
                    FeatureType.COMMUNITY_NEEDS_REVALIDATION -> { title="REPORTED HAZARD"; subtitle="in ${distance.toInt()} meters"; "Reported hazard ahead in ${distance.toInt()} meters, needs verification" }
                    else -> { title="HAZARD AHEAD"; subtitle="in ${distance.toInt()} meters"; "Hazard ahead in ${distance.toInt()} meters" }
                }
                
                if (isNavigating) {
                    runOnUiThread {
                        val banner = findViewById<View>(R.id.nav_hazard_banner)
                        findViewById<TextView>(R.id.tv_hazard_banner_title)?.text = title
                        findViewById<TextView>(R.id.tv_hazard_banner_subtitle)?.text = subtitle
                        banner?.visibility = View.VISIBLE
                        
                        // Auto-hide banner after 10 seconds
                        banner?.postDelayed({
                            banner.visibility = View.GONE
                        }, 10000)
                    }
                }
                Log.d("Zwap", "HAZARD ALERT: $msg")
                sendHazardNotification(title, subtitle, feature.id)
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                if (isNavigating) {
                    tts?.speak(msg, TextToSpeech.QUEUE_ADD, null, null)
                }
                addHazardToPanel(feature, distance.toInt())
            } else if (alertedHazardIds.contains(feature.id) && distance < HAZARD_ALERT_DISTANCE) {
                // If already alerted but still close, ensure it's visually added if not already
                addHazardToPanel(feature, distance.toInt())
            }
        }
        // Update panel with current distances and remove crossed hazards
        updateHazardPanel(userLat, userLon)
    }

    private fun setupHazardAlertPanel() { /* panel removed */ }

    private fun toggleHazardPanel() { /* panel removed */ }

    private fun updateHazardPanel(userLat: Double, userLon: Double) { /* panel removed */ }

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
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 202 && resultCode == RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val query = results[0]
                showSearchOverlay(query)
            }
        }
    }

    private fun getDirections(autoStart: Boolean = false) {
        // Check if either origin or destination is at least set (one can be null for "Your location")
        if (originPlace == null && selectedPlace == null) {
            Toast.makeText(this, "Please select a destination", Toast.LENGTH_SHORT).show()
            return
        }
        
        val lastLocation = mapplsMap?.locationComponent?.lastKnownLocation
        
        // Determine Origin
        val originObj: Any = if (originPlace != null) {
            val lat = originPlace?.latitude
            val lng = originPlace?.longitude
            if (lat != null && lng != null) {
                Point.fromLngLat(lng, lat)
            } else {
                originPlace?.mapplsPin ?: ""
            }
        } else if (lastLocation != null) {
            Point.fromLngLat(lastLocation.longitude, lastLocation.latitude)
        } else {
            Toast.makeText(this, "Getting your location...", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Resolve Destination
        val destObj: Any = if (selectedPlace != null) {
            val lat = selectedPlace?.latitude
            val lng = selectedPlace?.longitude
            if (lat != null && lng != null) {
                Point.fromLngLat(lng, lat)
            } else {
                selectedELoc ?: selectedPlace?.mapplsPin ?: ""
            }
        } else if (lastLocation != null) {
            Point.fromLngLat(lastLocation.longitude, lastLocation.latitude)
        } else {
            Toast.makeText(this, "Destination location required", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Calculating route...", Toast.LENGTH_SHORT).show()
        
        // Reset navigation state — fresh route search always shows directions UI
        isNavigating = false
        isPreviewMode = true
        alertedHazardIds.clear()       // reset so hazards alert fresh on the new route
        hazardPillSpokenIds.clear()
        taskPillSpokenIds.clear()
        
        Log.d("Zwap", "Getting directions from $originObj to $destObj")
        
        val builder = MapplsDirections.builder()
            .profile(DirectionsCriteria.PROFILE_DRIVING)
            .resource(DirectionsCriteria.RESOURCE_ROUTE)
            .steps(true)
            .alternatives(true)
            .overview(DirectionsCriteria.OVERVIEW_FULL)

        // Set Origin
        if (originObj is String) builder.origin(originObj)
        else if (originObj is Point) builder.origin(originObj)
        
        // Set Destination
        if (destObj is String) builder.destination(destObj)
        else if (destObj is Point) builder.destination(destObj)
        
        val finalizedRequest = builder.annotations(DirectionsCriteria.ANNOTATION_CONGESTION, DirectionsCriteria.ANNOTATION_DISTANCE)
            .build()
            
        MapplsDirectionManager.newInstance(finalizedRequest).call(object : OnResponseCallback<DirectionsResponse> {
            override fun onSuccess(response: DirectionsResponse?) {
                if (response != null && response.routes().isNotEmpty()) {
                    currentRoute = response
                    val allRoutes = response.routes()
                    
                    // The primary route (shortest)
                    val primaryRoute = allRoutes.minByOrNull { it.distance() ?: Double.MAX_VALUE } ?: allRoutes[0]
                    currentPrimaryRoute = primaryRoute
                    
                    Log.d("Zwap", "Found ${allRoutes.size} routes. Primary distance: ${primaryRoute.distance()}m")
                    
                    drawRoute(allRoutes, primaryRoute)
                    extractHazardsFromRoute(primaryRoute.legs()?.get(0)?.steps())

                    // Show directions UI (Google Maps style)
                    // If we are auto-starting, suppress the preview UI cards (prevents glitch)
                    if (autoStart) {
                        isNavigating = true
                    }
                    
                    showDirectionsUI(primaryRoute.distance()!!, primaryRoute.duration()!!, skipUI = autoStart)
                    if (!autoStart) showTripBriefingScanning()  // Skip when jumping straight to navigation

                    Toast.makeText(this@MainActivity, "Route ready!", Toast.LENGTH_SHORT).show()

                    if (autoStart) {
                        // Start navigation without delay — FABs already set to START_NAVIGATION
                        // by prepareStartPageTransition called inside showDirectionsUI(skipUI=true)
                        if (!isDestroyed && !isFinishing) {
                            startNavigation()
                        }
                    }
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
    
    private var isPanelCollapsed = false

    private fun repositionFabsAbovePanel() {
        // Do not run during Start/Navigation (no panel on that page)
        if (fabLayoutManager.getCurrentState() == PageState.START_NAVIGATION) return
        val panel = findViewById<View>(R.id.directions_bottom_panel) ?: return
        val bottomNav = findViewById<View>(R.id.bottom_navigation) ?: return
        val fabStack = findViewById<View>(R.id.fab_stack_container)
        val aiVoice = findViewById<View>(R.id.fab_ai_voice)
        val speedWidget = findViewById<View>(R.id.speed_limit_widget)

        // Measure synchronously — caller must ensure panel is laid out.
        // FABs stay anchored to ALIGN_PARENT_BOTTOM (set in XML).
        // We only use translationY to lift them above the panel — no layout-param changes.
        val doReposition = Runnable {
            val panelHeight = panel.height
            // When bottom_nav is GONE its height is 0 — correct for translationY calc
            val navHeight = if (bottomNav.visibility == View.VISIBLE) bottomNav.height else 0

            if (panelHeight == 0) return@Runnable
            // FABs have marginBottom=96dp in XML, so lift formula must subtract that base
            // margin so the visual gap above the panel is just `clearance` dp, not 96+gap dp.
            val fabBaseMargin = (96 * resources.displayMetrics.density)
            val clearance = (8 * resources.displayMetrics.density)
            val targetTranslationY = -(panelHeight.toFloat() - navHeight.toFloat() - fabBaseMargin + clearance)
            
            // Bring to front
            fabStack?.bringToFront()
            aiVoice?.bringToFront()
            speedWidget?.bringToFront()
            
            // Vertical translation
            fabStack?.animate()?.translationY(targetTranslationY)?.setDuration(200)?.start()
            aiVoice?.animate()?.translationY(targetTranslationY)?.setDuration(200)?.start()
            speedWidget?.animate()?.translationY(targetTranslationY)?.setDuration(200)?.start()
        }
        if (panel.height > 0) {
            doReposition.run()
        } else {
            panel.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int,
                    ol: Int, ot: Int, or2: Int, ob: Int) {
                    panel.removeOnLayoutChangeListener(this)
                    doReposition.run()
                }
            })
        }
    }

    private fun resetFabPositions() {
        // FABs are anchored by XML (ALIGN_PARENT_BOTTOM + fixed margins).
        // Reset only translationY — no layout-param changes needed.
        listOf(R.id.fab_stack_container, R.id.fab_ai_voice, R.id.speed_limit_widget).forEach { id ->
            findViewById<View>(id)?.translationY = 0f
        }
    }

    private fun showDirectionsUI(distance: Double, duration: Double, skipUI: Boolean = false) {
        Log.d("Zwap", "showDirectionsUI called. distance=$distance, isOriginCurrentLocation=$isOriginCurrentLocation")
        
        // Set FAB state: DIRECTIONS normally, START_NAVIGATION immediately when skipUI
        if (skipUI) {
            fabLayoutManager.prepareStartPageTransition {}
        } else {
            fabLayoutManager.setPageState(PageState.DIRECTIONS)
        }
        
        // Hide bottom navigation
        findViewById<View>(R.id.bottom_navigation)?.visibility = View.GONE

        // Hide search bar, hazard toggle and compass for clean directions view (like Google Maps)
        findViewById<View>(R.id.search_card).visibility = View.GONE
        findViewById<View>(R.id.btn_toggle_osm)?.visibility = View.GONE
        findViewById<View>(R.id.fab_compass)?.visibility = View.GONE
        // Hide old hazard alert panel — it overlaps directions panel
        findViewById<View>(R.id.trip_briefing_card)?.visibility = View.GONE
        
        // 1. IMPROVED: Setup map for active navigation context (Stable margins)
        val mapParams = findViewById<View>(R.id.map_view).layoutParams as? ViewGroup.MarginLayoutParams
        mapParams?.let {
            it.topMargin = 0 // Header card floats over the map
            it.bottomMargin = (85 * resources.displayMetrics.density).toInt() // Room for bottom card
            findViewById<View>(R.id.map_view).layoutParams = it
        }
        
        mapplsMap?.setPadding(0, 0, 0, 0)
        isMapTilted = false
        isFollowMode = false
        mapplsMap?.locationComponent?.cameraMode = com.mappls.sdk.maps.location.modes.CameraMode.NONE
        mapplsMap?.animateCamera(com.mappls.sdk.maps.camera.CameraUpdateFactory.tiltTo(0.0), 300)

        // 2. Resolve Target LatLng (Coord-aware or PIN-aware fallback)
        var targetLatLng: com.mappls.sdk.maps.geometry.LatLng? = null
        
        if (isOriginCurrentLocation) {
            mapplsMap?.locationComponent?.lastKnownLocation?.let { loc ->
                targetLatLng = com.mappls.sdk.maps.geometry.LatLng(loc.latitude, loc.longitude)
                Log.d("Zwap", "Target: Current Location (${targetLatLng?.latitude}, ${targetLatLng?.longitude})")
            }
        } else {
            originPlace?.let { eLoc ->
                val lat = eLoc.latitude
                val lng = eLoc.longitude
                if (lat != null && lng != null) {
                    targetLatLng = com.mappls.sdk.maps.geometry.LatLng(lat.toDouble(), lng.toDouble())
                    Log.d("Zwap", "Target: Custom Place Coord (${targetLatLng?.latitude}, ${targetLatLng?.longitude})")
                }
            }
        }

        // Fallback: If no coords (e.g. only eLoc PIN), extract from route geom
        if (targetLatLng == null) {
            currentPrimaryRoute?.geometry()?.let { geometry ->
                val points = PolylineUtils.decode(geometry, com.mappls.sdk.services.utils.Constants.PRECISION_6)
                if (points.isNotEmpty()) {
                    targetLatLng = com.mappls.sdk.maps.geometry.LatLng(points[0].latitude(), points[0].longitude())
                    Log.d("Zwap", "Target: Route Start Fallback (${targetLatLng?.latitude}, ${targetLatLng?.longitude})")
                }
            }
        }

        // 3. Execute Unified Preview Camera Update (Standard "Direction Page" Overview)
        val routePoints = currentPrimaryRoute?.geometry()?.let { geometry ->
            PolylineUtils.decode(geometry, com.mappls.sdk.services.utils.Constants.PRECISION_6)
        }
        
        if (routePoints != null && routePoints.isNotEmpty()) {
            val builder = com.mappls.sdk.maps.geometry.LatLngBounds.Builder()
            routePoints.forEach { pt -> builder.include(com.mappls.sdk.maps.geometry.LatLng(pt.latitude(), pt.longitude())) }
            
            val bounds = builder.build()
            val zoomPadding = (60 * resources.displayMetrics.density).toInt()
            
            Log.d("Zwap", "Zooming to route bounds overview")
            mapplsMap?.animateCamera(
                com.mappls.sdk.maps.camera.CameraUpdateFactory.newLatLngBounds(bounds, zoomPadding), 1000
            )
        } else if (targetLatLng != null) {
            Log.d("Zwap", "Executing preview camera move to target (Fallback)...")
            mapplsMap?.animateCamera(
                com.mappls.sdk.maps.camera.CameraUpdateFactory.newCameraPosition(
                    com.mappls.sdk.maps.camera.CameraPosition.Builder()
                        .target(targetLatLng)
                        .tilt(0.0)
                        .bearing(0.0)
                        .zoom(14.5)
                        .build()
                ), 800
            )
        } else {
            Log.w("Zwap", "Could not resolve camera target for centering!")
            // Last ditch: at least ensure it's top-down
            mapplsMap?.animateCamera(com.mappls.sdk.maps.camera.CameraUpdateFactory.tiltTo(0.0), 300)
        }
        
        // Show directions header card (only if not skipping UI for direct navigation)
        if (!skipUI) {
            findViewById<View>(R.id.directions_header_card).visibility = View.VISIBLE
        }
        
        val etOrigin = findViewById<TextView>(R.id.et_origin_input)
        val etDest = findViewById<TextView>(R.id.et_destination_input)
        
        etOrigin?.text = originPlace?.placeName ?: "Current Location"
        etDest?.text = selectedPlace?.placeName ?: if (originPlace != null) "Current Location" else "Destination"
        
        // Tapping fields opens search overlay
        etOrigin?.setOnClickListener {
            showSearchOverlay(null, SearchSource.ORIGIN)
        }
        etDest?.setOnClickListener {
            showSearchOverlay(null, SearchSource.DESTINATION)
        }
        
        // Setup swap locations button
        findViewById<View>(R.id.btn_swap_locations).setOnClickListener {
            val tempPlace = originPlace
            originPlace = selectedPlace
            selectedPlace = tempPlace
            selectedELoc = selectedPlace?.mapplsPin
            
            // UI Update
            etOrigin?.text = originPlace?.placeName ?: "Current Location"
            etDest?.text = selectedPlace?.placeName ?: "Current Location"
            
            isOriginCurrentLocation = (originPlace == null || originPlace?.placeName == "Current Location")
            
            Toast.makeText(this, "Swapping origin and destination", Toast.LENGTH_SHORT).show()
            getDirections() // Reactively update map path
            updateOriginArrowMarker() // Update arrow marker position
            updateStartButtonVisibility() // Update start button visibility
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
        
        // Ensure panel is at bottom position (may have been moved to top during navigation)
        val panel = findViewById<View>(R.id.directions_bottom_panel)
        val panelParams = panel.layoutParams as? RelativeLayout.LayoutParams
        panelParams?.let {
            it.removeRule(RelativeLayout.ALIGN_PARENT_TOP)
            it.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            panel.layoutParams = it
        }
        panel.setBackgroundResource(R.drawable.bg_nav_card_bottom)

        // Restore elements that may have been hidden during navigation
        findViewById<View>(R.id.directions_actions_layout)?.visibility = View.VISIBLE
        findViewById<View>(R.id.panel_drag_handle)?.visibility = View.VISIBLE
        findViewById<View>(R.id.btn_collapse_panel)?.visibility = View.VISIBLE
        findViewById<View>(R.id.btn_stop_navigation)?.visibility = View.GONE

        // Show directions bottom panel and scan progress
        if (!skipUI) {
            panel.visibility = View.VISIBLE
        }
        findViewById<View>(R.id.hazard_scan_progress_container)?.visibility = View.VISIBLE
        
        // Ensure FABs are on top (no underlay behind panel)
        findViewById<View>(R.id.fab_stack_container)?.bringToFront()
        findViewById<View>(R.id.fab_ai_voice)?.bringToFront()
        findViewById<View>(R.id.speed_limit_widget)?.bringToFront()

        // SET TO MAXIMIZED BY DEFAULT (as requested: "minimized by default set to maximized")
        val panelContainerDefault = findViewById<android.view.ViewGroup>(R.id.directions_bottom_panel_container)
        panelContainerDefault?.let { vg ->
            for (i in 0 until vg.childCount) {
                vg.getChildAt(i).visibility = View.VISIBLE
            }
        }
        findViewById<android.widget.ImageButton>(R.id.btn_collapse_panel)?.rotation = 0f
        isPanelCollapsed = false

        // Only reposition FABs above panel when the panel is actually visible
        if (!skipUI) {
            repositionFabsAbovePanel()
            findViewById<View>(R.id.directions_bottom_panel_container)?.postDelayed({
                repositionFabsAbovePanel()
            }, 150)
            findViewById<View>(R.id.directions_bottom_panel_container)?.postDelayed({
                repositionFabsAbovePanel()
            }, 400)
        }

        // DEBUG: Log FAB positions after repositioning
        val fabDbg = findViewById<View>(R.id.fab_stack_container)
        val aiDbg = findViewById<View>(R.id.fab_ai_voice)
        val spdDbg = findViewById<View>(R.id.speed_limit_widget)
        val bnDbg = findViewById<View>(R.id.bottom_navigation)
        val panelDbg = findViewById<View>(R.id.directions_bottom_panel)
        fabDbg?.post {
            val fabP = fabDbg.layoutParams as? RelativeLayout.LayoutParams
            Log.w("FAB_DEBUG", "showDirectionsUI -> fabStack: tY=${fabDbg.translationY}, y=${fabDbg.y}, h=${fabDbg.height}, above=${fabP?.getRule(RelativeLayout.ABOVE)}, alignBot=${fabP?.getRule(RelativeLayout.ALIGN_PARENT_BOTTOM)}")
            Log.w("FAB_DEBUG", "showDirectionsUI -> aiVoice: tY=${aiDbg?.translationY}, y=${aiDbg?.y}, vis=${aiDbg?.visibility}")
            Log.w("FAB_DEBUG", "showDirectionsUI -> speedWidget: tY=${spdDbg?.translationY}, y=${spdDbg?.y}, vis=${spdDbg?.visibility}")
            Log.w("FAB_DEBUG", "showDirectionsUI -> bottomNav: vis=${bnDbg?.visibility}, h=${bnDbg?.height}, y=${bnDbg?.y}")
            Log.w("FAB_DEBUG", "showDirectionsUI -> panel: vis=${panelDbg?.visibility}, h=${panelDbg?.height}, y=${panelDbg?.y}")
            Log.w("FAB_DEBUG", "showDirectionsUI -> isNavigating=$isNavigating, isPreviewMode=$isPreviewMode")
        }

        // Setup collapse/expand toggle
        val collapseBtn = findViewById<android.widget.ImageButton>(R.id.btn_collapse_panel)
        val panelContainer = findViewById<View>(R.id.directions_bottom_panel_container)
        collapseBtn?.setOnClickListener {
            val panel = findViewById<View>(R.id.directions_bottom_panel) ?: return@setOnClickListener
            if (isPanelCollapsed) {
                // Expand: show all children
                panelContainer?.let { container ->
                    for (i in 0 until (container as android.view.ViewGroup).childCount) {
                        container.getChildAt(i).visibility = View.VISIBLE
                    }
                }
                collapseBtn.rotation = 0f
                isPanelCollapsed = false
            } else {
                // Collapse: hide all except drag handle row + stats row
                panelContainer?.let { container ->
                    val vg = container as android.view.ViewGroup
                    for (i in 2 until vg.childCount) {
                        vg.getChildAt(i).visibility = View.GONE
                    }
                }
                collapseBtn.rotation = 180f
                isPanelCollapsed = true
            }
            // Wait for layout pass then reposition — requestLayout forces re-measure
            panel.requestLayout()
            panel.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int,
                    ol: Int, ot: Int, or2: Int, ob: Int) {
                    panel.removeOnLayoutChangeListener(this)
                    repositionFabsAbovePanel()
                }
            })
        }
        
        // Setup initial directions text
        // steps[0]=depart step (distance = road to first turn); steps[1]=first turn maneuver
        val allSteps   = currentPrimaryRoute?.legs()?.firstOrNull()?.steps()
        val departStep = allSteps?.getOrNull(0)
        val firstTurn  = allSteps?.getOrNull(1) ?: allSteps?.getOrNull(0)
        val instruction = firstTurn?.maneuver()?.instruction()?.takeIf { it.isNotBlank() } ?: "Continue on route"
        val departDist  = departStep?.distance() ?: 0.0
        val distToTurn  = if (departDist > 0) {
            if (departDist < 1000) "in ${departDist.toInt()}m" else "in %.1fkm".format(departDist / 1000.0)
        } else "calculating..."
        val initIconRes = maneuverIconRes(firstTurn?.maneuver()?.type(), firstTurn?.maneuver()?.modifier())
        findViewById<android.widget.ImageView>(R.id.iv_active_turn_icon)?.setImageResource(initIconRes)
        findViewById<TextView>(R.id.tv_active_turn_instruction)?.text = instruction
        findViewById<TextView>(R.id.tv_active_turn_distance)?.text = distToTurn

        val durationMin = (duration / 60.0).toInt()
        val durationText = if (durationMin >= 60) {
            "${durationMin / 60} hr ${durationMin % 60} min"
        } else {
            "$durationMin min"
        }
        findViewById<TextView>(R.id.tv_route_duration).text = durationText
        findViewById<TextView>(R.id.tv_active_time)?.text = durationText
        val distText = "%.1f km".format(distance / 1000.0)
        findViewById<TextView>(R.id.tv_route_distance).text = distText
        findViewById<TextView>(R.id.tv_active_distance)?.text = distText

        // Set initial ETA based on current time + duration
        updateETADisplay(duration)
        
        // Setup close button
        findViewById<View>(R.id.btn_close_directions)?.setOnClickListener {
            closeDirectionsUI()
        }
        
        // Setup start navigation button
        findViewById<View>(R.id.btn_start_navigation).setOnClickListener {
            startNavigation()
        }
        // Only show start button if origin is current location
        updateStartButtonVisibility()
        
        // Setup share button
        findViewById<View>(R.id.btn_share_route)?.setOnClickListener {
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, "Check out this route to ${selectedPlace?.placeName ?: "destination"}")
            }
            startActivity(android.content.Intent.createChooser(shareIntent, "Share route"))
        }

        // Setup stop navigation button (red X)
        findViewById<View>(R.id.btn_stop_navigation)?.setOnClickListener {
            closeDirectionsUI()
        }
        
        // Setup options button
        findViewById<View>(R.id.btn_route_options)?.setOnClickListener {
            Toast.makeText(this, "Route options - Coming soon", Toast.LENGTH_SHORT).show()
        }
        
        // Hide old route info layout
        findViewById<View>(R.id.route_info_layout).visibility = View.GONE
    }
    
    private fun startNavigation() {
        isNavigating = true
        isPreviewMode = false
        currentStepIndex = 0
        
        // Post silent persistent notification on navigation channel
        postNavigationActiveNotification()

        // Pre-compose Start page FABs via post-frame callback to prevent transition flash
        fabLayoutManager.prepareStartPageTransition {}
        isMapTilted = true
        
        // Update ETA for the start of navigation
        currentPrimaryRoute?.duration()?.let { updateETADisplay(it) }

        
        // Apply custom arrow marker for navigation
        updateOriginArrowMarker()
        Toast.makeText(this, "Navigation started!", Toast.LENGTH_SHORT).show()

        // 1. Calculate Initial Bearing from Route and Determine Start Point
        var routeBearing = 0.0
        var startLat: Double? = null
        var startLng: Double? = null
        
        currentPrimaryRoute?.geometry()?.let { geometry ->
            val points = PolylineUtils.decode(geometry, com.mappls.sdk.services.utils.Constants.PRECISION_6)
            if (points.size >= 2) {
                val loc1 = android.location.Location("").apply {
                    latitude = points[0].latitude()
                    longitude = points[0].longitude()
                }
                val loc2 = android.location.Location("").apply {
                    latitude = points[1].latitude()
                    longitude = points[1].longitude()
                }
                routeBearing = loc1.bearingTo(loc2).toDouble()
                startLat = points[0].latitude()
                startLng = points[0].longitude()
            }
        }

        // Fallback to current location or selected origin if route geometry failed
        if (startLat == null || startLng == null) {
            if (originPlace != null) {
                startLat = originPlace?.latitude
                startLng = originPlace?.longitude
            } else {
                mapplsMap?.locationComponent?.lastKnownLocation?.let { loc ->
                    startLat = loc.latitude
                    startLng = loc.longitude
                }
            }
        }

        // 2. Set Map Padding to shift "puck center" downwards for better forward visibility
        // This ensures the origin is in the lower portion of the screen (recessed into horizon)
        val mapHeight = findViewById<View>(R.id.map_view).height
        if (mapHeight > 0) {
            mapplsMap?.setPadding(0, (mapHeight * 0.45).toInt(), 0, 0)
        }

        // 3. Configure Camera for immersive 3D navigation perspective
        if (startLat != null && startLng != null) {
            val cameraPosition = com.mappls.sdk.maps.camera.CameraPosition.Builder()
                .target(com.mappls.sdk.maps.geometry.LatLng(startLat, startLng))
                .zoom(18.5)   // Street-level detail
                .tilt(60.0)   // Maximum immersive 3D tilt
                .bearing(routeBearing) // Facing direction of travel
                .build()

            mapplsMap?.animateCamera(
                com.mappls.sdk.maps.camera.CameraUpdateFactory.newCameraPosition(cameraPosition),
                1200, // Smooth transition to navigation view
                object : com.mappls.sdk.maps.MapplsMap.CancelableCallback {
                    override fun onCancel() {}
                    override fun onFinish() {
                        // After animation, lock tracking if it's current location
                        if (isOriginCurrentLocation) {
                            isFollowMode = true
                            mapplsMap?.locationComponent?.cameraMode = com.mappls.sdk.maps.location.modes.CameraMode.TRACKING_GPS
                        } else {
                            isFollowMode = false
                            mapplsMap?.locationComponent?.cameraMode = com.mappls.sdk.maps.location.modes.CameraMode.NONE
                        }
                    }
                }
            )
        }

        // Hide views not needed during active navigation
        findViewById<View>(R.id.directions_header_card)?.visibility = View.GONE
        findViewById<View>(R.id.directions_actions_layout)?.visibility = View.GONE
        findViewById<View>(R.id.search_card)?.visibility = View.GONE
        findViewById<View>(R.id.bottom_navigation)?.visibility = View.GONE
        findViewById<View>(R.id.trip_briefing_card)?.visibility = View.GONE
        findViewById<View>(R.id.directions_bottom_panel)?.visibility = View.GONE // completely hide old panel

        // Show our new Navigation Top and Bottom panels
        findViewById<View>(R.id.nav_active_top_panel)?.visibility = View.VISIBLE
        val navBottomBar = findViewById<android.view.View>(R.id.nav_active_bottom_bar)
        navBottomBar?.visibility = android.view.View.VISIBLE
        // Re-apply curved bg AFTER layout pass so view has real width/height
        navBottomBar?.post { setupNavBottomBarCurvedBg() }

        // Seed header immediately with last known location (before first GPS tick arrives)
        mapplsMap?.locationComponent?.lastKnownLocation?.let { updateNavActiveHeader(it) }

        // Fix 2: Show hazard pill immediately in SAFE state when navigation starts
        // (never leaves it invisible/missing on nav start)
        val pill = findViewById<android.view.ViewGroup>(R.id.hazard_alert_pill)
        if (pill != null) {
            pill.visibility = View.VISIBLE
            val bg = android.graphics.drawable.GradientDrawable()
            bg.cornerRadius = 100f
            bg.setColor(android.graphics.Color.parseColor("#00C853"))
            bg.setStroke(3, android.graphics.Color.BLACK)
            pill.background = bg
            findViewById<android.widget.ImageView>(R.id.iv_hazard_pill_icon)?.let {
                it.setImageResource(R.drawable.ic_hazard_waze)
                it.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.BLACK)
            }
            findViewById<android.widget.TextView>(R.id.tv_hazard_pill_type)?.apply {
                text = "SAFE"
                setTextColor(android.graphics.Color.BLACK)
            }
            findViewById<android.widget.TextView>(R.id.tv_hazard_pill_dist)?.text = ""
        }

        // Set up the Active Bottom Bar actions
        findViewById<View>(R.id.btn_nav_active_menu)?.setOnClickListener {
            toggleMusicTray()
        }

        findViewById<View>(R.id.btn_nav_active_search)?.setOnClickListener {
            showSearchOverlay(null)
        }
        findViewById<View>(R.id.task_indicator_view)?.setOnClickListener {
            showNoteBottomSheet()
        }
        findViewById<View>(R.id.btn_nav_active_exit)?.setOnClickListener {
            closeDirectionsUI()
        }
        
        // Setup hazard banner dismiss
        findViewById<View>(R.id.btn_dismiss_hazard_banner)?.setOnClickListener {
            findViewById<View>(R.id.nav_hazard_banner)?.visibility = View.GONE
        }

        // FAB visibility handled by FabLayoutManager.prepareStartPageTransition()
        // START_NAVIGATION state: only speedWidget visible, fabStack+voice GONE
        findViewById<View>(R.id.btn_toggle_osm)?.visibility = View.GONE
        findViewById<View>(R.id.fab_compass)?.visibility = View.GONE
        
        // Restore the blue floating recenter button
        findViewById<View>(R.id.fab_recenter)?.apply {
            visibility = View.VISIBLE
            bringToFront()
        }

        findViewById<View>(R.id.speed_limit_widget)?.apply {
            visibility = View.VISIBLE
            translationY = 0f
            bringToFront()
        }

        // Adjust map margin to fill bottom bar space and make room for opaque top header
        val mapParams = findViewById<View>(R.id.map_view).layoutParams as? ViewGroup.MarginLayoutParams
        mapParams?.let {
            it.topMargin = (165 * resources.displayMetrics.density).toInt() // Clear space for top active nav panel
            it.bottomMargin = 0 // Nav bar floats over map — no black strip behind curved corners
            findViewById<View>(R.id.map_view).layoutParams = it
        }

        // Hide old big red stop button
        findViewById<View>(R.id.btn_stop_navigation)?.visibility = View.GONE

        // DEBUG: Log FAB positions after startNavigation
        val fabDbgN = findViewById<View>(R.id.fab_stack_container)
        val fabPN = fabDbgN?.layoutParams as? RelativeLayout.LayoutParams
        Log.w("FAB_DEBUG", "startNavigation -> fabStack: tY=${fabDbgN?.translationY}, above=${fabPN?.getRule(RelativeLayout.ABOVE)}, alignBot=${fabPN?.getRule(RelativeLayout.ALIGN_PARENT_BOTTOM)}")
        Log.w("FAB_DEBUG", "startNavigation -> aiVoice: tY=${findViewById<View>(R.id.fab_ai_voice)?.translationY}, speed: tY=${findViewById<View>(R.id.speed_limit_widget)?.translationY}")
        Log.w("FAB_DEBUG", "startNavigation -> bottomNav vis=${findViewById<View>(R.id.bottom_navigation)?.visibility}")


        // Trigger immediate hazard proximity check
        mapplsMap?.locationComponent?.lastKnownLocation?.let { loc ->
            checkHazardProximity(loc.latitude, loc.longitude)
        }
    }
    
    private fun showNoteBottomSheet() {
        val loc = mapplsMap?.locationComponent?.lastKnownLocation
        val lat = loc?.latitude ?: 0.0
        val lon = loc?.longitude ?: 0.0
        // Pass route geometry so DriverTasksManager can search along the full
        // path (not just a fixed radius around current position).
        val routePts = currentRoutePoints.map { Pair(it.latitude(), it.longitude()) }
        driverTasksManager?.show(lat, lon, routePts)
    }
    
    private fun closeDirectionsUI() {
        hideMusicTray()
        originPlace = null // Reset origin state locally when UI closes
        isOriginCurrentLocation = true
        isNavigating = false
        currentStepIndex = 0
        isPreviewMode = false
        isMapTilted = false
        isFollowMode = true // Restore follow mode for map home
        // Use FabLayoutManager to reset FAB state to Home
        fabLayoutManager.setPageState(PageState.HOME_EXPLORE)
        fabLayoutManager.forceUpdateLayout()
        // Reset to default blue dot marker
        resetToDefaultLocationMarker()
        // Reset map to flat view, reset padding, and start tracking again
        mapplsMap?.setPadding(0, 0, 0, 0)
        mapplsMap?.animateCamera(com.mappls.sdk.maps.camera.CameraUpdateFactory.tiltTo(0.0), 300)
        mapplsMap?.locationComponent?.cameraMode = com.mappls.sdk.maps.location.modes.CameraMode.TRACKING_GPS
        
        // Restore UI
        findViewById<View>(R.id.search_card).visibility = View.VISIBLE
        findViewById<View>(R.id.bottom_navigation)?.visibility = View.VISIBLE
        findViewById<View>(R.id.nav_active_top_panel)?.visibility = View.GONE
        findViewById<View>(R.id.nav_active_bottom_bar)?.visibility = View.GONE
        findViewById<View>(R.id.nav_hazard_banner)?.visibility = View.GONE
        findViewById<View>(R.id.hazard_alert_pill)?.visibility = View.GONE
        alertedHazardIds.clear()       // reset so alerts fire fresh on next navigation session
        hazardPillSpokenIds.clear()
        taskPillSpokenIds.clear()
        driverTasksManager?.dismiss()

        // Restore map margins
        val mapParams = findViewById<View>(R.id.map_view).layoutParams as? ViewGroup.MarginLayoutParams
        mapParams?.let {
            it.topMargin = 0
            it.bottomMargin = (56 * resources.displayMetrics.density).toInt() // Required room for bottom nav bar natively
            findViewById<View>(R.id.map_view).layoutParams = it
        }

        // Reset FAB translationY — XML anchors (ALIGN_PARENT_BOTTOM) are already correct
        // Animate FABs smoothly back to XML-anchored positions
        listOf(R.id.fab_stack_container, R.id.fab_ai_voice, R.id.speed_limit_widget).forEach { id ->
            findViewById<View>(id)?.animate()?.translationY(0f)?.setDuration(250)?.start()
        }

        // Restore bottom panel position and background
        val panel = findViewById<View>(R.id.directions_bottom_panel)
        val panelParams = panel.layoutParams as? RelativeLayout.LayoutParams
        panelParams?.let {
            it.removeRule(RelativeLayout.ALIGN_PARENT_TOP)
            it.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            panel.layoutParams = it
        }
        panel.setBackgroundResource(R.drawable.bg_nav_card_bottom)

        // Restore hazard toggle, compass, AI voice and speed widget
        findViewById<View>(R.id.btn_toggle_osm)?.visibility = View.VISIBLE
        findViewById<View>(R.id.fab_compass)?.visibility = View.VISIBLE
        findViewById<View>(R.id.speed_limit_widget)?.visibility = View.VISIBLE
        // Restore drag handle and collapse button
        findViewById<View>(R.id.panel_drag_handle)?.visibility = View.VISIBLE
        findViewById<View>(R.id.btn_collapse_panel)?.visibility = View.VISIBLE

        // Hide navigation panels and buttons
        findViewById<View>(R.id.directions_header_card).visibility = View.GONE
        findViewById<View>(R.id.directions_bottom_panel).visibility = View.GONE
        findViewById<View>(R.id.directions_actions_layout)?.visibility = View.VISIBLE
        findViewById<View>(R.id.btn_stop_navigation)?.visibility = View.GONE
        findViewById<View>(R.id.bottom_navigation)?.visibility = View.VISIBLE

        // Clear route
        lineManager?.clearAll()
        mapplsMap?.clear()
        clearRouteHazards()

        // Reset state
        currentRoute = null
        currentPrimaryRoute = null
        isNavigating = false
        isPreviewMode = false
        alertedHazardIds.clear()       // reset so alerts fire fresh on next navigation session
        hazardPillSpokenIds.clear()
        taskPillSpokenIds.clear()

        // Important: Re-show place details sheet when coming back from directions
        selectedPlace?.let { place ->
            showPlaceDetailsBottomSheet(place)
        }
    }
    
    private fun showPlaceDetailsBottomSheet(location: ELocation) {
        // Hide FABs while place details sheet is open
        val fabIds = listOf(R.id.fab_ai_voice, R.id.fab_stack_container, R.id.speed_limit_widget)
        fabIds.forEach { id -> findViewById<View>(id)?.visibility = View.GONE }

        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        var isNavigatingFromSheet = false
        
        dialog.setOnDismissListener {
            if (!isNavigatingFromSheet) {
                selectedPlace = null
                selectedELoc = null
                // Restore correct home layout via FabLayoutManager
                // Avoids repositionFabsAbovePanel() pushing FABs to center
                // while the sheet dismiss animation still has nonzero height
                fabLayoutManager.setPageState(PageState.HOME_EXPLORE, immediate = true)
            }
        }
        
        val view = layoutInflater.inflate(R.layout.fragment_location_details, null)
        
        view.findViewById<TextView>(R.id.tv_detail_title).text = location.placeName ?: "Unknown Place"
        view.findViewById<TextView>(R.id.tv_detail_type).text = location.orderIndex?.toString() ?: location.type ?: "Location"
        view.findViewById<TextView>(R.id.tv_detail_address).text = "📍 ${location.placeAddress ?: "Address not available"}"
        
        val distanceView = view.findViewById<TextView>(R.id.tv_detail_distance)
        if (location.distance != null) {
            distanceView.text = "📏 ${location.distance!!.toInt()}m away"
            distanceView.visibility = View.VISIBLE
        } else {
            distanceView.visibility = View.GONE
        }
        
        view.findViewById<View>(R.id.btn_detail_directions).setOnClickListener {
            // Set flags BEFORE dismiss to ensure onDismissListener sees them
            isNavigatingFromSheet = true
            isPreviewMode = true // Force Start Page mode
            originPlace = null 
            isOriginCurrentLocation = true
            getDirections()
            dialog.dismiss()
        }
        
        view.findViewById<View>(R.id.btn_detail_start).setOnClickListener {
            // Set flags BEFORE dismiss to ensure onDismissListener sees them
            isNavigatingFromSheet = true
            isNavigating = true // Force active navigation
            originPlace = null 
            isOriginCurrentLocation = true
            getDirections(autoStart = true)
            dialog.dismiss()
        }
        
        view.findViewById<View>(R.id.btn_detail_save).setOnClickListener {
            dialog.dismiss()
            Toast.makeText(this@MainActivity, "${location.placeName} saved!", Toast.LENGTH_SHORT).show()
        }
        
        dialog.setContentView(view)
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

    // Switch to a selected route: redraw, update hazards and UI
    private fun switchToRoute(selectedRoute: DirectionsRoute) {
        // Block route switching during active navigation (start page)
        if (isNavigating) return

        // If switching route while in navigation mode, undo startNavigation() layout changes
        if (false) { // Dead code — kept for reference
            isNavigating = false
            isPreviewMode = true
            // Restore bottom navigation bar (startNavigation hides it)
            findViewById<View>(R.id.bottom_navigation)?.visibility = View.VISIBLE
            // Hide navigation-only widgets
            findViewById<View>(R.id.speed_limit_widget)?.visibility = View.GONE
            findViewById<View>(R.id.btn_stop_navigation)?.visibility = View.GONE
            // Reset FAB translationY — XML anchors are already correct
            listOf(R.id.fab_stack_container, R.id.fab_ai_voice, R.id.speed_limit_widget).forEach { id ->
                findViewById<View>(id)?.translationY = 0f
            }
            // Restore map bottom margin (startNavigation sets it to 0)
            val mapParams = findViewById<View>(R.id.map_view).layoutParams as? ViewGroup.MarginLayoutParams
            mapParams?.let {
                it.bottomMargin = (56 * resources.displayMetrics.density).toInt()
                findViewById<View>(R.id.map_view).layoutParams = it
            }
        }
        currentPrimaryRoute = selectedRoute
        drawRoute(currentRoute?.routes(), selectedRoute)
        extractHazardsFromRoute(selectedRoute.legs()?.get(0)?.steps())
        showDirectionsUI(selectedRoute.distance()!!, selectedRoute.duration()!!)
        showTripBriefingScanning()  // Show briefing card in scanning state
    }

    // Returns the perpendicular (or endpoint) distance in meters from 'point' to the segment [a, b]
    private fun distanceToSegmentMeters(
        point: com.mappls.sdk.maps.geometry.LatLng,
        a: com.mappls.sdk.maps.geometry.LatLng,
        b: com.mappls.sdk.maps.geometry.LatLng
    ): Double {
        // Convert to a rough flat-earth coordinate system (meters)
        val mPerLatDeg = 111_320.0
        val mPerLngDeg = 111_320.0 * Math.cos(Math.toRadians(a.latitude))

        val px = (point.longitude - a.longitude) * mPerLngDeg
        val py = (point.latitude  - a.latitude)  * mPerLatDeg
        val dx = (b.longitude - a.longitude) * mPerLngDeg
        val dy = (b.latitude  - a.latitude)  * mPerLatDeg

        val lenSq = dx * dx + dy * dy
        if (lenSq == 0.0) return Math.hypot(px, py)

        val t = ((px * dx + py * dy) / lenSq).coerceIn(0.0, 1.0)
        val nearX = px - t * dx
        val nearY = py - t * dy
        return Math.hypot(nearX, nearY)
    }

    private fun drawRoute(routes: List<DirectionsRoute>?, primaryRoute: DirectionsRoute? = null) {
        if (routes.isNullOrEmpty()) return
        
        lineManager?.clearAll()
        symbolManager?.clearAll()
        
        // Remove previous route metadata markers (labels, destination pin)
        mapplsMap?.let { map ->
            map.markers.filter { routeMetaMarkerIds.contains(it.id) }.forEach { 
                map.removeMarker(it) 
            }
        }
        routeMetaMarkerIds.clear()
        markerToRouteMap.clear()
        
        clearRouteHazards()  // Clear previous hazard points/markers/jobs
        clearTaskRouteMarkers()   // Clear previous task overlay markers
        
        // Use provided primary route or the shortest one as default if null
        val actualPrimaryRoute = primaryRoute ?: routes.minByOrNull { it.distance() ?: Double.MAX_VALUE } ?: routes[0]
        
        // Sort routes: draw alternatives (gray) first, then primary (blue) last (on top)
        val sortedRoutes = routes.toMutableList()
        sortedRoutes.remove(actualPrimaryRoute)
        sortedRoutes.add(actualPrimaryRoute) 
        
        sortedRoutes.forEachIndexed { index, route ->
            val isPrimary = route == actualPrimaryRoute
            val geometry = route.geometry() ?: return@forEachIndexed
            
            val points = PolylineUtils.decode(geometry, Constants.PRECISION_6)
            val latLngs = points.map { com.mappls.sdk.maps.geometry.LatLng(it.latitude(), it.longitude()) }
            
            val lineOptions = LineOptions()
                .points(latLngs)
                .lineColor(if (isPrimary) "#00B0FF" else "#A9A9A9") 
                .lineWidth(if (isPrimary) 8f else 6f)
                .lineOpacity(if (isPrimary) 1.0f else 0.8f)
            
            lineManager?.create(lineOptions)
            
            // Store this route's geometry for tap-detection
            allRouteGeometries[route] = latLngs
            
            // Add duration label at the midpoint (varying index slightly)
            if (latLngs.size > 10) {
                val offset = 0.35 + (index.toDouble() / routes.size) * 0.2
                val labelIndex = (latLngs.size * offset).toInt()
                val labelPoint = latLngs[labelIndex]
                
                val labelMarkerOptions = MarkerOptions()
                    .position(labelPoint)
                    .icon(com.mappls.sdk.maps.annotations.IconFactory.getInstance(this)
                        .fromBitmap(createRouteLabelBitmap(route.duration()!!, isPrimary)))
                
                val marker = mapplsMap?.addMarker(labelMarkerOptions)
                if (marker != null) {
                    markerToRouteMap[marker.id] = route
                    routeMetaMarkerIds.add(marker.id)
                }
            }
            
            // Add markers ONLY for the primary route (Red/Green pins)
            if (isPrimary && latLngs.size > 1) {
                val endPoint = latLngs.last()
                val endMarkerOptions = MarkerOptions()
                    .position(endPoint)
                    .title("Destination")
                    .icon(com.mappls.sdk.maps.annotations.IconFactory.getInstance(this)
                        .fromBitmap(createRouteMarkerBitmap(false)))
                
                val endMarker = mapplsMap?.addMarker(endMarkerOptions)
                if (endMarker != null) routeMetaMarkerIds.add(endMarker.id)
                
                // Only add green origin pin if origin is a custom place (not current location)
                // Current location uses the arrow LocationComponent marker instead
                if (!isOriginCurrentLocation) {
                    val startPoint = latLngs.first()
                    val startMarkerOptions = MarkerOptions()
                        .position(startPoint)
                        .title("Origin")
                        .icon(com.mappls.sdk.maps.annotations.IconFactory.getInstance(this)
                            .fromBitmap(createRouteMarkerBitmap(true)))
                    val startMarker = mapplsMap?.addMarker(startMarkerOptions)
                    if (startMarker != null) routeMetaMarkerIds.add(startMarker.id)
                }
                // Fetch hazards ONLY for the primary route
                if (isPrimary) {
                    fetchHazardsAlongRouteProgressive(points)
                    addTaskMarkersOnRoute()
                }
            }
        }
    }
    
    private fun fetchHazardsAlongRouteProgressive(routePoints: List<Point>) {
        if (routePoints.isEmpty()) {
            Log.d("Zwap", "No route points to fetch hazards for")
            return
        }
        
        // Cancel any existing fetch job
        hazardFetchJob?.cancel()
        // Calculate total route distance
        totalRouteDistanceMeters = 0.0
        val points = mutableListOf<Point>()
        routePoints.forEach { points.add(it) }
        currentRoutePoints = points
        currentHazardFetchIndex = 0
        
        for (i in 0 until points.size - 1) {
            totalRouteDistanceMeters += calculateDistance(
                points[i].latitude(), points[i].longitude(),
                points[i + 1].latitude(), points[i + 1].longitude()
            )
        }
        
        if (totalRouteDistanceMeters == 0.0) {
            Log.w("Zwap", "Route distance is 0, setting to 1 to avoid div by zero")
            totalRouteDistanceMeters = 1.0
        }
        
        val destPt = points.last()
        Log.d("HazardScan", "=== ROUTE SCAN START ===")
        Log.d("HazardScan", "Total points: ${points.size}, distance: ${(totalRouteDistanceMeters / 1000).toInt()} km")
        Log.d("HazardScan", "DESTINATION: lat=${destPt.latitude()}, lon=${destPt.longitude()}")
        
        // Show progress indicator
        runOnUiThread {
            isScanningHazards = true
            findViewById<View>(R.id.hazard_scan_progress_container)?.visibility = View.VISIBLE
            updateHazardScanProgress(0)
        }
        
        // Start fetching in 50km chunks
        hazardFetchJob = lifecycleScope.launch(Dispatchers.IO) {
            fetchNextHazardChunk()
        }
    }
    
    private fun updateHazardScanProgress(percent: Int) {
        if (!isScanningHazards && percent < 100) isScanningHazards = true
        runOnUiThread {
            findViewById<android.widget.ProgressBar>(R.id.hazard_scan_progress)?.progress = percent
            findViewById<android.widget.TextView>(R.id.tv_hazard_scan_percent)?.text = "$percent%"
            
            // Also update the navigation header circle if visible
            findViewById<android.widget.ProgressBar>(R.id.progress_battery)?.progress = percent
            findViewById<android.widget.TextView>(R.id.tv_active_battery_pct)?.text = "$percent%"
            
            // Set label to HAZARD while scanning
            findViewById<android.widget.TextView>(R.id.tv_battery_label)?.text = "HAZARD"
        }
    }
    
    private fun hideHazardScanProgress() {
        isScanningHazards = false
        runOnUiThread {
            findViewById<View>(R.id.hazard_scan_progress_container)?.visibility = View.GONE
            // Set label back to BATTERY
            findViewById<android.widget.TextView>(R.id.tv_battery_label)?.text = "BATTERY"
        }
        
        // Re-update battery display once scanning is done
        val batteryIntent = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level != -1 && scale != -1) {
            updateBatteryDisplay((level * 100 / scale.toFloat()).toInt())
        }
    }

    /** Show the Trip Briefing card in "Scanning..." state immediately when route loads */
    private fun showTripBriefingScanning() {
        val card = findViewById<View>(R.id.trip_briefing_card) ?: return
        val title = findViewById<TextView>(R.id.tv_trip_briefing_title) ?: return
        val detail = findViewById<TextView>(R.id.tv_trip_briefing_detail) ?: return

        title.text = "Scanning route..."
        detail.text = "Checking for cameras and hazards ahead"
        
        card.visibility = View.VISIBLE
        card.alpha = 0f
        card.animate().alpha(1f).setDuration(300).start()
        repositionFabsAbovePanel()
    }

    /** Update the Trip Briefing card with the real hazard count summary */
    private fun showTripBriefingSummary() {
        val card = findViewById<View>(R.id.trip_briefing_card) ?: return
        if (card.visibility != View.VISIBLE) return  // User already dismissed it
        
        val title = findViewById<TextView>(R.id.tv_trip_briefing_title) ?: return
        val detail = findViewById<TextView>(R.id.tv_trip_briefing_detail) ?: return

        // Count hazard types from routeHazardMarkerIds using osmFeatures data
        val cameraCount = routeHazardMarkers.count { 
            osmFeatures.any { f -> it.id == featureToMarkerMap[f.id]?.id && f.type == FeatureType.SPEED_CAMERA }
        }
        val hazardCount = routeHazardMarkers.count { 
            osmFeatures.any { f -> it.id == featureToMarkerMap[f.id]?.id && f.type != FeatureType.SPEED_CAMERA }
        }
        val totalHazards = routeHazardMarkers.size

        if (totalHazards == 0) {
            title.text = "🟢 Route Clear"
            detail.text = "No cameras or hazards reported on this route"
        } else {
            val parts = mutableListOf<String>()
            if (cameraCount > 0) parts.add("$cameraCount speed camera${if (cameraCount > 1) "s" else ""}") 
            if (hazardCount > 0) parts.add("$hazardCount hazard${if (hazardCount > 1) "s" else ""} reported")
            if (parts.isEmpty()) parts.add("$totalHazards alerts")
            title.text = "🚨 Heads Up!"
            detail.text = parts.joinToString(" • ") + " ahead on this route"
        }

        // Auto-dismiss after 8 seconds
        card.postDelayed({
            if (card.isAttachedToWindow) {
                card.animate().alpha(0f).setDuration(400).withEndAction {
                    card.visibility = View.GONE
                }.start()
            }
        }, 8000)
    }
    
    private suspend fun fetchNextHazardChunk() {
        if (!(isNavigating || isPreviewMode) || currentRoutePoints.isEmpty()) {
            Log.d("HazardScan", "SCAN STOPPED — isNav=$isNavigating isPreview=$isPreviewMode pts=${currentRoutePoints.size} idx=$currentHazardFetchIndex")
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
            Log.d("HazardScan", "=== ALL CHUNKS DONE — final index ${currentHazardFetchIndex}/${currentRoutePoints.size-1} ===")
            withContext(Dispatchers.Main) {
                updateHazardScanProgress(100)
                // Hide after a brief delay to show 100%
                kotlinx.coroutines.MainScope().launch {
                    kotlinx.coroutines.delay(1500)
                    hideHazardScanProgress()
                    showTripBriefingSummary()
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
        
        withContext(Dispatchers.Main) {
            updateHazardScanProgress(progressPercent)
        }
        
        // Run OSM and Community hazard fetches IN PARALLEL for speed
        var osmHazards: List<RouteHazard> = emptyList()
        var communityHazards: List<RouteHazard> = emptyList()
        
        // Compute bbox once — shared by both jobs
        var bMinLat = Double.MAX_VALUE; var bMaxLat = -Double.MAX_VALUE
        var bMinLon = Double.MAX_VALUE; var bMaxLon = -Double.MAX_VALUE
        chunkPoints.forEach { pt ->
            bMinLat = minOf(bMinLat, pt.latitude()); bMaxLat = maxOf(bMaxLat, pt.latitude())
            bMinLon = minOf(bMinLon, pt.longitude()); bMaxLon = maxOf(bMaxLon, pt.longitude())
        }
        val padding = 0.004
        val fMinLat = bMinLat - padding; val fMaxLat = bMaxLat + padding
        val fMinLon = bMinLon - padding; val fMaxLon = bMaxLon + padding
        val centerLat = (fMinLat + fMaxLat) / 2; val centerLon = (fMinLon + fMaxLon) / 2
        val latDiff = fMaxLat - fMinLat; val lonDiff = fMaxLon - fMinLon
        val radiusKm = (Math.sqrt(latDiff * latDiff + lonDiff * lonDiff) * 111.0) / 2

        val isLastChunk = endIndex >= currentRoutePoints.size - 1
        Log.d("HazardScan", "--- Chunk $progressPercent% | pts[$startIndex..$endIndex]/${currentRoutePoints.size - 1} | ${(getDistanceToIndex(endIndex)/1000).toInt()}km | lastChunk=$isLastChunk")
        Log.d("HazardScan", "  bbox lat[${String.format("%.4f", fMinLat)}..${String.format("%.4f", fMaxLat)}] lon[${String.format("%.4f", fMinLon)}..${String.format("%.4f", fMaxLon)}]")
        if (isLastChunk) {
            val destPt = currentRoutePoints.last()
            Log.d("HazardScan", "  DESTINATION lat=${destPt.latitude()} lon=${destPt.longitude()} — covered in this chunk")
        }

        kotlinx.coroutines.coroutineScope {
            // ── 1. OSM Overpass (fired simultaneously)
            val osmJob = async(Dispatchers.IO) {
                var result: List<RouteHazard> = emptyList()
                val overpassMirrors = listOf(
                    "https://overpass-api.de/api/interpreter",
                    "https://overpass.kumi.systems/api/interpreter",
                    "https://maps.mail.ru/osm/tools/overpass/api/interpreter"
                )
                val query = """
                    [out:json][timeout:10];
                    (
                      node["highway"="speed_camera"]($fMinLat,$fMinLon,$fMaxLat,$fMaxLon);
                      node["enforcement"="speed"]($fMinLat,$fMinLon,$fMaxLat,$fMaxLon);
                      node["hazard"]($fMinLat,$fMinLon,$fMaxLat,$fMaxLon);
                      node["traffic_calming"]($fMinLat,$fMinLon,$fMaxLat,$fMaxLon);
                      node["barrier"="toll_booth"]($fMinLat,$fMinLon,$fMaxLat,$fMaxLon);
                    );
                    out body;
                """.trimIndent()
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                for ((idx, mirror) in overpassMirrors.withIndex()) {
                    try {
                        val response = client.newCall(
                            okhttp3.Request.Builder()
                                .url(mirror)
                                .post(query.toRequestBody("text/plain".toMediaTypeOrNull()))
                                .build()
                        ).execute()
                        val body = response.body?.string()
                        when {
                            response.isSuccessful && body != null -> {
                                result = parseOSMHazards(body, chunkPoints)
                                Log.d("HazardScan", "  OSM[$idx]: ${result.size} hazards (${result.groupBy{it.type}.map{"${it.key.name}x${it.value.size}"}.joinToString(",")})")
                                break
                            }
                            response.code == 429 || response.code == 504 -> {
                                Log.w("HazardScan", "  OSM[$idx] ${response.code} — trying next mirror")
                                // no delay: next mirror immediately
                            }
                            else -> {
                                Log.w("HazardScan", "  OSM[$idx] failed ${response.code}")
                                break
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("HazardScan", "  OSM[$idx] ERROR: ${e.javaClass.simpleName}: ${e.message}")
                    }
                }
                result
            }

            // ── 2. Community hazards (our own backend — faster)
            val communityJob = async(Dispatchers.IO) {
                var result: List<RouteHazard> = emptyList()
                try {
                    val commResponse = kotlinx.coroutines.withTimeoutOrNull(3000L) {
                        com.swapmap.zwap.demo.network.ApiClient.hazardApiService.getHazards(
                            centerLat, centerLon, radiusKm
                        )
                    }
                    if (commResponse?.isSuccessful == true && commResponse.body() != null) {
                        result = commResponse.body()!!.hazards.map { cluster ->
                            RouteHazard(
                                lat = cluster.latitude, lon = cluster.longitude,
                                type = if (cluster.status == "NEEDS_REVALIDATION")
                                    HazardType.COMMUNITY_REVALIDATE else HazardType.COMMUNITY_VERIFIED_HAZARD,
                                name = "${cluster.hazard_type.replace("_", " ")} (${cluster.verified_image_count} reports)"
                            )
                        }
                        Log.d("HazardScan", "  Community: ${result.size} hazards")
                    } else if (commResponse == null) {
                        Log.w("Zwap", "Community hazard fetch timed out (server unreachable)")
                    }
                } catch (e: Exception) {
                    Log.e("Zwap", "Community fetch error: ${e.message}")
                }
                result
            }

            // Show community hazards IMMEDIATELY as they arrive (fast path)
            communityHazards = communityJob.await()
            if (communityHazards.isNotEmpty() && (isNavigating || isPreviewMode)) {
                withContext(Dispatchers.Main) { displayRouteHazards(communityHazards) }
            }

            // Merge with OSM when done
            osmHazards = osmJob.await()
        }

        // Display merged final results
        val allHazards = (osmHazards + communityHazards)
            .distinctBy { "${(it.lat * 1000).toInt()}_${(it.lon * 1000).toInt()}_${it.type}" }
        if ((isNavigating || isPreviewMode) && allHazards.isNotEmpty()) {
            withContext(Dispatchers.Main) { displayRouteHazards(allHazards) }
        }

        
        // Update index for next chunk and continue
        currentHazardFetchIndex = endIndex
        
        // Minimal delay between chunks to be respectful to the API
        kotlinx.coroutines.delay(100)
        
        // Fetch next chunk if still navigating
        if ((isNavigating || isPreviewMode) && currentHazardFetchIndex < currentRoutePoints.size - 1) {
            fetchNextHazardChunk()
        } else if (isNavigating || isPreviewMode) {
            // Completed scanning  
            withContext(Dispatchers.Main) {
                updateHazardScanProgress(100)
                kotlinx.coroutines.MainScope().launch {
                    kotlinx.coroutines.delay(1500)
                    hideHazardScanProgress()
                    showTripBriefingSummary()
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
        if (!(isNavigating || isPreviewMode)) return
        // Show hazard markers during both preview and active navigation
        
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
                
                marker?.let { 
                    routeHazardMarkers.add(it)
                    Log.d("Zwap", "SUCCESSfully added ${hazard.type} marker at (${hazard.lat}, ${hazard.lon}) - total: ${routeHazardMarkers.size}")
                }
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
                
                // Dedup: skip if an existing OSMFeature is already within 50m (avoids double-speak)
                val isDuplicate = osmFeatures.any { existing ->
                    val r = FloatArray(1)
                    android.location.Location.distanceBetween(
                        existing.lat, existing.lon, osmFeature.lat, osmFeature.lon, r)
                    r[0] < 50f
                }
                if (!isDuplicate) osmFeatures.add(osmFeature)
                marker?.let { featureToMarkerMap[osmFeature.id] = it }
                
                Log.d("Zwap", "Added OSM-backed route hazard alert for ${hazard.type} (dup=$isDuplicate)")
            } catch (e: Exception) {
                Log.e("Zwap", "Error adding hazard marker: ${e.message}")
            }
        }
        
        // Update the visual pill immediately; TTS alert fires via the location listener
        if (hazards.isNotEmpty()) {
            val currentLocation = mapplsMap?.locationComponent?.lastKnownLocation
            if (currentLocation != null) {
                updateHazardAlertPill(currentLocation.latitude, currentLocation.longitude)
                Log.d("Zwap", "Route hazards added: ${hazards.size} — pill updated")
            }
        }
    }
    
    private fun createHazardMarkerBitmap(type: HazardType): Bitmap {
        val size = 64
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgColor = when (type) {
            HazardType.SPEED_CAMERA -> Color.parseColor("#2196F3")           // Blue
            HazardType.HAZARD -> Color.parseColor("#FF9800")                 // Orange
            HazardType.TOLL -> Color.parseColor("#9C27B0")                   // Purple
            HazardType.TRAFFIC_CALMING -> Color.parseColor("#FFC107")        // Yellow
            HazardType.COMMUNITY_VERIFIED_HAZARD -> Color.parseColor("#4CAF50") // Green
            HazardType.COMMUNITY_REVALIDATE -> Color.parseColor("#FFC107")   // Yellow
            else -> Color.parseColor("#F44336")                              // Red
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

        // Draw emoji icon
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
        
        // Hide progress and briefing card
        hideHazardScanProgress()
        runOnUiThread {
            findViewById<View>(R.id.trip_briefing_card)?.visibility = View.GONE
        }
        
        // Clear hazard points data
        routeHazards.clear()
        
        mapplsMap?.let { map ->
            routeHazardMarkers.forEach { marker ->
                map.removeMarker(marker)
            }
        }
        routeHazardMarkers.clear()
        
        // Remove route-specific features from the global proximity alert list
        // and its association map to prevent stale alerts and memory leaks
        osmFeatures.removeAll { it.tags.containsKey("route_hazard") }
        
        // Markers are managed via routeHazardMarkers list
        val hazardIds = routeHazardMarkers.map { it.id }.toSet()
        val iterator = featureToMarkerMap.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (hazardIds.contains(entry.value.id)) {
                iterator.remove()
            }
        }
    }
    
    // Data classes for route hazards
    data class RouteHazard(val lat: Double, val lon: Double, val type: HazardType, val name: String)
    
    private fun updateETADisplay(durationSeconds: Double) {
        try {
            val calendar = java.util.Calendar.getInstance()
            calendar.add(java.util.Calendar.SECOND, durationSeconds.toInt())
            val etaTime = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(calendar.time)
            findViewById<TextView>(R.id.tv_route_eta)?.text = "ETA $etaTime"
            findViewById<TextView>(R.id.tv_active_eta)?.text = etaTime
            Log.d("Zwap", "Updated ETA display to: $etaTime")
        } catch (e: Exception) {
            Log.e("Zwap", "Error updating ETA", e)
        }
    }
    
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

    private fun createRouteLabelBitmap(duration: Double, isPrimary: Boolean): Bitmap {
        val durationMin = (duration / 60.0).toInt()
        val durationText = if (durationMin >= 60) {
            "${durationMin / 60}h ${durationMin % 60}m"
        } else {
            "$durationMin min"
        }

        val paint = Paint().apply {
            textSize = 28f
            isAntiAlias = true
            color = if (isPrimary) Color.WHITE else Color.BLACK
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val bounds = android.graphics.Rect()
        paint.getTextBounds(durationText, 0, durationText.length, bounds)

        val paddingW = 24
        val paddingH = 16
        val width = bounds.width() + paddingW * 2
        val height = bounds.height() + paddingH * 2

        val bitmap = Bitmap.createBitmap(width, height + 12, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw bubble background
        val bgPaint = Paint().apply {
            color = if (isPrimary) Color.parseColor("#1976D2") else Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
            setShadowLayer(4f, 0f, 2f, Color.parseColor("#40000000"))
        }

        val rectF = android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rectF, 12f, 12f, bgPaint)

        // Draw tiny triangle at bottom
        val path = android.graphics.Path()
        path.moveTo(width / 2f - 10, height.toFloat())
        path.lineTo(width / 2f + 10, height.toFloat())
        path.lineTo(width / 2f, height.toFloat() + 10)
        path.close()
        canvas.drawPath(path, bgPaint)

        // Draw border if not primary
        if (!isPrimary) {
            val borderPaint = Paint().apply {
                color = Color.LTGRAY
                style = Paint.Style.STROKE
                strokeWidth = 2f
                isAntiAlias = true
            }
            canvas.drawRoundRect(rectF, 12f, 12f, borderPaint)
        }

        // Draw text
        canvas.drawText(durationText, paddingW.toFloat(), height / 2f + bounds.height() / 2f, paint)

        return bitmap
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.setLanguage(Locale.US)
    }

    // SensorEventListener implementation for arrow marker rotation
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            val azimuthDegrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
            val newAzimuth = (azimuthDegrees + 360) % 360
            
            // Only update if rotation changed significantly (>5 degrees) to reduce redraws
            if (!isOriginCurrentLocation && kotlin.math.abs(newAzimuth - currentDeviceAzimuth) > 5f) {
                currentDeviceAzimuth = newAzimuth
                updateArrowMarkerRotation()
            }
        }
    }
    
    private fun updateArrowMarkerRotation() {
        originArrowMarker?.let { marker ->
            val position = marker.position
            mapplsMap?.removeMarker(marker)
            
            val arrowDrawable = ContextCompat.getDrawable(this, R.drawable.ic_navigation_arrow_marker) ?: return
            val originalBitmap = Bitmap.createBitmap(
                arrowDrawable.intrinsicWidth,
                arrowDrawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(originalBitmap)
            arrowDrawable.setBounds(0, 0, canvas.width, canvas.height)
            arrowDrawable.draw(canvas)
            
            // Rotate bitmap
            val matrix = Matrix()
            matrix.postRotate(currentDeviceAzimuth, originalBitmap.width / 2f, originalBitmap.height / 2f)
            val rotatedBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
            
            originArrowMarker = mapplsMap?.addMarker(
                MarkerOptions()
                    .position(position)
                    .icon(IconFactory.getInstance(this).fromBitmap(rotatedBitmap))
            )
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for rotation
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            // Upfront all-permissions request on first launch (code 100)
            100 -> {
                val locIdx = permissions.indexOf(Manifest.permission.ACCESS_FINE_LOCATION)
                if (locIdx >= 0 && grantResults.getOrElse(locIdx) { -1 } == PackageManager.PERMISSION_GRANTED) {
                    mapplsMap?.getStyle { enableLocationComponent(it) }
                }
                val audioIdx = permissions.indexOf(android.Manifest.permission.RECORD_AUDIO)
                if (audioIdx >= 0 && grantResults.getOrElse(audioIdx) { -1 } == PackageManager.PERMISSION_GRANTED) {
                    val prefs = getSharedPreferences("zwap_prefs", android.content.Context.MODE_PRIVATE)
                    if (prefs.getBoolean("wake_word_enabled", false)) {
                        startForegroundService(android.content.Intent(this, WakeWordService::class.java))
                    }
                }
            }
            // Location requested from map init flow
            201 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mapplsMap?.getStyle { enableLocationComponent(it) }
                }
            }
        }
    }

    /**
     * Requests every runtime permission the app needs in ONE upfront dialog.
     * Only includes permissions not already granted.
     */
    private fun requestAllAppPermissions() {
        val needed = mutableListOf<String>()
        fun need(perm: String) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, perm)
                != PackageManager.PERMISSION_GRANTED) needed.add(perm)
        }
        need(Manifest.permission.ACCESS_FINE_LOCATION)
        need(Manifest.permission.ACCESS_COARSE_LOCATION)
        need(android.Manifest.permission.CAMERA)
        need(android.Manifest.permission.RECORD_AUDIO)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            need(android.Manifest.permission.READ_MEDIA_IMAGES)
            need(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            @Suppress("DEPRECATION")
            need(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 100)
        }
    }

    /**
     * Creates all system notification channels used by Zwap.
     * Must be called before any notify() call.
     * Safe to call multiple times — Android ignores duplicate channel creation.
     */
    private fun createAppNotificationChannels() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        val nm = getSystemService(android.app.NotificationManager::class.java) ?: return

        // zwap_alerts: high-importance heads-up for nearby hazard alerts (create once)
        if (nm.getNotificationChannel("zwap_alerts") == null) {
            nm.createNotificationChannel(
                android.app.NotificationChannel(
                    "zwap_alerts",
                    "Hazard Alerts",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Heads-up banners for nearby road hazards"
                    enableVibration(true)
                    enableLights(true)
                    lightColor = android.graphics.Color.parseColor("#FF9800")
                }
            )
        }

        // zwap_community: community/Firestore notifications (create once)
        if (nm.getNotificationChannel("zwap_community") == null) {
            nm.createNotificationChannel(
                android.app.NotificationChannel(
                    "zwap_community",
                    "Community & Rewards",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Verification results, rewards and community activity"
                    enableVibration(true)
                }
            )
        }

        // zwap_navigation: silent persistent notification while navigating (create once)
        if (nm.getNotificationChannel("zwap_navigation") == null) {
            nm.createNotificationChannel(
                android.app.NotificationChannel(
                    "zwap_navigation",
                    "Navigation",
                    android.app.NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Ongoing navigation status"
                    setSound(null, null)
                }
            )
        }
    }

    /**
     * Returns a PendingIntent that brings MainActivity to the foreground when tapped.
     * Used as setContentIntent on all app notifications.
     */
    private fun buildMainPendingIntent(): android.app.PendingIntent {
        val intent = android.content.Intent(this, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        else
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
        return android.app.PendingIntent.getActivity(this, 0, intent, flags)
    }

    /**
     * Posts a system-level (status-bar) notification for a hazard alert.
     * Uses the zwap_alerts channel; auto-cancels after 15s.
     */
    private fun sendHazardNotification(title: String, subtitle: String, featureId: Long) {
        Log.d("Zwap", "sendHazardNotification: called title=$title id=$featureId")

        // Check POST_NOTIFICATIONS permission (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w("Zwap", "sendHazardNotification: POST_NOTIFICATIONS permission denied — skipping")
            return
        }

        val notifId = (featureId % Int.MAX_VALUE).toInt().let { if (it < 0) -it else it } + 9000

        // Use a system built-in icon — custom drawables with color cause silent failures
        // on many OEMs (notification small icons must be white/transparent only)
        val iconRes = android.R.drawable.ic_dialog_alert

        val notification = NotificationCompat.Builder(this, "zwap_alerts")
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setContentIntent(buildMainPendingIntent())
            .setAutoCancel(true)
            .setTimeoutAfter(15_000)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(notifId, notification)
            Log.d("Zwap", "sendHazardNotification: posted id=$notifId channel=zwap_alerts")
        } catch (e: Exception) {
            Log.e("Zwap", "sendHazardNotification: FAILED to post — ${e.message}", e)
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

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
        // Register rotation sensor for arrow marker rotation
        rotationSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        // Register wake-word receiver and re-start service if user has it enabled
        LocalBroadcastManager.getInstance(this).registerReceiver(
            wakeWordReceiver,
            android.content.IntentFilter(WakeWordService.ACTION_WAKE_WORD_DETECTED)
        )
        // Register battery receiver
        registerReceiver(batteryReceiver, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        
        val prefs = getSharedPreferences("zwap_prefs", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean("wake_word_enabled", false)) {
            val audioPerm = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            if (audioPerm == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                startForegroundService(android.content.Intent(this, WakeWordService::class.java))
            } else {
                android.util.Log.w("Zwap", "WakeWordService not started: RECORD_AUDIO permission missing")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
        sensorManager?.unregisterListener(this)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(wakeWordReceiver)
        unregisterReceiver(batteryReceiver)
    }

    override fun onStop() { super.onStop(); mapView?.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView?.onSaveInstanceState(outState) }
    // ── Real-time Firestore notification listener ────────────────────────────
    /**
     * Attaches a snapshot listener to users/{uid}/notifications.
     * Unread docs not yet seen post a system notification on zwap_community.
     */
    private fun startUserNotificationListener(uid: String) {
        userNotifListener?.remove()
        seenNotifIds.clear()
        var isFirstSnapshot = true
        userNotifListener = FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .collection("notifications")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.w("Zwap", "Notification listener error: ${err.message}")
                    return@addSnapshotListener
                }
                if (isFirstSnapshot) {
                    // Seed seenNotifIds with all existing doc IDs so we don't
                    // re-notify for documents that were already in Firestore.
                    snap?.documents?.forEach { doc -> seenNotifIds.add(doc.id) }
                    isFirstSnapshot = false
                    return@addSnapshotListener
                }
                // Only process genuinely new documents added after we attached.
                snap?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val doc = change.document
                        val notif = doc.toObject(com.swapmap.zwap.demo.model.UserNotification::class.java)
                            ?.copy(id = doc.id) ?: return@forEach
                        if (!notif.isRead && notif.id.isNotEmpty() && !seenNotifIds.contains(notif.id)) {
                            seenNotifIds.add(notif.id)
                            sendCommunityNotification(notif.title, notif.message, notif.type, notif.id.hashCode())
                        }
                    }
                }
            }
        Log.d("Zwap", "startUserNotificationListener: attached for uid=$uid")
    }

    /**
     * Posts a system notification on the zwap_community channel.
     * Used for Firestore-sourced user notifications.
     */
    private fun sendCommunityNotification(title: String, message: String, type: String, notifId: Int) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w("Zwap", "sendCommunityNotification: POST_NOTIFICATIONS denied")
            return
        }
        val iconRes = when (type) {
            "reward"       -> android.R.drawable.btn_star
            "verification" -> android.R.drawable.ic_dialog_info
            else           -> android.R.drawable.ic_dialog_email
        }
        val notification = NotificationCompat.Builder(this, "zwap_community")
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(buildMainPendingIntent())
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        try {
            NotificationManagerCompat.from(this).notify(notifId, notification)
            Log.d("Zwap", "sendCommunityNotification: posted id=$notifId type=$type")
        } catch (e: Exception) {
            Log.e("Zwap", "sendCommunityNotification: FAILED — ${e.message}", e)
        }
    }

    /**
     * Posts a silent ongoing notification on zwap_navigation while navigating.
     * Fixed ID 8001 so it updates in place.
     */
    private fun postNavigationActiveNotification() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val notification = NotificationCompat.Builder(this, "zwap_navigation")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("Navigation Active")
            .setContentText("Hazard alerts will appear as they are detected")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(buildMainPendingIntent())
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        try {
            NotificationManagerCompat.from(this).notify(8001, notification)
        } catch (e: Exception) {
            Log.e("Zwap", "postNavigationActiveNotification: FAILED — ${e.message}", e)
        }
    }

    override fun onDestroy() {
        userNotifListener?.remove()
        locationEngine?.removeLocationUpdates(callback)
        tts?.stop(); tts?.shutdown()
        cameraExecutor.shutdown()
        NotificationManagerCompat.from(this).cancel(8001)
        super.onDestroy()
        mapView?.onDestroy()
    }

    private fun initLaunchers() {
        // 1. Camera launcher for AI flow (Manual Mode)
        takeAIPicture = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.TakePicture()
        ) { success ->
            if (success && aiImageFileMain != null) {
                Toast.makeText(this, "⏳ Scanning road for hazards...", Toast.LENGTH_SHORT).show()
                val lat = mapplsMap?.cameraPosition?.target?.latitude  ?: 0.0
                val lng = mapplsMap?.cameraPosition?.target?.longitude ?: 0.0
                sendToAiAndSubmit(aiImageFileMain!!, aiVoiceTranscript, lat, lng)
            }
        }

        // 2. Speech recognizer launcher (kept for fallback)
        speechLauncherMain = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val results = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                if (!results.isNullOrEmpty()) {
                    processVoiceTranscript(results[0])
                }
            }
        }
    }
}
