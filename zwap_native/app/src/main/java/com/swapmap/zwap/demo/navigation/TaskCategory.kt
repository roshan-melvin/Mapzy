package com.swapmap.zwap.demo.navigation

/** Each category maps to an emoji icon, display label, background color, and OSM Overpass filter. */
enum class TaskCategory(
    val emoji: String,
    val label: String,
    val color: Int,           // background color as ARGB int (used for chip bg)
    val overpassFilter: String   // OSM tag to search via Overpass
) {
    GROCERY_SHOP(
        emoji = "🛒",
        label = "Grocery Shop",
        color = 0xFF4CAF50.toInt(),
        overpassFilter = """node["shop"="supermarket"]"""
    ),
    BARBER_SHOP(
        emoji = "✂️",
        label = "Barber Shop",
        color = 0xFF9C27B0.toInt(),
        overpassFilter = """node["shop"="hairdresser"]"""
    ),
    PETROL_BUNK(
        emoji = "⛽",
        label = "Petrol Bunk",
        color = 0xFFFF9800.toInt(),
        overpassFilter = """node["amenity"="fuel"]"""
    ),
    PHARMACY(
        emoji = "💊",
        label = "Pharmacy",
        color = 0xFF2196F3.toInt(),
        overpassFilter = """node["amenity"="pharmacy"]"""
    ),
    RESTAURANT(
        emoji = "🍽️",
        label = "Restaurant",
        color = 0xFFFF5722.toInt(),
        overpassFilter = """node["amenity"="restaurant"]"""
    ),
    ATM(
        emoji = "🏧",
        label = "ATM",
        color = 0xFF00BCD4.toInt(),
        overpassFilter = """node["amenity"="atm"]"""
    ),
    HOSPITAL(
        emoji = "🏥",
        label = "Hospital",
        color = 0xFFE53935.toInt(),
        overpassFilter = """node["amenity"="hospital"]"""
    ),
    MECHANIC(
        emoji = "🔧",
        label = "Mechanic",
        color = 0xFF607D8B.toInt(),
        overpassFilter = """node["shop"="car_repair"]"""
    );

    companion object {
        fun fromName(name: String?): TaskCategory? =
            values().firstOrNull { it.name.equals(name, ignoreCase = true) }

        /** Try to match Gemini's free-text response to a category */
        fun fromGeminiResponse(response: String): TaskCategory? {
            val r = response.trim().uppercase()
            return values().firstOrNull {
                r.contains(it.name) || r.contains(it.label.uppercase())
            }
        }

        val GEMINI_LIST_TEXT =
            values().joinToString(", ") { it.label }
    }
}
