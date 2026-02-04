package com.swapmap.zwap.demo.model

import com.google.firebase.Timestamp

data class Channel(
    val id: String = "",
    val region_id: String = "",
    val name: String = "",
    val description: String = "",
    val type: String = "text",
    val created_at: Timestamp? = null
)
