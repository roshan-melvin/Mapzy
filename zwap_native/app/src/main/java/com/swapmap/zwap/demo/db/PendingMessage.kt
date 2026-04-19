package com.swapmap.zwap.demo.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_messages")
data class PendingMessage(
    @PrimaryKey val id: String, // UUID for local identification
    val channelId: String,
    val threadId: String,
    val userId: String,
    val userName: String,
    val messageText: String,
    val messageType: String, // text, image
    val imageUri: String?,   // Local URI
    val status: String = "Pending", // Pending, Sending, Failed
    val createdAt: Long = System.currentTimeMillis()
)
