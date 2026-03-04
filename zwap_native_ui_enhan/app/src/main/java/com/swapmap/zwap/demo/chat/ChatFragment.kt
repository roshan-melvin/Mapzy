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
import com.swapmap.zwap.demo.network.CloudinaryManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import com.google.firebase.firestore.ListenerRegistration

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

    private var messages: List<com.swapmap.zwap.demo.model.ChatMessage> = emptyList()
    private var uploadingMessages = mutableListOf<com.swapmap.zwap.demo.model.ChatMessage>()
    private var messageListener: ListenerRegistration? = null

    private val getContent = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri: android.net.Uri? ->
        if (uri != null) {
            val context = context ?: return@registerForActivityResult
            val type = if (context.contentResolver.getType(uri)?.startsWith("video") == true) "video" else "image"
            
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
            
            contentResolver.openInputStream(uri)?.use { input ->
                java.io.FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            
            file.absolutePath
        } catch (e: Exception) {
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

        // Native Gesture Handling: Handle back swipe to navigate back or pop the fragment
        val callback = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val msgContainer = view.findViewById<View>(R.id.message_container)
                val rvChannelsView = view.findViewById<View>(R.id.rv_chat_channels)
                
                when {
                    msgContainer?.visibility == View.VISIBLE -> {
                        // If in a chat channel, go back to channel list
                        msgContainer.visibility = View.GONE
                        rvChannelsView.visibility = View.VISIBLE
                        val parts = currentRegionId.split("-")
                        val cityName = if (parts.isNotEmpty()) parts.last().uppercase() else currentRegionId
                        titleView.text = "$cityName Channels"
                        messageListener?.remove()
                    }
                    else -> {
                        // Otherwise, pop the fragment itself
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)

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

        view.findViewById<View>(R.id.btn_attach_image)?.setOnClickListener {
            getContent.launch(arrayOf("image/*", "video/*"))
        }
        
        view.findViewById<View>(R.id.btn_send_message)?.setOnClickListener {
            val etMessage = view.findViewById<android.widget.EditText>(R.id.et_message_input)
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
        chatContentArea.visibility = View.VISIBLE
        rvRegions.visibility = View.GONE
        rvChannels.visibility = View.GONE
        view?.findViewById<View>(R.id.message_container)?.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
    }

    private fun showRegionsSidebar(regions: List<String>) {
        emptyState.visibility = View.GONE
        chatContentArea.visibility = View.VISIBLE
        rvRegions.visibility = View.VISIBLE
        
        if (regions.isNotEmpty()) {
            loadChannels(regions[0])
        }

        val adapter = RegionAdapter(regions) { regionId ->
            loadChannels(regionId)
        }
        rvRegions.adapter = adapter
    }

    private fun loadChannels(regionId: String) {
        currentRegionId = regionId
        
        rvChannels.visibility = View.VISIBLE
        view?.findViewById<View>(R.id.message_container)?.visibility = View.GONE
        
        val parts = regionId.split("-")
        val cityName = if (parts.isNotEmpty()) parts.last().uppercase() else regionId
        titleView.text = "$cityName Channels"

        val staticChannels = listOf(
            com.swapmap.zwap.demo.model.Channel(id = "welcome", name = "Welcome", description = "Welcome to $cityName!"),
            com.swapmap.zwap.demo.model.Channel(id = "general", name = "General", description = "General discussion"),
            com.swapmap.zwap.demo.model.Channel(id = "hazard", name = "Hazards", description = "Report hazards"),
            com.swapmap.zwap.demo.model.Channel(id = "traffic", name = "Traffic", description = "Traffic updates"),
            com.swapmap.zwap.demo.model.Channel(id = "speed-cameras", name = "Speed Cameras", description = "Speed trap alerts")
        )
        
        val adapter = ChannelAdapter(staticChannels) { channel ->
            selectChannel(channel)
        }
        rvChannels.adapter = adapter
    }

    private fun selectChannel(channel: com.swapmap.zwap.demo.model.Channel) {
        // Remove old listener and clear messages
        messageListener?.remove()
        messages = emptyList()
        uploadingMessages.clear()
        updateMessageList()
        
        currentChannelId = channel.id
        rvChannels.visibility = View.GONE
        val msgContainer = view?.findViewById<View>(R.id.message_container)
        msgContainer?.visibility = View.VISIBLE
        
        titleView.text = "# ${channel.name}"

        if (currentRegionId.isNotEmpty()) {
            messageListener = db.collection("chat").document(currentRegionId)
                .collection("threads").document(channel.id)
                .collection("messages")
                .orderBy("created_at", com.google.firebase.firestore.Query.Direction.ASCENDING)
                .addSnapshotListener { snapshots, e ->
                    if (e != null) return@addSnapshotListener
                    
                    val msgList = mutableListOf<com.swapmap.zwap.demo.model.ChatMessage>()
                    if (snapshots != null) {
                        for (doc in snapshots) {
                            msgList.add(doc.toObject(com.swapmap.zwap.demo.model.ChatMessage::class.java).copy(id = doc.id))
                        }
                    }
                    messages = msgList
                    
                    // Remove uploaded messages from local list (they're now in Firestore)
                    val firestoreIds = messages.map { it.id }.toSet()
                    uploadingMessages.removeAll { firestoreIds.contains(it.id) }
                    
                    updateMessageList()
                }
        }
    }

    private fun updateMessageList() {
        val currentUid = auth.currentUser?.uid ?: ""
        // Combine Firestore messages with uploading messages
        val allMessages = messages + uploadingMessages
        val sorted = allMessages.sortedBy { it.created_at?.seconds ?: Long.MAX_VALUE }
        val adapter = MessageAdapter(sorted, currentUid) { _ -> }
        rvMessages.adapter = adapter
        if (sorted.isNotEmpty()) {
            rvMessages.scrollToPosition(sorted.size - 1)
        }
    }

    private fun sendMessage(channelName: String, text: String, type: String = "text", imageUrl: String? = null) {
        val user = auth.currentUser ?: return
        if (currentRegionId.isEmpty()) return
        
        val msgId = UUID.randomUUID().toString()
        
        if (imageUrl != null && imageUrl.startsWith("file://")) {
            uploadMediaAndSend(msgId, channelName, text, type, imageUrl)
        } else {
            sendToFirebase(msgId, channelName, text, type, imageUrl)
        }
    }
    
    private fun uploadMediaAndSend(msgId: String, channelName: String, text: String, type: String, localPath: String) {
        val user = auth.currentUser ?: return
        val filePath = localPath.removePrefix("file://")
        val fileUri = android.net.Uri.fromFile(java.io.File(filePath))
        
        // Add local message with uploading state (WhatsApp-style)
        val localMessage = com.swapmap.zwap.demo.model.ChatMessage(
            id = msgId,
            channel_id = channelName,
            user_id = user.uid,
            username = user.displayName ?: "User",
            text = text,
            type = type,
            created_at = com.google.firebase.Timestamp.now()
        ).apply {
            localUri = fileUri
            isUploading = true
            uploadProgress = 0
        }
        uploadingMessages.add(localMessage)
        updateMessageList()
        
        CloudinaryManager.uploadImage(fileUri, { progress ->
            // Update progress in UI
            activity?.runOnUiThread {
                uploadingMessages.find { it.id == msgId }?.uploadProgress = progress
                updateMessageList()
            }
        }) { cloudinaryUrl ->
            activity?.runOnUiThread {
                if (cloudinaryUrl != null) {
                    // Mark as done uploading
                    uploadingMessages.find { it.id == msgId }?.isUploading = false
                    sendToFirebase(msgId, channelName, text, type, cloudinaryUrl)
                } else {
                    // Mark as failed
                    uploadingMessages.find { it.id == msgId }?.apply {
                        isUploading = false
                        localStatus = "Failed"
                    }
                    updateMessageList()
                    Toast.makeText(context, "Upload failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun sendToFirebase(msgId: String, channelName: String, text: String, type: String, imageUrl: String?) {
        val user = auth.currentUser ?: return
        
        val messageData = hashMapOf(
            "user_id" to user.uid,
            "username" to (user.displayName ?: "User"),
            "text" to text,
            "type" to type,
            "image_url" to imageUrl,
            "created_at" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        
        db.collection("chat").document(currentRegionId)
            .collection("threads").document(channelName)
            .collection("messages").document(msgId)
            .set(messageData)
            .addOnFailureListener { e ->
                Toast.makeText(context, "Send failed: ${e.message}", Toast.LENGTH_SHORT).show()
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

        val regionId = "$country-$state-$city".lowercase().replace(" ", "-")

        val data = mapOf(
            "chat_regions" to com.google.firebase.firestore.FieldValue.arrayUnion(regionId)
        )

        db.collection("users").document(userId)
            .set(data, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(context, "Joined $city chat!", Toast.LENGTH_SHORT).show()
                checkUserRegions()
            }.addOnFailureListener { e ->
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        messageListener?.remove()
    }
}
