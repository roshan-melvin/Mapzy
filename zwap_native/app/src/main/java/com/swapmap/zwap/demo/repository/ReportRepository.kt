package com.swapmap.zwap.demo.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.swapmap.zwap.demo.model.Report
import kotlinx.coroutines.tasks.await
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import com.swapmap.zwap.demo.network.ApiClient
import com.swapmap.zwap.demo.network.ReportResponse

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class SupabaseReport(
    @SerialName("report_id") val reportId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("incident_type") val incidentType: String,
    val description: String,
    val severity: Int,
    val latitude: Double,
    val longitude: Double,
    @SerialName("image_url") val imageUrl: String,
    val status: String
)

class ReportRepository {
    private val db = FirebaseFirestore.getInstance()
    private val reportsCollection = db.collection("reports")

    private fun normalizeChannelName(raw: String): String {
        return raw
            .removePrefix("#")
            .trim()
            .lowercase()
            .replace("_", "-")
            .replace(" ", "-")
    }

    // Submit a new report to channel-specific subcollection AND Supabase
    suspend fun submitReport(report: Report) {
        // 1. Submit to Firestore (UI Feed)
        val channelName = normalizeChannelName(report.incidentType)
        val channelDoc = reportsCollection.document(channelName)
        channelDoc.set(mapOf("lastUpdated" to com.google.firebase.Timestamp.now()))
        
        channelDoc.collection("threads")
            .document(report.id)
            .set(report)
            .await()

        try {
            // 2. Submit to Supabase (AI Verification) - Using concrete Data Class
            val supabaseReport = SupabaseReport(
                reportId = report.id,
                userId = report.userId,
                incidentType = report.incidentType,
                description = report.description,
                severity = 1, // Default severity
                latitude = report.latitude,
                longitude = report.longitude,
                imageUrl = report.imageUrl ?: "",
                status = "Pending"
            )
            
            com.swapmap.zwap.demo.network.SupabaseManager.client
                .from("reports_analysis")
                .insert(supabaseReport)
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("SupabaseError", "Failed to upload: ${e.message}")
            // Show error on UI for debugging
            kotlinx.coroutines.MainScope().launch {
                android.widget.Toast.makeText(
                    com.swapmap.zwap.demo.ZwapApplication.instance,
                    "Supabase Sync Failed: ${e.message}", 
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Submit report with image file to FastAPI Backend
    suspend fun submitReportToBackend(
        userId: String,
        hazardType: String,
        description: String,
        lat: Double,
        lng: Double,
        imageFile: File
    ): ReportResponse? {
        val userIdReq = userId.toRequestBody("text/plain".toMediaTypeOrNull())
        val hazardTypeReq = hazardType.toRequestBody("text/plain".toMediaTypeOrNull())
        val descriptionReq = description.toRequestBody("text/plain".toMediaTypeOrNull())
        val latReq = lat.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val lngReq = lng.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val severityReq = "1".toRequestBody("text/plain".toMediaTypeOrNull())

        val imagePart = MultipartBody.Part.createFormData(
            "image",
            imageFile.name,
            imageFile.asRequestBody("image/*".toMediaTypeOrNull())
        )

        val response = ApiClient.hazardApiService.submitReport(
            userIdReq, hazardTypeReq, descriptionReq, latReq, lngReq, severityReq, imagePart
        )

        if (response.isSuccessful && response.body() != null) {
            val backendRes = response.body()!!
            
            val safeHazardType = normalizeChannelName(hazardType)
            
            // Sync to Firestore for real-time feed
            val report = Report(
                id = backendRes.report_id,
                userId = userId,
                incidentType = safeHazardType,
                description = description,
                latitude = lat,
                longitude = lng,
                imageUrl = backendRes.image_url,
                status = backendRes.status,
                pointsAwarded = if (backendRes.status == "Verified") 2 else 0,
                hazardCondition = backendRes.hazard_condition ?: "active",
                createdAt = com.google.firebase.Timestamp.now()
            )
            
            reportsCollection.document(safeHazardType)
                .collection("threads")
                .document(report.id)
                .set(report)
                .await()
                
            return backendRes
        } else {
            val errorBody = response.errorBody()?.string()
            android.util.Log.e("BackendError", "Failed: $errorBody")
            throw Exception("Backend error: ${response.code()}")
        }
    }


    // Get reports for a specific user across all channels
    suspend fun getUserReports(userId: String): List<Report> {
        val allReports = mutableListOf<Report>()
        
        // Query all channel documents
        val channels = reportsCollection.get().await()
        
        for (channel in channels.documents) {
            val channelReports = channel.reference
                .collection("threads")
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()
                .toObjects(Report::class.java)
            
            allReports.addAll(channelReports)
        }
        
        return allReports.sortedByDescending { it.createdAt }
    }

    // Get reports for a specific channel
    fun getChannelReports(channelName: String): Query {
        val cleanChannelName = normalizeChannelName(channelName)
        return reportsCollection
            .document(cleanChannelName)
            .collection("threads")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
    }

    // Get all recent reports across all channels for feed
    suspend fun getFeedReports(): List<Report> {
        val allReports = mutableListOf<Report>()
        
        // Query all channel documents
        val channels = reportsCollection.get().await()
        
        for (channel in channels.documents) {
            val channelReports = channel.reference
                .collection("threads")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(10) // Limit per channel
                .get()
                .await()
                .toObjects(Report::class.java)
            
            allReports.addAll(channelReports)
        }
        
        return allReports.sortedByDescending { it.createdAt }.take(50)
    }
}
