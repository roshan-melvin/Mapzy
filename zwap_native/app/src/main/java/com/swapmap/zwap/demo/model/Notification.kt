package com.swapmap.zwap.demo.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

// Renamed to UserNotification to avoid conflict with android.app.Notification
data class UserNotification(
    @DocumentId val id: String = "",  // Auto-populated from Firestore document ID
    val userId: String = "",
    val title: String = "",
    val message: String = "",
    val type: String = "info", // "verification", "reward", "system"
    val relatedReportId: String? = null,
    val isRead: Boolean = false,
    val createdAt: Timestamp? = null
)
