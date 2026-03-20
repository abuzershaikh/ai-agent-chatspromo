package com.message.bulksend.pdfviewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    pdfUri: Uri,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var currentPageIndex by remember { mutableStateOf(0) }
    var totalPages by remember { mutableStateOf(0) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pdfTitle by remember { mutableStateOf("PDF Document") }
    var showInfo by remember { mutableStateOf(false) }
    
    // Zoom and pan states
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.5f, 5f)
        offset += offsetChange
    }

    // Load PDF
    LaunchedEffect(pdfUri) {
        try {
            var localFilePath: String? = null
            
            // Get file name
            context.contentResolver.query(pdfUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        pdfTitle = cursor.getString(nameIndex)
                    }
                }
            }
            
            // If URI is from file picker (content://), copy to app storage
            if (pdfUri.scheme == "content") {
                localFilePath = RecentPdfManager.addRecentPdf(context, pdfUri, pdfTitle)
            } else if (pdfUri.scheme == "file") {
                // Already a local file, just add to recent
                val file = File(pdfUri.path ?: "")
                if (file.exists() && file.absolutePath.contains("chatspromo/pdfs")) {
                    // Already in our folder, just update recent list
                    localFilePath = file.absolutePath
                } else {
                    // Copy to our folder
                    localFilePath = RecentPdfManager.addRecentPdf(context, pdfUri, pdfTitle)
                }
            }
            
            // Use local file path if available, otherwise use original URI
            val fileToOpen = if (localFilePath != null) {
                File(localFilePath)
            } else {
                // Fallback: copy URI content to temp file
                val tempFile = File(context.cacheDir, "temp.pdf")
                context.contentResolver.openInputStream(pdfUri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile
            }

            // Open PDF with PdfRenderer
            val fileDescriptor = ParcelFileDescriptor.open(fileToOpen, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor)
            totalPages = pdfRenderer?.pageCount ?: 0
            
            // Render first page
            renderPage(pdfRenderer, 0) { bitmap ->
                currentBitmap = bitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            currentBitmap?.recycle()
            pdfRenderer?.close()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(pdfTitle, maxLines = 1)
                        if (totalPages > 0) {
                            Text(
                                "Page ${currentPageIndex + 1} of $totalPages",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        // Share PDF
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(android.content.Intent.EXTRA_STREAM, pdfUri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share PDF"))
                    }) {
                        Icon(Icons.Default.Share, "Share")
                    }
                    IconButton(onClick = { showInfo = true }) {
                        Icon(Icons.Default.Info, "Info")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFEF4444),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFF3F4F6)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (currentPageIndex > 0) {
                                currentPageIndex--
                                scale = 1f
                                offset = Offset.Zero
                                renderPage(pdfRenderer, currentPageIndex) { bitmap ->
                                    currentBitmap?.recycle()
                                    currentBitmap = bitmap
                                }
                            }
                        },
                        enabled = currentPageIndex > 0,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444)
                        )
                    ) {
                        Text("Previous")
                    }
                    
                    Button(
                        onClick = {
                            if (currentPageIndex < totalPages - 1) {
                                currentPageIndex++
                                scale = 1f
                                offset = Offset.Zero
                                renderPage(pdfRenderer, currentPageIndex) { bitmap ->
                                    currentBitmap?.recycle()
                                    currentBitmap = bitmap
                                }
                            }
                        },
                        enabled = currentPageIndex < totalPages - 1,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444)
                        )
                    ) {
                        Text("Next")
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            currentBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "PDF Page",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .transformable(state = transformableState)
                )
            } ?: CircularProgressIndicator()
        }
    }

    // Info Dialog
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("PDF Information") },
            text = {
                Column {
                    Text("File: $pdfTitle")
                    Text("Pages: $totalPages")
                    Text("Current Page: ${currentPageIndex + 1}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Pinch to zoom in/out", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text("OK")
                }
            }
        )
    }
}

private fun renderPage(
    pdfRenderer: PdfRenderer?,
    pageIndex: Int,
    onBitmapReady: (Bitmap) -> Unit
) {
    pdfRenderer?.let { renderer ->
        val page = renderer.openPage(pageIndex)
        val bitmap = Bitmap.createBitmap(
            page.width * 2,
            page.height * 2,
            Bitmap.Config.ARGB_8888
        )
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        onBitmapReady(bitmap)
    }
}
