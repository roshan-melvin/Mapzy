package com.swapmap.zwap.demo.community.models

import com.google.firebase.Timestamp

data class Review(
    val review_id: String = "",
    val user_id: String = "",
    val user_name: String = "",
    
    // Place Information
    val place_id: String = "",
    val place_name: String = "",
    val place_location: Map<String, Double> = mapOf("lat" to 0.0, "lng" to 0.0),
    
    // Review Content
    val star_rating: Int = 0, // 1-5
    val review_text: String = "",
    
    // Media
    val images: List<String> = emptyList(), // Max 3 images
    
    // Metadata
    val created_at: Timestamp? = null,
    val updated_at: Timestamp? = null,
    
    // Moderation
    val status: String = "active",
    val helpful_count: Int = 0
) {
    fun toMap(): Map<String, Any?> {
        return hashMapOf(
            "review_id" to review_id,
            "user_id" to user_id,
            "user_name" to user_name,
            "place_id" to place_id,
            "place_name" to place_name,
            "place_location" to place_location,
            "star_rating" to star_rating,
            "review_text" to review_text,
            "images" to images,
            "created_at" to created_at,
            "updated_at" to updated_at,
            "status" to status,
            "helpful_count" to helpful_count
        )
    }
}
