package com.swapmap.zwap.demo.community

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.google.firebase.storage.FirebaseStorage
import com.swapmap.zwap.demo.community.models.*
import kotlinx.coroutines.tasks.await
import java.util.*

class ChatManager(private val context: Context) {
    
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    companion object {
        private const val TAG = "ChatManager"
        private const val COLLECTION_MESSAGES = "chat_messages"
        private const val COLLECTION_CHANNELS = "chat_channels"
        private const val COLLECTION_USERS = "users"
    }
    
    /**
     * Observe messages in a specific channel
     */
    fun observeChannelMessages(
        channelId: String,
        onUpdate: (List<ChatMessage>) -> Unit
    ): ListenerRegistration {
        return db.collection(COLLECTION_MESSAGES)
            .whereEqualTo("channel_id", channelId)
            .whereEqualTo("is_deleted", false)
            .orderBy("created_at", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing messages", error)
                    return@addSnapshotListener
                }
                
                val messages = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(ChatMessage::class.java)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing message", e)
                        null
                    }
                } ?: emptyList()
                
                onUpdate(messages)
            }
    }
    
    /**
     * Send a text message to a channel
     */
    suspend fun sendMessage(
        channelId: String,
        messageText: String,
        messageType: String = "text",
        mediaUri: Uri? = null,
        sharedLocation: Map<String, Double>? = null
    ): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
            
            // Upload media if provided
            val mediaUrl = mediaUri?.let { uploadChatMedia(it) }
            
            val messageId = UUID.randomUUID().toString()
            val message = ChatMessage(
                message_id = messageId,
                channel_id = channelId,
                user_id = user.uid,
                user_name = user.displayName ?: "Anonymous",
                user_photo = user.photoUrl?.toString() ?: "",
                message_text = messageText,
                message_type = messageType,
                media_url = mediaUrl,
                shared_location = sharedLocation,
                created_at = Timestamp.now()
            )
            
            db.collection(COLLECTION_MESSAGES)
                .document(messageId)
                .set(message.toMap())
                .await()
            
            Log.d(TAG, "Message sent successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            Result.failure(e)
        }
    }
    
    private suspend fun uploadChatMedia(mediaUri: Uri): String {
        val user = auth.currentUser ?: throw Exception("User not authenticated")
        val mediaId = UUID.randomUUID().toString()
        val storageRef = storage.reference
            .child("chat_media")
            .child(user.uid)
            .child("$mediaId.jpg")
        
        storageRef.putFile(mediaUri).await()
        return storageRef.downloadUrl.await().toString()
    }
    
    /**
     * Get user's assigned region
     */
    suspend fun getUserRegion(): String? {
        return try {
            val user = auth.currentUser ?: return null
            val userDoc = db.collection(COLLECTION_USERS)
                .document(user.uid)
                .get()
                .await()
            
            userDoc.getString("assigned_region")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user region", e)
            null
        }
    }
    
    /**
     * Switch user to a different region
     */
    suspend fun switchRegion(newRegionId: String): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
            
            db.collection(COLLECTION_USERS)
                .document(user.uid)
                .update("assigned_region", newRegionId)
                .await()
            
            Log.d(TAG, "Region switched successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error switching region", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get available channels for the user
     */
    suspend fun getAvailableChannels(): List<ChatChannel> {
        return try {
            val snapshot = db.collection(COLLECTION_CHANNELS)
                .whereEqualTo("is_active", true)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(ChatChannel::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing channel", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting channels", e)
            emptyList()
        }
    }
    
    /**
     * Delete a message (user can only delete their own)
     */
    suspend fun deleteMessage(messageId: String): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
            
            val messageDoc = db.collection(COLLECTION_MESSAGES)
                .document(messageId)
                .get()
                .await()
            
            if (messageDoc.getString("user_id") != user.uid) {
                return Result.failure(Exception("You can only delete your own messages"))
            }
            
            db.collection(COLLECTION_MESSAGES)
                .document(messageId)
                .update(
                    mapOf(
                        "is_deleted" to true,
                        "deleted_by" to user.uid
                    )
                )
                .await()
            
            Log.d(TAG, "Message deleted successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting message", e)
            Result.failure(e)
        }
    }
}
