package com.swapmap.zwap.demo.model

import com.google.firebase.Timestamp

data class Report(
    val id: String = "",
    val userId: String = "",
    val incidentType: String = "",
    val description: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val imageUrl: String? = null,
    val status: String = "Pending", // Pending, Verified, Rejected
    val pointsAwarded: Int = 0,
    val hazardCondition: String = "active", // active, broken, none
    val createdAt: Timestamp? = null
)
