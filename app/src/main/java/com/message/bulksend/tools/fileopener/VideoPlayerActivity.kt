package com.message.bulksend.tools.fileopener

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.message.bulksend.ui.theme.BulksendTestTheme

class VideoPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val fileUri = intent.data ?: run {
            finish()
            return
        }
        val fileName = intent.getStringExtra(FileOpenRouter.EXTRA_FILE_NAME) ?: "Video File"
        val mimeType = intent.getStringExtra(FileOpenRouter.EXTRA_FILE_MIME)

        setContent {
            BulksendTestTheme {
                VideoPlayerScreen(
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
private fun VideoPlayerScreen(
    fileUri: Uri,
    fileName: String,
    mimeType: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val errorMessage = remember(fileUri) { mutableStateOf<String?>(null) }
    val exoPlayer = remember(fileUri) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
        }
    }
    val playerListener = remember(fileUri) {
        object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                errorMessage.value = error.localizedMessage ?: "Unable to play this video file."
            }
        }
    }

    DisposableEffect(exoPlayer) {
        exoPlayer.addListener(playerListener)
        exoPlayer.setMediaItem(MediaItem.fromUri(fileUri))
        exoPlayer.prepare()
        onDispose {
            exoPlayer.removeListener(playerListener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(fileUri) {
        errorMessage.value = null
    }

    val background = Brush.verticalGradient(
        colors = listOf(Color(0xFF050816), Color(0xFF0F172A), Color(0xFF111827))
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF020617))
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .padding(paddingValues)
        ) {
            if (errorMessage.value != null) {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircleOutline,
                            contentDescription = null,
                            tint = Color(0xFF60A5FA)
                        )
                        Text(
                            "Could not play video",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                        Text(
                            errorMessage.value ?: "",
                            color = Color(0xFFCBD5E1),
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { androidContext ->
                        PlayerView(androidContext).apply {
                            useController = true
                            controllerAutoShow = true
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShowNextButton(false)
                            setShowPreviousButton(false)
                            setShowFastForwardButton(false)
                            setShowRewindButton(false)
                            setBackgroundColor(android.graphics.Color.BLACK)
                            keepScreenOn = true
                            player = exoPlayer
                        }
                    },
                    update = { view ->
                        view.player = exoPlayer
                    }
                )
            }
        }
    }
}
