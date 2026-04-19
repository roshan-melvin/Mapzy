package com.swapmap.zwap.demo.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_messages")
data class PendingMessage(
    @PrimaryKey
    val id: String,
    val channelId: String,
    val threadId: String,
    val userId: String,
    val userName: String,
    val messageText: String,
    val messageType: String = "text",
    val imageUri: String? = null,
    val status: String = "Pending", // Pending, Uploading, Sent, Failed
    val createdAt: Long = System.currentTimeMillis()
)
