package com.swapmap.zwap.demo.community.models

enum class ReportCategory(val value: String) {
    HAZARD("hazard"),
    SPEED_CAMERA("speed_camera"),
    POLICE("police"),
    TRAFFIC("traffic"),
    MAP_ISSUE("map_issue")
}

enum class HazardType(val value: String, val displayName: String) {
    ACCIDENT("accident", "Accident"),
    CONSTRUCTION("construction", "Construction"),
    POTHOLE("pothole", "Pothole"),
    VEHICLE_STOPPED("vehicle_stopped", "Vehicle Stopped"),
    OBJECT_ON_ROAD("object_on_road", "Object on Road"),
    OTHER("other", "Other")
}

enum class VoteType(val value: String) {
    UPVOTE("upvote"),
    DOWNVOTE("downvote")
}
