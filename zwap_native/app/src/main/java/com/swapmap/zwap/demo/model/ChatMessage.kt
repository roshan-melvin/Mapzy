package com.swapmap.zwap.demo.model

import com.google.firebase.Timestamp

data class ChatMessage(
    val id: String = "",
    val channel_id: String = "",
    val user_id: String = "",
    val username: String = "",
    val user_avatar: String = "",
    val text: String = "",
    val image_url: String? = null,
    val type: String = "text",
    val created_at: Timestamp? = null,
    
    // Local Upload State (Not stored in Firestore)
    @get:com.google.firebase.firestore.Exclude var localUri: android.net.Uri? = null,
    @get:com.google.firebase.firestore.Exclude var isUploading: Boolean = false,
    @get:com.google.firebase.firestore.Exclude var uploadProgress: Int = 0,
    @get:com.google.firebase.firestore.Exclude var localStatus: String? = null // "Pending", "Failed"
)
