package com.message.bulksend.tools.fileopener

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.ui.theme.BulksendTestTheme

class FileOpenerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (handleIncomingIntent(intent)) {
            return
        }

        setContent {
            BulksendTestTheme {
                FileOpenerHomeScreen(
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent): Boolean {
        val uri = FileOpenRouter.extractUri(intent) ?: return false
        tryPersistableReadPermission(intent, uri)
        val opened = FileOpenRouter.routeToViewer(this, uri, intent.type)
        if (opened) {
            finish()
        } else {
            Toast.makeText(this, "Unsupported file type for in-app opening.", Toast.LENGTH_LONG).show()
        }
        return opened
    }

    private fun tryPersistableReadPermission(intent: Intent, uri: Uri) {
        val grantFlags = intent.flags and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (grantFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) {
            return
        }

        try {
            contentResolver.takePersistableUriPermission(uri, grantFlags)
        } catch (_: SecurityException) {
            // Some file managers give one-time URI grants only; immediate open still works.
        } catch (_: IllegalArgumentException) {
            // Non-document URIs cannot be persisted.
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileOpenerHomeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val background = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF08121F),
            Color(0xFF132238),
            Color(0xFF101C2E)
        )
    )
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // One-time grants from some file managers are enough; persistable access is optional.
        }

        val opened = FileOpenRouter.routeToViewer(context, uri)
        if (!opened) {
            Toast.makeText(context, "Unsupported file type for in-app opening.", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "File Opener",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A)
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF11263E)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .background(Color(0xFF1D4ED8), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                        Column {
                            Text(
                                "Open files inside Bulk Send",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "TXT, images, MP3, and MP4 open in their own screens automatically.",
                                color = Color(0xFFBFDBFE),
                                fontSize = 13.sp
                            )
                        }
                    }

                    Button(
                        onClick = { pickerLauncher.launch(arrayOf("*/*")) },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Choose File", color = Color.White)
                    }
                }
            }

            Text(
                "Supported Formats",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )

            SupportCard(
                icon = Icons.Default.Description,
                title = "Text Viewer",
                subtitle = "TXT, LOG, MD, JSON, XML, YAML"
            )
            SupportCard(
                icon = Icons.Default.Image,
                title = "Image Viewer",
                subtitle = "JPG, JPEG, PNG, WEBP, BMP, HEIC, HEIF, GIF"
            )
            SupportCard(
                icon = Icons.Default.AudioFile,
                title = "Audio Player",
                subtitle = "MP3, WAV, OGG, M4A, AAC, FLAC"
            )
            SupportCard(
                icon = Icons.Default.PlayCircleOutline,
                title = "Video Player",
                subtitle = "MP4, MKV, MOV, AVI, 3GP, WEBM"
            )
            SupportCard(
                icon = Icons.Default.PictureAsPdf,
                title = "PDF Viewer",
                subtitle = "PDF files route to the built-in PDF screen"
            )
            SupportCard(
                icon = Icons.Default.TableChart,
                title = "Sheet Import",
                subtitle = "CSV, XLS, XLSX, VCF route to TableSheet"
            )
        }
    }
}

@Composable
private fun SupportCard(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0xFF1E293B), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color(0xFF60A5FA))
            }
            Column {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, color = Color(0xFF9CA3AF), fontSize = 12.sp)
            }
        }
    }
}
