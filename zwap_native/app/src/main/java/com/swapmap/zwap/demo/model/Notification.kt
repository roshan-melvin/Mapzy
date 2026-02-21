package com.swapmap.zwap.demo.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

// Renamed to UserNotification to avoid conflict with android.app.Notification
data class UserNotification(
    @DocumentId var id: String = "",  // Auto-populated from Firestore document ID
    var userId: String = "",
    var title: String = "",
    var message: String = "",
    var type: String = "info", // "verification", "reward", "system"
    var relatedReportId: String? = null,
    var isRead: Boolean = false,
    var createdAt: Timestamp? = null
)
