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
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val name = document.getString("username") ?: "Zwap User"
                    view.findViewById<TextView>(R.id.tv_profile_name).text = name
                    
                    // Placeholder stats - can be real later
                    val points = document.getLong("points") ?: 0
                    val reports = document.getLong("reports_count") ?: 0
                    
                    view.findViewById<TextView>(R.id.tv_stats_points).text = points.toString()
                    view.findViewById<TextView>(R.id.tv_stats_reports).text = reports.toString()
                }
            }
            .addOnFailureListener {
                view.findViewById<TextView>(R.id.tv_profile_name).text = "User (Offline)"
            }

        // Fetch trust summary from subcollection
        db.collection("users").document(user.uid)
            .collection("trust").document("summary").get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val trustScore = document.getDouble("trust_score") ?: 50.0
                    val badgeLevel = document.getString("badge_level") ?: "BRONZE"

                    view.findViewById<TextView>(R.id.tv_stats_trust).text = String.format("%.0f%%", trustScore)
                    view.findViewById<TextView>(R.id.tv_stats_badge).text = badgeLevel
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
