package com.swapmap.zwap.demo.community

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.swapmap.zwap.R

class ChannelReportFragment : BottomSheetDialogFragment() {

    private var onReportSubmittedListener: ((String, String, String, android.net.Uri?) -> Unit)? = null
    private var selectedImageUri: android.net.Uri? = null
    private var channelName: String = ""
    private var autoCategory: String = "Hazard"
    private var selectedLocation: com.mappls.sdk.geojson.Point? = null
    
    private var miniMapView: com.mappls.sdk.maps.MapView? = null
    private var miniMap: com.mappls.sdk.maps.MapplsMap? = null

    // Permission launcher for camera
    private val cameraPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(context, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    fun setChannelContext(channel: String) {
        channelName = channel
        // Map channel to category
        autoCategory = when {
            channel.contains("camera") -> "Speed Camera"
            channel.contains("police") -> "Police"
            channel.contains("traffic") -> "Traffic"
            channel.contains("map") -> "Map Issue"
            else -> "Hazard" // Default for accident, construction, waterlogging, etc.
        }
    }

    fun setOnReportSubmittedListener(listener: (String, String, String, android.net.Uri?) -> Unit) {
        onReportSubmittedListener = listener
    }

    private val pickImage = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedImageUri = it
            view?.findViewById<android.widget.ImageView>(R.id.iv_channel_photo_preview)?.apply {
                visibility = View.VISIBLE
                setImageURI(it)
            }
        }
    }

    private val takePicture = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { success ->
        if (success && selectedImageUri != null) {
            view?.findViewById<android.widget.ImageView>(R.id.iv_channel_photo_preview)?.apply {
                visibility = View.VISIBLE
                setImageURI(selectedImageUri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_channel_report, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set channel info
        view.findViewById<android.widget.TextView>(R.id.tv_report_channel_info)?.text = "Posting to #$channelName"
        
        // Set category display (read-only)
        val categoryView = view.findViewById<android.widget.TextView>(R.id.tv_report_category)
        categoryView?.text = autoCategory
        
        // Set icon based on category
        val iconRes = when (autoCategory) {
            "Speed Camera" -> android.R.drawable.ic_menu_camera
            "Police" -> android.R.drawable.ic_lock_idle_lock
            "Traffic" -> android.R.drawable.ic_menu_rotate
            "Map Issue" -> android.R.drawable.ic_dialog_map
            else -> android.R.drawable.ic_dialog_alert // Hazard
        }
        categoryView?.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0)
        
        // Set default hashtag (non-editable)
        view.findViewById<android.widget.TextView>(R.id.tv_default_hashtag)?.text = "#$channelName"
        
        // Clear additional hashtags input (optional field)
        val additionalHashtagsField = view.findViewById<android.widget.EditText>(R.id.et_channel_hashtags)
        additionalHashtagsField?.setText("")

        // Initialize mini map
        setupMiniMap(view)

        // Take photo with camera
        view.findViewById<View>(R.id.btn_channel_take_photo)?.setOnClickListener {
            // Check camera permission
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.CAMERA
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                launchCamera()
            } else {
                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            }
        }

        // Add photo from gallery
        view.findViewById<View>(R.id.btn_channel_add_photo)?.setOnClickListener {
            pickImage.launch("image/*")
        }

        // Confirm location from mini map
        view.findViewById<View>(R.id.btn_channel_set_location)?.setOnClickListener {
            val center = miniMap?.cameraPosition?.target
            
            if (center != null) {
                selectedLocation = com.mappls.sdk.geojson.Point.fromLngLat(
                    center.longitude,
                    center.latitude
                )
                Toast.makeText(context, "Location confirmed!", Toast.LENGTH_SHORT).show()
                view.findViewById<android.widget.Button>(R.id.btn_channel_set_location)?.text = "✓ Location Confirmed"
            } else {
                Toast.makeText(context, "Please wait for map to load", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Manual coordinate input - update map when edited
        val latField = view.findViewById<android.widget.EditText>(R.id.et_latitude)
        val lngField = view.findViewById<android.widget.EditText>(R.id.et_longitude)
        
        latField?.setOnEditorActionListener { _, _, _ ->
            updateMapFromCoordinates(latField, lngField)
            true
        }
        
        lngField?.setOnEditorActionListener { _, _, _ ->
            updateMapFromCoordinates(latField, lngField)
            true
        }

        // Cancel
        view.findViewById<View>(R.id.btn_channel_cancel)?.setOnClickListener {
            dismiss()
        }

        // Submit
        view.findViewById<View>(R.id.btn_channel_submit)?.setOnClickListener {
            val description = view.findViewById<android.widget.EditText>(R.id.et_channel_description)?.text.toString()
            val additionalHashtags = view.findViewById<android.widget.EditText>(R.id.et_channel_hashtags)?.text.toString().trim()
            
            // Combine default hashtag with additional ones
            val allHashtags = if (additionalHashtags.isNotEmpty()) {
                "#$channelName $additionalHashtags"
            } else {
                "#$channelName"
            }

            if (description.isEmpty()) {
                Toast.makeText(context, "Please add a description", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            onReportSubmittedListener?.invoke(autoCategory, description, allHashtags, selectedImageUri)
            dismiss()
        }
    }

    override fun getTheme(): Int {
        return R.style.BottomSheetDialogTheme
    }

    private fun setupMiniMap(view: View) {
        miniMapView = view.findViewById(R.id.mini_map_view)
        miniMapView?.onCreate(null)
        miniMapView?.onStart()
        miniMapView?.onResume()
        
        miniMapView?.getMapAsync(object : com.mappls.sdk.maps.OnMapReadyCallback {
            override fun onMapReady(map: com.mappls.sdk.maps.MapplsMap) {
                miniMap = map
                
                // Configure map settings
                map.uiSettings?.isLogoEnabled = false
                map.uiSettings?.isCompassEnabled = false
                map.uiSettings?.isRotateGesturesEnabled = false
                map.uiSettings?.isTiltGesturesEnabled = false
                
                // Get user's current location or main map position
                val activity = activity as? com.swapmap.zwap.demo.MainActivity
                val initialLocation = activity?.mapplsMap?.cameraPosition?.target
                
                if (initialLocation != null) {
                    map.moveCamera(com.mappls.sdk.maps.camera.CameraUpdateFactory.newLatLngZoom(
                        initialLocation, 14.0
                    ))
                    updateCoordinateDisplay(view, initialLocation.latitude, initialLocation.longitude)
                } else {
                    // Default to a reasonable location if no main map position
                    val defaultLocation = com.mappls.sdk.maps.geometry.LatLng(28.7041, 77.1025) // Delhi
                    map.moveCamera(com.mappls.sdk.maps.camera.CameraUpdateFactory.newLatLngZoom(
                        defaultLocation, 12.0
                    ))
                    updateCoordinateDisplay(view, defaultLocation.latitude, defaultLocation.longitude)
                }
                
                // Update coordinates as map moves
                map.addOnCameraMoveListener {
                    val center = map.cameraPosition.target
                    updateCoordinateDisplay(view, center.latitude, center.longitude)
                }
            }
            
            override fun onMapError(errorCode: Int, errorMessage: String) {
                android.util.Log.e("Zwap", "Mini map error: $errorCode - $errorMessage")
                Toast.makeText(context, "Map loading...", Toast.LENGTH_SHORT).show()
            }
        })
    }
    
    private fun updateCoordinateDisplay(view: View, lat: Double, lng: Double) {
        view.findViewById<android.widget.TextView>(R.id.tv_map_coordinates)?.text = 
            "Lat: ${String.format("%.6f", lat)}, Lng: ${String.format("%.6f", lng)}"
        
        // Update manual input fields
        view.findViewById<android.widget.EditText>(R.id.et_latitude)?.setText(String.format("%.6f", lat))
        view.findViewById<android.widget.EditText>(R.id.et_longitude)?.setText(String.format("%.6f", lng))
    }
    
    private fun updateMapFromCoordinates(latField: android.widget.EditText, lngField: android.widget.EditText) {
        try {
            val lat = latField.text.toString().toDoubleOrNull()
            val lng = lngField.text.toString().toDoubleOrNull()
            
            if (lat != null && lng != null && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180) {
                miniMap?.moveCamera(com.mappls.sdk.maps.camera.CameraUpdateFactory.newLatLng(
                    com.mappls.sdk.maps.geometry.LatLng(lat, lng)
                ))
                Toast.makeText(context, "Map updated", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Invalid coordinates", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createImageFile(): java.io.File {
        val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        val storageDir = requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
        return java.io.File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        )
    }

    private fun launchCamera() {
        try {
            val photoFile = createImageFile()
            selectedImageUri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                photoFile
            )
            takePicture.launch(selectedImageUri)
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onStart() {
        super.onStart()
        miniMapView?.onStart()
    }
    
    override fun onResume() {
        super.onResume()
        miniMapView?.onResume()
    }
    
    override fun onPause() {
        super.onPause()
        miniMapView?.onPause()
    }
    
    override fun onStop() {
        super.onStop()
        miniMapView?.onStop()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        miniMapView?.onDestroy()
    }
}
