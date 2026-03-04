package com.swapmap.zwap.demo.community

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.swapmap.zwap.R
import com.swapmap.zwap.demo.model.Report
import com.swapmap.zwap.demo.model.UserNotification
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
    private var fetchJob: Job? = null

    private lateinit var feedAdapter: FeedAdapter
    private lateinit var leaderboardAdapter: LeaderboardAdapter
    private lateinit var notificationAdapter: NotificationAdapter

    private var rvFeed: RecyclerView? = null
    private var rvLeaderboard: RecyclerView? = null
    private var rvNotifications: RecyclerView? = null
    private var tvEmpty: TextView? = null

    // Selection mode UI
    private var headerNormal: LinearLayout? = null
    private var headerSelection: LinearLayout? = null
    private var tvSelectionCount: TextView? = null
    private var btnSelectAll: TextView? = null
    private var btnDeleteSelected: TextView? = null

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_community, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Native Gesture Handling: Handle back swipe to navigate back or pop the fragment
        val callback = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val threadView = view.findViewById<View>(R.id.thread_view_container)
                val rvChannels = view.findViewById<RecyclerView>(R.id.rv_channels)
                
                when {
                    threadView?.visibility == View.VISIBLE -> {
                        threadView.visibility = View.GONE
                    }
                    rvChannels?.visibility == View.GONE -> {
                        // If we are in a sub-view (feed, notifications, etc.), go back to channel list
                        val hideAll: () -> Unit = {
                            view.findViewById<View>(R.id.rv_feed)?.visibility = View.GONE
                            view.findViewById<View>(R.id.rv_leaderboard)?.visibility = View.GONE
                            view.findViewById<View>(R.id.rv_notifications)?.visibility = View.GONE
                            view.findViewById<View>(R.id.thread_view_container)?.visibility = View.GONE
                            view.findViewById<View>(R.id.tv_empty_feed)?.visibility = View.GONE
                        }
                        hideAll()
                        rvChannels?.visibility = View.VISIBLE
                        view.findViewById<ImageView>(R.id.btn_community_options)?.visibility = View.GONE
                    }
                    else -> {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)

        mapView = view.findViewById(R.id.thread_map_preview)
        mapView?.onCreate(savedInstanceState)

        // --- Selection mode header refs ---
        headerNormal = view.findViewById(R.id.header_normal)
        headerSelection = view.findViewById(R.id.header_selection)
        tvSelectionCount = view.findViewById(R.id.tv_selection_count)
        btnSelectAll = view.findViewById<TextView>(R.id.btn_select_all)
        btnDeleteSelected = view.findViewById<TextView>(R.id.btn_delete_selected)

        // --- Setup Servers Sidebar ---
        val rvServers = view.findViewById<RecyclerView>(R.id.rv_servers)
        rvServers.layoutManager = LinearLayoutManager(context)

        val sidebarItems = listOf(
            SidebarItem("hazards", R.drawable.ic_hazard, "Hazards"),
            SidebarItem("cameras", R.drawable.ic_camera_outlined, "Speed Cameras"),
            SidebarItem("leaderboard", R.drawable.ic_star_outlined, "Leaderboard"),
            SidebarItem("notifications", R.drawable.ic_bell_outlined, "Notifications"),
            SidebarItem("add", android.R.drawable.ic_input_add, "Create Report")
        )

        val channelMap = mapOf(
            "hazards" to listOf("all-hazards", "accident", "construction", "waterlogging", "fallen-tree", "other"),
            "cameras" to listOf("speed-camera", "fixed-camera", "mobile-camera", "recently-added"),
            "leaderboard" to listOf("global-ranking", "Region ranking")
        )

        val rvChannels = view.findViewById<RecyclerView>(R.id.rv_channels)
        val tvTitle = view.findViewById<TextView>(R.id.tv_server_name)
        val threadView = view.findViewById<View>(R.id.thread_view_container)
        val rvComments = view.findViewById<RecyclerView>(R.id.rv_comments)
        val btnOptions = view.findViewById<ImageView>(R.id.btn_community_options)

        rvChannels.layoutManager = LinearLayoutManager(context)
        rvFeed = view.findViewById(R.id.rv_feed)
        rvFeed?.layoutManager = LinearLayoutManager(context)
        rvComments.layoutManager = LinearLayoutManager(context)

        feedAdapter = FeedAdapter(mutableListOf()) { report -> showThread(report, threadView) }
        rvFeed?.adapter = feedAdapter

        rvLeaderboard = view.findViewById(R.id.rv_leaderboard)
        rvLeaderboard?.layoutManager = LinearLayoutManager(context)
        leaderboardAdapter = LeaderboardAdapter(mutableListOf())
        rvLeaderboard?.adapter = leaderboardAdapter

        rvNotifications = view.findViewById(R.id.rv_notifications)
        rvNotifications?.layoutManager = LinearLayoutManager(context)
        notificationAdapter = NotificationAdapter(mutableListOf(), onClick = {}) { count, inSelectionMode ->
            onSelectionChanged(count, inSelectionMode)
        }
        rvNotifications?.adapter = notificationAdapter

        rvComments.adapter = ChannelAdapter(listOf("Is this cleared?", "Traffic is backing up", "Still there @ 5pm")) {}

        tvEmpty = view.findViewById(R.id.tv_empty_feed)
        fetchRealReports(rvFeed!!, threadView, tvEmpty!!)

        // ── Helper: hides ALL content views ──────────────────────────────
        fun hideAllContentViews() {
            rvFeed?.visibility = View.GONE
            rvLeaderboard?.visibility = View.GONE
            rvNotifications?.visibility = View.GONE
            threadView.visibility = View.GONE
            tvEmpty?.visibility = View.GONE
            // Exit notification selection mode if switching away
            if (notificationAdapter.isSelectionMode) {
                notificationAdapter.exitSelectionMode()
            }
        }

        // ── Channel click handler ─────────────────────────────────────────
        val channelAdapter = ChannelAdapter(mutableListOf()) { channelName ->
            if (channelName == "my-reports") {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, ContributionFragment())
                    .addToBackStack(null).commit()
                return@ChannelAdapter
            }

            hideAllContentViews()
            rvChannels.visibility = View.GONE
            tvTitle.text = "#$channelName"
            view.setTag(R.id.rv_feed, channelName)

            when (channelName) {
                "global-ranking" -> {
                    rvLeaderboard?.visibility = View.VISIBLE
                    fetchLeaderboard(rvLeaderboard!!, tvEmpty!!)
                }
                else -> {
                    rvFeed?.visibility = View.VISIBLE
                    feedAdapter.updateData(emptyList())
                    if (channelName == "all-hazards") {
                        fetchRealReports(rvFeed!!, threadView, tvEmpty!!)
                    } else {
                        fetchChannelReports(channelName, rvFeed!!, threadView, tvEmpty!!)
                    }
                }
            }
        }
        rvChannels.adapter = channelAdapter


        btnOptions.setOnClickListener {
            val popup = android.widget.PopupMenu(context, btnOptions)
            popup.menu.add("Filter")
            popup.show()
        }


        // ── Selection mode toolbar buttons ────────────────────────────────
        btnSelectAll?.setOnClickListener {
            notificationAdapter.selectAll()
        }

        btnDeleteSelected?.setOnClickListener {
            val selected = notificationAdapter.getSelectedNotifications()
            android.util.Log.d("ZwapNotif", "Delete tapped, selected count: ${selected.size}")
            if (selected.isNotEmpty()) {
                deleteNotifications(selected)
            }
        }

        // ── Default view ──────────────────────────────────────────────────
        updateChannels(view, channelMap["hazards"] ?: emptyList(), "Hazards")

        rvServers.adapter = ServerAdapter(sidebarItems) { item ->
            when (item.id) {
                "add" -> {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, CreateReportFragment())
                        .addToBackStack(null).commit()
                }
                "notifications" -> {
                    hideAllContentViews()
                    rvChannels.visibility = View.GONE
                    rvNotifications?.visibility = View.VISIBLE
                    btnOptions.visibility = View.GONE
                    tvTitle.text = "Notifications"
                    // Ensure normal header is showing (not selection header)
                    headerNormal?.visibility = View.VISIBLE
                    headerSelection?.visibility = View.GONE
                    fetchNotifications()
                }
                else -> {
                    hideAllContentViews()
                    updateChannels(view, channelMap[item.id] ?: emptyList(), item.name)
                    rvChannels.visibility = View.VISIBLE
                    btnOptions.visibility = View.GONE
                }
            }
        }
    }

    // ── Selection mode callback ───────────────────────────────────────────
    private fun onSelectionChanged(count: Int, inSelectionMode: Boolean) {
        if (inSelectionMode) {
            headerNormal?.visibility = View.GONE
            headerSelection?.visibility = View.VISIBLE
            tvSelectionCount?.text = "$count selected"
        } else {
            headerSelection?.visibility = View.GONE
            headerNormal?.visibility = View.VISIBLE
            // Don't restore btnBack — notifications view has no back arrow
        }
    }

    // ── Fetch notifications from Firestore ────────────────────────────────
    private fun fetchNotifications() {
        val uid = auth.currentUser?.uid ?: return
        fetchJob?.cancel()
        fetchJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val snap = db.collection("users").document(uid)
                    .collection("notifications")
                    .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .get().await()

                val notifications = snap.documents.mapNotNull { doc ->
                    doc.toObject(UserNotification::class.java)?.copy(id = doc.id)
                }

                withContext(Dispatchers.Main) {
                    notificationAdapter.updateData(notifications)
                    tvEmpty?.text = "No notifications yet."
                    tvEmpty?.visibility = if (notifications.isEmpty()) View.VISIBLE else View.GONE
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ── Delete selected notifications from Firestore ───────────────────────
    private fun deleteNotifications(selected: List<UserNotification>) {
        val uid = auth.currentUser?.uid ?: return

        // 1. Remove from UI immediately for instant feedback
        notificationAdapter.removeNotifications(selected)
        tvEmpty?.visibility = if (notificationAdapter.itemCount == 0) View.VISIBLE else View.GONE

        // 2. Delete from Firestore in background
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            var allSuccess = true
            selected.forEach { notif ->
                android.util.Log.d("ZwapNotif", "Attempting to delete notif with id: '${notif.id}'")
                if (notif.id.isNotEmpty()) {
                    try {
                        db.collection("users").document(uid)
                            .collection("notifications").document(notif.id)
                            .delete().await()
                        android.util.Log.d("ZwapNotif", "Successfully deleted notif: ${notif.id}")
                    } catch (e: Exception) {
                        e.printStackTrace()
                        android.util.Log.e("ZwapNotif", "Error deleting notif: ${notif.id}", e)
                        allSuccess = false
                    }
                } else {
                    android.util.Log.e("ZwapNotif", "Notif id is empty! Cannot delete.")
                    allSuccess = false
                }
            }
            withContext(Dispatchers.Main) {
                if (allSuccess) {
                    android.widget.Toast.makeText(context, "Notifications deleted", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(context, "Error deleting some notifications", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchLeaderboard(rvLeaderboard: RecyclerView, tvEmpty: TextView) {
        fetchJob?.cancel()
        fetchJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = com.swapmap.zwap.demo.network.ApiClient.hazardApiService.getLeaderboard(10)
                if (response.isSuccessful && response.body() != null) {
                    val leaderboard = response.body()!!.leaderboard

                    val firestore = FirebaseFirestore.getInstance()
                    val resolvedItems = leaderboard.map { item ->
                        try {
                            val doc = firestore.collection("users").document(item.userId).get().await()
                            val name = doc.getString("name") ?: "User ${item.userId.takeLast(4)}"
                            com.swapmap.zwap.demo.model.LeaderboardItem(
                                userId = item.userId,
                                trustScore = item.trustScore,
                                badgeLevel = item.badgeLevel,
                                rewardPoints = item.rewardPoints,
                                acceptedReports = item.acceptedReports,
                                username = name
                            )
                        } catch (e: Exception) {
                            item.copy(username = "User ${item.userId.takeLast(4)}")
                        }
                    }

                    withContext(Dispatchers.Main) {
                        leaderboardAdapter.updateData(resolvedItems.sortedByDescending { it.rewardPoints })
                        tvEmpty.text = "The legends of the road will appear here!"
                        tvEmpty.visibility = if (resolvedItems.isEmpty()) View.VISIBLE else View.GONE
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Failed to load rankings", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
            } catch (e: Exception) { e.printStackTrace() }
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
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun showThread(report: Report, threadView: View) {
        threadView.visibility = View.VISIBLE
        val postView = threadView.findViewById<View>(R.id.thread_original_post)
        postView.findViewById<TextView>(R.id.tv_username).text = "User"
        postView.findViewById<TextView>(R.id.tv_hazard_type).text = report.incidentType
        postView.findViewById<TextView>(R.id.tv_location).text = report.description
        val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        postView.findViewById<TextView>(R.id.tv_timestamp).text =
            report.createdAt?.toDate()?.let { sdf.format(it) } ?: "Just now"
        postView.findViewById<TextView>(R.id.tv_upvotes).text = report.pointsAwarded.toString()
        val badge = postView.findViewById<TextView>(R.id.tv_category_badge)
        badge.text = report.status
        badge.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (report.status == "Verified") Color.parseColor("#43B581")
            else Color.parseColor("#FAA61A")
        )
        mapView?.getMapAsync(object : com.mappls.sdk.maps.OnMapReadyCallback {
            override fun onMapReady(map: com.mappls.sdk.maps.MapplsMap) {
                val pos = com.mappls.sdk.maps.geometry.LatLng(report.latitude, report.longitude)
                map.animateCamera(com.mappls.sdk.maps.camera.CameraUpdateFactory.newLatLngZoom(pos, 14.0))
                map.clear()
                map.addMarker(com.mappls.sdk.maps.annotations.MarkerOptions().position(pos))
            }
            override fun onMapError(code: Int, message: String?) {}
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
        tvEmpty.visibility = if (channels.isEmpty() && title == "Notifications") {
            tvEmpty.text = "No notification channels"
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    data class SidebarItem(val id: String, val iconRes: Int, val name: String)

    class ServerAdapter(private val items: List<SidebarItem>, private val onClick: (SidebarItem) -> Unit) : RecyclerView.Adapter<ServerAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val iconImg: ImageView = view.findViewById(R.id.iv_icon)
            val iconText: TextView = view.findViewById(R.id.tv_icon)
            val label: TextView = view.findViewById(R.id.tv_label)
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
                holder.label.visibility = View.GONE
            } else {
                holder.iconImg.visibility = View.VISIBLE
                holder.iconText.visibility = View.GONE
                holder.label.visibility = View.GONE
                try { holder.iconImg.setImageResource(item.iconRes); holder.iconImg.setColorFilter(Color.WHITE) } catch (e: Exception) {}
            }
            holder.itemView.setOnClickListener { onClick(item) }
        }
        override fun getItemCount() = items.size
    }

    class ChannelAdapter(private var items: List<String>, private val onClick: (String) -> Unit) : RecyclerView.Adapter<ChannelAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) { val textView: TextView = view.findViewById(R.id.tv_channel_name) }
        fun updateData(newItems: List<String>) { items = newItems; notifyDataSetChanged() }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) { holder.textView.text = items[position].lowercase(); holder.itemView.setOnClickListener { onClick(items[position]) } }
        override fun getItemCount() = items.size
    }

    class FeedAdapter(private var items: List<Report>, private val onClick: (Report) -> Unit) : RecyclerView.Adapter<FeedAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvUser: TextView = view.findViewById(R.id.tv_username)
            val tvType: TextView = view.findViewById(R.id.tv_hazard_type)
            val tvLoc: TextView = view.findViewById(R.id.tv_location)
            val tvTime: TextView = view.findViewById(R.id.tv_timestamp)
            val tvVotes: TextView = view.findViewById(R.id.tv_upvotes)
            val badge: TextView = view.findViewById(R.id.tv_category_badge)
        }
        fun updateData(newItems: List<Report>) { items = newItems; notifyDataSetChanged() }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_report_card, parent, false)
            return ViewHolder(view)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvUser.text = "User"
            holder.tvType.text = item.incidentType
            holder.tvLoc.text = item.description
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            holder.tvTime.text = item.createdAt?.toDate()?.let { sdf.format(it) } ?: "Just now"
            holder.tvVotes.text = item.pointsAwarded.toString()
            holder.badge.text = item.status
            holder.badge.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (item.status == "Verified") Color.parseColor("#43B581") else Color.parseColor("#FAA61A")
            )
            holder.itemView.setOnClickListener { onClick(item) }
        }
        override fun getItemCount() = items.size
    }
}
