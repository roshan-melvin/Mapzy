package com.swapmap.zwap.demo.model

import com.google.gson.annotations.SerializedName

data class LeaderboardItem(
    @SerializedName("user_id") val userId: String,
    @SerializedName("trust_score") val trustScore: Double,
    @SerializedName("badge_level") val badgeLevel: String,
    @SerializedName("reward_points") val rewardPoints: Int,
    @SerializedName("accepted_reports") val acceptedReports: Int,
    @SerializedName("username") val username: String? // For UI display, can be email or masked ID
)

data class LeaderboardResponse(
    @SerializedName("leaderboard") val leaderboard: List<LeaderboardItem>,
    @SerializedName("count") val count: Int
)
