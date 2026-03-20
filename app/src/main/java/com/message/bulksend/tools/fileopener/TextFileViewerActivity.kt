package com.message.bulksend.tools.fileopener

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TextFileViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val fileUri = intent.data ?: run {
            finish()
            return
        }
        val fileName = intent.getStringExtra(FileOpenRouter.EXTRA_FILE_NAME) ?: "Text File"
        val mimeType = intent.getStringExtra(FileOpenRouter.EXTRA_FILE_MIME)

        setContent {
            BulksendTestTheme {
                TextFileViewerScreen(
                    fileUri = fileUri,
                    fileName = fileName,
                    mimeType = mimeType,
                    onBack = { finish() }
                )
            }
        }
    }
}

private data class TextFileContent(
    val text: String,
    val truncated: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextFileViewerScreen(
    fileUri: Uri,
    fileName: String,
    mimeType: String?,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isLoading by remember(fileUri) { mutableStateOf(true) }
    var content by remember(fileUri) { mutableStateOf(TextFileContent("", truncated = false)) }
    var errorMessage by remember(fileUri) { mutableStateOf<String?>(null) }

    LaunchedEffect(fileUri) {
        isLoading = true
        errorMessage = null
        try {
            content = withContext(Dispatchers.IO) {
                readTextFile(context, fileUri)
            }
        } catch (error: Exception) {
            errorMessage = error.message ?: "Unable to open text file."
        } finally {
            isLoading = false
        }
    }

    val background = Brush.verticalGradient(
        colors = listOf(Color(0xFF0B1020), Color(0xFF10192C), Color(0xFF0E1728))
    )

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(fileName, color = Color.White, fontWeight = FontWeight.Bold)
                        if (!mimeType.isNullOrBlank()) {
                            Text(
                                mimeType,
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
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
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xFF60A5FA)
                    )
                }
                errorMessage != null -> {
                    MessageCard(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        title = "Could not open file",
                        body = errorMessage ?: "Unknown error"
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF132238))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Text Preview",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (content.truncated) {
                                    Text(
                                        "Large file detected. Showing the first part of the file.",
                                        color = Color(0xFFFBBF24),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        SelectionContainer {
                            Card(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
                            ) {
                                Text(
                                    text = content.text.ifBlank { "File is empty." },
                                    modifier = Modifier.padding(18.dp),
                                    color = Color(0xFFE2E8F0),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 21.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageCard(
    modifier: Modifier = Modifier,
    title: String,
    body: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = Color(0xFF60A5FA),
                modifier = Modifier.size(32.dp)
            )
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(body, color = Color(0xFFCBD5E1), fontSize = 13.sp)
        }
    }
}

private const val MAX_TEXT_PREVIEW_BYTES = 512 * 1024

private fun readTextFile(context: Context, uri: Uri): TextFileContent {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalStateException("Unable to read file.")

    if (bytes.isEmpty()) {
        return TextFileContent("", truncated = false)
    }

    val previewBytes = if (bytes.size > MAX_TEXT_PREVIEW_BYTES) {
        bytes.copyOfRange(0, MAX_TEXT_PREVIEW_BYTES)
    } else {
        bytes
    }

    return TextFileContent(
        text = decodeText(previewBytes),
        truncated = bytes.size > MAX_TEXT_PREVIEW_BYTES
    )
}

private fun decodeText(bytes: ByteArray): String {
    val candidates = listOf(
        Charsets.UTF_8,
        Charsets.UTF_16,
        Charsets.UTF_16LE,
        Charsets.UTF_16BE,
        Charsets.ISO_8859_1
    )
    for (charset in candidates) {
        try {
            return bytes.toString(charset)
        } catch (_: Exception) {
            // Try next charset.
        }
    }
    return bytes.toString(Charsets.UTF_8)
}
