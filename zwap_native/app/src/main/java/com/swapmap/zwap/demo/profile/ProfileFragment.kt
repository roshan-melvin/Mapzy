package com.swapmap.zwap.demo.profile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.swapmap.zwap.R
import com.swapmap.zwap.demo.AuthActivity
import com.swapmap.zwap.demo.WakeWordService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileFragment : Fragment() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadUserProfile(view)
        setupButtons(view)
        setupCameraPreference(view)
        setupWakeWordToggle(view)
    }

    // ── Wake Word Toggle ──────────────────────────────────────────────────────
    private fun setupWakeWordToggle(view: View) {
        val prefs = requireContext().getSharedPreferences("zwap_prefs", Context.MODE_PRIVATE)
        val swWakeWord = view.findViewById<SwitchCompat>(R.id.sw_wake_word)

        // Restore saved state
        val isEnabled = prefs.getBoolean("wake_word_enabled", false)
        swWakeWord.isChecked = isEnabled

        swWakeWord.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                val audioPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.RECORD_AUDIO
                )
                if (audioPerm != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(context, "Please grant microphone permission in App Info to use this feature", Toast.LENGTH_LONG).show()
                    swWakeWord.isChecked = false
                    return@setOnCheckedChangeListener
                }
                
                prefs.edit().putBoolean("wake_word_enabled", true).apply()
                val serviceIntent = Intent(requireContext(), WakeWordService::class.java)
                requireContext().startForegroundService(serviceIntent)
                Toast.makeText(
                    context,
                    "🎙 'Hey Mapzy' is now active — speak to report!",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                prefs.edit().putBoolean("wake_word_enabled", false).apply()
                val serviceIntent = Intent(requireContext(), WakeWordService::class.java)
                requireContext().stopService(serviceIntent)
                Toast.makeText(context, "Wake word disabled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupCameraPreference(view: View) {
        val prefs = requireContext().getSharedPreferences("zwap_prefs", Context.MODE_PRIVATE)
        val rgMode = view.findViewById<RadioGroup>(R.id.rg_camera_mode)
        val rgLens = view.findViewById<RadioGroup>(R.id.rg_camera_lens)
        val llLens = view.findViewById<View>(R.id.ll_lens_selector)

        // Restore saved preferences
        val savedMode = prefs.getString("voice_camera_mode", "manual")
        val savedLens = prefs.getString("voice_camera_lens", "back")

        if (savedMode == "auto") {
            view.findViewById<android.widget.RadioButton>(R.id.rb_camera_auto).isChecked = true
            llLens.visibility = View.VISIBLE
        } else {
            view.findViewById<android.widget.RadioButton>(R.id.rb_camera_manual).isChecked = true
            llLens.visibility = View.GONE
        }

        if (savedLens == "front") {
            view.findViewById<android.widget.RadioButton>(R.id.rb_lens_front).isChecked = true
        } else {
            view.findViewById<android.widget.RadioButton>(R.id.rb_lens_back).isChecked = true
        }

        // Wire Preview Camera button
        val btnPreview = view.findViewById<Button>(R.id.btn_preview_camera)
        btnPreview?.setOnClickListener {
            val useFront = prefs.getString("voice_camera_lens", "back") == "front"
            showCameraPreviewDialog(useFront)
        }

        // Toggle lens selector visibility on mode change
        rgMode.setOnCheckedChangeListener { _, checkedId ->
            val isAuto = checkedId == R.id.rb_camera_auto
            llLens.visibility = if (isAuto) View.VISIBLE else View.GONE
            prefs.edit().putString("voice_camera_mode", if (isAuto) "auto" else "manual").apply()
            Toast.makeText(context,
                if (isAuto) "✅ Auto camera enabled" else "✅ Manual camera enabled",
                Toast.LENGTH_SHORT).show()
        }

        // Save lens preference on change
        rgLens.setOnCheckedChangeListener { _, checkedId ->
            val lens = if (checkedId == R.id.rb_lens_front) "front" else "back"
            prefs.edit().putString("voice_camera_lens", lens).apply()
            Toast.makeText(context, "✅ Lens set to ${lens.replaceFirstChar { it.uppercase() }} Camera", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadUserProfile(view: View) {
        val user = auth.currentUser
        if (user == null) {
            view.findViewById<TextView>(R.id.tv_profile_name).text = "Guest"
            return
        }

        view.findViewById<TextView>(R.id.tv_profile_email).text = user.email

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                val firestoreName = if (document.exists()) document.getString("name") else null
                val name = firestoreName
                    ?: user.displayName?.takeIf { it.isNotBlank() }
                    ?: "Zwap User"
                view.findViewById<TextView>(R.id.tv_profile_name).text = name
            }
            .addOnFailureListener {
                val fallback = user.displayName?.takeIf { it.isNotBlank() } ?: "User (Offline)"
                view.findViewById<TextView>(R.id.tv_profile_name).text = fallback
            }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("ZwapProfile", "Fetching stats for user: ${user.uid}")
                val response = com.swapmap.zwap.demo.network.ApiClient.hazardApiService.getUserStats(user.uid)
                if (response.isSuccessful && response.body() != null) {
                    val stats = response.body()!!
                    withContext(Dispatchers.Main) {
                        view.findViewById<TextView>(R.id.tv_stats_trust).text = String.format("%.2f%%", stats.trust_score)
                        view.findViewById<TextView>(R.id.tv_stats_badge).text = stats.badge_level
                        view.findViewById<TextView>(R.id.tv_stats_points).text = stats.reward_points.toString()
                        view.findViewById<TextView>(R.id.tv_stats_reports).text = stats.total_reports.toString()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ZwapProfile", "Error fetching stats", e)
            }
        }
    }

    // ── Camera Preview Dialog ────────────────────────────────────────────────
    /**
     * Opens a full-screen live viewfinder so the user can position the phone.
     * Uses the lens (front/back) saved in preferences.
     * Tap anywhere or press Back to close.
     */
    private fun showCameraPreviewDialog(useFrontCamera: Boolean) {
        val ctx = requireContext()
        val dialog = android.app.Dialog(ctx, android.R.style.Theme_Black_NoTitleBar_Fullscreen)

        // Root container
        val container = FrameLayout(ctx)
        container.setBackgroundColor(android.graphics.Color.BLACK)

        // Live viewfinder
        val previewView = PreviewView(ctx)
        previewView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE

        // Instruction overlay
        val tvHint = TextView(ctx)
        tvHint.text = if (useFrontCamera) "Front camera — Position your phone, then tap to close"
                      else "Back camera — Point at the road, then tap to close"
        tvHint.setTextColor(android.graphics.Color.WHITE)
        tvHint.setBackgroundColor(0xAA000000.toInt())
        tvHint.gravity = android.view.Gravity.CENTER
        tvHint.textSize = 14f
        tvHint.setPadding(24, 16, 24, 16)
        val hintParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.BOTTOM
        )
        hintParams.bottomMargin = 80
        tvHint.layoutParams = hintParams

        container.addView(previewView)
        container.addView(tvHint)
        dialog.setContentView(container)

        // Bind CameraX preview
        val cameraSelector = if (useFrontCamera)
            CameraSelector.DEFAULT_FRONT_CAMERA
        else
            CameraSelector.DEFAULT_BACK_CAMERA

        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(viewLifecycleOwner, cameraSelector, preview)
            } catch (e: Exception) {
                android.util.Log.e("CameraPreview", "bind failed", e)
                Toast.makeText(ctx, "Could not open camera", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }, ContextCompat.getMainExecutor(ctx))

        // Close on tap anywhere
        container.setOnClickListener {
            dialog.dismiss()
        }

        // Release camera on dismiss
        dialog.setOnDismissListener {
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
            } catch (e: Exception) { /* camera already unbound */ }
        }

        dialog.show()
    }

    private fun setupButtons(view: View) {
        view.findViewById<Button>(R.id.btn_sign_out).setOnClickListener {
            auth.signOut()
            Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
            val intent = Intent(context, AuthActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        view.findViewById<Button>(R.id.btn_edit_profile).setOnClickListener {
            Toast.makeText(context, "Edit Profile Coming Soon", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<TextView>(R.id.btn_notifications).setOnClickListener {
            Toast.makeText(context, "Notifications Settings Coming Soon", Toast.LENGTH_SHORT).show()
        }
    }
}

