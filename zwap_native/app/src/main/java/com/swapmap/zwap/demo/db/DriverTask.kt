package com.swapmap.zwap.demo.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "driver_tasks")
data class DriverTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val category: String? = null,          // TaskCategory.name or null
    val nearestPlaceName: String? = null,
    val nearestPlaceDistance: Double? = null,  // km
    val nearestPlaceLat: Double? = null,
    val nearestPlaceLon: Double? = null,
    val isCompleted: Boolean = false,
    val geminiProcessing: Boolean = false,     // true while Gemini is working
    val createdAt: Long = System.currentTimeMillis()
)
