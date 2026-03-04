package com.swapmap.zwap.demo.model

data class OSMFeature(
    val id: Long,
    val lat: Double,
    val lon: Double,
    var type: FeatureType,
    var name: String? = null,
    val tags: Map<String, String> = emptyMap()
)

enum class FeatureType(val osmTag: String, val color: String, val icon: String) {
    SPEED_CAMERA("highway=speed_camera", "#ef4444", "🚨"),
    TRAFFIC_CALMING("traffic_calming", "#f59e0b", "⚠️"),
    STOP_SIGN("highway=stop", "#ef4444", "🛑"),
    GIVE_WAY("highway=give_way", "#f59e0b", "⚠️"),
    TOLL("barrier=toll_booth", "#8b5cf6", "💰"),
    COMMUNITY_VERIFIED("community=verified", "#22c55e", "✅"),
    COMMUNITY_NEEDS_REVALIDATION("community=revalidate", "#eab308", "⏳");
    
    companion object {
        fun fromOSMTag(key: String, value: String): FeatureType? {
            val tag = "$key=$value"
            return when (tag) {
                "highway=speed_camera" -> SPEED_CAMERA
                "highway=stop" -> STOP_SIGN
                "highway=give_way" -> GIVE_WAY
                "barrier=toll_booth" -> TOLL
                else -> when (key) {
                    "traffic_calming" -> TRAFFIC_CALMING
                    else -> null
                }
            }
        }
    }
}

data class OverpassResponse(
    val version: Double,
    val elements: List<OverpassElement>
)

data class OverpassElement(
    val type: String,
    val id: Long,
    val lat: Double,
    val lon: Double,
    val tags: Map<String, String>? = null
)
