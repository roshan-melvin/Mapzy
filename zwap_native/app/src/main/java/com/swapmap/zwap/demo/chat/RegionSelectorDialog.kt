package com.swapmap.zwap.demo.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.swapmap.zwap.R

class RegionSelectorDialog : BottomSheetDialogFragment() {

    private var onRegionSelectedListener: ((String, String, String) -> Unit)? = null

    // Sample data - in production, fetch from API or Firestore
    private val countries = listOf("India", "United States", "United Kingdom")
    private val statesIndia = listOf("Tamil Nadu", "Karnataka", "Maharashtra", "Delhi", "Kerala")
    private val statesUS = listOf("California", "Texas", "New York", "Florida")
    private val citiesTamilNadu = listOf("Chennai", "Coimbatore", "Madurai", "Salem")
    private val citiesKarnataka = listOf("Bangalore", "Mysore", "Mangalore")
    private val citiesCalifornia = listOf("Los Angeles", "San Francisco", "San Diego")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_region_selector, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val spinnerCountry = view.findViewById<Spinner>(R.id.spinner_country)
        val spinnerState = view.findViewById<Spinner>(R.id.spinner_state)
        val spinnerCity = view.findViewById<Spinner>(R.id.spinner_city)

        // Setup country spinner
        val countryAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, countries)
        countryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCountry.adapter = countryAdapter

        // Country selection listener
        spinnerCountry.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedCountry = countries[position]
                updateStateSpinner(spinnerState, spinnerCity, selectedCountry)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // State selection listener
        spinnerState.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedCountry = spinnerCountry.selectedItem.toString()
                val selectedState = spinnerState.selectedItem.toString()
                updateCitySpinner(spinnerCity, selectedCountry, selectedState)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Cancel button
        view.findViewById<View>(R.id.btn_region_cancel)?.setOnClickListener {
            dismiss()
        }

        // Join button
        view.findViewById<View>(R.id.btn_region_join)?.setOnClickListener {
            val country = spinnerCountry.selectedItem?.toString() ?: ""
            val state = spinnerState.selectedItem?.toString() ?: ""
            val city = spinnerCity.selectedItem?.toString() ?: ""

            if (country.isNotEmpty() && state.isNotEmpty() && city.isNotEmpty()) {
                onRegionSelectedListener?.invoke(country, state, city)
                dismiss()
            } else {
                Toast.makeText(context, "Please select all fields", Toast.LENGTH_SHORT).show()
            }
        }

        // Initialize state spinner
        updateStateSpinner(spinnerState, spinnerCity, countries[0])
    }

    private fun updateStateSpinner(spinnerState: Spinner, spinnerCity: Spinner, country: String) {
        val states = when (country) {
            "India" -> statesIndia
            "United States" -> statesUS
            else -> listOf("N/A")
        }

        val stateAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, states)
        stateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerState.adapter = stateAdapter

        if (states.isNotEmpty()) {
            updateCitySpinner(spinnerCity, country, states[0])
        }
    }

    private fun updateCitySpinner(spinnerCity: Spinner, country: String, state: String) {
        val cities = when {
            country == "India" && state == "Tamil Nadu" -> citiesTamilNadu
            country == "India" && state == "Karnataka" -> citiesKarnataka
            country == "United States" && state == "California" -> citiesCalifornia
            else -> listOf("Other")
        }

        val cityAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, cities)
        cityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCity.adapter = cityAdapter
    }

    fun setOnRegionSelectedListener(listener: (String, String, String) -> Unit) {
        onRegionSelectedListener = listener
    }
}
