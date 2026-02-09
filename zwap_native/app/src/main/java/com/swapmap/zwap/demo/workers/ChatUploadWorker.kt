package com.swapmap.zwap.demo.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.firestore.FirebaseFirestore
import com.swapmap.zwap.R
import com.swapmap.zwap.demo.community.models.ChatMessage
import kotlinx.coroutines.tasks.await
import android.content.pm.ServiceInfo
import com.swapmap.zwap.demo.db.AppDatabase
import com.swapmap.zwap.demo.db.PendingMessage
import java.util.UUID

class ChatUploadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val channelId = inputData.getString("channelId") ?: return Result.failure()
        val threadId = inputData.getString("threadId") ?: return Result.failure()
        val userId = inputData.getString("userId") ?: return Result.failure()
        val userName = inputData.getString("userName") ?: "Anonymous"
        val messageText = inputData.getString("messageText") ?: ""
        val imageUriString = inputData.getString("imageUri")
        val messageType = inputData.getString("messageType") ?: (if (imageUriString != null) "image" else "text")
        val messageId = inputData.getString("messageId") ?: UUID.randomUUID().toString()

        val dao = AppDatabase.getDatabase(applicationContext).pendingMessageDao()

        // Ensure exists or update
        var pendingMsg = dao.getMessageById(messageId)
        if (pendingMsg == null) {
            pendingMsg = PendingMessage(
                id = messageId,
                channelId = channelId,
                threadId = threadId,
                userId = userId,
                userName = userName,
                messageText = messageText,
                messageType = if (imageUriString != null) "image" else "text",
                imageUri = imageUriString,
                status = "Sending"
            )
            dao.insert(pendingMsg)
        } else {
            pendingMsg = pendingMsg.copy(status = "Sending")
            dao.update(pendingMsg)
        }
        
        // 1. Setup Notification
        setForeground(createForegroundInfo())
        
        try {
            // 2. Upload Logic
            var downloadUrl = imageUriString ?: ""
            
            // If it's a local file path (starts with file://), upload to Firebase Storage
            if (imageUriString != null && imageUriString.startsWith("file://")) {
                 val fileUri = android.net.Uri.parse(imageUriString)
                 val filePath = fileUri.path
                 
                 // Debug: Check if file exists
                 android.util.Log.d("ChatUploadWorker", "Attempting upload for: $imageUriString")
                 android.util.Log.d("ChatUploadWorker", "File path: $filePath")
                 
                 if (filePath != null) {
                     val file = java.io.File(filePath)
                     android.util.Log.d("ChatUploadWorker", "File exists: ${file.exists()}, Size: ${file.length()} bytes")
                     
                     if (!file.exists()) {
                         throw java.io.FileNotFoundException("Cached file not found: $filePath")
                     }
                 }
                 
                 val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference
                 val extension = if (imageUriString.endsWith(".mp4")) "mp4" else "jpg"
                 val ref = storageRef.child("chat_media/${UUID.randomUUID()}.$extension")
                 
                 android.util.Log.d("ChatUploadWorker", "Uploading to: ${ref.path}")
                 
                 // Perform Upload (Blocking)
                 ref.putFile(fileUri).await()
                 downloadUrl = ref.downloadUrl.await().toString()
                 
                 android.util.Log.d("ChatUploadWorker", "Upload complete, URL: $downloadUrl")
            }
            
            // 3. Prepare Firestore Message
            val db = FirebaseFirestore.getInstance()
            
            val message = hashMapOf(
                "channel_id" to channelId,
                "user_id" to userId,
                "username" to userName,
                "text" to messageText,
                "type" to messageType,
                "image_url" to downloadUrl,
                "created_at" to com.google.firebase.Timestamp.now()
            )

            // 4. Write to Firestore: chat/{regionId}/threads/{threadId}/messages
            db.collection("chat")
                .document(channelId)
                .collection("threads")
                .document(threadId)
                .collection("messages")
                .document(messageId)
                .set(message)
                .await()
            
            // Success: Delete from local DB
            dao.delete(pendingMsg)
            
            // Delete cached file to free space
            if (imageUriString != null && imageUriString.startsWith("file://")) {
                try {
                    val file = java.io.File(android.net.Uri.parse(imageUriString).path!!)
                    if (file.exists()) file.delete()
                } catch (e: Exception) { 
                    android.util.Log.e("ChatUploadWorker", "Failed to delete cache file", e)
                }
            }

            android.util.Log.d("ChatUploadWorker", "Upload successful for message $messageId")
            return Result.success()
            
        } catch (e: Exception) {
            android.util.Log.e("ChatUploadWorker", "Upload failed for message $messageId", e)
            e.printStackTrace()
            // Mark as Failed
            dao.update(pendingMsg.copy(status = "Failed"))
            showErrorNotification()
            return Result.failure()
        }
    }

    private fun createForegroundInfo(): androidx.work.ForegroundInfo {
        val channelId = "chat_upload_channel"
        val title = "Sending Message..."
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Chat Messages",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setSmallIcon(R.drawable.ic_contribute)
            .setOngoing(true)
            .setProgress(0, 0, true) // Indeterminate progress
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            androidx.work.ForegroundInfo(200, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            androidx.work.ForegroundInfo(200, notification)
        }
    }

    private fun showErrorNotification() {
         val notification = NotificationCompat.Builder(applicationContext, "chat_upload_channel")
            .setContentTitle("Message Failed")
            .setContentText("Tap to retry sending.")
            .setSmallIcon(R.drawable.ic_hazard)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(300, notification)
    }
}
