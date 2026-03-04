package com.swapmap.zwap.demo.community

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.swapmap.zwap.R

class ReportSubmissionFragment : BottomSheetDialogFragment() {

    private var onReportSelectedListener: ((String, android.net.Uri?) -> Unit)? = null
    private var selectedImageUri: android.net.Uri? = null

    fun setOnReportSelectedListener(listener: (String, android.net.Uri?) -> Unit) {
        onReportSelectedListener = listener
    }

    private val pickImage = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedImageUri = it
            view?.findViewById<android.widget.ImageView>(R.id.iv_photo_preview)?.apply {
                visibility = android.view.View.VISIBLE
                setImageURI(it)
            }
            view?.findViewById<android.widget.TextView>(R.id.tv_add_photo)?.text = "Change Photo"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_report_submission, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btn_report_hazard)?.setOnClickListener {
            onReportSelectedListener?.invoke("Hazard", selectedImageUri)
            dismiss()
        }

        view.findViewById<View>(R.id.btn_report_camera)?.setOnClickListener {
            onReportSelectedListener?.invoke("Speed Camera", selectedImageUri)
            dismiss()
        }

        view.findViewById<View>(R.id.btn_report_police)?.setOnClickListener {
            onReportSelectedListener?.invoke("Police", selectedImageUri)
            dismiss()
        }
        
        view.findViewById<View>(R.id.btn_report_traffic)?.setOnClickListener {
            onReportSelectedListener?.invoke("Traffic", selectedImageUri)
            dismiss()
        }
        
        view.findViewById<View>(R.id.btn_report_map)?.setOnClickListener {
            onReportSelectedListener?.invoke("Map Issue", selectedImageUri)
            dismiss()
        }

        view.findViewById<View>(R.id.btn_add_photo)?.setOnClickListener {
            pickImage.launch("image/*")
        }

        view.findViewById<View>(R.id.btn_cancel)?.setOnClickListener {
            dismiss()
        }
    }
    
    override fun getTheme(): Int {
        return R.style.BottomSheetDialogTheme
    }
}
