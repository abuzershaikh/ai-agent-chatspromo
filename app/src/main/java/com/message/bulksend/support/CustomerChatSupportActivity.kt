package com.message.bulksend.support

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.message.bulksend.ui.theme.BulksendTestTheme
import com.message.bulksend.support.WelcomeMessageManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class CustomerChatSupportActivity : ComponentActivity() {
    
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Check if opened from notification
        val fromNotification = intent.getBooleanExtra("fromNotification", false)
        val notificationOderId = intent.getStringExtra("oderId")
        
        if (fromNotification) {
            Toast.makeText(this, "Opening chat from notification", Toast.LENGTH_SHORT).show()
        }
        
        setContent {
            BulksendTestTheme {
                CustomerChatScreen(
                    onBack = { finish() },
                    initialOderId = notificationOderId
                )
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { 
            setOnlineStatus(true)
            // Clear unread count when user opens chat
            val welcomeManager = WelcomeMessageManager(this@CustomerChatSupportActivity)
            welcomeManager.clearUnreadCount()
        }
    }
    
    override fun onPause() {
        super.onPause()
        lifecycleScope.launch { setOnlineStatus(false) }
    }
    
    private suspend fun setOnlineStatus(isOnline: Boolean) {
        val email = auth.currentUser?.email ?: return
        val oderId = email.replace(".", "_")
        try {
            db.collection("admin_chats").document(oderId).update(
                mapOf("isOnline" to isOnline, "lastSeen" to Timestamp.now())
            ).await()
        } catch (_: Exception) { }
    }
}

// Data Models
data class ChatMessage(
    val messageId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderEmail: String = "",
    val message: String = "",
    val messageType: String = "text", // text, image, video, video_link, link, whatsapp_template
    val imageUrl: String = "",
    val videoUrl: String = "",
    val videoThumbnail: String = "",
    val videoTitle: String = "",
    val videoDuration: Long = 0, // in seconds
    val linkUrl: String = "",
    val linkTitle: String = "",
    val linkDescription: String = "",
    val linkImage: String = "",
    val timestamp: Timestamp? = null,
    val isFromAdmin: Boolean = false,
    val isRead: Boolean = false,
    val readAt: Timestamp? = null,
    val whatsappNumber: String = "", // For WhatsApp template
    val whatsappMessage: String = "" // For WhatsApp template prefilled message
)

data class VideoInfo(
    val url: String,
    val thumbnail: String,
    val title: String,
    val platform: String
)

data class LinkPreview(
    val url: String,
    val title: String,
    val description: String,
    val image: String,
    val isYouTube: Boolean = false,
    val youtubeId: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerChatScreen(
    onBack: () -> Unit,
    initialOderId: String? = null
) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val storage = FirebaseStorage.getInstance()
    val scope = rememberCoroutineScope()
    
    val user = auth.currentUser
    val userEmail = user?.email ?: ""
    val oderId = initialOderId ?: userEmail.replace(".", "_")
    
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var messageText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var selectedImageUrl by remember { mutableStateOf<String?>(null) }
    
    val listState = rememberLazyListState()
    
    // Image picker
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { imageUri ->
            scope.launch {
                isSending = true
                val success = sendImage(context, db, storage, oderId, user, imageUri)
                if (!success) {
                    Toast.makeText(context, "Failed to send image. Please try again.", Toast.LENGTH_SHORT).show()
                }
                isSending = false
            }
        }
    }
    
    // Video picker with chooser
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { videoUri ->
                scope.launch {
                    isSending = true
                    // Video sending functionality can be implemented later
                    Toast.makeText(context, "Video sending feature coming soon", Toast.LENGTH_SHORT).show()
                    isSending = false
                }
            }
        }
    }
    
    // Initialize chat session
    LaunchedEffect(Unit) {
        if (oderId.isNotEmpty()) {
            isLoading = true
            initializeChatSession(context, db, oderId, user)
            isLoading = false
        }
    }
    
    // Listen to messages
    LaunchedEffect(oderId) {
        if (oderId.isEmpty()) return@LaunchedEffect
        
        db.collection("admin_chats").document(oderId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                val newMessages = snapshot?.documents?.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    
                    // Manual parsing - isFromAdmin field check
                    val isFromAdminRaw = data["isFromAdmin"]
                    val isFromAdmin = when (isFromAdminRaw) {
                        is Boolean -> isFromAdminRaw
                        is String -> isFromAdminRaw.equals("true", ignoreCase = true)
                        else -> false
                    }
                    
                    val isReadRaw = data["isRead"]
                    val isRead = when (isReadRaw) {
                        is Boolean -> isReadRaw
                        is String -> isReadRaw.equals("true", ignoreCase = true)
                        else -> false
                    }
                    
                    ChatMessage(
                        messageId = doc.id,
                        senderId = data["senderId"] as? String ?: "",
                        senderName = data["senderName"] as? String ?: "",
                        senderEmail = data["senderEmail"] as? String ?: "",
                        message = data["message"] as? String ?: "",
                        messageType = data["messageType"] as? String ?: "text",
                        imageUrl = data["imageUrl"] as? String ?: "",
                        videoUrl = data["videoUrl"] as? String ?: "",
                        videoThumbnail = data["videoThumbnail"] as? String ?: "",
                        videoTitle = data["videoTitle"] as? String ?: "",
                        videoDuration = (data["videoDuration"] as? Number)?.toLong() ?: 0,
                        linkUrl = data["linkUrl"] as? String ?: "",
                        linkTitle = data["linkTitle"] as? String ?: "",
                        linkDescription = data["linkDescription"] as? String ?: "",
                        linkImage = data["linkImage"] as? String ?: "",
                        timestamp = data["timestamp"] as? Timestamp,
                        isFromAdmin = isFromAdmin,
                        isRead = isRead,
                        readAt = data["readAt"] as? Timestamp,
                        whatsappNumber = data["whatsappNumber"] as? String ?: "",
                        whatsappMessage = data["whatsappMessage"] as? String ?: ""
                    )
                } ?: emptyList()
                messages = newMessages
                
                // Mark admin messages as read
                scope.launch {
                    markMessagesAsRead(db, oderId)
                }
            }
    }
    
    // Periodic sync every 15 seconds for read status
    LaunchedEffect(oderId) {
        if (oderId.isEmpty()) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(15000L) // 15 seconds
            // Force refresh messages to get updated read status
            try {
                val snapshot = db.collection("admin_chats").document(oderId)
                    .collection("messages")
                    .orderBy("timestamp", Query.Direction.ASCENDING)
                    .get()
                    .await()
                
                val refreshedMessages = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    val isFromAdminRaw = data["isFromAdmin"]
                    val isFromAdmin = when (isFromAdminRaw) {
                        is Boolean -> isFromAdminRaw
                        is String -> isFromAdminRaw.equals("true", ignoreCase = true)
                        else -> false
                    }
                    val isReadRaw = data["isRead"]
                    val isRead = when (isReadRaw) {
                        is Boolean -> isReadRaw
                        is String -> isReadRaw.equals("true", ignoreCase = true)
                        else -> false
                    }
                    ChatMessage(
                        messageId = doc.id,
                        senderId = data["senderId"] as? String ?: "",
                        senderName = data["senderName"] as? String ?: "",
                        senderEmail = data["senderEmail"] as? String ?: "",
                        message = data["message"] as? String ?: "",
                        messageType = data["messageType"] as? String ?: "text",
                        imageUrl = data["imageUrl"] as? String ?: "",
                        videoUrl = data["videoUrl"] as? String ?: "",
                        videoThumbnail = data["videoThumbnail"] as? String ?: "",
                        videoTitle = data["videoTitle"] as? String ?: "",
                        videoDuration = (data["videoDuration"] as? Number)?.toLong() ?: 0,
                        linkUrl = data["linkUrl"] as? String ?: "",
                        linkTitle = data["linkTitle"] as? String ?: "",
                        linkDescription = data["linkDescription"] as? String ?: "",
                        linkImage = data["linkImage"] as? String ?: "",
                        timestamp = data["timestamp"] as? Timestamp,
                        isFromAdmin = isFromAdmin,
                        isRead = isRead,
                        readAt = data["readAt"] as? Timestamp,
                        whatsappNumber = data["whatsappNumber"] as? String ?: "",
                        whatsappMessage = data["whatsappMessage"] as? String ?: ""
                    )
                }
                messages = refreshedMessages
            } catch (_: Exception) { }
        }
    }
    
    // Auto scroll to bottom
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    Scaffold(
        topBar = {
            ChatTopBar(onBack = onBack)
        },
        bottomBar = {
            ChatInputBar(
                messageText = messageText,
                onMessageChange = { messageText = it },
                onSend = {
                    if (messageText.isNotBlank() && !isSending) {
                        scope.launch {
                            isSending = true
                            val success = sendMessageWithLinkDetection(context, db, oderId, user, messageText)
                            if (success) messageText = ""
                            isSending = false
                        }
                    }
                },
                onAttach = { imagePicker.launch("image/*") },
                isSending = isSending
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF5F5F5))
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (messages.isEmpty()) {
                EmptyChatView()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.messageId }) { msg ->
                        MessageBubble(
                            message = msg,
                            onImageClick = { selectedImageUrl = it }
                        )
                    }
                }
            }
        }
    }
    
    // Image preview dialog
    selectedImageUrl?.let { url ->
        ImagePreviewDialog(
            imageUrl = url,
            onDismiss = { selectedImageUrl = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    onBack: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.SupportAgent,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF667eea), CircleShape)
                        .padding(8.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Support Team", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
    )
}

@Composable
fun ChatInputBar(
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    isSending: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAttach, enabled = !isSending) {
                Icon(Icons.Default.AttachFile, contentDescription = "Attach", tint = Color(0xFF667eea))
            }
            
            OutlinedTextField(
                value = messageText,
                onValueChange = onMessageChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...") },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF667eea),
                    unfocusedBorderColor = Color.LightGray
                ),
                maxLines = 4
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            IconButton(
                onClick = onSend,
                enabled = messageText.isNotBlank() && !isSending,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (messageText.isNotBlank()) Color(0xFF667eea) else Color.LightGray,
                        CircleShape
                    )
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    onImageClick: (String) -> Unit
) {
    val context = LocalContext.current
    val isFromAdmin = message.isFromAdmin
    // WhatsApp style colors - Admin (left/white), User (right/light green like WhatsApp)
    val bubbleColor = if (isFromAdmin) Color(0xFFFFFFFF) else Color(0xFFDCF8C6)
    val textColor = Color.Black
    
    // State for inline link preview (for text messages with links)
    var inlineLinkPreview by remember { mutableStateOf<LinkPreview?>(null) }
    val scope = rememberCoroutineScope()
    
    // Check for links in text messages and fetch preview
    LaunchedEffect(message.message) {
        if (message.messageType == "text" && message.message.isNotEmpty()) {
            val urls = extractUrls(message.message)
            if (urls.isNotEmpty()) {
                scope.launch {
                    val firstUrl = urls.first()
                    val linkPreview = fetchLinkPreview(firstUrl)
                    inlineLinkPreview = linkPreview
                }
            }
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = if (isFromAdmin) Arrangement.Start else Arrangement.End
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(
                topStart = if (isFromAdmin) 4.dp else 16.dp,
                topEnd = if (isFromAdmin) 16.dp else 4.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Image message
                if (message.messageType == "image" && message.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = message.imageUrl,
                        contentDescription = "Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onImageClick(message.imageUrl) },
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                // Link preview (for messageType "link")
                if (message.messageType == "link" && message.linkUrl.isNotEmpty()) {
                    LinkPreviewCard(
                        linkUrl = message.linkUrl,
                        linkTitle = message.linkTitle,
                        linkDescription = message.linkDescription,
                        linkImage = message.linkImage,
                        onClick = {
                            openLink(context, message.linkUrl, message.linkTitle)
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                // Inline link preview (for text messages with links)
                if (message.messageType == "text" && inlineLinkPreview != null) {
                    LinkPreviewCard(
                        linkUrl = inlineLinkPreview!!.url,
                        linkTitle = inlineLinkPreview!!.title,
                        linkDescription = inlineLinkPreview!!.description,
                        linkImage = inlineLinkPreview!!.image,
                        onClick = {
                            openLink(context, inlineLinkPreview!!.url, inlineLinkPreview!!.title)
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                // Text message
                if (message.message.isNotEmpty() && message.message != "📷 Image") {
                    Text(
                        text = message.message,
                        color = textColor,
                        fontSize = 15.sp,
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                // Check if message contains links
                                val urls = extractUrls(message.message)
                                if (urls.isNotEmpty()) {
                                    openLink(context, urls.first(), "Link")
                                }
                            },
                            onLongClick = {
                                // Copy to clipboard
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Message", message.message)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
                            }
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                // WhatsApp Template Button (for Indian users) - After text message
                if (message.messageType == "whatsapp_template" && message.whatsappNumber.isNotEmpty()) {
                    Button(
                        onClick = {
                            val phoneNumber = message.whatsappNumber
                            val prefillMessage = message.whatsappMessage.ifEmpty { "Hi ChatsPromo Support, I need help with the app." }
                            val encodedMessage = java.net.URLEncoder.encode(prefillMessage, "UTF-8")
                            val whatsappUrl = "https://wa.me/$phoneNumber?text=$encodedMessage"
                            
                            try {
                                val intent = Intent(Intent.ACTION_VIEW)
                                intent.data = android.net.Uri.parse(whatsappUrl)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF25D366) // WhatsApp green
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Chat,
                                contentDescription = "WhatsApp",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Chat on WhatsApp",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                // Timestamp and double tick for user messages
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTimestamp(message.timestamp),
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                    // Show double tick only for user messages (sent by user)
                    if (!isFromAdmin) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (message.isRead) Color(0xFF34B7F1) else Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LinkPreviewCard(
    linkUrl: String,
    linkTitle: String,
    linkDescription: String,
    linkImage: String,
    onClick: () -> Unit
) {
    val isYouTube = linkUrl.contains("youtube.com") || linkUrl.contains("youtu.be")
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isYouTube) Color(0xFFF0F8FF) else Color(0xFFF0F0F0)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column {
            // Image/Thumbnail with better aspect ratio for videos
            if (linkImage.isNotEmpty()) {
                Box {
                    AsyncImage(
                        model = linkImage,
                        contentDescription = if (isYouTube) "YouTube video thumbnail" else "Link preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isYouTube) 160.dp else 120.dp),
                        contentScale = ContentScale.Crop
                    )
                    
                    // YouTube play button overlay with better styling
                    if (isYouTube) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            // YouTube play button with shadow effect
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(
                                        Color.Red,
                                        CircleShape
                                    )
                                    .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Play YouTube Video",
                                    modifier = Modifier.size(32.dp),
                                    tint = Color.White
                                )
                            }
                        }
                        
                        // YouTube logo in corner
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .background(
                                    Color.Red,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "YouTube",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            // Content with YouTube-specific styling
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                if (linkTitle.isNotEmpty()) {
                    Text(
                        text = linkTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isYouTube) 15.sp else 14.sp,
                        maxLines = 2,
                        color = if (isYouTube) Color(0xFF1565C0) else Color.Black
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                if (linkDescription.isNotEmpty()) {
                    Text(
                        text = linkDescription,
                        fontSize = 12.sp,
                        maxLines = if (isYouTube) 3 else 2,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                // URL with YouTube-specific styling
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isYouTube) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF1976D2)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = if (isYouTube) "Watch on YouTube" else linkUrl,
                        fontSize = 11.sp,
                        color = Color(0xFF1976D2),
                        textDecoration = TextDecoration.Underline,
                        maxLines = 1,
                        fontWeight = if (isYouTube) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyChatView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Chat,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color(0xFF667eea).copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Start a conversation",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Gray
        )
        Text(
            "Send a message to get help from our support team",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
fun ImagePreviewDialog(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onDismiss() }
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Full Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

// Helper Functions
private fun openLink(context: Context, url: String, title: String) {
    val intent = Intent(context, WebViewActivity::class.java).apply {
        putExtra("URL", url)
        putExtra("TITLE", title)
    }
    context.startActivity(intent)
}

private fun extractUrls(text: String): List<String> {
    val urlPattern = Pattern.compile(
        "(?:^|[\\W])((ht|f)tp(s?):\\/\\/|www\\.)" +
        "(([\\w\\-]+\\.){1,}?([\\w\\-.~]+\\/?)*" +
        "[\\p{Alnum}.,%_=?&#\\-+()\\[\\]\\*$~@!:/{};']*)",
        Pattern.CASE_INSENSITIVE or Pattern.MULTILINE or Pattern.DOTALL
    )
    val matcher = urlPattern.matcher(text)
    val urls = mutableListOf<String>()
    while (matcher.find()) {
        var url = matcher.group()
        if (!url.startsWith("http")) {
            url = "https://$url"
        }
        urls.add(url.trim())
    }
    return urls
}

private fun isYouTubeUrl(url: String): Boolean {
    return url.contains("youtube.com") || url.contains("youtu.be")
}

private fun getYouTubeVideoId(url: String): String {
    val patterns = listOf(
        "(?<=watch\\?v=)[^&\\n]*",
        "(?<=youtu.be/)[^&\\n]*",
        "(?<=embed/)[^&\\n]*"
    )
    
    for (pattern in patterns) {
        val regex = Regex(pattern)
        val match = regex.find(url)
        if (match != null) {
            return match.value
        }
    }
    return ""
}

// Extract video info similar to ChatSupportHelper
private fun extractVideoInfo(url: String): VideoInfo? {
    return when {
        url.contains("youtube.com/watch?v=") || url.contains("youtu.be/") -> {
            val videoId = getYouTubeVideoId(url)
            if (videoId.isNotEmpty()) {
                VideoInfo(
                    url = url,
                    thumbnail = "https://img.youtube.com/vi/$videoId/maxresdefault.jpg",
                    title = "YouTube Video",
                    platform = "YouTube"
                )
            } else null
        }
        url.contains("vimeo.com/") -> {
            VideoInfo(
                url = url,
                thumbnail = "",
                title = "Vimeo Video",
                platform = "Vimeo"
            )
        }
        url.contains("dailymotion.com/") -> {
            VideoInfo(
                url = url,
                thumbnail = "",
                title = "Dailymotion Video",
                platform = "Dailymotion"
            )
        }
        else -> null
    }
}

private suspend fun fetchLinkPreview(url: String): LinkPreview? {
    return withContext(Dispatchers.IO) {
        try {
            // First check if it's a video link using ChatSupportHelper approach
            val videoInfo = extractVideoInfo(url)
            if (videoInfo != null && videoInfo.platform == "YouTube") {
                // For YouTube, use direct video info approach
                return@withContext LinkPreview(
                    url = url,
                    title = videoInfo.title,
                    description = "Watch on ${videoInfo.platform}",
                    image = videoInfo.thumbnail,
                    isYouTube = true,
                    youtubeId = getYouTubeVideoId(url)
                )
            }
            
            // For other links, fetch metadata from HTML
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val html = reader.readText()
                reader.close()
                
                val isYouTube = isYouTubeUrl(url)
                val youtubeId = if (isYouTube) getYouTubeVideoId(url) else ""
                
                val title = extractMetaTag(html, "og:title") 
                    ?: extractMetaTag(html, "twitter:title")
                    ?: extractTitleTag(html)
                    ?: if (isYouTube) "YouTube Video" else "Link"
                
                val description = extractMetaTag(html, "og:description")
                    ?: extractMetaTag(html, "twitter:description")
                    ?: extractMetaTag(html, "description")
                    ?: if (isYouTube) "Watch on YouTube" else ""
                
                val image = if (isYouTube && youtubeId.isNotEmpty()) {
                    // Try multiple YouTube thumbnail qualities
                    val thumbnailOptions = listOf(
                        "https://img.youtube.com/vi/$youtubeId/maxresdefault.jpg",
                        "https://img.youtube.com/vi/$youtubeId/hqdefault.jpg",
                        "https://img.youtube.com/vi/$youtubeId/mqdefault.jpg",
                        "https://img.youtube.com/vi/$youtubeId/default.jpg"
                    )
                    // Return the first available thumbnail (maxresdefault is highest quality)
                    thumbnailOptions.first()
                } else {
                    extractMetaTag(html, "og:image")
                        ?: extractMetaTag(html, "twitter:image")
                        ?: ""
                }
                
                LinkPreview(
                    url = url,
                    title = title,
                    description = description,
                    image = image,
                    isYouTube = isYouTube,
                    youtubeId = youtubeId
                )
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to video info if HTML parsing fails
            val videoInfo = extractVideoInfo(url)
            if (videoInfo != null) {
                LinkPreview(
                    url = url,
                    title = videoInfo.title,
                    description = "Watch on ${videoInfo.platform}",
                    image = videoInfo.thumbnail,
                    isYouTube = videoInfo.platform == "YouTube",
                    youtubeId = if (videoInfo.platform == "YouTube") getYouTubeVideoId(url) else ""
                )
            } else {
                null
            }
        }
    }
}

private fun extractMetaTag(html: String, property: String): String? {
    val patterns = listOf(
        "<meta\\s+property=\"$property\"\\s+content=\"([^\"]*)\">",
        "<meta\\s+name=\"$property\"\\s+content=\"([^\"]*)\">",
        "<meta\\s+content=\"([^\"]*)\"\\s+property=\"$property\">",
        "<meta\\s+content=\"([^\"]*)\"\\s+name=\"$property\">"
    )
    
    for (pattern in patterns) {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        val match = regex.find(html)
        if (match != null && match.groupValues.size > 1) {
            return match.groupValues[1].trim()
        }
    }
    return null
}

private fun extractTitleTag(html: String): String? {
    val regex = Regex("<title>([^<]*)</title>", RegexOption.IGNORE_CASE)
    val match = regex.find(html)
    return if (match != null && match.groupValues.size > 1) {
        match.groupValues[1].trim()
    } else null
}

private fun formatTimestamp(timestamp: Timestamp?): String {
    if (timestamp == null) return ""
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(timestamp.toDate())
}

private fun formatLastSeen(timestamp: Timestamp?): String {
    if (timestamp == null) return "Offline"
    val now = System.currentTimeMillis()
    val lastSeen = timestamp.toDate().time
    val diff = now - lastSeen
    
    return when {
        diff < 60000 -> "Last seen just now"
        diff < 3600000 -> "Last seen ${diff / 60000} min ago"
        diff < 86400000 -> "Last seen ${diff / 3600000} hours ago"
        else -> {
            val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
            "Last seen ${sdf.format(timestamp.toDate())}"
        }
    }
}

// Firebase Functions
private suspend fun initializeChatSession(
    context: android.content.Context,
    db: FirebaseFirestore,
    oderId: String,
    user: com.google.firebase.auth.FirebaseUser?
) {
    if (user == null) return
    val userEmail = user.email ?: return
    
    try {
        // Fetch user data from email_data
        val userDataDoc = db.collection("email_data").document(userEmail).get().await()
        val userData = userDataDoc.data
        
        // Get device info
        val deviceBrand = Build.MANUFACTURER
        val deviceModel = Build.MODEL
        val osVersion = "Android ${Build.VERSION.RELEASE}"
        val appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) { "1.0.0" }
        
        val chatSession = hashMapOf(
            "oderId" to oderId,
            "oderemail" to userEmail,
            "email" to userEmail,
            "displayName" to (userData?.get("displayName") ?: user.displayName ?: ""),
            "profilePhotoUrl" to (userData?.get("profilePhotoUrl") ?: user.photoUrl?.toString() ?: ""),
            "deviceBrand" to deviceBrand,
            "deviceModel" to deviceModel,
            "osVersion" to osVersion,
            "appVersion" to appVersion,
            "subscriptionType" to (userData?.get("subscriptionType") ?: "free"),
            "planType" to (userData?.get("planType") ?: ""),
            "isOnline" to true,
            "lastSeen" to Timestamp.now()
        )
        
        // Check if chat exists
        val chatDoc = db.collection("admin_chats").document(oderId).get().await()
        if (!chatDoc.exists()) {
            chatSession["chatStartedAt"] = Timestamp.now()
            chatSession["totalMessages"] = 0
            chatSession["unreadCount"] = 0
            chatSession["userUnreadCount"] = 0
        }
        
        db.collection("admin_chats").document(oderId)
            .set(chatSession, SetOptions.merge())
            .await()
            
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private suspend fun sendMessage(
    db: FirebaseFirestore,
    oderId: String,
    user: com.google.firebase.auth.FirebaseUser?,
    messageText: String
): Boolean {
    if (user == null || messageText.isBlank()) return false
    
    return try {
        val message = hashMapOf(
            "senderId" to user.uid,
            "senderName" to (user.displayName ?: "User"),
            "senderEmail" to (user.email ?: ""),
            "message" to messageText,
            "messageType" to "text",
            "imageUrl" to "",
            "timestamp" to Timestamp.now(),
            "isFromAdmin" to false,
            "isRead" to false
        )
        
        db.collection("admin_chats").document(oderId)
            .collection("messages").add(message).await()
        
        db.collection("admin_chats").document(oderId).update(
            mapOf(
                "lastMessage" to messageText,
                "lastMessageTime" to Timestamp.now(),
                "lastMessageBy" to "user",
                "unreadCount" to FieldValue.increment(1),
                "totalMessages" to FieldValue.increment(1)
            )
        ).await()
        
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

private suspend fun sendMessageWithLinkDetection(
    context: android.content.Context,
    db: FirebaseFirestore,
    oderId: String,
    user: com.google.firebase.auth.FirebaseUser?,
    messageText: String
): Boolean {
    if (user == null || messageText.isBlank()) return false
    
    return try {
        val urls = extractUrls(messageText)
        
        if (urls.isNotEmpty()) {
            // Message contains links - create link preview
            val firstUrl = urls.first()
            val linkPreview = fetchLinkPreview(firstUrl)
            
            if (linkPreview != null) {
                // Send message with link preview
                val message = hashMapOf(
                    "senderId" to user.uid,
                    "senderName" to (user.displayName ?: "User"),
                    "senderEmail" to (user.email ?: ""),
                    "message" to messageText,
                    "messageType" to "link",
                    "linkUrl" to linkPreview.url,
                    "linkTitle" to linkPreview.title,
                    "linkDescription" to linkPreview.description,
                    "linkImage" to linkPreview.image,
                    "timestamp" to Timestamp.now(),
                    "isFromAdmin" to false,
                    "isRead" to false
                )
                
                db.collection("admin_chats").document(oderId)
                    .collection("messages").add(message).await()
                
                db.collection("admin_chats").document(oderId).update(
                    mapOf(
                        "lastMessage" to if (linkPreview.isYouTube) "🎥 YouTube Video" else "🔗 Link",
                        "lastMessageTime" to Timestamp.now(),
                        "lastMessageBy" to "user",
                        "unreadCount" to FieldValue.increment(1),
                        "totalMessages" to FieldValue.increment(1)
                    )
                ).await()
                
                return true
            }
        }
        
        // No links or failed to fetch preview - send as regular text message
        sendMessage(db, oderId, user, messageText)
        
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

private suspend fun sendImage(
    context: android.content.Context,
    db: FirebaseFirestore,
    storage: FirebaseStorage,
    oderId: String,
    user: com.google.firebase.auth.FirebaseUser?,
    imageUri: Uri
): Boolean {
    if (user == null) {
        Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show()
        return false
    }
    
    return try {
        // Upload image to Firebase Storage
        val fileName = "chat_images/${oderId}/${UUID.randomUUID()}.jpg"
        val ref = storage.reference.child(fileName)
        
        android.util.Log.d("ChatImage", "Uploading to: $fileName")
        
        // Upload file
        val uploadTask = ref.putFile(imageUri).await()
        android.util.Log.d("ChatImage", "Upload complete: ${uploadTask.bytesTransferred} bytes")
        
        // Get download URL
        val downloadUrl = ref.downloadUrl.await().toString()
        android.util.Log.d("ChatImage", "Download URL: $downloadUrl")
        
        val message = hashMapOf(
            "senderId" to user.uid,
            "senderName" to (user.displayName ?: "User"),
            "senderEmail" to (user.email ?: ""),
            "message" to "📷 Image",
            "messageType" to "image",
            "imageUrl" to downloadUrl,
            "timestamp" to Timestamp.now(),
            "isFromAdmin" to false,
            "isRead" to false
        )
        
        db.collection("admin_chats").document(oderId)
            .collection("messages").add(message).await()
        
        db.collection("admin_chats").document(oderId).update(
            mapOf(
                "lastMessage" to "📷 Image",
                "lastMessageTime" to Timestamp.now(),
                "lastMessageBy" to "user",
                "unreadCount" to FieldValue.increment(1),
                "totalMessages" to FieldValue.increment(1)
            )
        ).await()
        
        true
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to send image", Toast.LENGTH_SHORT).show()
        false
    }
}

private suspend fun markMessagesAsRead(db: FirebaseFirestore, oderId: String) {
    try {
        db.collection("admin_chats").document(oderId)
            .update("userUnreadCount", 0).await()
        
        val unreadMessages = db.collection("admin_chats").document(oderId)
            .collection("messages")
            .whereEqualTo("isFromAdmin", true)
            .whereEqualTo("isRead", false)
            .get().await()
        
        unreadMessages.documents.forEach { doc ->
            doc.reference.update(
                mapOf("isRead" to true, "readAt" to Timestamp.now())
            )
        }
    } catch (_: Exception) { }
}
