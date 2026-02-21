package com.swapmap.zwap.demo.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_reports")
data class PendingReport(
    @PrimaryKey
    val id: String,
    val userId: String,
    val incidentType: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val imageUri: String? = null,
    val status: String = "Pending", // Pending, Uploading, Submitted, Failed
    val createdAt: Long = System.currentTimeMillis()
)
