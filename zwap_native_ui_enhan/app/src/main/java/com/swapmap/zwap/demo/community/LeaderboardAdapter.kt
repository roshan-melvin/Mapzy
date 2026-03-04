package com.swapmap.zwap.demo.community

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.swapmap.zwap.R
import com.swapmap.zwap.demo.model.LeaderboardItem

class LeaderboardAdapter(private var items: List<LeaderboardItem>) : RecyclerView.Adapter<LeaderboardAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvRank: TextView = view.findViewById(R.id.tv_rank)
        val tvUser: TextView = view.findViewById(R.id.tv_username)
        val tvPoints: TextView = view.findViewById(R.id.tv_points)
        val tvBadge: TextView = view.findViewById(R.id.tv_badge)
        val tvAccuracy: TextView = view.findViewById(R.id.tv_accuracy)
    }

    fun updateData(newItems: List<LeaderboardItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_leaderboard, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvRank.text = (position + 1).toString()
        
        // Username
        holder.tvUser.text = item.username ?: "User ${item.userId.takeLast(4)}"
        
        holder.tvPoints.text = "${item.rewardPoints} pts"
        holder.tvBadge.text = item.badgeLevel
        holder.tvAccuracy.text = "Score: ${item.trustScore}%"

        // Rank colors
        when (position) {
            0 -> holder.tvRank.setTextColor(Color.parseColor("#FFD700")) // Gold
            1 -> holder.tvRank.setTextColor(Color.parseColor("#C0C0C0")) // Silver
            2 -> holder.tvRank.setTextColor(Color.parseColor("#CD7F32")) // Bronze
            else -> holder.tvRank.setTextColor(Color.WHITE)
        }

        // Badge colors
        when (item.badgeLevel) {
            "PLATINUM" -> holder.tvBadge.setTextColor(Color.parseColor("#E5E4E2"))
            "GOLD" -> holder.tvBadge.setTextColor(Color.parseColor("#FFD700"))
            "SILVER" -> holder.tvBadge.setTextColor(Color.parseColor("#C0C0C0"))
            "BRONZE" -> holder.tvBadge.setTextColor(Color.parseColor("#CD7F32"))
        }
    }

    override fun getItemCount() = items.size
}
