package com.swapmap.zwap.demo.community.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint

data class CommunityReport(
    val report_id: String = "",
    val created_by: String = "",
    val created_by_name: String = "",
    
    // Classification
    val category: String = "", // "hazard" or "speed_camera"
    val hazard_type: String? = null, // Only for hazards
    
    // Location
    val location: GeoPoint = GeoPoint(0.0, 0.0),
    val place_name: String? = null,
    
    // Camera-specific
    val speed_limit: Int? = null,
    val camera_direction: String? = null,
    
    // Media
    val image_url: String? = null,
    
    // Community Validation
    val upvotes: Int = 0,
    val downvotes: Int = 0,
    val vote_score: Int = 0,
    val verified: Boolean = false,
    val verification_level: String = "unverified",
    
    // Status
    val status: String = "active",
    
    // Metadata
    val created_at: Timestamp? = null,
    val expires_at: Timestamp? = null,
    val last_confirmed_at: Timestamp? = null,
    
    // Engagement
    val view_count: Int = 0,
    val confirmation_count: Int = 0
) {
    // Helper to convert to Map for Firestore
    fun toMap(): Map<String, Any?> {
        return hashMapOf(
            "report_id" to report_id,
            "created_by" to created_by,
            "created_by_name" to created_by_name,
            "category" to category,
            "hazard_type" to hazard_type,
            "location" to location,
            "place_name" to place_name,
            "speed_limit" to speed_limit,
            "camera_direction" to camera_direction,
            "image_url" to image_url,
            "upvotes" to upvotes,
            "downvotes" to downvotes,
            "vote_score" to vote_score,
            "verified" to verified,
            "verification_level" to verification_level,
            "status" to status,
            "created_at" to created_at,
            "expires_at" to expires_at,
            "last_confirmed_at" to last_confirmed_at,
            "view_count" to view_count,
            "confirmation_count" to confirmation_count
        )
    }
}

