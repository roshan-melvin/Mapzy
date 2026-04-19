package com.swapmap.zwap.demo.community

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.swapmap.zwap.R
import com.swapmap.zwap.demo.model.Report
import java.text.SimpleDateFormat
import java.util.Locale

class ContributionAdapter(
    private val reports: List<Report>
) : RecyclerView.Adapter<ContributionAdapter.ReportViewHolder>() {

    class ReportViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvType: TextView = view.findViewById(R.id.tv_incident_type)
        val tvStatus: TextView = view.findViewById(R.id.tv_status_badge)
        val tvDesc: TextView = view.findViewById(R.id.tv_description)
        val tvDate: TextView = view.findViewById(R.id.tv_date)
        val tvPoints: TextView = view.findViewById(R.id.tv_points)
        val uploadOverlay: View = view.findViewById(R.id.upload_overlay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_report, parent, false)
        return ReportViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
        val report = reports[position]
        
        holder.tvType.text = report.incidentType
        holder.tvDesc.text = report.description
        holder.tvStatus.text = report.status
        
        // Status Badge Color
        val bgRes = when (report.status) {
            "Verified" -> R.drawable.rounded_bg_green
            "Rejected" -> R.drawable.rounded_bg_red
            else -> R.drawable.rounded_bg_yellow
        }
        holder.tvStatus.setBackgroundResource(bgRes)

        // Points
        if (report.pointsAwarded > 0) {
            holder.tvPoints.visibility = View.VISIBLE
            holder.tvPoints.text = "+${report.pointsAwarded} XP"
        } else {
            holder.tvPoints.visibility = View.GONE
        }

        // Date
        if (report.createdAt != null) {
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            holder.tvDate.text = sdf.format(report.createdAt.toDate())
        } else {
            holder.tvDate.text = "Just now"
        }
        
        // Upload Overlay (WhatsApp-style)
        when (report.status) {
            "Pending", "Uploading" -> {
                holder.uploadOverlay.visibility = View.VISIBLE
            }
            "Failed" -> {
                holder.uploadOverlay.visibility = View.GONE
                // Could add a retry button here later
            }
            else -> {
                holder.uploadOverlay.visibility = View.GONE
            }
        }
    }

    override fun getItemCount() = reports.size
}
