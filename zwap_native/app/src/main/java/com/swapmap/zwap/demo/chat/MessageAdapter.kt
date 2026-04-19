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
    messagesIn: List<ChatMessage>,
    private val currentUserId: String,
    private val onRetry: (ChatMessage) -> Unit = {}
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    private val messages = messagesIn.toMutableList()

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvUser: TextView = view.findViewById(R.id.tv_username)
        val tvTime: TextView = view.findViewById(R.id.tv_timestamp)
        val tvText: TextView = view.findViewById(R.id.tv_message_text)
        val ivAvatar: ImageView = view.findViewById(R.id.iv_user_avatar)
        val ivImage: ImageView = view.findViewById(R.id.iv_message_image)
        val vvVideo: android.widget.VideoView = view.findViewById(R.id.vv_message_video)
        val ivPlayIcon: ImageView = view.findViewById(R.id.iv_play_icon)
        val uploadOverlay: View = view.findViewById(R.id.upload_overlay)
        val progressBar: android.widget.ProgressBar = view.findViewById(R.id.pb_upload_progress)
        val tvPercent: TextView = view.findViewById(R.id.tv_upload_percent)
        val retryOverlay: View = view.findViewById(R.id.retry_overlay)
        val ivRetry: ImageView = view.findViewById(R.id.iv_retry)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        
        holder.tvUser.text = message.username.ifEmpty { "User" }
        
        // Reset Visibility
        holder.ivImage.visibility = View.GONE
        holder.vvVideo.visibility = View.GONE
        holder.tvText.visibility = View.GONE
        holder.ivPlayIcon.visibility = View.GONE
        holder.uploadOverlay.visibility = View.GONE
        holder.retryOverlay.visibility = View.GONE
        
        // Handle Local Upload State (uploading)
        if (message.isUploading) {
             holder.uploadOverlay.visibility = View.VISIBLE
             val safeProgress = message.uploadProgress.coerceIn(0, 100)
             holder.progressBar.progress = safeProgress
             holder.tvPercent.text = "${safeProgress}%"
        }
        
        // Handle Failed State (only for media messages)
        val isMediaMessage = message.type == "image" || message.type == "video"
        if (message.localStatus == "Failed" && isMediaMessage) {
            holder.retryOverlay.visibility = View.VISIBLE
            holder.retryOverlay.setOnClickListener {
                onRetry(message)
            }
            holder.ivRetry.setOnClickListener {
                onRetry(message)
            }
        }
        
        // ... rest of binding ...
        
        // Determine Media Source (Local URI or Remote URL)
        val mediaUri = message.localUri?.toString() ?: message.image_url

        when (message.type) {
            "image" -> {
                if (!mediaUri.isNullOrEmpty()) {
                     holder.ivImage.visibility = View.VISIBLE
                     com.bumptech.glide.Glide.with(holder.itemView.context)
                        .load(mediaUri)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .centerCrop()
                        .into(holder.ivImage)
                } else holder.tvText.visibility = View.VISIBLE
            }
            "video" -> {
                if (!mediaUri.isNullOrEmpty()) {
                    // Show Thumbnail (Image View)
                    holder.ivImage.visibility = View.VISIBLE
                    holder.ivPlayIcon.visibility = View.VISIBLE
                    
                    // Load Video Thumbnail using Glide
                    com.bumptech.glide.Glide.with(holder.itemView.context)
                        .load(mediaUri) // Glide loads video frame automatically
                        .centerCrop()
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .into(holder.ivImage)

                    // Setup video playback
                    holder.vvVideo.setVideoURI(android.net.Uri.parse(mediaUri))
                    
                    val playVideo = {
                         holder.ivImage.visibility = View.GONE
                         holder.ivPlayIcon.visibility = View.GONE
                         holder.vvVideo.visibility = View.VISIBLE
                         holder.vvVideo.start()
                    }

                    holder.ivImage.setOnClickListener { playVideo() }
                    holder.ivPlayIcon.setOnClickListener { playVideo() }
                    
                    holder.vvVideo.setOnCompletionListener {
                         // On finish, show thumbnail again
                         holder.vvVideo.visibility = View.GONE
                         holder.ivImage.visibility = View.VISIBLE
                         holder.ivPlayIcon.visibility = View.VISIBLE
                    }
                    
                    // Allow pausing/resuming via VideoView click
                    holder.vvVideo.setOnClickListener {
                         if (holder.vvVideo.isPlaying) holder.vvVideo.pause()
                         else holder.vvVideo.start()
                    }

                } else holder.tvText.visibility = View.VISIBLE
            }
            else -> { // "text"
                holder.tvText.visibility = View.VISIBLE
                holder.tvText.text = message.text
            }
        }
        
        // Format timestamp
        if (message.created_at != null) {
            val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            holder.tvTime.text = sdf.format(message.created_at.toDate())
        } else {
            holder.tvTime.text = "Just now"
        }

        // Highlight own messages (optional visual cue)
        if (message.user_id == currentUserId) {
            holder.tvUser.setTextColor(android.graphics.Color.parseColor("#43B581"))
        } else {
            holder.tvUser.setTextColor(android.graphics.Color.WHITE)
        }
    }
    
    fun addLocalMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }
    
    fun updateUploadProgress(id: String, progress: Int) {
        val index = messages.indexOfFirst { it.id == id }
        if (index != -1) {
            messages[index].uploadProgress = progress
            notifyItemChanged(index)
        }
    }
    
    fun removeLocalMessage(id: String) {
        val index = messages.indexOfFirst { it.id == id }
        if (index != -1) {
            messages.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    override fun getItemCount() = messages.size
}
