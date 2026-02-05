package com.swapmap.zwap.demo.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.swapmap.zwap.demo.model.Report
import kotlinx.coroutines.tasks.await
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

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

    // Submit a new report to channel-specific subcollection AND Supabase
    suspend fun submitReport(report: Report) {
        // 1. Submit to Firestore (UI Feed)
        val channelName = report.incidentType.removePrefix("#")
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
        val cleanChannelName = channelName.removePrefix("#")
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
