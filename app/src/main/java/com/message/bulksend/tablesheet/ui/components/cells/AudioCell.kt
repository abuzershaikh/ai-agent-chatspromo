package com.message.bulksend.tablesheet.ui.components.cells

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.message.bulksend.tablesheet.ui.theme.TableTheme
import java.io.File

@Composable
fun AudioCell(
    value: String,
    cellWidth: Dp,
    cellHeight: Dp,
    isRowSelected: Boolean = false,
    onValueChange: (String) -> Unit
) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(false) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var currentFilePath by remember { mutableStateOf("") }
    
    val hasAudio = value.isNotEmpty() && File(value).exists()
    
    fun startRecording() {
        try {
            val audioDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "ChatsPromo/Sheet/Audio"
            )
            if (!audioDir.exists()) audioDir.mkdirs()
            
            val fileName = "audio_${System.currentTimeMillis()}.m4a"
            val audioFile = File(audioDir, fileName)
            currentFilePath = audioFile.absolutePath
            
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(currentFilePath)
                prepare()
                start()
            }
            isRecording = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun stopRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            if (currentFilePath.isNotEmpty()) {
                onValueChange(currentFilePath)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted && !hasAudio) {
            startRecording()
        }
    }
    
    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    DisposableEffect(Unit) {
        onDispose {
            mediaRecorder?.release()
            mediaPlayer?.release()
        }
    }
    
    val backgroundColor = when {
        isRecording -> Color(0xFFFFEBEE)
        isRowSelected -> Color(0xFFE3F2FD).copy(alpha = 0.7f)
        else -> Color.White
    }
    
    Box(
        modifier = Modifier
            .width(cellWidth)
            .height(cellHeight)
            .background(backgroundColor)
            .border(1.dp, if (isRecording) Color(0xFFF43F5E) else TableTheme.GRID_COLOR),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isRecording) {
                IconButton(
                    onClick = { stopRecording() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = "Stop Recording",
                        tint = Color(0xFFF43F5E),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text("REC", fontSize = 10.sp, color = Color(0xFFF43F5E), fontWeight = FontWeight.Bold)
            } else if (hasAudio) {
                IconButton(
                    onClick = {
                        if (isPlaying) {
                            mediaPlayer?.stop()
                            mediaPlayer?.release()
                            mediaPlayer = null
                            isPlaying = false
                        } else {
                            try {
                                mediaPlayer = MediaPlayer().apply {
                                    setDataSource(value)
                                    prepare()
                                    start()
                                    setOnCompletionListener {
                                        isPlaying = false
                                        release()
                                        mediaPlayer = null
                                    }
                                }
                                isPlaying = true
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Stop" else "Play",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = {
                        try { File(value).delete() } catch (e: Exception) { }
                        onValueChange("")
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        if (hasPermission) {
                            startRecording()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Record",
                        tint = Color(0xFFF43F5E),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}