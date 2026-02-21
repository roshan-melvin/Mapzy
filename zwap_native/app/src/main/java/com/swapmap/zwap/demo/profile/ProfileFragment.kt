package com.swapmap.zwap.demo.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.swapmap.zwap.R
import com.swapmap.zwap.demo.AuthActivity
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
    }

    private fun loadUserProfile(view: View) {
        val user = auth.currentUser
        if (user == null) {
            // Should not happen, but safe fallback
            view.findViewById<TextView>(R.id.tv_profile_name).text = "Guest"
            return
        }

        view.findViewById<TextView>(R.id.tv_profile_email).text = user.email

        // Fetch extra details from Firestore
        // Priority: Firestore username -> Firebase Auth displayName -> "Zwap User"
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                val firestoreName = if (document.exists()) document.getString("name") else null
                val name = firestoreName
                    ?: user.displayName?.takeIf { it.isNotBlank() }
                    ?: "Zwap User"
                view.findViewById<TextView>(R.id.tv_profile_name).text = name
                // Stats are fetched from FastAPI below
            }
            .addOnFailureListener {
                // If Firestore fails, still try displayName
                val fallback = user.displayName?.takeIf { it.isNotBlank() } ?: "User (Offline)"
                view.findViewById<TextView>(R.id.tv_profile_name).text = fallback
            }

        // Fetch real-time trust and stats from FastAPI Backend (Supabase)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("ZwapProfile", "Fetching stats for user: ${user.uid}")
                val response = com.swapmap.zwap.demo.network.ApiClient.hazardApiService.getUserStats(user.uid)
                if (response.isSuccessful && response.body() != null) {
                    val stats = response.body()!!
                    android.util.Log.d("ZwapProfile", "Stats received: Trust=${stats.trust_score}, Points=${stats.reward_points}")
                    withContext(Dispatchers.Main) {
                        view.findViewById<TextView>(R.id.tv_stats_trust).text = String.format("%.2f%%", stats.trust_score)
                        view.findViewById<TextView>(R.id.tv_stats_badge).text = stats.badge_level
                        view.findViewById<TextView>(R.id.tv_stats_points).text = stats.reward_points.toString()
                        view.findViewById<TextView>(R.id.tv_stats_reports).text = stats.total_reports.toString()
                        // stats.username is not returned by the backend properly, so rely on Firestore name
                    }
                } else {
                    android.util.Log.e("ZwapProfile", "Failed to fetch stats: ${response.code()} ${response.message()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("ZwapProfile", "Error fetching stats", e)
                e.printStackTrace()
            }
        }

    }

    private fun setupButtons(view: View) {
        view.findViewById<Button>(R.id.btn_sign_out).setOnClickListener {
            auth.signOut()
            Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
            
            // Redirect to AuthActivity (or restart Main)
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
