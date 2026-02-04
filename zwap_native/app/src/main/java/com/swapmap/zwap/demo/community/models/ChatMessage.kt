package com.swapmap.zwap.demo.community.models

import com.google.firebase.Timestamp

data class ChatMessage(
    val message_id: String = "",
    val channel_id: String = "",
    val user_id: String = "",
    val user_name: String = "",
    val user_photo: String = "",
    
    val message_text: String = "",
    val message_type: String = "text", // "text", "image", "location"
    val media_url: String? = null,
    
    // For location sharing
    val shared_location: Map<String, Double>? = null,
    
    val created_at: Timestamp? = null,
    val edited_at: Timestamp? = null,
    
    // Moderation
    val is_deleted: Boolean = false,
    val deleted_by: String? = null,
    val flagged_count: Int = 0
) {
    fun toMap(): Map<String, Any?> {
        return hashMapOf(
            "message_id" to message_id,
            "channel_id" to channel_id,
            "user_id" to user_id,
            "user_name" to user_name,
            "user_photo" to user_photo,
            "message_text" to message_text,
            "message_type" to message_type,
            "media_url" to media_url,
            "shared_location" to shared_location,
            "created_at" to created_at,
            "edited_at" to edited_at,
            "is_deleted" to is_deleted,
            "deleted_by" to deleted_by,
            "flagged_count" to flagged_count
        )
    }
}

data class ChatChannel(
    val channel_id: String = "",
    val channel_type: String = "", // "regional", "support", "global"
    val region_name: String = "",
    val member_count: Int = 0,
    val max_members: Int = 500,
    val created_at: Timestamp? = null,
    val is_active: Boolean = true
)

enum class ChannelType(val value: String) {
    REGIONAL("regional"),
    SUPPORT("support"),
    GLOBAL("global")
}
