package com.message.bulksend.tools.fileopener

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.delay

class AudioPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val fileUri = intent.data ?: run {
            finish()
            return
        }
        val fileName = intent.getStringExtra(FileOpenRouter.EXTRA_FILE_NAME) ?: "Audio File"
        val mimeType = intent.getStringExtra(FileOpenRouter.EXTRA_FILE_MIME)

        setContent {
            BulksendTestTheme {
                AudioPlayerScreen(
                    fileUri = fileUri,
                    fileName = fileName,
                    mimeType = mimeType,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioPlayerScreen(
    fileUri: Uri,
    fileName: String,
    mimeType: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var player by remember(fileUri) { mutableStateOf<MediaPlayer?>(null) }
    var isPrepared by remember(fileUri) { mutableStateOf(false) }
    var isPlaying by remember(fileUri) { mutableStateOf(false) }
    var durationMs by remember(fileUri) { mutableLongStateOf(0L) }
    var positionMs by remember(fileUri) { mutableLongStateOf(0L) }
    var sliderPosition by remember(fileUri) { mutableStateOf(0f) }
    var isSeeking by remember(fileUri) { mutableStateOf(false) }
    var errorMessage by remember(fileUri) { mutableStateOf<String?>(null) }

    DisposableEffect(fileUri) {
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer

        try {
            mediaPlayer.setDataSource(context, fileUri)
            mediaPlayer.setOnPreparedListener {
                isPrepared = true
                durationMs = it.duration.toLong().coerceAtLeast(0L)
                it.start()
                isPlaying = true
            }
            mediaPlayer.setOnCompletionListener {
                isPlaying = false
                positionMs = durationMs
                sliderPosition = durationMs.toFloat()
            }
            mediaPlayer.setOnErrorListener { _, _, _ ->
                errorMessage = "Unable to play this audio file."
                isPlaying = false
                true
            }
            mediaPlayer.prepareAsync()
        } catch (error: Exception) {
            errorMessage = error.message ?: "Unable to play this audio file."
        }

        onDispose {
            mediaPlayer.release()
            player = null
        }
    }

    LaunchedEffect(player, isPlaying, isPrepared, isSeeking) {
        while (player != null && isPrepared && isPlaying && !isSeeking) {
            positionMs = player?.currentPosition?.toLong() ?: positionMs
            sliderPosition = positionMs.toFloat()
            delay(300)
        }
    }

    val background = Brush.verticalGradient(
        colors = listOf(Color(0xFF08111D), Color(0xFF122237), Color(0xFF0E1827))
    )

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(fileName, color = Color.White, fontWeight = FontWeight.Bold)
                        if (!mimeType.isNullOrBlank()) {
                            Text(mimeType, color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .padding(paddingValues)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .align(Alignment.Center),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .background(Color(0xFF1D4ED8), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (errorMessage == null && !isPrepared) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(30.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.AudioFile,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }

                    if (errorMessage != null) {
                        Text(errorMessage ?: "", color = Color(0xFFFCA5A5))
                    } else {
                        Text(
                            "Audio Player",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 20.sp
                        )

                        Slider(
                            value = sliderPosition.coerceIn(0f, durationMs.toFloat().coerceAtLeast(1f)),
                            onValueChange = { value ->
                                isSeeking = true
                                sliderPosition = value
                            },
                            onValueChangeFinished = {
                                player?.seekTo(sliderPosition.toInt())
                                positionMs = sliderPosition.toLong()
                                isSeeking = false
                            },
                            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                            enabled = isPrepared
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatDuration(positionMs), color = Color(0xFF9CA3AF), fontSize = 12.sp)
                            Text(formatDuration(durationMs), color = Color(0xFF9CA3AF), fontSize = 12.sp)
                        }

                        IconButton(
                            onClick = {
                                val mediaPlayer = player ?: return@IconButton
                                if (!isPrepared) return@IconButton
                                if (mediaPlayer.isPlaying) {
                                    mediaPlayer.pause()
                                    isPlaying = false
                                } else {
                                    mediaPlayer.start()
                                    isPlaying = true
                                }
                            },
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color(0xFF2563EB), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
