package com.swapmap.zwap.demo.community

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.swapmap.zwap.R
import com.swapmap.zwap.demo.model.Report
import com.swapmap.zwap.demo.network.CloudinaryManager
import com.swapmap.zwap.demo.repository.ReportRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    
    // Media Picker
    private val pickMedia = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedUri = uri
            val type = requireContext().contentResolver.getType(uri)
            isVideo = type?.startsWith("video") == true
            updateMediaPreview()
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
        val btnUpload = view.findViewById<android.widget.Button>(R.id.btn_upload_media)
        val btnSubmit = view.findViewById<android.widget.Button>(R.id.btn_submit_report)
        val pbLoading = view.findViewById<android.widget.ProgressBar>(R.id.pb_uploading)

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
                    etHashtag.showDropDown()
                } else {
                    // Show all options when just # is typed
                    val allOptions = availableChannels.map { "#$it" }.toTypedArray()
                    val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, allOptions)
                    etHashtag.setAdapter(adapter)
                }
            }
        })
        
        etHashtag.threshold = 1

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
                    val file = uriToFile(selectedUri!!)
                    if (file == null) {
                        withContext(Dispatchers.Main) {
                            pbLoading.visibility = View.GONE
                            btnSubmit.isEnabled = true
                            Toast.makeText(context, "Failed to process image file", Toast.LENGTH_SHORT).show()
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
                            Toast.makeText(context, "Report Verified: ${response.status} (Score: ${String.format("%.0f%%", response.verification_score * 100)})", Toast.LENGTH_LONG).show()
                            parentFragmentManager.popBackStack()
                        } else {
                            btnSubmit.isEnabled = true
                            Toast.makeText(context, "Submission Failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        pbLoading.visibility = View.GONE
                        btnSubmit.isEnabled = true
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
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

    
    private fun setupMap(map: com.mappls.sdk.maps.MapplsMap, view: View) {
        map.uiSettings?.isLogoEnabled = false
        map.uiSettings?.isCompassEnabled = false
        
        // Initial Loc (Try to get from Activity or Default)
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
    override fun onLowMemory() { super.onLowMemory(); mapView?.onLowMemory() }
}
