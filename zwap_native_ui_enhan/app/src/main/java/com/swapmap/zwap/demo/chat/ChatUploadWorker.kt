package com.swapmap.zwap.demo.chat

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.swapmap.zwap.demo.db.AppDatabase
import kotlinx.coroutines.tasks.await

/**
 * Background worker for uploading chat messages to Firestore
 * Handles offline-first messaging with retry capability
 */
class ChatUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    private val db = FirebaseFirestore.getInstance()
    private val TAG = "ChatUploadWorker"
    
    override suspend fun doWork(): Result {
        val channelId = inputData.getString("channelId") ?: return Result.failure()
        val threadId = inputData.getString("threadId") ?: return Result.failure()
        val userId = inputData.getString("userId") ?: return Result.failure()
        val userName = inputData.getString("userName") ?: "User"
        val messageText = inputData.getString("messageText") ?: ""
        val imageUri = inputData.getString("imageUri")
        val messageType = inputData.getString("messageType") ?: "text"
        val messageId = inputData.getString("messageId") ?: return Result.failure()
        
        val localDb = AppDatabase.getDatabase(applicationContext)
        
        return try {
            // Update local status to Uploading
            localDb.pendingMessageDao().updateStatus(messageId, "Uploading")
            
            // Create message document
            val messageData = hashMapOf(
                "id" to messageId,
                "userId" to userId,
                "userName" to userName,
                "text" to messageText,
                "type" to messageType,
                "imageUrl" to imageUri,
                "timestamp" to com.google.firebase.Timestamp.now()
            )
            
            // Upload to Firestore
            db.collection("regions")
                .document(channelId)
                .collection("channels")
                .document(threadId)
                .collection("messages")
                .document(messageId)
                .set(messageData)
                .await()
            
            Log.d(TAG, "Message uploaded successfully: $messageId")
            
            // Delete from local DB on success
            localDb.pendingMessageDao().deleteById(messageId)
            
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload message: ${e.message}", e)
            
            // Update local status to Failed
            localDb.pendingMessageDao().updateStatus(messageId, "Failed")
            
            // Retry up to 3 times
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
