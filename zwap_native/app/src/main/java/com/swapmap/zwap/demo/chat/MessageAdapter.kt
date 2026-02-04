package com.swapmap.zwap.demo.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.swapmap.zwap.R
import com.swapmap.zwap.demo.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Locale

class MessageAdapter(
    private val messages: List<ChatMessage>,
    private val currentUserId: String
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvUser: TextView = view.findViewById(R.id.tv_username)
        val tvTime: TextView = view.findViewById(R.id.tv_timestamp)
        val tvText: TextView = view.findViewById(R.id.tv_message_text)
        val ivAvatar: ImageView = view.findViewById(R.id.iv_user_avatar)
        // val ivImage: ImageView = view.findViewById(R.id.iv_message_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        
        holder.tvUser.text = message.username.ifEmpty { "User" }
        holder.tvText.text = message.text
        
        // Format timestamp
        if (message.created_at != null) {
            val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            holder.tvTime.text = sdf.format(message.created_at.toDate())
        } else {
            holder.tvTime.text = "Just now"
        }

        // Highlight own messages (optional visual cue)
        if (message.user_id == currentUserId) {
            holder.tvUser.setTextColor(android.graphics.Color.parseColor("#43B581")) // Discord Green
        } else {
            holder.tvUser.setTextColor(android.graphics.Color.WHITE)
        }
    }

    override fun getItemCount() = messages.size
}
