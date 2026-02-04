package com.swapmap.zwap.demo.community.models

data class UserStats(
    val total_reports: Int = 0,
    val hazard_reports: Int = 0,
    val speed_camera_reports: Int = 0,
    val total_reviews: Int = 0,
    val total_votes: Int = 0,
    val verified_reports: Int = 0
)

data class ReportVote(
    val report_id: String = "",
    val user_id: String = "",
    val vote: String = "", // "upvote" or "downvote"
    val created_at: com.google.firebase.Timestamp? = null
) {
    fun toMap(): Map<String, Any?> {
        return hashMapOf(
            "report_id" to report_id,
            "user_id" to user_id,
            "vote" to vote,
            "created_at" to created_at
        )
    }
}

