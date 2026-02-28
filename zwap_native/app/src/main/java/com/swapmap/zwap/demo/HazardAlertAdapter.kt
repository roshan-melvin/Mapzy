package com.swapmap.zwap.demo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.swapmap.zwap.R
import com.swapmap.zwap.demo.model.FeatureType

data class HazardAlert(
    val id: Long,
    var type: FeatureType,
    var name: String,
    val distance: Int,
    var crossed: Boolean = false
)

class HazardAlertAdapter(
    private val hazards: MutableList<HazardAlert>,
    private val onHazardCrossed: (HazardAlert) -> Unit
) : RecyclerView.Adapter<HazardAlertAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconView: TextView = view.findViewById(R.id.tv_hazard_icon)
        val nameView: TextView = view.findViewById(R.id.tv_hazard_name)
        val distanceView: TextView = view.findViewById(R.id.tv_hazard_distance)
        val badgeView: TextView = view.findViewById(R.id.tv_distance_badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hazard_alert, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val hazard = hazards[position]
        
        // Set icon based on type
        holder.iconView.text = when (hazard.type) {
            FeatureType.SPEED_CAMERA -> "📷"
            FeatureType.TRAFFIC_CALMING -> "⚠️"
            FeatureType.STOP_SIGN -> "🛑"
            FeatureType.GIVE_WAY -> "🔺"
            FeatureType.TOLL -> "💰"
            FeatureType.COMMUNITY_VERIFIED -> "✅"
            FeatureType.COMMUNITY_NEEDS_REVALIDATION -> "⏳"
            else -> "⚠️"
        }
        
        // Set name
        // Set name (which contains type and confidence score)
        holder.nameView.text = hazard.name
        
        // Set distance
        holder.distanceView.text = "${hazard.distance}m ahead"
        holder.badgeView.text = "${hazard.distance}m"
        
        // Update badge color based on distance
        val badgeBg = when {
            hazard.distance < 50 -> android.graphics.Color.parseColor("#F44336") // Red
            hazard.distance < 150 -> android.graphics.Color.parseColor("#FF9800") // Orange
            else -> android.graphics.Color.parseColor("#4CAF50") // Green
        }
        holder.badgeView.background.setTint(badgeBg)
    }

    override fun getItemCount() = hazards.size

    fun updateHazard(id: Long, newDistance: Int) {
        val index = hazards.indexOfFirst { it.id == id }
        if (index >= 0) {
            hazards[index] = hazards[index].copy(distance = newDistance)
            notifyItemChanged(index)
        }
    }

    fun removeHazard(id: Long) {
        val index = hazards.indexOfFirst { it.id == id }
        if (index >= 0) {
            val hazard = hazards.removeAt(index)
            notifyItemRemoved(index)
            onHazardCrossed(hazard)
        }
    }

    fun addHazard(hazard: HazardAlert) {
        if (hazards.none { it.id == hazard.id }) {
            hazards.add(hazard)
            hazards.sortBy { it.distance }
            notifyDataSetChanged()
        }
    }

    fun clear() {
        hazards.clear()
        notifyDataSetChanged()
    }

    fun getHazards() = hazards.toList()
}
