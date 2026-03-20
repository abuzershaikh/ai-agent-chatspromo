package com.message.bulksend.voicenotereply

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.ui.theme.BulkSendTheme

/**
 * Voice Note Reply Activity
 * Automatically extracts voice notes from WhatsApp and saves them
 */
class VoiceNoteReplyActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Register real-time callback for voice note updates
        VoiceNoteFileObserver.setOnVoiceNoteSavedCallback { phoneNumber, file ->
            runOnUiThread {
                // Trigger refresh when new voice note is saved
                refreshTrigger++
                Toast.makeText(
                    this,
                    "🎤 New voice note from $phoneNumber",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        
        // Register transcription callback for real-time UI updates
        VoiceTranscriptionService.setTranscriptionCallback { result ->
            runOnUiThread {
                when (result) {
                    is TranscriptionResult.Success -> {
                        refreshTrigger++
                        Toast.makeText(
                            this,
                            "✅ Transcribed: ${result.text.take(50)}...",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    is TranscriptionResult.Error -> {
                        Toast.makeText(
                            this,
                            "❌ Transcription failed: ${result.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
        
        setContent {
            BulkSendTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0B0F17)
                ) {
                    VoiceNoteReplyScreen()
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clear callbacks when activity is destroyed
        VoiceNoteFileObserver.clearCallback()
        VoiceTranscriptionService.clearCallback()
    }
    
    // Refresh trigger for real-time updates
    private var refreshTrigger by mutableStateOf(0)
    
    /**
     * Check if we have persistent permission for the folder
     */
    private fun checkFolderPermission(): Boolean {
        val savedUri = VoiceNoteReplyManager.getFolderUri(this) ?: return false
        
        return try {
            // Check if we have persistent permission
            val persistedUris = contentResolver.persistedUriPermissions
            val hasPermission = persistedUris.any { 
                it.uri == savedUri && it.isReadPermission 
            }
            
            if (hasPermission) {
                // Auto-start monitoring if enabled
                if (VoiceNoteReplyManager.isEnabled(this)) {
                    VoiceNoteFileObserver.startMonitoring(this, savedUri)
                }
            }
            
            hasPermission
        } catch (e: Exception) {
            false
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun VoiceNoteReplyScreen() {
        var isEnabled by remember { mutableStateOf(VoiceNoteReplyManager.isEnabled(this)) }
        var folderSelected by remember { mutableStateOf(checkFolderPermission()) }
        var folderPath by remember { mutableStateOf(VoiceNoteReplyManager.getFolderPath(this) ?: "Not selected") }
        var localRefreshTrigger by remember { mutableStateOf(0) }
        val scrollState = rememberScrollState()
        
        // Combine external refresh trigger with local trigger
        val combinedRefreshTrigger = refreshTrigger + localRefreshTrigger
        
        // Refresh on resume
        androidx.compose.runtime.DisposableEffect(Unit) {
            val listener = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    localRefreshTrigger++
                }
            }
            lifecycle.addObserver(listener)
            onDispose {
                lifecycle.removeObserver(listener)
            }
        }
        
        // Folder picker launcher
        val folderPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri: Uri? ->
            if (uri != null) {
                // Take persistable permission
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    
                    // Save folder URI
                    VoiceNoteReplyManager.saveFolderUri(this, uri)
                    folderSelected = true
                    folderPath = uri.toString()
                    
                    // Start monitoring
                    VoiceNoteFileObserver.startMonitoring(this, uri)
                    
                    Toast.makeText(this, "✅ Folder selected successfully!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "❌ Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        Scaffold(
            containerColor = Color(0xFF0B0F17),
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF221538), Color(0xFF0B0F17))
                            )
                        )
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { finish() }, modifier = Modifier.size(36.dp).background(Color(0xFF141921), CircleShape)) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = Color(0xFFE8EAED), modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                "Voice Note Reply",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE8EAED)
                            )
                            Text(
                                "Auto-save & Transcribe Audio",
                                fontSize = 12.sp,
                                color = Color(0xFF8896A5)
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0B0F17))
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Enable/Disable Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF141921),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color(0xFF8B5CF6).copy(0.15f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Mic, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    "Voice Note Engine",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE8EAED)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    if (isEnabled) "Active & Monitoring" else "Feature Paused",
                                    fontSize = 12.sp,
                                    color = if (isEnabled) Color(0xFF10B981) else Color(0xFF8896A5)
                                )
                            }
                        }
                        
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = {
                                if (!folderSelected && it) {
                                    Toast.makeText(
                                        this@VoiceNoteReplyActivity,
                                        "Please select WhatsApp voice notes folder first",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    isEnabled = it
                                    VoiceNoteReplyManager.setEnabled(this@VoiceNoteReplyActivity, it)
                                    
                                    if (it && folderSelected) {
                                        VoiceNoteReplyManager.getFolderUri(this@VoiceNoteReplyActivity)?.let { uri ->
                                            VoiceNoteFileObserver.startMonitoring(this@VoiceNoteReplyActivity, uri)
                                        }
                                    } else {
                                        VoiceNoteFileObserver.stopMonitoring()
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF8B5CF6),
                                uncheckedThumbColor = Color(0xFF8896A5),
                                uncheckedTrackColor = Color(0xFF1A2030),
                                uncheckedBorderColor = Color(0xFF2A3244)
                            )
                        )
                    }
                }
                
                // Folder Selection Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF141921),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (folderSelected) Color(0xFF10B981).copy(0.35f) else Color(0xFFEF4444).copy(0.35f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(if (folderSelected) Color(0xFF10B981).copy(0.15f) else Color(0xFFEF4444).copy(0.15f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (folderSelected) Icons.Default.Folder else Icons.Default.FolderOff,
                                        null,
                                        tint = if (folderSelected) Color(0xFF10B981) else Color(0xFFEF4444),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text(
                                        "Voice Notes Folder",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE8EAED)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        if (folderSelected) "Folder mapped correctly" else "Mapping required",
                                        fontSize = 12.sp,
                                        color = if (folderSelected) Color(0xFF10B981) else Color(0xFFEF4444)
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedButton(
                            onClick = { folderPickerLauncher.launch(null) },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF3B82F6)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6).copy(0.4f))
                        ) {
                            Icon(Icons.Default.FolderOpen, "Select Folder", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Map Folder Manually", fontWeight = FontWeight.Medium)
                        }
                        
                        if (folderSelected) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1A2030)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, null, tint = Color(0xFF8896A5), modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes",
                                        fontSize = 11.sp,
                                        color = Color(0xFF8896A5),
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }
                
                
                // Saved Voice Notes
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF141921),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEC4899).copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        // Calculate total count and get voice notes (refreshes on trigger change)
                        val voiceNotesDir = remember { java.io.File(filesDir, "voice_notes") }
                        
                        val voiceNotesList = remember(combinedRefreshTrigger) {
                            if (voiceNotesDir.exists()) {
                                voiceNotesDir.listFiles()?.filter { it.isDirectory }?.flatMap { phoneDir ->
                                    phoneDir.listFiles()?.filter { it.extension == "opus" }?.map { file ->
                                        Triple(phoneDir.name, file.name, file.absolutePath)
                                    } ?: emptyList()
                                } ?: emptyList()
                            } else emptyList()
                        }
                        val totalCount = voiceNotesList.size
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(Color(0xFFEC4899).copy(0.15f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Mic, null, tint = Color(0xFFEC4899), modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "Saved Voice Notes",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE8EAED)
                                )
                            }
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Count badge
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFEC4899), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        totalCount.toString(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                
                                // Delete all button
                                if (totalCount > 0) {
                                    IconButton(
                                        onClick = {
                                            // Delete all voice notes
                                            if (voiceNotesDir.exists()) {
                                                voiceNotesDir.deleteRecursively()
                                                localRefreshTrigger++
                                                Toast.makeText(
                                                    this@VoiceNoteReplyActivity,
                                                    "🗑️ All voice notes deleted",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete All",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (totalCount > 0) {
                            Text(
                                "Tap to play voice notes",
                                fontSize = 14.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // List of voice notes
                            voiceNotesList.take(10).forEach { (phone, fileName, filePath) ->
                                VoiceNoteItem(phone, fileName, filePath)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            
                            if (voiceNotesList.size > 10) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Showing 10 of ${voiceNotesList.size} voice notes",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        } else {
                            Text(
                                "No voice notes saved yet",
                                fontSize = 14.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Voice notes will appear here when received",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            }
        }
    }
    


    
    @Composable
    fun VoiceNoteItem(phoneNumber: String, fileName: String, filePath: String) {
        var isPlaying by remember { mutableStateOf(false) }
        
        // Get transcription text if available
        val transcriptionText = remember(filePath) {
            getTranscriptionText(filePath)
        }
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF0B0F17),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A3244))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            phoneNumber,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE8EAED)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            fileName,
                            fontSize = 12.sp,
                            color = Color(0xFF8896A5),
                            maxLines = 1
                        )
                    }
                    
                    IconButton(
                        onClick = {
                            if (!isPlaying) {
                                playVoiceNote(filePath)
                                isPlaying = true
                                // Reset after 3 seconds (approximate)
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    isPlaying = false
                                }, 3000)
                            }
                        }
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Stop" else "Play",
                            tint = Color(0xFFEC4899),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                
                // Show transcription text if available
                if (transcriptionText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF141921),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.TextFields,
                                contentDescription = "Transcription",
                                tint = Color(0xFF3B82F6),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                transcriptionText,
                                fontSize = 12.sp,
                                color = Color(0xFFE8EAED),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Get transcription text for voice note
     */
    private fun getTranscriptionText(voiceFilePath: String): String? {
        return try {
            // Transcription is saved as .txt file with same name
            val transcriptionFile = java.io.File(voiceFilePath.replace(".opus", ".txt"))
            if (transcriptionFile.exists()) {
                transcriptionFile.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun playVoiceNote(filePath: String) {
        try {
            val mediaPlayer = android.media.MediaPlayer()
            mediaPlayer.setDataSource(filePath)
            mediaPlayer.prepare()
            mediaPlayer.start()
            
            mediaPlayer.setOnCompletionListener {
                it.release()
            }
            
            Toast.makeText(this, "▶️ Playing voice note", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Error playing voice note: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
