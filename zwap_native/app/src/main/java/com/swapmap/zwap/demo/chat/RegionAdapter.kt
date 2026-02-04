package com.swapmap.zwap.demo.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.swapmap.zwap.R
import java.util.Locale

class RegionAdapter(
    private val regionIds: List<String>,
    private val onRegionClick: (String) -> Unit
) : RecyclerView.Adapter<RegionAdapter.RegionViewHolder>() {

    class RegionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvInitial: TextView = view.findViewById(R.id.tv_region_initial)
        val selectionOverlay: View = view.findViewById(R.id.selection_overlay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RegionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_region, parent, false)
        return RegionViewHolder(view)
    }

    override fun onBindViewHolder(holder: RegionViewHolder, position: Int) {
        val regionId = regionIds[position]
        
        // Example: india-tamil-nadu-chennai -> "C"
        val parts = regionId.split("-")
        val initial = if (parts.isNotEmpty()) {
            parts.last().take(1).uppercase()
        } else {
            "?"
        }
        
        holder.tvInitial.text = initial
        
        // TODO: Handle selection state visual if needed (using onRegionClick to track selected index)

        holder.itemView.setOnClickListener {
            onRegionClick(regionId)
        }
    }

    override fun getItemCount() = regionIds.size
}
