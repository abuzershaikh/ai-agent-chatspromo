package com.message.bulksend.pdfviewer

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.message.bulksend.ui.theme.BulkSendTheme

/**
 * PDF List Activity - Jetpack Compose
 * Shows recent PDFs and file picker
 */
class PdfListActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            BulkSendTheme {
                var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
                
                if (selectedPdfUri != null) {
                    PdfViewerScreen(
                        pdfUri = selectedPdfUri!!,
                        onBack = { selectedPdfUri = null }
                    )
                } else {
                    PdfListScreen(
                        onBack = { finish() },
                        onPdfSelected = { uri -> selectedPdfUri = uri }
                    )
                }
            }
        }
    }
}
