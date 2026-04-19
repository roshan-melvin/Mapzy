package com.swapmap.zwap.demo.community

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.google.firebase.storage.FirebaseStorage
import com.mappls.sdk.geojson.Point
import com.swapmap.zwap.demo.community.models.*
import kotlinx.coroutines.tasks.await
import java.util.*

class CommunityManager(private val context: Context) {
    
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    companion object {
        private const val TAG = "CommunityManager"
        private const val COLLECTION_REPORTS = "reports"
        private const val COLLECTION_VOTES = "report_votes"
        private const val COLLECTION_REVIEWS = "reviews"
        private const val COLLECTION_USERS = "users"
    }
    
    /**
     * Submit a new community report (hazard or speed camera)
     */
    suspend fun submitReport(
        category: ReportCategory,
        hazardType: HazardType? = null,
        location: Point,
        placeName: String? = null,
        speedLimit: Int? = null,
        cameraDirection: String? = null,
        imageUri: Uri? = null
    ): Result<String> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
            
            // Upload image if provided
            val imageUrl = imageUri?.let { uploadReportImage(it) }
            
            // Create report document
            val reportId = UUID.randomUUID().toString()
            val report = CommunityReport(
                report_id = reportId,
                created_by = user.uid,
                created_by_name = user.displayName ?: "Anonymous",
                category = category.value,
                hazard_type = hazardType?.value,
                location = GeoPoint(location.latitude(), location.longitude()),
                place_name = placeName,
                speed_limit = speedLimit,
                camera_direction = cameraDirection,
                image_url = imageUrl,
                created_at = Timestamp.now(),
                expires_at = Timestamp(Date(System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000)) // 7 days
            )
            
            db.collection(COLLECTION_REPORTS)
                .document(reportId)
                .set(report.toMap())
                .await()
            
            Log.d(TAG, "Report submitted successfully: $reportId")
            Result.success(reportId)
        } catch (e: Exception) {
            Log.e(TAG, "Error submitting report", e)
            Result.failure(e)
        }
    }
    
    /**
     * Upload report image to Cloud Storage
     */
    private suspend fun uploadReportImage(imageUri: Uri): String {
        val user = auth.currentUser ?: throw Exception("User not authenticated")
        val reportId = UUID.randomUUID().toString()
        val storageRef = storage.reference
            .child("report_images")
            .child(user.uid)
            .child("$reportId.jpg")
        
        storageRef.putFile(imageUri).await()
        return storageRef.downloadUrl.await().toString()
    }
    
    /**
     * Observe reports in a specific bounding box (for map display)
     */
    fun observeReportsInArea(
        southWest: Point,
        northEast: Point,
        onUpdate: (List<CommunityReport>) -> Unit
    ): ListenerRegistration {
        // Note: Firestore doesn't support geoqueries natively
        // For production, use GeoFirestore or filter client-side
        return db.collection(COLLECTION_REPORTS)
            .whereEqualTo("status", "active")
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing reports", error)
                    return@addSnapshotListener
                }
                
                val reports = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(CommunityReport::class.java)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing report", e)
                        null
                    }
                } ?: emptyList()
                
                // Filter by bounding box client-side
                val filtered = reports.filter { report ->
                    val lat = report.location.latitude
                    val lng = report.location.longitude
                    lat >= southWest.latitude() && lat <= northEast.latitude() &&
                    lng >= southWest.longitude() && lng <= northEast.longitude()
                }
                
                onUpdate(filtered)
            }
    }
    
    /**
     * Submit a vote (upvote or downvote) on a report
     */
    suspend fun submitVote(
        reportId: String,
        voteType: VoteType
    ): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
            
            // Check if user already voted
            val voteId = "${reportId}_${user.uid}"
            val existingVote = db.collection(COLLECTION_VOTES)
                .document(voteId)
                .get()
                .await()
            
            if (existingVote.exists()) {
                return Result.failure(Exception("You have already voted on this report"))
            }
            
            // Check if user is voting on their own report
            val report = db.collection(COLLECTION_REPORTS)
                .document(reportId)
                .get()
                .await()
            
            if (report.getString("created_by") == user.uid) {
                return Result.failure(Exception("Cannot vote on your own report"))
            }
            
            // Create vote document
            val vote = ReportVote(
                report_id = reportId,
                user_id = user.uid,
                vote = voteType.value,
                created_at = Timestamp.now()
            )
            
            db.collection(COLLECTION_VOTES)
                .document(voteId)
                .set(vote.toMap())
                .await()
            
            Log.d(TAG, "Vote submitted successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error submitting vote", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get user statistics
     */
    suspend fun getUserStats(uid: String): UserStats? {
        return try {
            val userDoc = db.collection(COLLECTION_USERS)
                .document(uid)
                .get()
                .await()
            
            val statsMap = userDoc.get("stats") as? Map<*, *>
            statsMap?.let {
                UserStats(
                    total_reports = (it["total_reports"] as? Long)?.toInt() ?: 0,
                    hazard_reports = (it["hazard_reports"] as? Long)?.toInt() ?: 0,
                    speed_camera_reports = (it["speed_camera_reports"] as? Long)?.toInt() ?: 0,
                    total_reviews = (it["total_reviews"] as? Long)?.toInt() ?: 0,
                    total_votes = (it["total_votes"] as? Long)?.toInt() ?: 0,
                    verified_reports = (it["verified_reports"] as? Long)?.toInt() ?: 0
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user stats", e)
            null
        }
    }
    
    /**
     * Submit a review for a place
     */
    suspend fun submitReview(
        placeId: String,
        placeName: String,
        placeLocation: Point,
        starRating: Int,
        reviewText: String,
        imageUris: List<Uri> = emptyList()
    ): Result<String> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
            
            if (starRating < 1 || starRating > 5) {
                return Result.failure(Exception("Star rating must be between 1 and 5"))
            }
            
            // Upload images
            val imageUrls = imageUris.take(3).map { uploadReviewImage(it) }
            
            val reviewId = UUID.randomUUID().toString()
            val review = Review(
                review_id = reviewId,
                user_id = user.uid,
                user_name = user.displayName ?: "Anonymous",
                place_id = placeId,
                place_name = placeName,
                place_location = mapOf("lat" to placeLocation.latitude(), "lng" to placeLocation.longitude()),
                star_rating = starRating,
                review_text = reviewText,
                images = imageUrls,
                created_at = Timestamp.now()
            )
            
            db.collection(COLLECTION_REVIEWS)
                .document(reviewId)
                .set(review.toMap())
                .await()
            
            Log.d(TAG, "Review submitted successfully: $reviewId")
            Result.success(reviewId)
        } catch (e: Exception) {
            Log.e(TAG, "Error submitting review", e)
            Result.failure(e)
        }
    }
    
    private suspend fun uploadReviewImage(imageUri: Uri): String {
        val user = auth.currentUser ?: throw Exception("User not authenticated")
        val imageId = UUID.randomUUID().toString()
        val storageRef = storage.reference
            .child("review_images")
            .child(user.uid)
            .child("$imageId.jpg")
        
        storageRef.putFile(imageUri).await()
        return storageRef.downloadUrl.await().toString()
    }
    
    /**
     * Get reviews for a specific place
     */
    fun observePlaceReviews(
        placeId: String,
        onUpdate: (List<Review>) -> Unit
    ): ListenerRegistration {
        return db.collection(COLLECTION_REVIEWS)
            .whereEqualTo("place_id", placeId)
            .whereEqualTo("status", "active")
            .orderBy("created_at", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing reviews", error)
                    return@addSnapshotListener
                }
                
                val reviews = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(Review::class.java)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing review", e)
                        null
                    }
                } ?: emptyList()
                
                onUpdate(reviews)
            }
    }
}
