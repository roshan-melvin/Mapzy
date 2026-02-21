package com.swapmap.zwap.demo.community

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.swapmap.zwap.R
import com.swapmap.zwap.demo.model.UserNotification
import java.text.SimpleDateFormat
import java.util.Locale

class NotificationAdapter(
    private var notifications: MutableList<UserNotification>,
    private val onClick: (UserNotification) -> Unit,
    private val onSelectionChanged: (count: Int, inSelectionMode: Boolean) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    var isSelectionMode = false
        private set

    private val selectedIds = mutableSetOf<String>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val root: LinearLayout = view.findViewById(R.id.notif_root)
        val cbSelect: CheckBox = view.findViewById(R.id.cb_select)
        val ivIcon: ImageView = view.findViewById(R.id.iv_icon)
        val tvTitle: TextView = view.findViewById(R.id.tv_title)
        val tvMessage: TextView = view.findViewById(R.id.tv_message)
        val tvTimestamp: TextView = view.findViewById(R.id.tv_timestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return ViewHolder(view)
    }

    private fun getInternalItemId(position: Int): String {
        val n = notifications[position]
        return n.id.ifEmpty { "pos_$position" }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val notification = notifications[position]
        val notifId = getInternalItemId(position)

        holder.tvTitle.text = notification.title
        holder.tvMessage.text = notification.message

        val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        holder.tvTimestamp.text = notification.createdAt?.toDate()?.let { sdf.format(it) } ?: "Just now"

        // Icon based on type
        when (notification.type) {
            "reward" -> {
                holder.ivIcon.setImageResource(android.R.drawable.btn_star)
                holder.ivIcon.setColorFilter(android.graphics.Color.parseColor("#FAA61A"))
            }
            "verification" -> {
                holder.ivIcon.setImageResource(android.R.drawable.ic_dialog_info)
                holder.ivIcon.setColorFilter(android.graphics.Color.parseColor("#43B581")) // Green for verified
            }
            "system" -> {
                holder.ivIcon.setImageResource(android.R.drawable.ic_dialog_info)
                holder.ivIcon.setColorFilter(android.graphics.Color.parseColor("#5865F2"))
            }
            else -> {
                holder.ivIcon.setImageResource(android.R.drawable.ic_dialog_info)
                holder.ivIcon.setColorFilter(android.graphics.Color.parseColor("#B9BBBE"))
            }
        }

        // Selection mode UI
        if (isSelectionMode) {
            val isSelected = selectedIds.contains(notifId)
            holder.cbSelect.visibility = View.VISIBLE
            // Avoid triggering listeners while binding
            holder.cbSelect.setOnCheckedChangeListener(null)
            holder.cbSelect.isChecked = isSelected
            holder.root.setBackgroundColor(
                if (isSelected) android.graphics.Color.parseColor("#404855")
                else android.graphics.Color.parseColor("#36393F")
            )
        } else {
            holder.cbSelect.visibility = View.GONE
            holder.root.setBackgroundColor(android.graphics.Color.parseColor("#36393F"))
        }

        // Long press → enter selection mode and select this item
        holder.itemView.setOnLongClickListener {
            if (!isSelectionMode) {
                isSelectionMode = true
                selectedIds.add(notifId)
                onSelectionChanged(selectedIds.size, true)
                notifyDataSetChanged()
            }
            true
        }

        // Click: toggle selection in select mode, or open notification
        holder.itemView.setOnClickListener {
            if (isSelectionMode) {
                if (selectedIds.contains(notifId)) {
                    selectedIds.remove(notifId)
                } else {
                    selectedIds.add(notifId)
                }
                // Notify count (even if 0, we stay in selection mode — back arrow handles exit)
                onSelectionChanged(selectedIds.size, isSelectionMode)
                notifyItemChanged(position)
            } else {
                onClick(notification)
            }
        }

        // Checkbox click mirrors item click
        holder.cbSelect.setOnCheckedChangeListener { _, isChecked ->
            if (isSelectionMode) {
                if (isChecked) selectedIds.add(notifId) else selectedIds.remove(notifId)
                onSelectionChanged(selectedIds.size, isSelectionMode)
                notifyItemChanged(position)
            }
        }
    }

    /** Select all items. Also enters selection mode if not already. */
    fun selectAll() {
        isSelectionMode = true
        notifications.forEachIndexed { i, n ->
            val id = n.id.ifEmpty { "pos_$i" }
            selectedIds.add(id)
        }
        onSelectionChanged(selectedIds.size, true)
        notifyDataSetChanged()
    }

    /** Exit selection mode and clear selections. */
    fun exitSelectionMode() {
        isSelectionMode = false
        selectedIds.clear()
        onSelectionChanged(0, false)
        notifyDataSetChanged()
    }

    /** Returns the currently selected notifications list. */
    fun getSelectedNotifications(): List<UserNotification> {
        return notifications.filterIndexed { i, n ->
            val id = n.id.ifEmpty { "pos_$i" }
            selectedIds.contains(id)
        }
    }

    /** Remove notifications from the list after deletion. */
    fun removeNotifications(toRemove: List<UserNotification>) {
        notifications.removeAll(toRemove.toSet())
        exitSelectionMode()
    }

    fun updateData(newNotifications: List<UserNotification>) {
        notifications = newNotifications.toMutableList()
        selectedIds.clear()
        isSelectionMode = false
        notifyDataSetChanged()
    }

    fun getSelectedCount() = selectedIds.size

    override fun getItemCount() = notifications.size
}
