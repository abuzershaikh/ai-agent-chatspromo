package com.message.bulksend.aiagent.tools.woocommerce

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.message.bulksend.aiagent.tools.reverseai.ReverseAIManager
import com.message.bulksend.notification.FCMTokenManager
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.launch

/**
 * WooCommerce Setup Activity
 *
 * Owner enters:
 *  - WooCommerce site URL
 *  - Webhook secret (random string)
 *  - Owner Assistant number is auto-used from Reverse AI settings
 */
class WooCommerceSetupActivity : ComponentActivity() {

    private val tag = "WooCommerceSetup"

    private lateinit var manager: WooCommerceManager

    private var siteUrl by mutableStateOf("")
    private var secret by mutableStateOf("")
    private var ownerAssistantPhone by mutableStateOf("")
    private var workerUrl by mutableStateOf("")
    private var pushNotificationsEnabled by mutableStateOf(true)
    private var whatsappAlertsEnabled by mutableStateOf(true)

    private var webhookUrl by mutableStateOf("")
    private var statusMessage by mutableStateOf("")
    private var statusIsError by mutableStateOf(false)
    private var isLoading by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        manager = WooCommerceManager(this)

        setContent {
            BulksendTestTheme {
                WooCommerceSetupScreen(
                    siteUrl = siteUrl,
                    onSiteUrlChange = { siteUrl = it },
                    secret = secret,
                    onSecretChange = { secret = it },
                    ownerAssistantPhone = ownerAssistantPhone,
                    workerUrl = workerUrl,
                    onWorkerUrlChange = { workerUrl = it },
                    pushNotificationsEnabled = pushNotificationsEnabled,
                    onPushNotificationsEnabledChange = { pushNotificationsEnabled = it },
                    whatsappAlertsEnabled = whatsappAlertsEnabled,
                    onWhatsAppAlertsEnabledChange = { whatsappAlertsEnabled = it },
                    webhookUrl = webhookUrl,
                    statusMessage = statusMessage,
                    statusIsError = statusIsError,
                    isLoading = isLoading,
                    onBackPressed = { finish() },
                    onConnectClick = { connectWooCommerce() },
                    onCopyWebhookClick = { copyWebhookUrl() }
                )
            }
        }

        refreshOwnerAssistantPhone()
        loadExistingConfig()
    }

    override fun onResume() {
        super.onResume()
        refreshOwnerAssistantPhone()
    }

    private fun refreshOwnerAssistantPhone() {
        ownerAssistantPhone = ReverseAIManager(this).ownerPhoneNumber.trim()
    }

    private fun loadExistingConfig() {
        lifecycleScope.launch {
            val config = manager.getConfig() ?: return@launch

            siteUrl = config.siteUrl
            secret = config.webhookSecret
            webhookUrl = config.webhookUrl
            pushNotificationsEnabled = config.pushNotificationsEnabled
            whatsappAlertsEnabled = config.whatsappAlertsEnabled

            if (config.webhookUrl.isNotBlank()) {
                statusMessage = "WooCommerce connected. Setup at: ${config.setupAt}"
                statusIsError = false
            }
        }
    }

    private fun connectWooCommerce() {
        val site = siteUrl.trim()
        val hookSecret = secret.trim()
        val ownerPhone = ReverseAIManager(this).ownerPhoneNumber.trim()
        ownerAssistantPhone = ownerPhone
        val worker = workerUrl.trim()

        when {
            site.isBlank() -> {
                setStatus("Site URL required", true)
                return
            }
            hookSecret.isBlank() -> {
                setStatus("Webhook secret required (any random text)", true)
                return
            }
            whatsappAlertsEnabled && ownerPhone.isBlank() -> {
                setStatus(
                    "Owner Assistant number is required. Set it in Reverse AI settings.",
                    true
                )
                return
            }
        }

        if (worker.isNotBlank()) {
            manager.workerBaseUrl = worker
        }

        isLoading = true
        setStatus("", false)

        lifecycleScope.launch {
            try {
                val fcmToken = FCMTokenManager.getToken().orEmpty()
                if (pushNotificationsEnabled && fcmToken.isBlank()) {
                    Log.w(tag, "FCM token empty. Push notification delivery may fail until token refresh.")
                }

                val result = manager.setupWooCommerce(
                    siteUrl = site,
                    webhookSecret = hookSecret,
                    fcmToken = fcmToken,
                    pushNotificationsEnabled = pushNotificationsEnabled,
                    whatsappAlertsEnabled = whatsappAlertsEnabled
                )

                if (result.isSuccess) {
                    val config = result.getOrThrow()
                    webhookUrl = config.webhookUrl
                    setStatus(
                        "WooCommerce settings saved successfully. Copy webhook URL and paste in WooCommerce settings.",
                        false
                    )
                    Toast.makeText(this@WooCommerceSetupActivity, "Settings saved", Toast.LENGTH_SHORT).show()
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Unknown error"
                    setStatus("Error: $err", true)
                    Toast.makeText(this@WooCommerceSetupActivity, "Error: $err", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(tag, "Connect error: ${e.message}", e)
                setStatus("Exception: ${e.message}", true)
            } finally {
                isLoading = false
            }
        }
    }

    private fun copyWebhookUrl() {
        val url = webhookUrl.trim()
        if (url.isBlank()) return

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Webhook URL", url))
        Toast.makeText(this, "Webhook URL copied", Toast.LENGTH_SHORT).show()
    }

    private fun setStatus(message: String, isError: Boolean) {
        statusMessage = message
        statusIsError = isError
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WooCommerceSetupScreen(
    siteUrl: String,
    onSiteUrlChange: (String) -> Unit,
    secret: String,
    onSecretChange: (String) -> Unit,
    ownerAssistantPhone: String,
    workerUrl: String,
    onWorkerUrlChange: (String) -> Unit,
    pushNotificationsEnabled: Boolean,
    onPushNotificationsEnabledChange: (Boolean) -> Unit,
    whatsappAlertsEnabled: Boolean,
    onWhatsAppAlertsEnabledChange: (Boolean) -> Unit,
    webhookUrl: String,
    statusMessage: String,
    statusIsError: Boolean,
    isLoading: Boolean,
    onBackPressed: () -> Unit,
    onConnectClick: () -> Unit,
    onCopyWebhookClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WooCommerce Setup", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Connect your WooCommerce store to receive new order alerts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            OutlinedTextField(
                value = workerUrl,
                onValueChange = onWorkerUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Worker URL (optional)") },
                placeholder = { Text("https://woocommerce-worker.example.workers.dev") },
                singleLine = true
            )

            OutlinedTextField(
                value = siteUrl,
                onValueChange = onSiteUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("WooCommerce Site URL") },
                placeholder = { Text("https://yourstore.com") },
                singleLine = true
            )

            OutlinedTextField(
                value = secret,
                onValueChange = onSecretChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Webhook Secret") },
                placeholder = { Text("Any random text") },
                singleLine = true
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Owner Assistant Number (Auto from Reverse AI)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = ownerAssistantPhone.ifBlank { "Not set. Configure in Reverse AI settings." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Order Alert Settings",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    SettingSwitchRow(
                        title = "App Push Notifications",
                        subtitle = "Enable/disable WooCommerce order notification in app",
                        checked = pushNotificationsEnabled,
                        onCheckedChange = onPushNotificationsEnabledChange
                    )
                    SettingSwitchRow(
                        title = "WhatsApp Owner Assistant Alerts",
                        subtitle = "Enable/disable WhatsApp message to Owner Assistant number on each new order",
                        checked = whatsappAlertsEnabled,
                        onCheckedChange = onWhatsAppAlertsEnabledChange
                    )
                }
            }

            Button(
                onClick = onConnectClick,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connecting...")
                    }
                } else {
                    Text("Save WooCommerce Settings")
                }
            }

            if (statusMessage.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (statusIsError) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                    )
                ) {
                    Text(
                        text = statusMessage,
                        modifier = Modifier.padding(12.dp),
                        color = if (statusIsError) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )
                }
            }

            if (webhookUrl.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Paste this URL in WooCommerce -> Settings -> Advanced -> Webhooks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = webhookUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )
                            IconButton(onClick = onCopyWebhookClick) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "Copy Webhook URL"
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
