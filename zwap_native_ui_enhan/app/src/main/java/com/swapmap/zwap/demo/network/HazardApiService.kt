package com.swapmap.zwap.demo.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.GET
import retrofit2.http.Path

interface HazardApiService {
    @Multipart
    @POST("api/v1/reports")
    suspend fun submitReport(
        @Part("user_id") userId: RequestBody,
        @Part("hazard_type") hazardType: RequestBody,
        @Part("description") description: RequestBody,
        @Part("latitude") latitude: RequestBody,
        @Part("longitude") longitude: RequestBody,
        @Part("severity") severity: RequestBody,
        @Part image: MultipartBody.Part
    ): Response<ReportResponse>

    @GET("api/v1/users/{user_id}/stats")
    suspend fun getUserStats(
        @Path("user_id") userId: String
    ): Response<UserStatsResponse>
    @GET("api/v1/hazards")
    suspend fun getHazards(
        @retrofit2.http.Query("latitude") latitude: Double,
        @retrofit2.http.Query("longitude") longitude: Double,
        @retrofit2.http.Query("radius_km") radiusKm: Double
    ): Response<HazardClusterListResponse>

    @GET("api/v1/users/leaderboard")
    suspend fun getLeaderboard(
        @retrofit2.http.Query("limit") limit: Int = 10
    ): Response<com.swapmap.zwap.demo.model.LeaderboardResponse>
}

data class HazardClusterListResponse(
    val hazards: List<HazardClusterResponse>,
    val count: Int
)

data class HazardClusterResponse(
    val hazard_id: String,
    val hazard_type: String,
    val latitude: Double,
    val longitude: Double,
    val verified_image_count: Int,
    val confidence_score: Double,
    val status: String,
    val condition: String?
)

data class ReportResponse(
    val report_id: String,
    val status: String,
    val verification_score: Double,
    val ai_reasoning: String?,
    val image_url: String?,
    val hazard_condition: String?
)

data class UserStatsResponse(
    val user_id: String,
    val username: String,
    val trust_score: Double,
    val total_reports: Int,
    val accepted_reports: Int,
    val reward_points: Int,
    val badge_level: String
)
