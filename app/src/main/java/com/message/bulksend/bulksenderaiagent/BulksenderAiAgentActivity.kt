package com.message.bulksend.bulksenderaiagent

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.message.bulksend.bulksend.BulksendActivity
import com.message.bulksend.bulksend.sheetscampaign.SheetsendActivity
import com.message.bulksend.bulksend.textcamp.BulktextActivity
import com.message.bulksend.bulksend.textmedia.TextmediaActivity
import com.message.bulksend.contactmanager.ContactzActivity
import com.message.bulksend.overlay.OverlayHelper
import com.message.bulksend.utils.NotificationPermissionHelper

class BulksenderAiAgentActivity : ComponentActivity() {
    private val viewModel: ChatViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        viewModel.refreshEnvironment()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val language by viewModel.language.collectAsState()
            var showAccessibilityConsent by remember { mutableStateOf(false) }
            var showOverlayConsent by remember { mutableStateOf(false) }

            val contactzLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == RESULT_OK) {
                    viewModel.onContactAdded()
                } else {
                    viewModel.refreshEnvironment()
                }
            }

            val mediaPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                uri?.let {
                    persistReadPermission(it)
                    viewModel.onMediaPicked(it.toString())
                }
            }

            val sheetPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                uri?.let {
                    persistReadPermission(it)
                    viewModel.onSheetPicked(it.toString())
                }
            }

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) {
                viewModel.refreshEnvironment()
            }

            LaunchedEffect(Unit) {
                viewModel.uiActions.collect { action ->
                    when (action) {
                        AgentUiAction.OpenAddContact -> {
                            val intent = Intent(this@BulksenderAiAgentActivity, ContactzActivity::class.java).apply {
                                putExtra("FROM_AI_AGENT", true)
                            }
                            contactzLauncher.launch(intent)
                        }
                        is AgentUiAction.PickAttachment -> {
                            when (action.kind) {
                                AttachmentKind.MEDIA -> mediaPickerLauncher.launch(
                                    arrayOf(
                                        "image/*",
                                        "video/*",
                                        "application/pdf",
                                        "application/*"
                                    )
                                )
                                AttachmentKind.SHEET -> sheetPickerLauncher.launch(
                                    arrayOf(
                                        "text/csv",
                                        "application/vnd.ms-excel",
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                    )
                                )
                            }
                        }
                        AgentUiAction.OpenAccessibilitySettings -> {
                            showAccessibilityConsent = true
                        }
                        AgentUiAction.OpenOverlaySettings -> {
                            showOverlayConsent = true
                        }
                        AgentUiAction.RequestNotificationPermission -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                NotificationPermissionHelper.openNotificationSettings(this@BulksenderAiAgentActivity)
                            }
                        }
                        AgentUiAction.LaunchCampaign -> {
                            launchConfiguredCampaign()
                        }
                    }
                }
            }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.ui.graphics.Color(0xFFEFE6DD)
                ) {
                    AiAgentScreen(
                        viewModel = viewModel,
                        onAddContactClick = {
                            val intent = Intent(this, ContactzActivity::class.java).apply {
                                putExtra("FROM_AI_AGENT", true)
                            }
                            contactzLauncher.launch(intent)
                        },
                        onPickAttachment = { kind ->
                            when (kind) {
                                AttachmentKind.MEDIA -> mediaPickerLauncher.launch(
                                    arrayOf(
                                        "image/*",
                                        "video/*",
                                        "application/pdf",
                                        "application/*"
                                    )
                                )
                                AttachmentKind.SHEET -> sheetPickerLauncher.launch(
                                    arrayOf(
                                        "text/csv",
                                        "application/vnd.ms-excel",
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                    )
                                )
                            }
                        },
                        onOpenAccessibilitySettings = {
                            showAccessibilityConsent = true
                        },
                        onOpenOverlaySettings = {
                            showOverlayConsent = true
                        },
                        onRequestNotificationPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                NotificationPermissionHelper.openNotificationSettings(this)
                            }
                        },
                        onRefreshPermissions = {
                            viewModel.refreshEnvironment()
                        },
                        onLaunchCampaign = {
                            launchConfiguredCampaign()
                        }
                    )

                    if (showAccessibilityConsent) {
                        PermissionConsentDialog(
                            title = if (language == ChatLanguage.HINDI) {
                                "Accessibility Permission"
                            } else {
                                "Accessibility Permission"
                            },
                            message = if (language == ChatLanguage.HINDI) {
                                "Bulk sending automation ke liye Accessibility Service chahiye. Agar aap allow karte hain to main aapko Accessibility settings screen par bhejunga."
                            } else {
                                "Accessibility Service is required for bulk send automation. If you accept, I will take you to the Accessibility settings screen."
                            },
                            confirmLabel = if (language == ChatLanguage.HINDI) "Allow" else "Allow",
                            dismissLabel = if (language == ChatLanguage.HINDI) "Cancel" else "Cancel",
                            onDismiss = { showAccessibilityConsent = false },
                            onConfirm = {
                                showAccessibilityConsent = false
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                        )
                    }

                    if (showOverlayConsent) {
                        PermissionConsentDialog(
                            title = if (language == ChatLanguage.HINDI) {
                                "Overlay Permission"
                            } else {
                                "Overlay Permission"
                            },
                            message = if (language == ChatLanguage.HINDI) {
                                "Campaign progress overlay dikhane ke liye overlay permission chahiye. Allow karne par main aapko overlay permission screen par bhejunga."
                            } else {
                                "Overlay permission is required to show the floating campaign controls and progress. If you accept, I will take you to the overlay permission screen."
                            },
                            confirmLabel = if (language == ChatLanguage.HINDI) "Allow" else "Allow",
                            dismissLabel = if (language == ChatLanguage.HINDI) "Cancel" else "Cancel",
                            onDismiss = { showOverlayConsent = false },
                            onConfirm = {
                                showOverlayConsent = false
                                OverlayHelper.requestOverlayPermission(this)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun persistReadPermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
        }
    }

    private fun launchConfiguredCampaign() {
        val permissionStatus = viewModel.permissionChecklist.value
        if (!permissionStatus.requiredReady) {
            Toast.makeText(this, "Permissions abhi complete nahi hue.", Toast.LENGTH_SHORT).show()
            return
        }

        val request = viewModel.buildLaunchRequest()
        if (request == null) {
            Toast.makeText(this, "Setup abhi complete nahi hua.", Toast.LENGTH_SHORT).show()
            return
        }

        val targetClass = when (request.campaignType) {
            CampaignType.TEXT -> BulktextActivity::class.java
            CampaignType.CAPTION -> BulksendActivity::class.java
            CampaignType.TEXT_MEDIA -> TextmediaActivity::class.java
            CampaignType.SHEET -> SheetsendActivity::class.java
        }

        val intent = Intent(this, targetClass).apply {
            putExtra("SELECTED_GROUP_ID", request.groupId)
            putExtra("SELECTED_GROUP_NAME", request.groupName)
            putExtra("CAMPAIGN_NAME", request.campaignName)
            putExtra("COUNTRY_CODE", request.countryCode)
            putExtra(AiAgentLaunchExtras.EXTRA_PRESET_MESSAGE, request.message)

            if (!request.mediaUri.isNullOrBlank()) {
                putExtra(AiAgentLaunchExtras.EXTRA_PRESET_MEDIA_URI, request.mediaUri)
            }
            if (!request.sheetUri.isNullOrBlank()) {
                putExtra(AiAgentLaunchExtras.EXTRA_PRESET_SHEET_URI, request.sheetUri)
            }
        }

        startActivity(intent)
    }
}

@Composable
private fun PermissionConsentDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title)
        },
        text = {
            Text(text = message)
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel)
            }
        }
    )
}
