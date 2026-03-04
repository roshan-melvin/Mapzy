package com.swapmap.zwap.demo

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.mappls.sdk.maps.MapView
import com.mappls.sdk.maps.MapplsMap
import com.mappls.sdk.maps.OnMapReadyCallback
import com.mappls.sdk.maps.camera.CameraUpdateFactory
import com.swapmap.zwap.R

class LocationPickerFragment : Fragment(), OnMapReadyCallback {

    private var mapView: MapView? = null
    private var mapplsMap: MapplsMap? = null
    private var locationType: String? = null

    companion object {
        const val EXTRA_LOCATION_TYPE = "location_type"
        
        fun newInstance(type: String): LocationPickerFragment {
            val fragment = LocationPickerFragment()
            val args = Bundle()
            args.putString(EXTRA_LOCATION_TYPE, type)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationType = arguments?.getString(EXTRA_LOCATION_TYPE)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_location_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.tv_picker_title).text = "Set $locationType Location"

        mapView = view.findViewById(R.id.map_picker)
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync(this)

        view.findViewById<Button>(R.id.btn_confirm_location).setOnClickListener {
            val target = mapplsMap?.cameraPosition?.target
            if (target != null && locationType != null) {
                val prefs = requireActivity().getSharedPreferences("user_locations", Context.MODE_PRIVATE)
                prefs.edit()
                    .putFloat("${locationType}_lat", target.latitude.toFloat())
                    .putFloat("${locationType}_lng", target.longitude.toFloat())
                    .apply()
                
                // Return to previous screen
                if (parentFragmentManager.backStackEntryCount > 0) {
                    parentFragmentManager.popBackStack()
                } else {
                    view.visibility = View.GONE
                }
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (parentFragmentManager.backStackEntryCount > 0) {
                    parentFragmentManager.popBackStack()
                } else {
                    view.visibility = View.GONE
                }
            }
        })
    }

    override fun onMapError(p0: Int, p1: String?) {
        // Handle map loading error
    }

    override fun onMapReady(map: MapplsMap) {
        mapplsMap = map
        mapplsMap?.uiSettings?.isCompassEnabled = false
        mapplsMap?.animateCamera(CameraUpdateFactory.zoomTo(14.0))
        
        // Show user's current location if available from MainActivity
        // Removed as currentLocation was not directly accessible, letting user pan starting from default map center.
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView?.onDestroy()
        mapView = null
    }
}
