package com.swapmap.zwap.demo.model

data class SpeedCamera(
    val id: Long,
    val lat: Double,
    val lon: Double,
    val type: String = "speed_camera"
)
