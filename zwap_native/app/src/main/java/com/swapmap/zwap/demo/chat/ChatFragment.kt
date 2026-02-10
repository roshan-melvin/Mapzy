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
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.swapmap.zwap.demo.chat.ChatUploadWorker
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.swapmap.zwap.demo.db.AppDatabase
import com.swapmap.zwap.demo.db.PendingMessage
import java.util.UUID

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
    private var currentRegionId: String = ""
    private var currentChannelId: String = "general"

    private var pendingMessages: List<PendingMessage> = emptyList()
    private var firestoreMessages: List<com.swapmap.zwap.demo.model.ChatMessage> = emptyList()

    private val getContent = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri: android.net.Uri? ->
        if (uri != null) {
            val context = context ?: return@registerForActivityResult
            val type = if (context.contentResolver.getType(uri)?.startsWith("video") == true) "video" else "image"
            
            // Copy to cache immediately to ensure access survives app close
            // Running in background to avoid blocking UI, then calling sendMessage
            lifecycleScope.launch(Dispatchers.IO) {
                val cachedPath = copyUriToCache(context, uri)
                if (cachedPath != null) {
                    withContext(Dispatchers.Main) {
                        sendMessage(currentChannelId, "Attachment", type, "file://$cachedPath")
                    }
                } else {
                     withContext(Dispatchers.Main) {
                         Toast.makeText(context, "Failed to process file", Toast.LENGTH_SHORT).show()
                     }
                }
            }
        }
    }
    
    private fun copyUriToCache(context: android.content.Context, uri: android.net.Uri): String? {
        return try {
            val contentResolver = context.contentResolver
            val extension = if (contentResolver.getType(uri)?.startsWith("video") == true) ".mp4" else ".jpg"
            val fileName = "chat_media_${System.currentTimeMillis()}$extension"
            val file = java.io.File(context.cacheDir, fileName)
            
            android.util.Log.d("ChatFragment", "Copying URI to cache: $uri")
            android.util.Log.d("ChatFragment", "Target file: ${file.absolutePath}")
            
            contentResolver.openInputStream(uri)?.use { input ->
                java.io.FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            
            android.util.Log.d("ChatFragment", "File copied successfully. Size: ${file.length()} bytes")
            android.util.Log.d("ChatFragment", "File exists: ${file.exists()}")
            
            file.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("ChatFragment", "Failed to copy file to cache", e)
            e.printStackTrace()
            null
        }
    }

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

        // Attachment Button
        view.findViewById<View>(R.id.btn_attach_image)?.setOnClickListener {
            getContent.launch(arrayOf("image/*", "video/*"))
        }
        
        // ... (Send button logic unchanged) ...
        
        view.findViewById<View>(R.id.btn_send_message)?.setOnClickListener {
            val etMessage = view?.findViewById<android.widget.EditText>(R.id.et_message_input)
            val text = etMessage?.text.toString()
            if (text.isNotEmpty()) {
                sendMessage(currentChannelId, text)
                etMessage?.text?.clear()
            }
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
        currentChannelId = channel.id
        rvChannels.visibility = View.GONE
        val msgContainer = view?.findViewById<View>(R.id.message_container)
        msgContainer?.visibility = View.VISIBLE
        
        titleView.text = "# ${channel.name}"

        // 1. Observe Pending Messages from Room DB
        val dao = AppDatabase.getDatabase(requireContext()).pendingMessageDao()
        lifecycleScope.launch {
            dao.getPendingMessages(currentRegionId, currentChannelId).collect { pending ->
                pendingMessages = pending
                updateMessageList()
            }
        }
        
        // 2. Listen for messages in Firestore
        if (currentRegionId.isNotEmpty()) {
            db.collection("chat").document(currentRegionId)
                .collection("threads").document(channel.id)
                .collection("messages")
                .orderBy("created_at", com.google.firebase.firestore.Query.Direction.ASCENDING)
                .addSnapshotListener { snapshots, e ->
                    if (e != null) return@addSnapshotListener
                    
                    val messages = mutableListOf<com.swapmap.zwap.demo.model.ChatMessage>()
                    if (snapshots != null) {
                        for (doc in snapshots) {
                            messages.add(doc.toObject(com.swapmap.zwap.demo.model.ChatMessage::class.java).copy(id = doc.id))
                        }
                    }
                    firestoreMessages = messages
                    updateMessageList()
                }
        }
    }

    private fun updateMessageList() {
        // Convert pending messages to ChatMessage format
        val convertedPending = pendingMessages.map { p ->
            com.swapmap.zwap.demo.model.ChatMessage(
                id = p.id,
                channel_id = p.threadId,
                user_id = p.userId,
                username = p.userName,
                text = p.messageText,
                image_url = p.imageUri,
                type = p.messageType,
                created_at = com.google.firebase.Timestamp(p.createdAt / 1000, 0)
            ).apply {
                this.localUri = if (p.imageUri != null) android.net.Uri.parse(p.imageUri) else null
                this.isUploading = (p.status == "Sending" || p.status == "Pending")
                this.localStatus = p.status
            }
        }
        
        // Deduplicate: If message is already in Firestore (upload complete), don't show pending version
        val firestoreIds = firestoreMessages.map { it.id }.toSet()
        val uniquePending = convertedPending.filter { !firestoreIds.contains(it.id) }
        
        // Ensure Firestore messages have NO local state (defensive programming)
        val cleanFirestoreMessages = firestoreMessages.map { it.copy().apply {
            localStatus = null
            isUploading = false
            uploadProgress = 0
            localUri = null
        }}
        
        val allMessages = cleanFirestoreMessages + uniquePending
        val sorted = allMessages.sortedBy { it.created_at?.seconds ?: 0L }
        
        val currentUid = auth.currentUser?.uid ?: ""
        val adapter = MessageAdapter(sorted, currentUid) { message ->
            retryMessage(message)
        }
        rvMessages.adapter = adapter
        if (sorted.isNotEmpty()) {
            rvMessages.scrollToPosition(sorted.size - 1)
        }
    }
    
    private fun retryMessage(message: com.swapmap.zwap.demo.model.ChatMessage) {
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getDatabase(requireContext()).pendingMessageDao()
            val pending = dao.getMessageById(message.id)
            if (pending != null) {
                // Reset status
                dao.update(pending.copy(status = "Pending"))
                
                // Re-enqueue
                val inputData = workDataOf(
                    "channelId" to pending.channelId,
                    "threadId" to pending.threadId,
                    "userId" to pending.userId,
                    "userName" to pending.userName,
                    "messageText" to pending.messageText,
                    "imageUri" to pending.imageUri,
                    "messageType" to pending.messageType,
                    "messageId" to pending.id
                )

                val uploadWork = OneTimeWorkRequest.Builder(ChatUploadWorker::class.java)
                    .setInputData(inputData)
                    .build()
                    
                WorkManager.getInstance(requireContext()).enqueue(uploadWork)
            }
        }
    }

    private fun sendMessage(channelName: String, text: String, type: String = "text", imageUrl: String? = null) {
        val user = auth.currentUser ?: return
        if (currentRegionId.isEmpty()) return
        
        val msgId = UUID.randomUUID().toString()
        
        // 1. Insert into Local DB (Optimistic)
        lifecycleScope.launch(Dispatchers.IO) {
            val pending = PendingMessage(
                id = msgId,
                channelId = currentRegionId,
                threadId = channelName,
                userId = user.uid,
                userName = user.displayName ?: "User",
                messageText = text,
                messageType = type,
                imageUri = imageUrl,
                status = "Pending"
            )
            AppDatabase.getDatabase(requireContext()).pendingMessageDao().insert(pending)
            
            // 2. Schedule Background Upload (Main thread NOT required for workmanager enqueue, safe to do here)
            val inputData = workDataOf(
                "channelId" to currentRegionId,
                "threadId" to channelName,
                "userId" to user.uid,
                "userName" to (user.displayName ?: "User"),
                "messageText" to text,
                "imageUri" to imageUrl,
                "messageType" to type,
                "messageId" to msgId
            )

            val uploadWork = OneTimeWorkRequest.Builder(ChatUploadWorker::class.java)
                .setInputData(inputData)
                .build()
                
            WorkManager.getInstance(requireContext()).enqueue(uploadWork)
        }
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
