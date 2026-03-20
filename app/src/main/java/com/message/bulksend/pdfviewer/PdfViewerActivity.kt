package com.message.bulksend.pdfviewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.message.bulksend.MainActivity
import com.message.bulksend.ui.theme.BulkSendTheme

/**
 * PDF Viewer Activity - Jetpack Compose
 * Shows PDF viewer with zoom and navigation
 */
class PdfViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get PDF URI from intent
        val pdfUri = intent.data ?: intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        
        if (pdfUri == null) {
            finish()
            return
        }

        setContent {
            BulkSendTheme {
                PdfViewerScreen(
                    pdfUri = pdfUri,
                    onBack = { 
                        // Navigate to MainActivity instead of going back to file manager
                        val intent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
    
    override fun onBackPressed() {
        // Navigate to MainActivity instead of going back to file manager
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }
}
