package com.swapmap.zwap.demo.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.swapmap.zwap.demo.model.Report
import kotlinx.coroutines.tasks.await

class ReportRepository {
    private val db = FirebaseFirestore.getInstance()
    private val reportsCollection = db.collection("reports")

    // Submit a new report to channel-specific subcollection
    suspend fun submitReport(report: Report) {
        // Extract channel name from hashtag (e.g., "#accident" -> "accident")
        val channelName = report.incidentType.removePrefix("#")
        
        // Store in: reports/{channel}/threads/{reportId}
        val channelDoc = reportsCollection.document(channelName)
        
        // Ensure channel document exists (for getFeedReports to find it)
        channelDoc.set(mapOf("lastUpdated" to com.google.firebase.Timestamp.now()))
        
        channelDoc.collection("threads")
            .document(report.id)
            .set(report)
            .await()
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
