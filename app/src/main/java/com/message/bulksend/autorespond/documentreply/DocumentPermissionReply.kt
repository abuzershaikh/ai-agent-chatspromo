package com.message.bulksend.autorespond.documentreply

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Permission handler for Document Reply feature.
 * Requires:
 * - Accessibility service
 * - Notification access
 * - Contacts permission
 */
class DocumentPermissionReply(private val context: Context) {

    companion object {
        const val TAG = "DocumentPermissionReply"
    }

    /**
     * Legacy helper used by some call sites.
     */
    suspend fun checkPermissionsAndProceed(
        onAllPermissionsGranted: () -> Unit,
        onPermissionsDenied: () -> Unit
    ) {
        if (!isAccessibilityServiceEnabled()) {
            onPermissionsDenied()
            return
        }
        if (!isNotificationAccessEnabled()) {
            onPermissionsDenied()
            return
        }
        if (!isContactPermissionGranted()) {
            onPermissionsDenied()
            return
        }
        onAllPermissionsGranted()
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val accessibilityManager =
                context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )
            val packageName = context.packageName
            val isEnabled = enabledServices.any { service ->
                service.id.contains(packageName) || service.id.contains("WhatsAppAutoSendService")
            }
            Log.d(TAG, "Accessibility service enabled: $isEnabled")
            isEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility service: ${e.message}")
            false
        }
    }

    fun isNotificationAccessEnabled(): Boolean {
        return try {
            val isEnabled = NotificationManagerCompat
                .getEnabledListenerPackages(context)
                .contains(context.packageName)
            Log.d(TAG, "Notification access enabled: $isEnabled")
            isEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Error checking notification access: ${e.message}")
            false
        }
    }

    fun isContactPermissionGranted(): Boolean {
        return try {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Contacts permission granted: $granted")
            granted
        } catch (e: Exception) {
            Log.e(TAG, "Error checking contacts permission: ${e.message}")
            false
        }
    }

    fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening accessibility settings: ${e.message}")
        }
    }

    fun openNotificationAccessSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening notification access settings: ${e.message}")
        }
    }
}

@Composable
fun DocumentReplyPermissionHandler(
    permissionReply: DocumentPermissionReply,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var showNotificationDialog by remember { mutableStateOf(false) }
    var showContactPermissionDialog by remember { mutableStateOf(false) }
    var permissionCheckInProgress by remember { mutableStateOf(false) }

    fun checkPermissions() {
        permissionCheckInProgress = true

        if (!permissionReply.isAccessibilityServiceEnabled()) {
            showAccessibilityDialog = true
            permissionCheckInProgress = false
            return
        }

        if (!permissionReply.isNotificationAccessEnabled()) {
            showNotificationDialog = true
            permissionCheckInProgress = false
            return
        }

        if (!permissionReply.isContactPermissionGranted()) {
            showContactPermissionDialog = true
            permissionCheckInProgress = false
            return
        }

        permissionCheckInProgress = false
        onSave()
    }

    val contactPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            checkPermissions()
        } else {
            onCancel()
        }
    }

    LaunchedEffect(Unit) {
        checkPermissions()
    }

    if (showAccessibilityDialog) {
        PermissionDialog(
            title = "Accessibility Permission Required",
            message = "Document Reply needs Accessibility Service to automatically send documents in WhatsApp.\n\n" +
                "- Open WhatsApp automatically\n" +
                "- Navigate to chats\n" +
                "- Send documents on your behalf\n\n" +
                "Without this permission, Document Reply cannot work.",
            onAgree = {
                showAccessibilityDialog = false
                permissionReply.openAccessibilitySettings()
                if (permissionReply.isNotificationAccessEnabled()) {
                    onSave()
                } else {
                    showNotificationDialog = true
                }
            },
            onDisagree = {
                showAccessibilityDialog = false
                onCancel()
            }
        )
    }

    if (showNotificationDialog) {
        PermissionDialog(
            title = "Notification Access Required",
            message = "Document Reply needs Notification Access to detect incoming WhatsApp messages.\n\n" +
                "- Read WhatsApp notifications\n" +
                "- Detect keyword matches\n" +
                "- Trigger automatic document replies\n\n" +
                "You will be redirected to Notification Access settings.\n\n" +
                "Without this permission, the app will not know when to send documents.",
            onAgree = {
                showNotificationDialog = false
                permissionReply.openNotificationAccessSettings()
            },
            onDisagree = {
                showNotificationDialog = false
                onCancel()
            }
        )
    }

    if (showContactPermissionDialog) {
        PermissionDialog(
            title = "Contacts Permission Required",
            message = "Document Reply needs Contacts permission to map saved contact names to phone numbers.\n\n" +
                "- Find the correct number when WhatsApp shows a saved contact name.\n" +
                "- Without it, document sending may fail for saved contacts.",
            onAgree = {
                showContactPermissionDialog = false
                contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            },
            onDisagree = {
                showContactPermissionDialog = false
                onCancel()
            }
        )
    }
}

@Composable
private fun PermissionDialog(
    title: String,
    message: String,
    onAgree: () -> Unit,
    onDisagree: () -> Unit
) {
    Dialog(onDismissRequest = onDisagree) {
        val backgroundBrush = Brush.verticalGradient(
            colors = listOf(Color(0xFF0f0c29), Color(0xFF302b63), Color(0xFF24243e))
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundBrush)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00D4FF),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.size(16.dp))

                Text(
                    text = message,
                    fontSize = 14.sp,
                    color = Color.White,
                    textAlign = TextAlign.Start,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.size(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDisagree,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Disagree", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onAgree,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Agree", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
