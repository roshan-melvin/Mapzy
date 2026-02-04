package com.swapmap.zwap.demo.community

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.swapmap.zwap.R
import android.graphics.Color
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.cardview.widget.CardView

class CommunityFragment : Fragment() {

    private var mapView: com.mappls.sdk.maps.MapView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_community, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        mapView = view.findViewById(R.id.thread_map_preview)
        mapView?.onCreate(savedInstanceState)

        // Setup Servers (Sidebar)
        val rvServers = view.findViewById<RecyclerView>(R.id.rv_servers)
        rvServers.layoutManager = LinearLayoutManager(context)
        
        // Sidebar Data: ID, IconRes, Name
        val sidebarItems = listOf(
            SidebarItem("hazards", android.R.drawable.ic_dialog_alert, "Hazards"), 
            SidebarItem("cameras", android.R.drawable.ic_menu_camera, "Speed Cameras"),
            SidebarItem("leaderboard", android.R.drawable.btn_star, "Leaderboard"),
            SidebarItem("activity", android.R.drawable.ic_menu_my_calendar, "My Activity"),
            SidebarItem("settings", android.R.drawable.ic_menu_preferences, "Settings"),
            SidebarItem("add", android.R.drawable.ic_input_add, "Create Report")
        )

        val channelMap = mapOf(
            "hazards" to listOf("all-hazards", "accident", "construction", "waterlogging", "fallen-tree", "other"),
            "cameras" to listOf("fixed-camera", "mobile-camera", "recently-added"),
            "leaderboard" to listOf("global-ranking", "friends"),
            "activity" to listOf("my-reports", "notifications"),
            "settings" to listOf("profile", "app-settings")
        )

        val rvChannels = view.findViewById<RecyclerView>(R.id.rv_channels)
        val rvFeed = view.findViewById<RecyclerView>(R.id.rv_feed)
        val btnBack = view.findViewById<ImageView>(R.id.btn_back_channels)
        val tvTitle = view.findViewById<TextView>(R.id.tv_server_name)
        val threadView = view.findViewById<View>(R.id.thread_view_container)
        
        // Thread View Controls
        val btnThreadBack = view.findViewById<ImageView>(R.id.btn_thread_back)
        val rvComments = view.findViewById<RecyclerView>(R.id.rv_comments)

        rvChannels.layoutManager = LinearLayoutManager(context)
        rvFeed.layoutManager = LinearLayoutManager(context)
        rvComments.layoutManager = LinearLayoutManager(context)
        
        // Mock Comments
        rvComments.adapter = ChannelAdapter(listOf("Is this cleared?", "Traffic is backing up", "Still there @ 5pm")) { } // reusing simple text adapter for now

        // Mock Reports
        val mockReports = listOf(
            ReportItem("User123", "Accident", "Near Anna Nagar", "2 mins ago", 12),
            ReportItem("SpeedDaemon", "Speed Camera", "GST Road", "15 mins ago", 45),
            ReportItem("SafeDriver", "Waterlogging", "Velachery Main Rd", "1 hour ago", 8),
            ReportItem("Commuter", "Fallen Tree", "Adyar", "2 hours ago", 5)
        )
        
        rvFeed.adapter = FeedAdapter(mockReports) { report ->
            // On Report Click -> Show Thread
            threadView.visibility = View.VISIBLE
            // Populate Thread Data (Simplistic binding for demo)
            val postView = threadView.findViewById<View>(R.id.thread_original_post)
            postView.findViewById<TextView>(R.id.tv_username).text = report.user
            postView.findViewById<TextView>(R.id.tv_hazard_type).text = report.type
            postView.findViewById<TextView>(R.id.tv_location).text = "Near ${report.location}"
            postView.findViewById<TextView>(R.id.tv_timestamp).text = report.time
            postView.findViewById<TextView>(R.id.tv_upvotes).text = report.upvotes.toString()
            
            val badge = postView.findViewById<TextView>(R.id.tv_category_badge)
             if (report.type == "Speed Camera") {
                badge.text = "Camera"
                badge.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#9C27B0"))
            } else {
                badge.text = "Hazard"
                badge.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#F44336"))
            }
            
            // Update Map Preview
            mapView?.getMapAsync(object : com.mappls.sdk.maps.OnMapReadyCallback {
                override fun onMapReady(map: com.mappls.sdk.maps.MapplsMap) {
                    val position = com.mappls.sdk.maps.geometry.LatLng(13.0045, 80.2013) // Mock location
                    val cameraPosition = com.mappls.sdk.maps.camera.CameraPosition.Builder()
                        .target(position)
                        .zoom(14.0)
                        .build()
                    map.moveCamera(com.mappls.sdk.maps.camera.CameraUpdateFactory.newCameraPosition(cameraPosition))
                    map.clear()
                    map.addMarker(com.mappls.sdk.maps.annotations.MarkerOptions().position(position))
                }

                override fun onMapError(code: Int, message: String?) {
                    android.util.Log.e("Zwap", "Thread Map Error: $code - $message")
                }
            })
        }

        val channelAdapter = ChannelAdapter(mutableListOf()) { channelName ->
            // On Channel Click -> Show Feed
            rvChannels.visibility = View.GONE
            rvFeed.visibility = View.VISIBLE
            btnBack.visibility = View.VISIBLE
            tvTitle.text = "#$channelName"
            
            // Store current channel name for report dialog
            view.setTag(R.id.rv_feed, channelName) // Store channel name in view tag
            
            // Hide message input for special aggregate channels, show for all others
            val feedInputArea = view.findViewById<View>(R.id.feed_input_area)
            val aggregateChannels = listOf("all-hazards", "recently-added", "my-reports", "notifications")
            if (aggregateChannels.contains(channelName)) {
                // These are aggregate/overview channels - no messaging
                feedInputArea.visibility = View.GONE
                rvFeed.setPadding(rvFeed.paddingLeft, rvFeed.paddingTop, rvFeed.paddingRight, 0)
            } else {
                // Regular topic channels - show messaging
                feedInputArea.visibility = View.VISIBLE
                rvFeed.setPadding(rvFeed.paddingLeft, rvFeed.paddingTop, rvFeed.paddingRight, 
                    (60 * resources.displayMetrics.density).toInt()) // Add bottom padding for input
            }
        }
        rvChannels.adapter = channelAdapter

        // Back Button Logic (Channels Header)
        btnBack.setOnClickListener {
            val feedInputArea = view.findViewById<View>(R.id.feed_input_area)
            if (threadView.visibility == View.VISIBLE) {
                 threadView.visibility = View.GONE
            } else {
                rvFeed.visibility = View.GONE
                rvChannels.visibility = View.VISIBLE
                btnBack.visibility = View.GONE
                feedInputArea.visibility = View.GONE
                tvTitle.text = "Hazards" 
            }
        }
        
        // Thread Back Button
        btnThreadBack.setOnClickListener {
            threadView.visibility = View.GONE
        }
        
        // Channel Report Button
        view.findViewById<View>(R.id.btn_create_channel_report)?.setOnClickListener {
            // Get current channel name from view tag
            val currentChannel = view.getTag(R.id.rv_feed) as? String ?: "unknown"
            (activity as? com.swapmap.zwap.demo.MainActivity)?.showChannelReportDialog(currentChannel)
        }

        // Select first item by default
        updateChannels(view, channelMap["hazards"] ?: emptyList(), "Hazards")

        rvServers.adapter = ServerAdapter(sidebarItems) { item ->
            val feedInputArea = view.findViewById<View>(R.id.feed_input_area)
            if (item.id == "add") {
                // Open Report Dialog
                (activity as? com.swapmap.zwap.demo.MainActivity)?.showReportDialog()
            } else {
                updateChannels(view, channelMap[item.id] ?: emptyList(), item.name)
                // Ensure we go back to channel list when switching categories
                rvFeed.visibility = View.GONE
                rvChannels.visibility = View.VISIBLE
                btnBack.visibility = View.GONE
                threadView.visibility = View.GONE
                feedInputArea.visibility = View.GONE
            }
        }
    }


    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView?.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    private fun updateChannels(view: View, channels: List<String>, title: String) {
        view.findViewById<TextView>(R.id.tv_server_name).text = title
        val rvChannels = view.findViewById<RecyclerView>(R.id.rv_channels)
        (rvChannels.adapter as? ChannelAdapter)?.updateData(channels)
    }

    data class SidebarItem(val id: String, val iconRes: Int, val name: String)
    data class ReportItem(val user: String, val type: String, val location: String, val time: String, val upvotes: Int)

    // -- Adapters --
    class ServerAdapter(private val items: List<SidebarItem>, private val onClick: (SidebarItem) -> Unit) : RecyclerView.Adapter<ServerAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val iconImg: ImageView = view.findViewById(R.id.iv_icon)
            val iconText: TextView = view.findViewById(R.id.tv_icon)
            val indicator: View = view.findViewById(R.id.indicator)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_server, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            
            if (item.id == "add") {
                holder.iconImg.visibility = View.GONE
                holder.iconText.visibility = View.VISIBLE
                holder.iconText.text = "+"
                holder.iconText.setTextColor(Color.GREEN)
                holder.iconText.textSize = 24f
            } else {
                holder.iconImg.visibility = View.VISIBLE
                holder.iconText.visibility = View.GONE
                try {
                    holder.iconImg.setImageResource(item.iconRes)
                    holder.iconImg.setColorFilter(Color.WHITE)
                } catch (e: Exception) {
                    holder.iconImg.setImageResource(android.R.drawable.ic_menu_info_details)
                }
            }
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }

    class ChannelAdapter(private var items: List<String>, private val onClick: (String) -> Unit) : RecyclerView.Adapter<ChannelAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textView: TextView = view.findViewById(R.id.tv_channel_name)
        }

        fun updateData(newItems: List<String>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val channel = items[position]
            holder.textView.text = "# ${channel.lowercase()}"
            holder.itemView.setOnClickListener { onClick(channel) }
        }

        override fun getItemCount() = items.size
    }

    class FeedAdapter(private val items: List<ReportItem>, private val onClick: (ReportItem) -> Unit) : RecyclerView.Adapter<FeedAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvUser: TextView = view.findViewById(R.id.tv_username)
            val tvType: TextView = view.findViewById(R.id.tv_hazard_type)
            val tvLoc: TextView = view.findViewById(R.id.tv_location)
            val tvTime: TextView = view.findViewById(R.id.tv_timestamp)
            val tvVotes: TextView = view.findViewById(R.id.tv_upvotes)
            val badge: TextView = view.findViewById(R.id.tv_category_badge)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_report_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvUser.text = item.user
            holder.tvType.text = item.type
            holder.tvLoc.text = "Near ${item.location}"
            holder.tvTime.text = item.time
            holder.tvVotes.text = item.upvotes.toString()
            
            if (item.type == "Speed Camera") {
                holder.badge.text = "Camera"
                holder.badge.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#9C27B0")) // Purple
            } else {
                holder.badge.text = "Hazard"
                holder.badge.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#F44336")) // Red
            }
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
