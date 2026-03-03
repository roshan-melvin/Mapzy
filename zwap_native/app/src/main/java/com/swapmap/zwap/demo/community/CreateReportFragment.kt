package com.swapmap.zwap.demo.community

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.swapmap.zwap.R
import com.swapmap.zwap.demo.model.Report
import com.swapmap.zwap.demo.repository.ReportRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.UUID

class CreateReportFragment : Fragment(R.layout.fragment_create_report) {

    private var selectedUri: Uri? = null
    private var isVideo: Boolean = false
    private val repository = ReportRepository()
    private val auth = FirebaseAuth.getInstance()
    
    // Map Stuff
    private var mapView: com.mappls.sdk.maps.MapView? = null
    private var mapplsMap: com.mappls.sdk.maps.MapplsMap? = null
    private var selectedLatLng: com.mappls.sdk.maps.geometry.LatLng? = null
    
    // AI Variables
    private var voiceTranscript: String = ""
    private var aiImageFile: File? = null

    // Suggestions Map
    private val suggestionsMap = mapOf(
        "#accident" to listOf("Left lane blocked", "Fender bender", "Police on scene", "Traffic backing up"),
        "#construction" to listOf("Road closed", "Detour active", "Workers on road", "Heavy machinery"),
        "#speed-camera" to listOf("Hidden behind tree", "New camera installed", "Not working", "Mobile van"),
        "#pothole" to listOf("Deep pothole, stay right", "Multiple potholes", "Causing traffic", "Car damaged"),
        "#waterlogging" to listOf("Road flooded", "Impassable", "Drive slowly", "Stalled vehicles"),
        "#fallen-tree" to listOf("Blocking whole road", "Blocking right lane", "Power lines down"),
        "#traffic" to listOf("Standstill traffic", "Moving slowly", "Accident ahead")
    )
    
    // Media Picker
    private val pickMedia = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedUri = uri
            val type = requireContext().contentResolver.getType(uri)
            isVideo = type?.startsWith("video") == true
            updateMediaPreview()
        }
    }

    // Voice Recognizer
    private val speechRecognizerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                voiceTranscript = results[0]
                Toast.makeText(context, "Voice captured: \"$voiceTranscript\". Now take a photo!", Toast.LENGTH_LONG).show()
                launchAICamera()
            }
        }
    }

    // Camera for AI Flow
    private var aiCameraUri: Uri? = null
    private val takeAICameraPicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && aiCameraUri != null) {
            selectedUri = aiCameraUri
            isVideo = false
            updateMediaPreview()
            submitAIReport()
        }
    }

    private fun launchAICamera() {
        try {
            aiImageFile = File(requireContext().cacheDir, "ai_report_${System.currentTimeMillis()}.jpg")
            aiCameraUri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                aiImageFile!!
            )
            takeAICameraPicture.launch(aiCameraUri)
        } catch (e: Exception) {
            Toast.makeText(context, "Error starting camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView = view.findViewById(R.id.map_pick_location)
        mapView?.onCreate(savedInstanceState)
        
        mapView?.getMapAsync(object : com.mappls.sdk.maps.OnMapReadyCallback {
            override fun onMapReady(map: com.mappls.sdk.maps.MapplsMap) {
                mapplsMap = map
                setupMap(map, view)
            }
            override fun onMapError(code: Int, msg: String?) {}
        })

        val etHashtag = view.findViewById<android.widget.AutoCompleteTextView>(R.id.et_hashtag_channel)
        val etDesc = view.findViewById<android.widget.EditText>(R.id.et_description)
        val cgSuggestions = view.findViewById<ChipGroup>(R.id.cg_suggestions)
        val cgPopularTags = view.findViewById<ChipGroup>(R.id.cg_popular_tags)
        val btnUpload = view.findViewById<android.widget.Button>(R.id.btn_upload_media)
        val btnSubmit = view.findViewById<android.widget.Button>(R.id.btn_submit_report)
        val pbLoading = view.findViewById<android.widget.ProgressBar>(R.id.pb_uploading)

        // ── Popular Tags Chips ────────────────────────────────────────────────
        val popularTags = listOf(
            "#accident", "#pothole", "#traffic",
            "#speed-camera", "#road-damage", "#police", "#hazard"
        )
        popularTags.forEach { tag ->
            val chip = Chip(requireContext()).apply {
                text = tag
                isClickable = true
                isCheckable = false
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(0xFF2F3136.toInt())
                setTextColor(android.graphics.Color.parseColor("#7289DA"))
                chipStrokeColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#7289DA"))
                chipStrokeWidth = 1.5f
                textSize = 11f
                setOnClickListener {
                    etHashtag.setText(tag)
                    etHashtag.setSelection(tag.length)   // cursor at end — user can edit
                    updateSuggestionChips(tag, cgSuggestions, etDesc)
                }
            }
            cgPopularTags.addView(chip)
        }


        val autoSubmit = arguments?.getBoolean("autoSubmit", false) ?: false
        if (autoSubmit) {
            val autoHazardType = arguments?.getString("autoHazardType") ?: "#hazard"
            val autoDescription = arguments?.getString("autoDescription") ?: ""
            val autoImagePath = arguments?.getString("autoImageFilePath") ?: ""

            etHashtag.setText(autoHazardType)
            etDesc.setText(autoDescription)
            pbLoading.visibility = View.VISIBLE

            // Load the captured image file
            if (autoImagePath.isNotEmpty()) {
                val imgFile = java.io.File(autoImagePath)
                if (imgFile.exists()) {
                    aiImageFile = imgFile
                    selectedUri = android.net.Uri.fromFile(imgFile)
                    isVideo = false
                }
            }

            // Wait for map to set location, then auto-trigger submit after 2s
            view.postDelayed({
                if (selectedLatLng != null) {
                    btnSubmit.performClick()
                } else {
                    // Wait a bit longer if map hasn't resolved yet
                    view.postDelayed({
                        if (selectedLatLng != null) btnSubmit.performClick()
                        else {
                            pbLoading.visibility = View.GONE
                            Toast.makeText(context, "Location not ready. Please submit manually.", Toast.LENGTH_LONG).show()
                        }
                    }, 2000)
                }
            }, 2000)
            return  // Skip setting up mic voice flow below
        }

        // ── Manual Mode: Setup AI Voice mic inside the form ──────────────────
        val fabAiVoice = view.findViewById<View>(R.id.fab_ai_voice)
        fabAiVoice?.setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe the hazard (e.g. 'Speed camera ahead')")
            try {
                speechRecognizerLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Speech recognition not supported on this device.", Toast.LENGTH_SHORT).show()
            }
        }

        // Setup Hashtag AutoComplete (excluding all-hazards)

        val availableChannels = arrayOf(
            "accident", "construction", "waterlogging", "fallen-tree", "other",
            "fixed-camera", "mobile-camera", "speed-camera"
        )
        
        // Set cursor after the # symbol
        etHashtag.post {
            etHashtag.setSelection(etHashtag.text.length)
        }
        
        // Custom filtering: only show suggestions after # is typed
        etHashtag.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val text = s.toString()
                
                // Ensure # is always at the start
                if (!text.startsWith("#")) {
                    etHashtag.setText("#$text")
                    etHashtag.setSelection(etHashtag.text.length)
                    return
                }
                
                // Filter suggestions based on what's after #
                if (text.length > 1) {
                    val query = text.substring(1).lowercase()
                    val filtered = availableChannels.filter { it.startsWith(query) }
                        .map { "#$it" }
                        .toMutableList()
                    
                    // Show #other as fallback
                    if (filtered.isEmpty()) {
                        filtered.add("#other")
                    }
                    
                    val adapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_dropdown_item_1line, filtered) {
                        override fun getFilter(): android.widget.Filter {
                            return object : android.widget.Filter() {
                                override fun performFiltering(constraint: CharSequence?): FilterResults {
                                    val results = FilterResults()
                                    results.values = filtered
                                    results.count = filtered.size
                                    return results
                                }
                                override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                                    if (results != null && results.count > 0) {
                                        notifyDataSetChanged()
                                    } else {
                                        notifyDataSetInvalidated()
                                    }
                                }
                            }
                        }
                    }
                    etHashtag.setAdapter(adapter)
                    if (etHashtag.isAttachedToWindow && etHashtag.hasFocus()) {
                        etHashtag.showDropDown()
                    }
                } else {
                    // Show all options when just # is typed
                    val allOptions = availableChannels.map { "#$it" }.toTypedArray()
                    val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, allOptions)
                    etHashtag.setAdapter(adapter)
                }
            }
        })
        
        etHashtag.threshold = 1
        
        // Show dropdown on click without typing
        etHashtag.setOnClickListener {
            if (etHashtag.text.toString() == "#") {
                val allOptions = availableChannels.map { "#$it" }.toTypedArray()
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, allOptions)
                etHashtag.setAdapter(adapter)
                etHashtag.showDropDown()
            }
        }

        // Handle item selection to populate chips
        etHashtag.setOnItemClickListener { parent, _, position, _ ->
            val selectedTag = parent.getItemAtPosition(position).toString()
            updateSuggestionChips(selectedTag, cgSuggestions, etDesc)
        }

        btnUpload.setOnClickListener {
            pickMedia.launch(arrayOf("image/*", "video/*"))
        }

        btnSubmit.setOnClickListener {
            val desc = etDesc.text.toString()
            val hashtag = etHashtag.text.toString().trim()

            if (hashtag.isBlank() || !hashtag.startsWith("#")) {
                etHashtag.error = "Please select a channel (e.g., #accident)"
                return@setOnClickListener
            }

// Description is now optional

            if (selectedLatLng == null) {
                Toast.makeText(context, "Please ensure location is set (wait for map)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedUri == null) {
                Toast.makeText(context, "Please attach proof (photo/video)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Start Backend Submit
            pbLoading.visibility = View.VISIBLE
            btnSubmit.isEnabled = false

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val file = aiImageFile ?: uriToFile(selectedUri!!)
                    if (file == null) {
                        withContext(Dispatchers.Main) {
                            pbLoading.visibility = View.GONE
                            btnSubmit.isEnabled = true
                            context?.let { Toast.makeText(it, "Failed to process image file", Toast.LENGTH_SHORT).show() }
                        }
                        return@launch
                    }

                    val response = repository.submitReportToBackend(
                        userId = auth.currentUser?.uid ?: "anonymous",
                        hazardType = hashtag,
                        description = desc,
                        lat = selectedLatLng?.latitude ?: 0.0,
                        lng = selectedLatLng?.longitude ?: 0.0,
                        imageFile = file
                    )

                    withContext(Dispatchers.Main) {
                        pbLoading.visibility = View.GONE
                        if (response != null) {
                            context?.let { Toast.makeText(it, "Report Verified: ${response.status} (Score: ${String.format("%.0f%%", response.verification_score * 100)})", Toast.LENGTH_LONG).show() }
                            parentFragmentManager.popBackStack()
                        } else {
                            btnSubmit.isEnabled = true
                            context?.let { Toast.makeText(it, "Submission Failed", Toast.LENGTH_SHORT).show() }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        pbLoading.visibility = View.GONE
                        btnSubmit.isEnabled = true
                        context?.let { Toast.makeText(it, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
                        android.util.Log.e("BackendSubmit", "Error", e)
                    }
                }
            }
        }
    }

    private fun uriToFile(uri: Uri): java.io.File? {
        try {
            val contentResolver = requireContext().contentResolver
            val fileName = "temp_report_${System.currentTimeMillis()}.jpg"
            val tempFile = java.io.File(requireContext().cacheDir, fileName)
            tempFile.createNewFile()
            
            contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun updateSuggestionChips(hashtag: String, cgSuggestions: ChipGroup, etDesc: android.widget.EditText) {
        cgSuggestions.removeAllViews()
        val suggestions = suggestionsMap[hashtag] ?: listOf("Hazard here", "Please be careful", "Just happened")
        
        for (suggestion in suggestions) {
            val chip = Chip(context)
            chip.text = suggestion
            chip.isClickable = true
            chip.isCheckable = false
            chip.setChipBackgroundColorResource(R.color.primary_blue)
            chip.setTextColor(android.graphics.Color.WHITE)
            
            chip.setOnClickListener {
                val currentText = etDesc.text.toString()
                if (currentText.isEmpty()) {
                    etDesc.setText(suggestion)
                } else {
                    etDesc.setText("$currentText. $suggestion")
                }
                etDesc.setSelection(etDesc.text.length)
            }
            cgSuggestions.addView(chip)
        }
    }

    private fun submitAIReport() {
        if (aiImageFile == null || voiceTranscript.isEmpty() || selectedLatLng == null) {
            Toast.makeText(context, "AI submission missing data. Please wait for map.", Toast.LENGTH_SHORT).show()
            return
        }

        val pbLoading = view?.findViewById<android.widget.ProgressBar>(R.id.pb_uploading)
        pbLoading?.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("transcript", voiceTranscript)
                    .addFormDataPart(
                        "image", aiImageFile!!.name,
                        aiImageFile!!.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    )
                    .build()

                // Connect to local Assistant Backend on port 8001
                val request = Request.Builder()
                    .url("http://192.168.0.101:8001/api/v1/reports/ai-draft") 
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val resBody = response.body?.string()

                if (response.isSuccessful && resBody != null) {
                    val json = JSONObject(resBody)
                    val success = json.getBoolean("success")

                    if (!success) {
                        val error = json.optString("error", "Unknown AI error")
                        withContext(Dispatchers.Main) {
                            pbLoading?.visibility = View.GONE
                            Toast.makeText(context, "🚫 AI Rejection: $error", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        // AI Processed correctly - fetch output and auto-submit to MAIN backend
                        val hazardType = json.getString("hazard_type")
                        val genDesc = json.getString("description")
                        
                        // Auto-fill the UI
                        withContext(Dispatchers.Main) {
                            view?.findViewById<android.widget.AutoCompleteTextView>(R.id.et_hashtag_channel)?.setText(hazardType)
                            view?.findViewById<android.widget.EditText>(R.id.et_description)?.setText(genDesc)
                            Toast.makeText(context, "🤖 AI: Found $hazardType -> Auto-submitting...", Toast.LENGTH_SHORT).show()
                        }
                        
                        // Submit to main DB
                        val submitRes = repository.submitReportToBackend(
                            userId = auth.currentUser?.uid ?: "anonymous",
                            hazardType = hazardType,
                            description = genDesc,
                            lat = selectedLatLng?.latitude ?: 0.0,
                            lng = selectedLatLng?.longitude ?: 0.0,
                            imageFile = aiImageFile!!
                        )
                        
                        withContext(Dispatchers.Main) {
                            pbLoading?.visibility = View.GONE
                            if (submitRes != null) {
                                Toast.makeText(context, "✅ AI Report Published Successfully!", Toast.LENGTH_LONG).show()
                                parentFragmentManager.popBackStack()
                            } else {
                                Toast.makeText(context, "Submission to main server failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        pbLoading?.visibility = View.GONE
                        Toast.makeText(context, "AI Server error: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    pbLoading?.visibility = View.GONE
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    android.util.Log.e("AI_Submit", "Exception", e)
                }
            }
        }
    }
    override fun onLowMemory() { super.onLowMemory(); mapView?.onLowMemory() }

    private fun setupMap(map: com.mappls.sdk.maps.MapplsMap, view: View) {
        map.uiSettings?.isLogoEnabled = false
        map.uiSettings?.isCompassEnabled = false
        val activity = activity as? com.swapmap.zwap.demo.MainActivity
        val startLoc = activity?.mapplsMap?.cameraPosition?.target ?: com.mappls.sdk.maps.geometry.LatLng(28.7041, 77.1025)
        map.moveCamera(com.mappls.sdk.maps.camera.CameraUpdateFactory.newLatLngZoom(startLoc, 15.0))
        selectedLatLng = startLoc
        updateCoordinateDisplay(view, startLoc)
        map.addOnCameraMoveListener {
            val center = map.cameraPosition.target
            selectedLatLng = center
            updateCoordinateDisplay(view, center)
        }
    }

    private fun updateCoordinateDisplay(view: View, latLng: com.mappls.sdk.maps.geometry.LatLng) {
        view.findViewById<android.widget.TextView>(R.id.tv_location_preview)?.text =
            "Lat: ${String.format("%.5f", latLng.latitude)}, Lng: ${String.format("%.5f", latLng.longitude)}"
    }

    private fun updateMediaPreview() {
        view?.let { v ->
            val iv = v.findViewById<android.widget.ImageView>(R.id.iv_media_preview)
            val vv = v.findViewById<android.widget.VideoView>(R.id.vv_media_preview)
            val play = v.findViewById<android.widget.ImageView>(R.id.iv_play_preview)
            if (isVideo) {
                iv.visibility = View.GONE
                vv.visibility = View.VISIBLE
                play.visibility = View.GONE
                vv.setVideoURI(selectedUri)
                vv.start()
            } else {
                vv.visibility = View.GONE
                play.visibility = View.GONE
                iv.visibility = View.VISIBLE
                iv.setImageURI(selectedUri)
            }
        }
    }

    // Lifecycle
    override fun onStart() { super.onStart(); mapView?.onStart() }
    override fun onResume() { super.onResume(); mapView?.onResume() }
    override fun onPause() { super.onPause(); mapView?.onPause() }
    override fun onStop() { super.onStop(); mapView?.onStop() }
    override fun onDestroyView() { super.onDestroyView(); mapView?.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView?.onSaveInstanceState(outState) }
}
