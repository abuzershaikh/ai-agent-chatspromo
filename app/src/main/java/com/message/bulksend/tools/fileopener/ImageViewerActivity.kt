package com.message.bulksend.tools.fileopener

import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
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
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.load
import com.github.chrisbanes.photoview.PhotoView
import com.message.bulksend.ui.theme.BulksendTestTheme

class ImageViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val fileUri = intent.data ?: run {
            finish()
            return
        }
        val fileName = intent.getStringExtra(FileOpenRouter.EXTRA_FILE_NAME) ?: "Image"
        val mimeType = intent.getStringExtra(FileOpenRouter.EXTRA_FILE_MIME)

        setContent {
            BulksendTestTheme {
                ImageViewerScreen(
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
private fun ImageViewerScreen(
    fileUri: Uri,
    fileName: String,
    mimeType: String?,
    onBack: () -> Unit
) {
    val errorMessage = remember(fileUri) { mutableStateOf<String?>(null) }

    val background = Brush.verticalGradient(
        colors = listOf(Color(0xFF040B16), Color(0xFF0F172A), Color(0xFF111827))
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
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (errorMessage.value != null) {
                Card(
                    modifier = Modifier.padding(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = Color(0xFF60A5FA)
                        )
                        Text(
                            "Could not open image",
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    factory = { context ->
                        PhotoView(context).apply {
                            adjustViewBounds = true
                            maximumScale = 6f
                            mediumScale = 3f
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            load(fileUri) {
                                listener(
                                    onError = { _, result ->
                                        errorMessage.value =
                                            result.throwable.message ?: "Unable to open this image."
                                    }
                                )
                            }
                        }
                    },
                    update = { photoView ->
                        errorMessage.value = null
                        photoView.load(fileUri) {
                            listener(
                                onError = { _, result ->
                                    errorMessage.value =
                                        result.throwable.message ?: "Unable to open this image."
                                }
                            )
                        }
                    }
                )
            }
        }
    }
}
