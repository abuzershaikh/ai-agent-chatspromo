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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.message.bulksend.autorespond.AutoRespondManager

/**
 * Permission handler for Document Reply feature
 * Checks accessibility, notification permissions, and AutoRespond switch before saving
 */
class DocumentPermissionReply(private val context: Context) {
    
    companion object {
        const val TAG = "DocumentPermissionReply"
    }
    
    private val autoRespondManager = AutoRespondManager(context)
    
    /**
     * Check all required permissions and settings before proceeding
     */
    suspend fun checkPermissionsAndProceed(
        onAllPermissionsGranted: () -> Unit,
        onPermissionsDenied: () -> Unit
    ) {
        Log.d(TAG, "Ã°Å¸â€Â Checking Document Reply permissions and settings...")
        
        // Check accessibility permission first
        if (!isAccessibilityServiceEnabled()) {
            Log.w(TAG, "Ã¢ÂÅ’ Accessibility service not enabled")
            // Show accessibility dialog - handled in Compose
            return
        }
        
        // Check notification access permission
        if (!isNotificationAccessEnabled()) {
            Log.w(TAG, "Ã¢ÂÅ’ Notification access not enabled")
            // Show notification dialog - handled in Compose
            return
        }
        
        // Check contacts permission
        if (!isContactPermissionGranted()) {
            Log.w(TAG, "Contacts permission not granted")
            // Show contacts dialog - handled in Compose
            return
        }

        // Check if AutoRespond switch is enabled
        if (!isAutoRespondEnabled()) {
            Log.w(TAG, "Ã¢ÂÅ’ AutoRespond switch is disabled")
            // Show AutoRespond dialog - handled in Compose
            return
        }
        
        Log.d(TAG, "Ã¢Å“â€¦ All permissions and settings are enabled, proceeding...")
        onAllPermissionsGranted()
    }
    
    /**
     * Check if accessibility service is enabled
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            
            val packageName = context.packageName
            val serviceName = "$packageName/.autorespond.WhatsAppAutoSendService"
            
            val isEnabled = enabledServices.any { service ->
                service.id.contains(packageName) || service.id.contains("WhatsAppAutoSendService")
            }
            
            Log.d(TAG, "Ã°Å¸â€Â§ Accessibility service enabled: $isEnabled")
            Log.d(TAG, "Ã°Å¸â€œÂ¦ Looking for service: $serviceName")
            
            isEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Ã¢ÂÅ’ Error checking accessibility service: ${e.message}")
            false
        }
    }
    
    /**
     * Check if notification access is enabled
     */
    fun isNotificationAccessEnabled(): Boolean {
        return try {
            val isEnabled = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            Log.d(TAG, "Ã°Å¸â€â€ Notification access enabled: $isEnabled")
            isEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Ã¢ÂÅ’ Error checking notification access: ${e.message}")
            false
        }
    }
    
    /**
     * Check if contacts permission is granted
     */
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

    /**
     * Check if AutoRespond switch is enabled
     */
    fun isAutoRespondEnabled(): Boolean {
        return try {
            val isEnabled = autoRespondManager.isAutoRespondEnabled()
            Log.d(TAG, "Ã°Å¸â€â€ž AutoRespond switch enabled: $isEnabled")
            isEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Ã¢ÂÅ’ Error checking AutoRespond status: ${e.message}")
            false
        }
    }
    
    /**
     * Open accessibility settings
     */
    fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.d(TAG, "Ã¢Å“â€¦ Opened accessibility settings")
        } catch (e: Exception) {
            Log.e(TAG, "Ã¢ÂÅ’ Error opening accessibility settings: ${e.message}")
        }
    }
    
    /**
     * Open notification access settings
     */
    fun openNotificationAccessSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.d(TAG, "Ã¢Å“â€¦ Opened notification access settings")
        } catch (e: Exception) {
            Log.e(TAG, "Ã¢ÂÅ’ Error opening notification access settings: ${e.message}")
        }
    }
    
    /**
     * Navigate to AutoRespond Home tab for notification access
     */
    fun navigateToAutoRespondHome() {
        try {
            // Navigate to AutoRespondActivity with Home tab selected
            val intent = Intent(context, com.message.bulksend.autorespond.AutoRespondActivity::class.java).apply {
                putExtra("navigate_to_autorespond_home", true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
            Log.d(TAG, "Ã¢Å“â€¦ Navigated to AutoRespond Home tab")
        } catch (e: Exception) {
            Log.e(TAG, "Ã¢ÂÅ’ Error navigating to AutoRespond Home: ${e.message}")
            // Fallback - open notification settings directly
            openNotificationAccessSettings()
        }
    }
}

/**
 * Composable for Document Reply permission dialogs
 */
@Composable
fun DocumentReplyPermissionHandler(
    permissionReply: DocumentPermissionReply,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var showNotificationDialog by remember { mutableStateOf(false) }
    var showContactPermissionDialog by remember { mutableStateOf(false) }
    var showAutoRespondDialog by remember { mutableStateOf(false) }
    var permissionCheckInProgress by remember { mutableStateOf(false) }
    
    // Function to check permissions step by step
    fun checkPermissions() {
        permissionCheckInProgress = true
        
        // Check accessibility first
        if (!permissionReply.isAccessibilityServiceEnabled()) {
            showAccessibilityDialog = true
            permissionCheckInProgress = false
            return
        }
        
        // Check notification access
        if (!permissionReply.isNotificationAccessEnabled()) {
            showNotificationDialog = true
            permissionCheckInProgress = false
            return
        }
        
        // Check contacts permission
        if (!permissionReply.isContactPermissionGranted()) {
            showContactPermissionDialog = true
            permissionCheckInProgress = false
            return
        }

        // Check AutoRespond switch
        if (!permissionReply.isAutoRespondEnabled()) {
            showAutoRespondDialog = true
            permissionCheckInProgress = false
            return
        }
        
        // All permissions and settings are enabled
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
    
    // Start permission check
    LaunchedEffect(Unit) {
        checkPermissions()
    }
    
    // Accessibility Permission Dialog
    if (showAccessibilityDialog) {
        PermissionDialog(
            title = "Ã°Å¸â€Â§ Accessibility Permission Required",
            message = "Document Reply needs Accessibility Service to automatically send documents in WhatsApp.\n\n" +
                    "Ã°Å¸â€œÂ± This permission allows the app to:\n" +
                    "Ã¢â‚¬Â¢ Open WhatsApp automatically\n" +
                    "Ã¢â‚¬Â¢ Navigate to chats\n" +
                    "Ã¢â‚¬Â¢ Send documents on your behalf\n\n" +
                    "Ã¢Å¡Â Ã¯Â¸Â Without this permission, Document Reply cannot work.",
            onAgree = {
                showAccessibilityDialog = false
                permissionReply.openAccessibilitySettings()
                // After opening settings, check next permissions
                if (permissionReply.isNotificationAccessEnabled() && permissionReply.isAutoRespondEnabled()) {
                    onSave()
                } else if (!permissionReply.isNotificationAccessEnabled()) {
                    showNotificationDialog = true
                } else if (!permissionReply.isAutoRespondEnabled()) {
                    showAutoRespondDialog = true
                }
            },
            onDisagree = {
                showAccessibilityDialog = false
                onCancel()
            }
        )
    }
    
    // Notification Access Permission Dialog
    if (showNotificationDialog) {
        PermissionDialog(
            title = "Ã°Å¸â€â€ Notification Access Required",
            message = "Document Reply needs Notification Access to detect incoming WhatsApp messages.\n\n" +
                    "Ã°Å¸â€œÂ¨ This permission allows the app to:\n" +
                    "Ã¢â‚¬Â¢ Read WhatsApp notifications\n" +
                    "Ã¢â‚¬Â¢ Detect keyword matches\n" +
                    "Ã¢â‚¬Â¢ Trigger automatic document replies\n\n" +
                    "Ã°Å¸â€œÂ± You will be redirected to the AutoRespond Home tab where you can enable notification access.\n\n" +
                    "Ã¢Å¡Â Ã¯Â¸Â Without this permission, the app won't know when to send documents.",
            onAgree = {
                showNotificationDialog = false
                // Navigate to AutoRespond Home tab where user can enable notification access
                permissionReply.navigateToAutoRespondHome()
            },
            onDisagree = {
                showNotificationDialog = false
                onCancel()
            }
        )
    }
    
    // Contacts Permission Dialog
    if (showContactPermissionDialog) {
        PermissionDialog(
            title = "Contacts Permission Required",
            message = "Document Reply needs Contacts permission to map saved contact names to phone numbers.\n\n" +
                    "- This lets the app find the correct number when WhatsApp shows a saved contact name.\n" +
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

    // AutoRespond Switch Dialog
    if (showAutoRespondDialog) {
        PermissionDialog(
            title = "Ã°Å¸â€â€ž AutoRespond Switch Required",
            message = "Document Reply requires the AutoRespond switch to be enabled.\n\n" +
                    "Ã¢Å¡â„¢Ã¯Â¸Â The AutoRespond switch controls:\n" +
                    "Ã¢â‚¬Â¢ All automatic reply features\n" +
                    "Ã¢â‚¬Â¢ Document Reply functionality\n" +
                    "Ã¢â‚¬Â¢ Keyword matching and responses\n\n" +
                    "Ã°Å¸â€œÂ± You will be redirected to the AutoRespond Home tab where you can enable the switch.\n\n" +
                    "Ã¢Å¡Â Ã¯Â¸Â Without this switch enabled, Document Reply will not work.",
            onAgree = {
                showAutoRespondDialog = false
                // Navigate to AutoRespond Home tab where user can enable the switch
                permissionReply.navigateToAutoRespondHome()
            },
            onDisagree = {
                showAutoRespondDialog = false
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundBrush)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Title
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00D4FF),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Message
                    Text(
                        text = message,
                        fontSize = 14.sp,
                        color = Color.White,
                        textAlign = TextAlign.Start,
                        lineHeight = 20.sp
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Disagree Button
                        OutlinedButton(
                            onClick = onDisagree,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFEF4444)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "Disagree",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        // Agree Button
                        Button(
                            onClick = onAgree,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "Agree",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}


