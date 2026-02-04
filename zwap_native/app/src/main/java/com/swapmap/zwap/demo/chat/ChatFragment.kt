package com.swapmap.zwap.demo.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.TextView
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.swapmap.zwap.R

class ChatFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private lateinit var rvRegions: RecyclerView
    private lateinit var rvChannels: RecyclerView
    private lateinit var rvMessages: RecyclerView
    private lateinit var chatContentArea: View
    private lateinit var emptyState: LinearLayout
    private lateinit var titleView: TextView
    
    private var userRegions: List<String> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Views
        rvRegions = view.findViewById(R.id.rv_regions)
        rvChannels = view.findViewById(R.id.rv_chat_channels)
        rvMessages = view.findViewById(R.id.rv_messages)
        chatContentArea = view.findViewById(R.id.chat_content_area)
        emptyState = view.findViewById(R.id.empty_state)
        titleView = view.findViewById(R.id.tv_chat_title)

        rvRegions.layoutManager = LinearLayoutManager(context)
        rvChannels.layoutManager = LinearLayoutManager(context)
        rvMessages.layoutManager = LinearLayoutManager(context)

        view.findViewById<View>(R.id.btn_join_region).setOnClickListener {
            showRegionSelector()
        }
        
        view.findViewById<View>(R.id.btn_empty_join).setOnClickListener {
            showRegionSelector()
        }

        checkUserRegions()
    }

    private fun checkUserRegions() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val regions = document.get("chat_regions") as? List<String> ?: emptyList()
                    if (regions.isEmpty()) {
                        showEmptyState()
                    } else {
                        showRegionsSidebar(regions)
                    }
                } else {
                    showEmptyState()
                }
            }
            .addOnFailureListener {
                showEmptyState()
            }
    }

    private fun showEmptyState() {
        // Fix: Do NOT hide chatContentArea because emptyState is INSIDE it!
        chatContentArea.visibility = View.VISIBLE
        rvRegions.visibility = View.GONE
        
        // Hide other content inside content area
        rvChannels.visibility = View.GONE
        view?.findViewById<View>(R.id.message_container)?.visibility = View.GONE
        // Hide header if desired, or keep it. Let's hide header for clean empty state
        // access header via index or ID if possible. 
        // Current XML structure has Header as first child of FrameLayout? 
        // No, Header is inside the FrameLayout.
        // Let's just make sure emptyState is ON TOP (opacity) or hide others.
        
        emptyState.visibility = View.VISIBLE
    }

    private fun showRegionsSidebar(regions: List<String>) {
        emptyState.visibility = View.GONE
        chatContentArea.visibility = View.VISIBLE
        rvRegions.visibility = View.VISIBLE
        
        // Select first region by default
        if (regions.isNotEmpty()) {
            loadChannels(regions[0]) // Auto-load first region
        }

        val adapter = RegionAdapter(regions) { regionId ->
            loadChannels(regionId)
        }
        rvRegions.adapter = adapter
    }

    private var currentRegionId: String = ""

    private fun loadChannels(regionId: String) {
        currentRegionId = regionId
        
        // Update View State: Show Channels, Hide Messages
        rvChannels.visibility = View.VISIBLE
        view?.findViewById<View>(R.id.message_container)?.visibility = View.GONE
        
        // Parse name for title
        val parts = regionId.split("-")
        val cityName = if (parts.isNotEmpty()) parts.last().uppercase() else regionId
        titleView.text = "$cityName Channels"

        // Static Channel List (As per new schema request)
        val staticChannels = listOf(
            com.swapmap.zwap.demo.model.Channel(id = "welcome", name = "Welcome", description = "Welcome to $cityName!"),
            com.swapmap.zwap.demo.model.Channel(id = "general", name = "General", description = "General discussion"),
            com.swapmap.zwap.demo.model.Channel(id = "hazard", name = "Hazards", description = "Report hazards"),
            com.swapmap.zwap.demo.model.Channel(id = "traffic", name = "Traffic", description = "Traffic updates"),
            com.swapmap.zwap.demo.model.Channel(id = "speed-cameras", name = "Speed Cameras", description = "Speed trap alerts")
        )
        
        val adapter = ChannelAdapter(staticChannels) { channel ->
            loadMessages(channel)
        }
        rvChannels.adapter = adapter
    }

    private fun loadMessages(channel: com.swapmap.zwap.demo.model.Channel) {
        rvChannels.visibility = View.GONE
        val msgContainer = view?.findViewById<View>(R.id.message_container)
        msgContainer?.visibility = View.VISIBLE
        
        titleView.text = "# ${channel.name}"
        
        // Setup send button
        val etMessage = view?.findViewById<android.widget.EditText>(R.id.et_message_input)
        
        // Remove previous listeners to avoid duplicates if any (though setOnClickListener replaces)
        view?.findViewById<View>(R.id.btn_send_message)?.setOnClickListener {
            val text = etMessage?.text.toString()
            if (text.isNotEmpty()) {
                sendMessage(channel.id, text)
                etMessage?.text?.clear()
            }
        }
        
        // Listen for messages in: chat/{regionId}/threads/{threadId}/messages
        if (currentRegionId.isNotEmpty()) {
            db.collection("chat").document(currentRegionId)
                .collection("threads").document(channel.id)
                .collection("messages")
                .orderBy("created_at", com.google.firebase.firestore.Query.Direction.ASCENDING)
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        return@addSnapshotListener
                    }

                    val messages = mutableListOf<com.swapmap.zwap.demo.model.ChatMessage>()
                    if (snapshots != null) {
                        for (doc in snapshots) {
                            messages.add(doc.toObject(com.swapmap.zwap.demo.model.ChatMessage::class.java).copy(id = doc.id))
                        }
                    }
                    
                    val currentUid = auth.currentUser?.uid ?: ""
                    val adapter = MessageAdapter(messages, currentUid)
                    rvMessages.adapter = adapter
                    if (messages.isNotEmpty()) {
                        rvMessages.scrollToPosition(messages.size - 1)
                    }
                }
        }
    }

    private fun sendMessage(channelName: String, text: String) {
        val user = auth.currentUser ?: return
        if (currentRegionId.isEmpty()) return
        
        val message = hashMapOf(
            "user_id" to user.uid,
            "username" to (user.displayName ?: "User"),
            "user_avatar" to (user.photoUrl?.toString() ?: ""),
            "text" to text,
            "type" to "text",
            "created_at" to com.google.firebase.Timestamp.now()
        )
        
        db.collection("chat").document(currentRegionId)
            .collection("threads").document(channelName)
            .collection("messages")
            .add(message)
    }

    private fun showRegionSelector() {
        val dialog = RegionSelectorDialog()
        dialog.setOnRegionSelectedListener { country, state, city ->
            joinRegion(country, state, city)
        }
        dialog.show(childFragmentManager, "RegionSelector")
    }

    private fun joinRegion(country: String, state: String, city: String) {
        val userId = auth.currentUser?.uid ?: return

        // Create or get region ID
        val regionId = "$country-$state-$city".lowercase().replace(" ", "-")

        // Add region to user's chat_regions (using set with merge to create doc if missing)
        val data = mapOf(
            "chat_regions" to com.google.firebase.firestore.FieldValue.arrayUnion(regionId)
        )

        db.collection("users").document(userId)
            .set(data, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(context, "Joined $city chat!", Toast.LENGTH_SHORT).show()
                checkUserRegions() // Refresh
            }.addOnFailureListener { e ->
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
