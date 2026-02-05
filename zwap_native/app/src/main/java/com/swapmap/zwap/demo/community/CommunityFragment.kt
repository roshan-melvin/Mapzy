package com.swapmap.zwap.demo.community

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.swapmap.zwap.R
import com.swapmap.zwap.demo.model.Report
import com.swapmap.zwap.demo.repository.ReportRepository
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class CommunityFragment : Fragment() {

    private var mapView: com.mappls.sdk.maps.MapView? = null
    private val repository = ReportRepository()
    private var fetchJob: kotlinx.coroutines.Job? = null
    private lateinit var feedAdapter: FeedAdapter

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
            SidebarItem("notifications", android.R.drawable.ic_popup_reminder, "Notifications"),
            SidebarItem("add", android.R.drawable.ic_input_add, "Create Report")
        )

        val channelMap = mapOf(
            "hazards" to listOf("all-hazards", "accident", "construction", "waterlogging", "fallen-tree", "other"),
            "cameras" to listOf("fixed-camera", "mobile-camera", "recently-added"),
            "leaderboard" to listOf("global-ranking", "Region ranking")
        )

        val rvChannels = view.findViewById<RecyclerView>(R.id.rv_channels)
        val rvFeed = view.findViewById<RecyclerView>(R.id.rv_feed)
        val btnBack = view.findViewById<ImageView>(R.id.btn_back_channels)
        val tvTitle = view.findViewById<TextView>(R.id.tv_server_name)
        val threadView = view.findViewById<View>(R.id.thread_view_container)
        
        // Thread View Controls
        val btnThreadBack = view.findViewById<ImageView>(R.id.btn_thread_back)
        val rvComments = view.findViewById<RecyclerView>(R.id.rv_comments)
        val btnOptions = view.findViewById<ImageView>(R.id.btn_community_options)

        rvChannels.layoutManager = LinearLayoutManager(context)
        rvFeed.layoutManager = LinearLayoutManager(context)
        rvComments.layoutManager = LinearLayoutManager(context)
        
        feedAdapter = FeedAdapter(mutableListOf()) { report ->
            showThread(report, threadView)
        }
        rvFeed.adapter = feedAdapter

        // Mock Comments (Keep for now, or remove)
        rvComments.adapter = ChannelAdapter(listOf("Is this cleared?", "Traffic is backing up", "Still there @ 5pm")) { } 

        // FETCH REAL REPORTS
        val tvEmpty = view.findViewById<TextView>(R.id.tv_empty_feed)
        fetchRealReports(rvFeed, threadView, tvEmpty)

        val channelAdapter = ChannelAdapter(mutableListOf()) { channelName ->
            // Intercept "my-reports" to show ContributionFragment
            if (channelName == "my-reports") {
                 parentFragmentManager.beginTransaction()
                     .replace(R.id.fragment_container, ContributionFragment())
                     .addToBackStack(null)
                     .commit()
                 return@ChannelAdapter
            }

            // On Channel Click -> Show Feed
            rvChannels.visibility = View.GONE
            rvFeed.visibility = View.VISIBLE
            btnBack.visibility = View.VISIBLE
            tvTitle.text = "#$channelName"
            
            // Store current channel name for report dialog
            view.setTag(R.id.rv_feed, channelName)

            val tvEmpty = view.findViewById<TextView>(R.id.tv_empty_feed)
            tvEmpty.visibility = View.GONE
            
            // Clear current list immediately to prevent "glitching" from old data
            feedAdapter.updateData(emptyList())

            // Fetch reports for this specific channel
            if (channelName == "all-hazards") {
                fetchRealReports(rvFeed, threadView, tvEmpty)
            } else {
                fetchChannelReports(channelName, rvFeed, threadView, tvEmpty)
            }
        }
        rvChannels.adapter = channelAdapter

        // Back Button Logic
        btnBack.setOnClickListener {
            if (threadView.visibility == View.VISIBLE) {
                 threadView.visibility = View.GONE
            } else {
                rvFeed.visibility = View.GONE
                rvChannels.visibility = View.VISIBLE
                btnBack.visibility = View.GONE
                btnOptions.visibility = View.GONE
                tvTitle.text = "Hazards" 
                view.findViewById<View>(R.id.tv_empty_feed).visibility = View.GONE
            }
        }
        
        btnOptions.setOnClickListener {
            val popup = android.widget.PopupMenu(context, btnOptions)
            popup.menu.add("Clear All")
            popup.menu.add("Filter").setOnMenuItemClickListener {
                // Nested options: read, unread
                val filterPopup = android.widget.PopupMenu(context, btnOptions)
                filterPopup.menu.add("read")
                filterPopup.menu.add("unread")
                filterPopup.show()
                true
            }
            popup.show()
        }
        
        btnThreadBack.setOnClickListener {
            threadView.visibility = View.GONE
        }
        
        // REMOVED: Channel Report Button

        // Select first item by default
        updateChannels(view, channelMap["hazards"] ?: emptyList(), "Hazards")

        rvServers.adapter = ServerAdapter(sidebarItems) { item ->
            when (item.id) {
                "add" -> {
                    parentFragmentManager.beginTransaction()
                         .replace(R.id.fragment_container, CreateReportFragment())
                         .addToBackStack(null)
                         .commit()
                }
                "notifications" -> {
                    // Update main area title
                    tvTitle.text = "Notifications"
                    rvChannels.visibility = View.VISIBLE
                    rvFeed.visibility = View.GONE
                    btnBack.visibility = View.GONE
                    btnOptions.visibility = View.VISIBLE
                    threadView.visibility = View.GONE
                    // tv_empty_feed visibility will be handled by updateChannels
                    
                    // Removed placeholder notifications
                    updateChannels(view, emptyList(), "Notifications")
                }
                else -> {
                    updateChannels(view, channelMap[item.id] ?: emptyList(), item.name)
                    rvFeed.visibility = View.GONE
                    rvChannels.visibility = View.VISIBLE
                    btnBack.visibility = View.GONE
                    btnOptions.visibility = View.GONE
                    threadView.visibility = View.GONE
                    view.findViewById<View>(R.id.tv_empty_feed).visibility = View.GONE
                }
            }
        }
    }

    private fun fetchRealReports(rvFeed: RecyclerView, threadView: View, tvEmpty: TextView) {
        fetchJob?.cancel()
        fetchJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val reports = repository.getFeedReports()
                withContext(Dispatchers.Main) {
                    feedAdapter.updateData(reports)
                    tvEmpty.visibility = if (reports.isEmpty()) View.VISIBLE else View.GONE
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun fetchChannelReports(channelName: String, rvFeed: RecyclerView, threadView: View, tvEmpty: TextView) {
        fetchJob?.cancel()
        fetchJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val snapshot = repository.getChannelReports(channelName).get().await()
                val reports = snapshot.toObjects(Report::class.java)
                
                withContext(Dispatchers.Main) {
                    feedAdapter.updateData(reports)
                    tvEmpty.visibility = if (reports.isEmpty()) View.VISIBLE else View.GONE
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showThread(report: Report, threadView: View) {
        threadView.visibility = View.VISIBLE
        val postView = threadView.findViewById<View>(R.id.thread_original_post)
        
        postView.findViewById<TextView>(R.id.tv_username).text = "User" // report.userId (Mask or fetch name)
        postView.findViewById<TextView>(R.id.tv_hazard_type).text = report.incidentType
        postView.findViewById<TextView>(R.id.tv_location).text = report.description // Showing Desc as location/details
        
        val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        postView.findViewById<TextView>(R.id.tv_timestamp).text = report.createdAt?.toDate()?.let { sdf.format(it) } ?: "Just now"
        
        postView.findViewById<TextView>(R.id.tv_upvotes).text = report.pointsAwarded.toString()
        
        val badge = postView.findViewById<TextView>(R.id.tv_category_badge)
        badge.text = report.status
        if (report.status == "Verified") {
            badge.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#43B581"))
        } else {
             badge.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FAA61A")) // Pending/Yellow
        }

        // Map Preview (Mocked Location for now since Report has lat/lng but Map needs pin)
        mapView?.getMapAsync(object : com.mappls.sdk.maps.OnMapReadyCallback {
            override fun onMapReady(map: com.mappls.sdk.maps.MapplsMap) {
                 val pos = com.mappls.sdk.maps.geometry.LatLng(report.latitude, report.longitude)
                 map.animateCamera(com.mappls.sdk.maps.camera.CameraUpdateFactory.newLatLngZoom(pos, 14.0))
                 map.clear()
                 map.addMarker(com.mappls.sdk.maps.annotations.MarkerOptions().position(pos))
            }

            override fun onMapError(code: Int, message: String?) {
                // Log error if needed
            }
        })
    }


    override fun onStart() { super.onStart(); mapView?.onStart() }
    override fun onResume() { super.onResume(); mapView?.onResume() }
    override fun onPause() { super.onPause(); mapView?.onPause() }
    override fun onStop() { super.onStop(); mapView?.onStop() }
    override fun onDestroyView() { super.onDestroyView(); mapView?.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView?.onSaveInstanceState(outState) }
    override fun onLowMemory() { super.onLowMemory(); mapView?.onLowMemory() }

    private fun updateChannels(view: View, channels: List<String>, title: String) {
        view.findViewById<TextView>(R.id.tv_server_name).text = title
        val rvChannels = view.findViewById<RecyclerView>(R.id.rv_channels)
        val tvEmpty = view.findViewById<TextView>(R.id.tv_empty_feed)
        
        (rvChannels.adapter as? ChannelAdapter)?.updateData(channels)
        
        if (title == "Notifications") {
            tvEmpty.text = "No messages found"
            tvEmpty.visibility = if (channels.isEmpty()) View.VISIBLE else View.GONE
        } else {
            tvEmpty.text = "No reports in this channel yet."
            // Ensure empty state is hidden for channel browsing unless explicitly handled
            if (title != "Hazards" && title != "Speed Cameras" && title != "Leaderboard") {
                tvEmpty.visibility = if (channels.isEmpty()) View.VISIBLE else View.GONE
            } else {
                tvEmpty.visibility = View.GONE
            }
        }
    }

    data class SidebarItem(val id: String, val iconRes: Int, val name: String)

    // -- Adapters --
    class ServerAdapter(private val items: List<SidebarItem>, private val onClick: (SidebarItem) -> Unit) : RecyclerView.Adapter<ServerAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val iconImg: ImageView = view.findViewById(R.id.iv_icon)
            val iconText: TextView = view.findViewById(R.id.tv_icon)
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
                } catch (e: Exception) {}
            }
            holder.itemView.setOnClickListener { onClick(item) }
        }
        override fun getItemCount() = items.size
    }

    class ChannelAdapter(private var items: List<String>, private val onClick: (String) -> Unit) : RecyclerView.Adapter<ChannelAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) { val textView: TextView = view.findViewById(R.id.tv_channel_name) }
        fun updateData(newItems: List<String>) { items = newItems; notifyDataSetChanged() }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) { holder.textView.text = "# ${items[position].lowercase()}"; holder.itemView.setOnClickListener { onClick(items[position]) } }
        override fun getItemCount() = items.size
    }

    // UPDATED FEED ADAPTER using Report
    class FeedAdapter(private var items: List<Report>, private val onClick: (Report) -> Unit) : RecyclerView.Adapter<FeedAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvUser: TextView = view.findViewById(R.id.tv_username)
            val tvType: TextView = view.findViewById(R.id.tv_hazard_type)
            val tvLoc: TextView = view.findViewById(R.id.tv_location)
            val tvTime: TextView = view.findViewById(R.id.tv_timestamp)
            val tvVotes: TextView = view.findViewById(R.id.tv_upvotes)
            val badge: TextView = view.findViewById(R.id.tv_category_badge)
        }

        fun updateData(newItems: List<Report>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_report_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvUser.text = "User" // Placeholder for Name (need user fetch)
            holder.tvType.text = item.incidentType
            holder.tvLoc.text = item.description // Using description as primary text
            
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            holder.tvTime.text = item.createdAt?.toDate()?.let { sdf.format(it) } ?: "Just now"
            
            holder.tvVotes.text = item.pointsAwarded.toString()
            
            holder.badge.text = item.status
            if (item.status == "Verified") {
                holder.badge.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#43B581"))
            } else {
                holder.badge.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FAA61A"))
            }
            holder.itemView.setOnClickListener { onClick(item) }
        }
        override fun getItemCount() = items.size
    }
}
