package com.message.bulksend.autorespond.statusscheduled

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.ScheduleSend
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.message.bulksend.bulksend.CampaignState
import com.message.bulksend.bulksend.WhatsAppAutoSendService
import com.message.bulksend.ui.theme.BulksendTestTheme
import com.message.bulksend.utils.AccessibilityHelper
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class StatusAutoScheduledActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BulksendTestTheme {
                StatusAutoScheduledScreen(onBack = { finish() })
            }
        }
    }
}

private data class PreparedStatusShare(
    val shareUri: Uri,
    val absolutePath: String,
    val normalizedLink: String,
    val mimeType: String
)

private class StatusAutoScheduledPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("status_auto_scheduled_prefs", Context.MODE_PRIVATE)

    fun getSelectedMediaUri(): String = prefs.getString("selected_media_uri", "").orEmpty()
    fun getSelectedMediaPath(): String = prefs.getString("selected_media_path", "").orEmpty()
    fun getSelectedMediaMime(): String = prefs.getString("selected_media_mime", "").orEmpty()
    fun getHyperlink(): String = prefs.getString("hyperlink", "").orEmpty()
    fun getLastPreparedPath(): String = prefs.getString("last_prepared_path", "").orEmpty()

    fun saveSelectedMedia(uri: String, path: String, mime: String) {
        prefs.edit()
            .putString("selected_media_uri", uri)
            .putString("selected_media_path", path)
            .putString("selected_media_mime", mime)
            .apply()
    }

    fun saveHyperlink(link: String) {
        prefs.edit().putString("hyperlink", link).apply()
    }

    fun saveLastPreparedPath(path: String) {
        prefs.edit().putString("last_prepared_path", path).apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusAutoScheduledScreen(
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { StatusAutoScheduledPrefs(context) }

    var selectedMediaUri by remember { mutableStateOf(prefs.getSelectedMediaUri()) }
    var selectedMediaPath by remember { mutableStateOf(prefs.getSelectedMediaPath()) }
    var selectedMediaMime by remember { mutableStateOf(prefs.getSelectedMediaMime()) }
    var hyperlink by remember { mutableStateOf(prefs.getHyperlink()) }
    var lastPreparedPath by remember { mutableStateOf(prefs.getLastPreparedPath()) }
    var accessibilityEnabled by remember { mutableStateOf(false) }

    val mediaPickerLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                    // Not all providers support persistable permissions.
                }

                val imported = importPickedMedia(context, uri)
                if (imported == null) {
                    Toast.makeText(context, "Media import failed", Toast.LENGTH_SHORT).show()
                    return@rememberLauncherForActivityResult
                }

                prefs.saveSelectedMedia(
                    uri = imported.shareUri.toString(),
                    path = imported.absolutePath,
                    mime = imported.mimeType
                )
                selectedMediaUri = imported.shareUri.toString()
                selectedMediaPath = imported.absolutePath
                selectedMediaMime = imported.mimeType
                lastPreparedPath = imported.absolutePath
                prefs.saveLastPreparedPath(imported.absolutePath)
            }
        }

    LaunchedEffect(Unit) {
        accessibilityEnabled = isAccessibilityEnabled(context)
    }

    BackHandler { onBack() }

    val background = Brush.verticalGradient(
        colors = listOf(Color(0xFF0f0c29), Color(0xFF302b63), Color(0xFF24243e))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Status Auto Scheduled",
                        color = Color(0xFF22C55E),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF22C55E))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1a1a2e))
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Flow",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "1. Pick image/video/audio\n2. Add hyperlink\n3. Share to WhatsApp / WhatsApp Business\n4. Accessibility will click 'My status' then Send arrow",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                OutlinedTextField(
                    value = hyperlink,
                    onValueChange = {
                        hyperlink = it
                        prefs.saveHyperlink(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Hyperlink (optional)", color = Color(0xFF94A3B8)) },
                    placeholder = { Text("https://example.com", color = Color(0xFF64748B)) },
                    leadingIcon = {
                        Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFF22C55E))
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF22C55E),
                        unfocusedBorderColor = Color(0xFF475569),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Selected Media Reference",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (selectedMediaUri.isBlank()) "No media selected" else selectedMediaUri,
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                        if (selectedMediaPath.isNotBlank()) {
                            Text(
                                text = "Local path: $selectedMediaPath",
                                color = Color(0xFF22C55E),
                                fontSize = 12.sp
                            )
                        }
                        if (selectedMediaMime.isNotBlank()) {
                            Text(
                                text = "MIME: $selectedMediaMime",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                        if (lastPreparedPath.isNotBlank()) {
                            Text(
                                text = "Last prepared path: $lastPreparedPath",
                                color = Color(0xFF22C55E),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        mediaPickerLauncher.launch(arrayOf("image/*", "video/*", "audio/*"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(" Pick Image / Video / Audio")
                }

                Button(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Text(" Open Accessibility Settings")
                }

                Button(
                    onClick = {
                        selectedMediaUri = prefs.getSelectedMediaUri()
                        selectedMediaPath = prefs.getSelectedMediaPath()
                        selectedMediaMime = prefs.getSelectedMediaMime()
                        accessibilityEnabled = isAccessibilityEnabled(context)

                        if (selectedMediaPath.isBlank()) {
                            Toast.makeText(context, "Please select media first.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        if (!accessibilityEnabled) {
                            Toast.makeText(context, "Please enable Accessibility permission.", Toast.LENGTH_SHORT).show()
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            return@Button
                        }

                        val prepared = prepareStatusMediaForShare(
                            context = context,
                            selectedLocalPath = selectedMediaPath,
                            selectedMime = selectedMediaMime,
                            hyperlinkRaw = hyperlink
                        )
                        if (prepared == null) {
                            Toast.makeText(context, "Media prepare failed", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val targetPackage = resolveAvailableWhatsAppPackage(context)
                        if (targetPackage == null) {
                            Toast.makeText(context, "WhatsApp or WhatsApp Business is not installed.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        prefs.saveLastPreparedPath(prepared.absolutePath)
                        lastPreparedPath = prepared.absolutePath

                        StatusAutoScheduledState.activate(
                            imagePath = prepared.absolutePath,
                            imageUri = prepared.shareUri.toString(),
                            hyperlink = prepared.normalizedLink
                        )
                        CampaignState.isSendActionSuccessful = null
                        CampaignState.isAutoSendEnabled = true
                        WhatsAppAutoSendService.activateService()

                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = prepared.mimeType
                            setPackage(targetPackage)
                            putExtra(Intent.EXTRA_STREAM, prepared.shareUri)
                            if (prepared.normalizedLink.isNotBlank()) {
                                putExtra(Intent.EXTRA_TEXT, prepared.normalizedLink)
                            }
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            clipData = ClipData.newRawUri("status_media", prepared.shareUri)
                        }

                        try {
                            grantShareUriToInstalledWhatsAppApps(context, prepared.shareUri)
                            if (shareIntent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(shareIntent)
                            } else {
                                val fallbackIntent = Intent(shareIntent).apply { setPackage(null) }
                                val chooser = Intent.createChooser(fallbackIntent, "Share status via")
                                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(chooser)
                            }
                            Toast.makeText(
                                context,
                                "${displayNameForWhatsAppPackage(targetPackage)} is open. Accessibility will tap 'My status' and the Send button.",
                                Toast.LENGTH_LONG
                            ).show()
                        } catch (e: Exception) {
                            StatusAutoScheduledState.reset()
                            CampaignState.isAutoSendEnabled = false
                            WhatsAppAutoSendService.deactivateService()
                            Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ScheduleSend, contentDescription = null)
                    Text(" Share to WhatsApp Status")
                }
            }
        }
    }
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    return AccessibilityHelper.isAccessibilityServiceEnabled(
        context,
        "com.message.bulksend.bulksend.WhatsAppAutoSendService"
    )
}

private fun resolveAvailableWhatsAppPackage(context: Context): String? {
    return when {
        isWhatsAppPackageInstalled(context, "com.whatsapp.w4b") -> "com.whatsapp.w4b"
        isWhatsAppPackageInstalled(context, "com.whatsapp") -> "com.whatsapp"
        else -> null
    }
}

private fun isWhatsAppPackageInstalled(context: Context, packageName: String): Boolean {
    return try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}

private fun grantShareUriToInstalledWhatsAppApps(context: Context, uri: Uri) {
    val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
    packages.forEach { packageName ->
        if (isWhatsAppPackageInstalled(context, packageName)) {
            try {
                context.grantUriPermission(
                    packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Ignore and continue with other package.
            }
        }
    }
}

private fun displayNameForWhatsAppPackage(packageName: String): String {
    return when (packageName) {
        "com.whatsapp.w4b" -> "WhatsApp Business"
        "com.whatsapp" -> "WhatsApp"
        else -> "WhatsApp"
    }
}

private fun importPickedMedia(
    context: Context,
    sourceUri: Uri
): PreparedStatusShare? {
    return try {
        val folder = File(context.filesDir, "status_scheduled")
        if (!folder.exists()) folder.mkdirs()

        val mime = resolveMimeType(context, sourceUri, "")
        val extension = guessExtensionFromMime(mime)
        val outFile = File(folder, "picked_${System.currentTimeMillis()}.$extension")

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        } ?: return null

        val shareUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            outFile
        )

        PreparedStatusShare(
            shareUri = shareUri,
            absolutePath = outFile.absolutePath,
            normalizedLink = "",
            mimeType = mime
        )
    } catch (e: Exception) {
        Log.e("StatusAutoScheduled", "importPickedMedia failed: ${e.message}", e)
        null
    }
}

private fun prepareStatusMediaForShare(
    context: Context,
    selectedLocalPath: String,
    selectedMime: String,
    hyperlinkRaw: String
): PreparedStatusShare? {
    return try {
        val file = File(selectedLocalPath)
        if (!file.exists() || !file.isFile) {
            Log.e("StatusAutoScheduled", "Selected local media not found: $selectedLocalPath")
            return null
        }

        val resolvedMime = resolveMimeType(context, Uri.fromFile(file), selectedMime)
        val shareUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        PreparedStatusShare(
            shareUri = shareUri,
            absolutePath = file.absolutePath,
            normalizedLink = normalizeHyperlink(hyperlinkRaw),
            mimeType = resolvedMime
        )
    } catch (e: Exception) {
        Log.e("StatusAutoScheduled", "prepareStatusMediaForShare failed: ${e.message}", e)
        null
    }
}

private fun resolveMimeType(context: Context, uri: Uri, fallback: String): String {
    val candidate = context.contentResolver.getType(uri).orEmpty().lowercase(Locale.getDefault())
    if (candidate.startsWith("image/") || candidate.startsWith("video/") || candidate.startsWith("audio/")) {
        return candidate
    }
    if (fallback.startsWith("image/") || fallback.startsWith("video/") || fallback.startsWith("audio/")) {
        return fallback
    }
    return "image/*"
}

private fun guessExtensionFromMime(mime: String): String {
    return when {
        mime.contains("png") -> "png"
        mime.contains("webp") -> "webp"
        mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
        mime.contains("gif") -> "gif"
        mime.contains("mp4") -> "mp4"
        mime.contains("3gpp") -> "3gp"
        mime.contains("webm") -> "webm"
        mime.contains("mpeg") -> "mp3"
        mime.contains("ogg") -> "ogg"
        mime.contains("wav") -> "wav"
        mime.startsWith("video/") -> "mp4"
        mime.startsWith("audio/") -> "mp3"
        else -> "jpg"
    }
}

private fun normalizeHyperlink(raw: String): String {
    val value = raw.trim()
    if (value.isBlank()) return ""
    if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) {
        return value
    }
    return "https://$value"
}
