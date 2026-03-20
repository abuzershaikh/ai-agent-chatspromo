package com.message.bulksend.autorespond.documentreply

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.launch

class DocumentReplyActivity : ComponentActivity() {
    
    private lateinit var documentManager: DocumentReplyManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display
        enableEdgeToEdge()
        
        documentManager = DocumentReplyManager(this)
        
        // Check if phone has lock screen security and warn user
        checkLockScreenAndWarnUser()
        
        setContent {
            BulksendTestTheme {
                DocumentReplyScreen(
                    documentManager = documentManager,
                    onBackPressed = { finish() }
                )
            }
        }
    }
    
    /**
     * Check if phone has lock screen security and warn user about limitations
     */
    private fun checkLockScreenAndWarnUser() {
        try {
            val hasSecurityLock = LockScreenDetector.hasSecurityLockSet(this)
            val isCurrentlyLocked = LockScreenDetector.isPhoneLocked(this)
            
            Log.d("DocumentReplyActivity", "🔒 Security lock set: $hasSecurityLock, Currently locked: $isCurrentlyLocked")
            
            if (hasSecurityLock) {
                // Show warning about lock screen limitations
                LockScreenWarningDialog.showLockScreenWarning(this) {
                    Log.d("DocumentReplyActivity", "✅ Lock screen warning dialog dismissed")
                }
            }
        } catch (e: Exception) {
            Log.e("DocumentReplyActivity", "❌ Error checking lock screen: ${e.message}")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentReplyScreen(
    documentManager: DocumentReplyManager,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var documentReplies by remember { mutableStateOf(documentManager.getAllDocumentReplies()) }
    var showAddScreen by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    
    // Track document counts for real-time updates
    var imageCount by remember { mutableStateOf(documentManager.getDocumentCount(DocumentType.IMAGE)) }
    var videoCount by remember { mutableStateOf(documentManager.getDocumentCount(DocumentType.VIDEO)) }
    var pdfCount by remember { mutableStateOf(documentManager.getDocumentCount(DocumentType.PDF)) }
    var audioCount by remember { mutableStateOf(documentManager.getDocumentCount(DocumentType.AUDIO)) }
    
    // Function to refresh document counts
    fun refreshDocumentCounts() {
        imageCount = documentManager.getDocumentCount(DocumentType.IMAGE)
        videoCount = documentManager.getDocumentCount(DocumentType.VIDEO)
        pdfCount = documentManager.getDocumentCount(DocumentType.PDF)
        audioCount = documentManager.getDocumentCount(DocumentType.AUDIO)
        Log.d("DocumentReply", "📊 Document counts refreshed - Images: $imageCount, Videos: $videoCount, PDFs: $pdfCount, Audios: $audioCount")
    }
    
    // File picker launchers
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                Log.d("DocumentReply", "📸 Image picked: $uri")
                val success = documentManager.saveDocument(it, DocumentType.IMAGE)
                if (success) {
                    Toast.makeText(context, "✅ Image added to app successfully!", Toast.LENGTH_SHORT).show()
                    // Refresh counts and replies
                    refreshDocumentCounts()
                    documentReplies = documentManager.getAllDocumentReplies()
                } else {
                    Toast.makeText(context, "❌ Failed to add image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                Log.d("DocumentReply", "🎥 Video picked: $uri")
                val success = documentManager.saveDocument(it, DocumentType.VIDEO)
                if (success) {
                    Toast.makeText(context, "✅ Video added to app successfully!", Toast.LENGTH_SHORT).show()
                    // Refresh counts and replies
                    refreshDocumentCounts()
                    documentReplies = documentManager.getAllDocumentReplies()
                } else {
                    Toast.makeText(context, "❌ Failed to add video", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                Log.d("DocumentReply", "📄 PDF picked: $uri")
                val success = documentManager.saveDocument(it, DocumentType.PDF)
                if (success) {
                    Toast.makeText(context, "✅ PDF added to app successfully!", Toast.LENGTH_SHORT).show()
                    // Refresh counts and replies
                    refreshDocumentCounts()
                    documentReplies = documentManager.getAllDocumentReplies()
                } else {
                    Toast.makeText(context, "❌ Failed to add PDF", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                Log.d("DocumentReply", "🎵 Audio picked: $uri")
                val success = documentManager.saveDocument(it, DocumentType.AUDIO)
                if (success) {
                    Toast.makeText(context, "✅ Audio added to app successfully!", Toast.LENGTH_SHORT).show()
                    // Refresh counts and replies
                    refreshDocumentCounts()
                    documentReplies = documentManager.getAllDocumentReplies()
                } else {
                    Toast.makeText(context, "❌ Failed to add audio", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Log.d("DocumentReply", "✅ Permissions granted after request")
            // Handle permission granted in AddDocumentReplyScreen
        } else {
            Log.d("DocumentReply", "❌ Permissions denied")
            Toast.makeText(context, "Permission required to access files", Toast.LENGTH_SHORT).show()
        }
    }
    
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF0f0c29), Color(0xFF302b63), Color(0xFF24243e))
    )
    
    if (showAddScreen) {
        // Full screen Add Document Reply Screen
        AddDocumentReplyScreen(
            documentManager = documentManager,
            imagePickerLauncher = imagePickerLauncher,
            videoPickerLauncher = videoPickerLauncher,
            pdfPickerLauncher = pdfPickerLauncher,
            audioPickerLauncher = audioPickerLauncher,
            permissionLauncher = permissionLauncher,
            onBackPressed = { showAddScreen = false },
            onSave = { reply ->
                documentManager.saveDocumentReply(reply)
                documentReplies = documentManager.getAllDocumentReplies()
                refreshDocumentCounts()
                showAddScreen = false
            },
            onDocumentAdded = { documentFile ->
                // This callback will be used to update selectedDocuments in AddDocumentReplyScreen
                refreshDocumentCounts()
                documentReplies = documentManager.getAllDocumentReplies()
            }
        )
    } else {
        Scaffold(
            topBar = {
                EdgeToEdgeTopAppBar(
                    title = "Document Reply",
                    onBackPressed = onBackPressed,
                    onInfoClicked = { showInfoDialog = true }
                )
            },
            floatingActionButton = {
                AnimatedFloatingActionButton(
                    onClick = { showAddScreen = true }
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundBrush)
                    .padding(padding)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Document Type Cards
                    item {
                        Text(
                            "Document Types",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    item {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            item {
                                DocumentTypeCard(
                                    icon = Icons.Default.Image,
                                    title = "Images",
                                    count = imageCount,
                                    color = Color(0xFF4CAF50),
                                    modifier = Modifier.width(120.dp)
                                ) {
                                    Log.d("DocumentReply", "🖼️ Image card clicked - launching image picker")
                                    checkPermissionAndLaunchPicker(context, permissionLauncher) {
                                        imagePickerLauncher.launch("image/*")
                                    }
                                }
                            }
                            
                            item {
                                DocumentTypeCard(
                                    icon = Icons.Default.VideoFile,
                                    title = "Videos",
                                    count = videoCount,
                                    color = Color(0xFF2196F3),
                                    modifier = Modifier.width(120.dp)
                                ) {
                                    Log.d("DocumentReply", "🎬 Video card clicked - launching video picker")
                                    checkPermissionAndLaunchPicker(context, permissionLauncher) {
                                        videoPickerLauncher.launch("video/*")
                                    }
                                }
                            }
                            
                            item {
                                DocumentTypeCard(
                                    icon = Icons.Default.PictureAsPdf,
                                    title = "PDFs",
                                    count = pdfCount,
                                    color = Color(0xFFFF5722),
                                    modifier = Modifier.width(120.dp)
                                ) {
                                    Log.d("DocumentReply", "📋 PDF card clicked - launching PDF picker")
                                    checkPermissionAndLaunchPicker(context, permissionLauncher) {
                                        pdfPickerLauncher.launch("application/pdf")
                                    }
                                }
                            }
                            
                            item {
                                DocumentTypeCard(
                                    icon = Icons.Default.AudioFile,
                                    title = "Audios",
                                    count = audioCount,
                                    color = Color(0xFF9C27B0),
                                    modifier = Modifier.width(120.dp)
                                ) {
                                    Log.d("DocumentReply", "🎵 Audio card clicked - launching audio picker")
                                    checkPermissionAndLaunchPicker(context, permissionLauncher) {
                                        audioPickerLauncher.launch("audio/*")
                                    }
                                }
                            }
                        }
                    }
                    
                    // Document Replies List
                    item {
                        Text(
                            "Document Replies",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    if (documentReplies.isEmpty()) {
                        item {
                            EmptyDocumentRepliesCard()
                        }
                    } else {
                        items(documentReplies) { reply ->
                            DocumentReplyCard(
                                reply = reply,
                                onToggle = {
                                    documentManager.toggleReplyEnabled(reply.id)
                                    documentReplies = documentManager.getAllDocumentReplies()
                                },
                                onDelete = {
                                    documentManager.deleteDocumentReply(reply.id)
                                    documentReplies = documentManager.getAllDocumentReplies()
                                }
                            )
                        }
                    }
                    
                    // Add bottom padding for FAB
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
    
    // Show info dialog
    if (showInfoDialog) {
        DocumentReplyInfoDialog.ShowDocumentReplyInfoDialog(
            onDismiss = { showInfoDialog = false }
        )
    }
}

@Composable
fun DocumentTypeCard(
    icon: ImageVector,
    title: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(120.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                "$count files",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
            )
        }
    }
}

@Composable
fun DocumentReplyCard(
    reply: DocumentReplyData,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        reply.keyword,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00D4FF)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${reply.getDocumentTypesSummary()} • ${reply.getTotalDocumentCount()} files",
                        fontSize = 14.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                Switch(
                    checked = reply.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF4ADE80)
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF2a2a3e)
                ) {
                    Text(
                        reply.matchOption.uppercase(),
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyDocumentRepliesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e))
    ) {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("No document replies yet", fontSize = 16.sp, color = Color(0xFF94A3B8))
            Text("Add keywords to send documents automatically", fontSize = 14.sp, color = Color(0xFF64748B))
        }
    }
}

private fun checkPermissionAndLaunchPicker(
    context: android.content.Context,
    permissionLauncher: androidx.activity.compose.ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    onPermissionGranted: () -> Unit
) {
    val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        // Android 13+ doesn't need READ_EXTERNAL_STORAGE for file picker
        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    } else {
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }
    
    val allPermissionsGranted = permissions.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    
    if (allPermissionsGranted || android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        Log.d("DocumentReply", "✅ Permissions granted, launching file picker")
        onPermissionGranted()
    } else {
        Log.d("DocumentReply", "❌ Permissions needed, requesting permissions")
        permissionLauncher.launch(permissions)
    }
}

@Composable
fun EdgeToEdgeTopAppBar(
    title: String,
    onBackPressed: () -> Unit,
    onInfoClicked: () -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars),
        color = Color(0xFF1a1a2e)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackPressed) {
                Icon(
                    Icons.Default.ArrowBack, 
                    contentDescription = "Back", 
                    tint = Color(0xFF00D4FF)
                )
            }
            Text(
                text = title,
                color = Color(0xFF00D4FF),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
            IconButton(onClick = onInfoClicked) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Info",
                    tint = Color(0xFF00D4FF)
                )
            }
        }
    }
}

@Composable
fun AnimatedFloatingActionButton(
    onClick: () -> Unit
) {
    // Animation states
    val infiniteTransition = rememberInfiniteTransition(label = "fab_animation")
    
    // Ripple animation
    val rippleScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ripple_scale"
    )
    
    // Color animation
    val colorAnimation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "color_animation"
    )
    
    // Button scale animation on press
    var isPressed by remember { mutableStateOf(false) }
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "button_scale"
    )
    
    // Color interpolation
    val fabColor = androidx.compose.ui.graphics.lerp(
        Color(0xFF00D4FF), // Cyan
        Color(0xFF9C27B0), // Purple
        colorAnimation
    )
    
    val rippleColor = androidx.compose.ui.graphics.lerp(
        Color(0xFF00D4FF).copy(alpha = 0.3f),
        Color(0xFF9C27B0).copy(alpha = 0.3f),
        colorAnimation
    )
    
    Box(
        modifier = Modifier.size(80.dp),
        contentAlignment = Alignment.Center
    ) {
        // Ripple effect background
        Canvas(
            modifier = Modifier
                .size(80.dp)
                .scale(rippleScale)
        ) {
            drawCircle(
                color = rippleColor,
                radius = size.minDimension / 2
            )
        }
        
        // Main FAB
        FloatingActionButton(
            onClick = {
                isPressed = true
                onClick()
                // Reset press state after animation
                kotlinx.coroutines.GlobalScope.launch {
                    kotlinx.coroutines.delay(150)
                    isPressed = false
                }
            },
            containerColor = fabColor,
            contentColor = Color.White,
            modifier = Modifier
                .size(56.dp)
                .scale(buttonScale),
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 8.dp,
                pressedElevation = 12.dp
            )
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add Document Reply",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}