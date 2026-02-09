package com.swapmap.zwap.demo.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.swapmap.zwap.R
import com.swapmap.zwap.demo.model.Report
import com.swapmap.zwap.demo.repository.ReportRepository
import com.google.firebase.Timestamp
import kotlinx.coroutines.delay
import android.content.pm.ServiceInfo
import com.swapmap.zwap.demo.db.AppDatabase
import com.swapmap.zwap.demo.db.PendingReport

class UploadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val reportId = inputData.getString("reportId") ?: return Result.failure()
        val userId = inputData.getString("userId") ?: return Result.failure()
        val incidentType = inputData.getString("incidentType") ?: return Result.failure()
        val description = inputData.getString("description") ?: ""
        val latitude = inputData.getDouble("latitude", 0.0)
        val longitude = inputData.getDouble("longitude", 0.0)
        val imageUriString = inputData.getString("imageUri")
        
        // Reconstruct Report object
        val report = Report(
            id = reportId,
            userId = userId,
            incidentType = incidentType,
            description = description,
            latitude = latitude,
            longitude = longitude,
            imageUrl = imageUriString, // Temporarily use URI, Repository handles upload
            status = "Pending",
            createdAt = Timestamp.now()
        )

        // Get DB Instance
        val dao = AppDatabase.getDatabase(applicationContext).pendingReportDao()
        
        // Ensure the report exists in DB (just in case) or update it
        var pendingReport = dao.getReportById(reportId)
        if (pendingReport == null) {
             // If not found, recreate it (edge case)
             pendingReport = PendingReport(
                id = reportId, userId = userId, incidentType = incidentType, 
                description = description, latitude = latitude, longitude = longitude, 
                imageUri = imageUriString, status = "Uploading"
             )
             dao.insert(pendingReport)
        }

        setForeground(createForegroundInfo(0))

        return try {
            // Simulate Upload Progress & Update DB
            for (i in 0..100 step 20) {
                setProgress(workDataOf("Progress" to i))
                setForeground(createForegroundInfo(i))
                
                // Update DB Progress
                pendingReport = pendingReport!!.copy(progress = i, status = "Uploading")
                dao.update(pendingReport!!)
                
                delay(500) 
            }

            // Actual Upload logic
            val repository = ReportRepository()
            repository.submitReport(report)

            // Final Success: Remove from local DB as it's now online
            dao.delete(pendingReport!!)

            // Final Success Notification
            showSuccessNotification()
            
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            
            // On Failure: Mark as Failed in DB for "Retry" UI
            if (pendingReport != null) {
                dao.update(pendingReport!!.copy(status = "Failed"))
            }
            
            showErrorNotification()
            Result.failure() // Use failure so it stays in DB as "Failed" state for manual retry
        }
    }

    private fun createForegroundInfo(progress: Int): androidx.work.ForegroundInfo {
        val channelId = "upload_channel"
        val title = "Uploading Report"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Uploads",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setTicker(title)
            .setContentText("$progress%")
            .setSmallIcon(R.drawable.ic_contribute)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            androidx.work.ForegroundInfo(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
             androidx.work.ForegroundInfo(1, notification)
        }
    }
    
    private fun showSuccessNotification() {
        val notification = NotificationCompat.Builder(applicationContext, "upload_channel")
            .setContentTitle("Upload Complete")
            .setContentText("Your report has been submitted.")
            .setSmallIcon(R.drawable.ic_contribute)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(2, notification)
    }

    private fun showErrorNotification() {
         val notification = NotificationCompat.Builder(applicationContext, "upload_channel")
            .setContentTitle("Upload Failed")
            .setContentText("Tap to retry.")
            .setSmallIcon(R.drawable.ic_hazard)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(3, notification)
    }
}
