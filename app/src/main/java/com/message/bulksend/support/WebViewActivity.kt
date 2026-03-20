package com.message.bulksend.support

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.message.bulksend.ui.theme.BulksendTestTheme


class WebViewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val title = intent.getStringExtra("TITLE") ?: "Details"
        val url = intent.getStringExtra("URL") ?: "https://chatspromo.blogspot.com"

        setContent {
            BulksendTestTheme {
                WebViewScreen(
                    title = title,
                    url = url,
                    onBackClicked = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(title: String, url: String, onBackClicked: () -> Unit) {
    var isLoading by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableStateOf(0) }
    var isFullScreen by remember { mutableStateOf(false) }
    
    Scaffold(
        containerColor = Color(0xFF0D1B2A), // Dark blue background
        topBar = {
            if (!isFullScreen) {
                TopAppBar(
                    title = { }, // Empty title - no text
                    navigationIcon = {
                        IconButton(onClick = onBackClicked) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack, 
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0D1B2A) // Dark blue header
                    )
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(if (isFullScreen) PaddingValues(0.dp) else padding)
                .background(Color(0xFF0D1B2A)) // Dark blue background
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        // Set dark background
                        setBackgroundColor(android.graphics.Color.parseColor("#0D1B2A"))
                        
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        
                        // Enable media playback
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        
                        // Set WebViewClient to keep navigation in-app and track loading
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                isLoading = true
                            }
                            
                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                            }
                        }
                        
                        // Set WebChromeClient for full screen video support
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress
                            }
                            
                            // Full screen video support
                            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                isFullScreen = true
                                // Enable landscape mode for full screen videos
                                (context as? ComponentActivity)?.requestedOrientation = 
                                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            }
                            
                            override fun onHideCustomView() {
                                isFullScreen = false
                                // Return to portrait mode
                                (context as? ComponentActivity)?.requestedOrientation = 
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            }
                        }
                        
                        // Load URL from internet
                        loadUrl(url)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // Show loading progress bar (only when not in full screen)
            if (isLoading && !isFullScreen) {
                LinearProgressIndicator(
                    progress = loadProgress / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    color = Color(0xFF4FC3F7) // Light blue progress bar
                )
            }
        }
    }
}
