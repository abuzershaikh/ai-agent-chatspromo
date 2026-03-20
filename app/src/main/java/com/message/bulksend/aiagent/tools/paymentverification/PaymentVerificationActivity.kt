package com.message.bulksend.aiagent.tools.paymentverification

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.aiagent.tools.ecommerce.PaymentFlowMode
import com.message.bulksend.aiagent.tools.ecommerce.PaymentFlowModeManager
import kotlinx.coroutines.launch

class PaymentVerificationActivity : ComponentActivity() {
    
    private lateinit var manager: PaymentVerificationManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        manager = PaymentVerificationManager.getInstance(this)
        
        // Start listening to Firestore
        manager.startListening()
        
        setContent {
            MaterialTheme {
                PaymentVerificationScreen(manager)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        manager.stopListening()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentVerificationScreen(manager: PaymentVerificationManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val paymentVerifyIntegration = remember { PaymentVerificationAIIntegration.getInstance(context) }
    val paymentFlowModeManager = remember { PaymentFlowModeManager(context) }
    
    // Collect verifications
    val allVerifications by manager.getAllVerifications().collectAsState(initial = emptyList())
    val pendingCount by manager.getPendingCount().collectAsState(initial = 0)
    
    // Filter state
    var selectedFilter by remember { mutableStateOf("ALL") }
    var showWebhookDialog by remember { mutableStateOf(false) }
    var showCustomFieldDialog by remember { mutableStateOf(false) }
    var autoSendVerificationLink by remember { mutableStateOf(false) }
    var decisionMode by remember { mutableStateOf(PaymentDecisionMode.OWNER_CONFIRM) }
    var paymentFlowMode by remember { mutableStateOf(PaymentFlowMode.MANUAL_QR_UPI_BANK) }

    LaunchedEffect(Unit) {
        autoSendVerificationLink = paymentVerifyIntegration.isEnabled()
        decisionMode = manager.getDecisionMode()
        paymentFlowMode = paymentFlowModeManager.getMode()
    }
    
    val filteredVerifications = when (selectedFilter) {
        "PENDING" -> allVerifications.filter { it.status == "PENDING" }
        "PAID" -> allVerifications.filter { it.recommendation == "PAID" }
        "MANUAL_REVIEW" -> allVerifications.filter { it.recommendation == "MANUAL_REVIEW" }
        "REJECTED" -> allVerifications.filter { it.recommendation == "REJECTED" }
        else -> allVerifications
    }
    
    Scaffold(
        containerColor = Color(0xFF0B0F17),
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1A2563), Color(0xFF0F1629))
                        )
                    )
                    .statusBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    // Row 1 — Title + pending badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Payment Verifications",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE8EAED)
                            )
                            Text(
                                "AI-Powered Screenshot Verification",
                                fontSize = 12.sp,
                                color = Color(0xFF8896A5)
                            )
                        }
                        if (pendingCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFFEF4444).copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .background(Color(0xFFEF4444), CircleShape)
                                    )
                                    Text(
                                        "$pendingCount pending",
                                        color = Color(0xFFEF4444),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Row 2 — Compact action buttons (no overflow)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { scope.launch { manager.fetchFromFirestore() } },
                            modifier = Modifier.height(34.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF3B82F6)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Refresh", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { showCustomFieldDialog = true },
                            modifier = Modifier.weight(1f).height(34.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8B5CF6)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Default.Settings, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Verification Field", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF0B0F17))
        ) {
            if (showWebhookDialog) {
                WebhookDialogCard(manager, context) { showWebhookDialog = false }
            }
            if (showCustomFieldDialog) {
                CustomFieldDialog(manager, context) { showCustomFieldDialog = false }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    VerificationLinkToggleCard(
                        enabled = autoSendVerificationLink,
                        onToggle = { enabled ->
                            autoSendVerificationLink = enabled
                            paymentVerifyIntegration.setEnabled(enabled)
                        }
                    )
                }
                item {
                    VerificationDecisionModeCard(
                        mode = decisionMode,
                        onModeChange = { mode ->
                            decisionMode = mode
                            manager.setDecisionMode(mode)
                        }
                    )
                }
                item {
                    PaymentFlowModeCard(
                        mode = paymentFlowMode,
                        onModeChange = { mode ->
                            paymentFlowMode = mode
                            paymentFlowModeManager.setMode(mode)
                        }
                    )
                }
                item {
                    FilterChips(
                        selectedFilter = selectedFilter,
                        onFilterSelected = { selectedFilter = it },
                        counts = mapOf(
                            "ALL" to allVerifications.size,
                            "PENDING" to allVerifications.count { it.status == "PENDING" },
                            "PAID" to allVerifications.count { it.recommendation == "PAID" },
                            "MANUAL_REVIEW" to allVerifications.count { it.recommendation == "MANUAL_REVIEW" },
                            "REJECTED" to allVerifications.count { it.recommendation == "REJECTED" }
                        )
                    )
                }
                if (filteredVerifications.isEmpty()) {
                    item { EmptyState() }
                } else {
                    items(filteredVerifications) { verification ->
                        VerificationCard(
                            verification = verification,
                            onApprove = {
                                scope.launch {
                                    manager.approvePayment(verification.id)
                                    Toast.makeText(context, "Payment Approved ✅", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onReject = {
                                scope.launch {
                                    manager.rejectPayment(verification.id)
                                    Toast.makeText(context, "Payment Rejected ❌", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onGenerateLink = {
                                val link = manager.generateCustomerLink(verification.customerPhone, verification.orderId)
                                copyToClipboard(context, link)
                                Toast.makeText(context, "Link copied! 📋", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
                item { Spacer(Modifier.navigationBarsPadding()) }
            }
        }
    }
}

@Composable
fun VerificationLinkToggleCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF141921),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2563EB).copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF2563EB).copy(0.15f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Verified, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Auto Send Verification Link",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color(0xFFE8EAED)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Screenshot verification link will be auto-added.",
                        fontSize = 11.sp,
                        color = Color(0xFF8896A5),
                        lineHeight = 15.sp
                    )
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF2563EB)
                )
            )
        }
    }
}

@Composable
fun VerificationDecisionModeCard(
    mode: PaymentDecisionMode,
    onModeChange: (PaymentDecisionMode) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF141921),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(Color(0xFFF59E0B).copy(0.15f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.HowToVote, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "Payment Confirmation Mode",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color(0xFFE8EAED)
                )
            }
            Text(
                "Owner Confirm: Every payment requires owner approval. Auto Strict: Auto approve on exact match.",
                fontSize = 11.sp,
                color = Color(0xFF8896A5),
                lineHeight = 15.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = mode == PaymentDecisionMode.OWNER_CONFIRM,
                    onClick = { onModeChange(PaymentDecisionMode.OWNER_CONFIRM) },
                    label = { Text("Owner Confirm", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFF59E0B).copy(0.2f),
                        selectedLabelColor = Color(0xFFF59E0B),
                        containerColor = Color(0xFF1A2030),
                        labelColor = Color(0xFF8896A5)
                    )
                )
                FilterChip(
                    selected = mode == PaymentDecisionMode.AUTO_CONFIRM_STRICT,
                    onClick = { onModeChange(PaymentDecisionMode.AUTO_CONFIRM_STRICT) },
                    label = { Text("Auto Strict", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFF59E0B).copy(0.2f),
                        selectedLabelColor = Color(0xFFF59E0B),
                        containerColor = Color(0xFF1A2030),
                        labelColor = Color(0xFF8896A5)
                    )
                )
            }
        }
    }
}

@Composable
fun PaymentFlowModeCard(
    mode: PaymentFlowMode,
    onModeChange: (PaymentFlowMode) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF141921),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(Color(0xFF10B981).copy(0.15f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Payments, null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "Active Payment Channel",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color(0xFFE8EAED)
                )
            }
            Text(
                "Only one channel will be active at a time.",
                fontSize = 11.sp,
                color = Color(0xFF8896A5),
                lineHeight = 15.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = mode == PaymentFlowMode.MANUAL_QR_UPI_BANK,
                    onClick = { onModeChange(PaymentFlowMode.MANUAL_QR_UPI_BANK) },
                    label = { Text("QR/UPI/Bank", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF10B981).copy(0.2f),
                        selectedLabelColor = Color(0xFF10B981),
                        containerColor = Color(0xFF1A2030),
                        labelColor = Color(0xFF8896A5)
                    )
                )
                FilterChip(
                    selected = mode == PaymentFlowMode.RAZORPAY,
                    onClick = { onModeChange(PaymentFlowMode.RAZORPAY) },
                    label = { Text("Razorpay", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF10B981).copy(0.2f),
                        selectedLabelColor = Color(0xFF10B981),
                        containerColor = Color(0xFF1A2030),
                        labelColor = Color(0xFF8896A5)
                    )
                )
            }
        }
    }
}

@Composable
fun CustomFieldDialog(manager: PaymentVerificationManager, context: Context, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var customFields by remember { mutableStateOf(manager.getCustomFields()) }
    var showAddFieldDialog by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141921),
        titleContentColor = Color(0xFFE8EAED),
        textContentColor = Color(0xFF8896A5),
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, "Custom Fields", tint = Color(0xFF3B82F6))
                Spacer(Modifier.width(8.dp))
                Text("Verification Fields", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Add custom fields for specific payment verification. AI will match these against the screenshot.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                
                Spacer(Modifier.height(8.dp))
                
                if (customFields.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1A2030)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Info, null, tint = Color(0xFF8896A5), modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("No custom fields added yet", fontSize = 13.sp, color = Color(0xFF8896A5))
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(customFields) { field ->
                            CustomFieldItem(
                                field = field,
                                onDelete = {
                                    customFields = customFields.filter { it.fieldName != field.fieldName }
                                    manager.saveCustomFields(customFields)
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showAddFieldDialog = true },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF3B82F6)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6).copy(0.4f))
                ) {
                    Icon(Icons.Default.Add, "Add Field", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Custom Field", fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) { Text("Done") }
        }
    )
    
    if (showAddFieldDialog) {
        AddCustomFieldDialog(
            onDismiss = { showAddFieldDialog = false },
            onAdd = { fieldName, fieldDescription ->
                val newField = CustomField(fieldName, fieldDescription)
                customFields = customFields + newField
                manager.saveCustomFields(customFields)
                showAddFieldDialog = false
                Toast.makeText(context, "Custom field added ✅", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun CustomFieldItem(field: CustomField, onDelete: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1A2030),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A3244))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(field.fieldName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFFE8EAED))
                Spacer(Modifier.height(4.dp))
                Text(field.fieldDescription, fontSize = 12.sp, color = Color(0xFF8896A5), lineHeight = 16.sp)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun AddCustomFieldDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var fieldName by remember { mutableStateOf("") }
    var fieldDescription by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141921),
        titleContentColor = Color(0xFFE8EAED),
        textContentColor = Color(0xFF8896A5),
        shape = RoundedCornerShape(16.dp),
        title = { Text("Add Verification Field", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = fieldName,
                    onValueChange = { fieldName = it },
                    label = { Text("Field Name") },
                    placeholder = { Text("e.g. Bank Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF3B82F6),
                        focusedLabelColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color(0xFF2A3244),
                        unfocusedLabelColor = Color(0xFF8896A5)
                    )
                )
                OutlinedTextField(
                    value = fieldDescription,
                    onValueChange = { fieldDescription = it },
                    label = { Text("Description for AI") },
                    placeholder = { Text("What should AI look for?") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3, maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF3B82F6),
                        focusedLabelColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color(0xFF2A3244),
                        unfocusedLabelColor = Color(0xFF8896A5)
                    )
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF2563EB).copy(0.15f)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "AI will extract this field from screenshots and match expected values.",
                            fontSize = 12.sp, color = Color(0xFF3B82F6), lineHeight = 16.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (fieldName.isNotBlank() && fieldDescription.isNotBlank()) onAdd(fieldName.trim(), fieldDescription.trim()) },
                enabled = fieldName.isNotBlank() && fieldDescription.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) { Text("Add Field") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF8896A5)) }
        }
    )
}

@Composable
fun WebhookDialogCard(manager: PaymentVerificationManager, context: Context, onDismiss: () -> Unit) {
    val webhookUrl = manager.generateWebhookUrl()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141921),
        titleContentColor = Color(0xFFE8EAED),
        textContentColor = Color(0xFF8896A5),
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Link, "Webhook", tint = Color(0xFF10B981))
                Spacer(Modifier.width(8.dp))
                Text("Your Webhook URL", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text("Share this URL with customers to collect payment screenshots:", fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1A2030),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A3244))
                ) {
                    Text(
                        webhookUrl,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 12.sp,
                        color = Color(0xFFE8EAED),
                        lineHeight = 18.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    copyToClipboard(context, webhookUrl)
                    Toast.makeText(context, "Webhook URL copied!", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy URL")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = Color(0xFF8896A5)) }
        }
    )
}

@Composable
fun FilterChips(
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    counts: Map<String, Int>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF141921), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Filter", fontSize = 11.sp, color = Color(0xFF8896A5), fontWeight = FontWeight.SemiBold)
        // First Row: ALL and PENDING
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == "ALL",
                onClick = { onFilterSelected("ALL") },
                label = { Text("All (${counts["ALL"] ?: 0})", fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF2563EB).copy(0.2f),
                    selectedLabelColor = Color(0xFF3B82F6),
                    containerColor = Color(0xFF1A2030),
                    labelColor = Color(0xFF8896A5)
                )
            )
            FilterChip(
                selected = selectedFilter == "PENDING",
                onClick = { onFilterSelected("PENDING") },
                label = { Text("Pending (${counts["PENDING"] ?: 0})", fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFFF59E0B).copy(0.2f),
                    selectedLabelColor = Color(0xFFF59E0B),
                    containerColor = Color(0xFF1A2030),
                    labelColor = Color(0xFF8896A5)
                )
            )
        }
        // Second Row: PAID and MANUAL_REVIEW
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == "PAID",
                onClick = { onFilterSelected("PAID") },
                label = { Text("Paid (${counts["PAID"] ?: 0})", fontWeight = FontWeight.Medium) },
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF10B981),
                    selectedLabelColor = Color.White,
                    containerColor = Color(0xFFD1FAE5),
                    labelColor = Color(0xFF059669)
                )
            )
            FilterChip(
                selected = selectedFilter == "MANUAL_REVIEW",
                onClick = { onFilterSelected("MANUAL_REVIEW") },
                label = { Text("Review (${counts["MANUAL_REVIEW"] ?: 0})", fontWeight = FontWeight.Medium) },
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF8B5CF6),
                    selectedLabelColor = Color.White,
                    containerColor = Color(0xFFEDE9FE),
                    labelColor = Color(0xFF7C3AED)
                )
            )
        }
        
        // Third Row: REJECTED (centered)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            FilterChip(
                selected = selectedFilter == "REJECTED",
                onClick = { onFilterSelected("REJECTED") },
                label = { Text("Rejected (${counts["REJECTED"] ?: 0})", fontWeight = FontWeight.Medium) },
                modifier = Modifier.fillMaxWidth(0.5f),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFFEF4444),
                    selectedLabelColor = Color.White,
                    containerColor = Color(0xFFFEE2E2),
                    labelColor = Color(0xFFDC2626)
                )
            )
        }
    }
}

@Composable
fun VerificationCard(
    verification: PaymentVerification,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onGenerateLink: () -> Unit,
    onClear: (() -> Unit)? = null
) {
    // Hidden local state to expand/collapse AI result
    var aiAnalysisExpanded by remember { mutableStateOf(false) }

    val cardBg = Color(0xFF141921)
    val innerBg = Color(0xFF0C111A)
    val borderCol = Color(0xFF2A3244)
    val textW = Color(0xFFE8EAED)
    val textMut = Color(0xFF8896A5)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderCol)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(36.dp).background(Color(0xFF2563EB).copy(0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Phone, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(verification.customerPhone, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = textW)
                        if (verification.orderId.isNotEmpty()) {
                            Text("Order: ${verification.orderId}", fontSize = 11.sp, color = textMut)
                        }
                    }
                }
                RecommendationBadge(verification.recommendation)
            }

            // Payment Details
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = innerBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, borderCol)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (verification.amount > 0) {
                        DarkDetailRow(Icons.Default.CurrencyRupee, "Amount", "₹${verification.amount}", Color(0xFF10B981))
                    }
                    if (verification.upiId.isNotEmpty()) {
                        DarkDetailRow(Icons.Default.AccountBalance, "UPI ID", verification.upiId, Color(0xFF3B82F6))
                    }
                    if (verification.transactionId.isNotEmpty()) {
                        DarkDetailRow(Icons.Default.Receipt, "Transaction ID", verification.transactionId, Color(0xFF8B5CF6))
                    }
                    if (verification.paymentDate.isNotEmpty()) {
                        DarkDetailRow(Icons.Default.CalendarToday, "Date", "${verification.paymentDate} ${verification.paymentTime}", Color(0xFF06B6D4))
                    }
                }
            }

            // Confidence & Authenticity Trackers
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = if (verification.confidence >= 90) Color(0xFF10B981).copy(0.12f) else Color(0xFFF59E0B).copy(0.12f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (verification.confidence >= 90) Color(0xFF10B981).copy(0.4f) else Color(0xFFF59E0B).copy(0.4f)
                    )
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, null, tint = if (verification.confidence >= 90) Color(0xFF10B981) else Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${verification.confidence}% Confidence", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = if (verification.confidence >= 90) Color(0xFF10B981) else Color(0xFFF59E0B))
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = if (verification.isFake) Color(0xFFEF4444).copy(0.12f) else Color(0xFF10B981).copy(0.12f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (verification.isFake) Color(0xFFEF4444).copy(0.4f) else Color(0xFF10B981).copy(0.4f)
                    )
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (verification.isFake) "⚠️ Suspicious" else "✅ Authentic",
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = if (verification.isFake) Color(0xFFEF4444) else Color(0xFF10B981)
                        )
                    }
                }
            }

            // Time Limit Warning
            if (!verification.isWithinTimeLimit) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEF4444).copy(0.1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(0.4f))
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Screenshot is older than 30 minutes", fontSize = 12.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Collapsible AI Analysis
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { aiAnalysisExpanded = !aiAnalysisExpanded },
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF1E1430).copy(0.6f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(0.3f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Psychology, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("AI Analysis", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF8B5CF6))
                        }
                        Icon(
                            if (aiAnalysisExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(18.dp)
                        )
                    }
                    if (aiAnalysisExpanded) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = verification.reasoning.ifBlank { "No analysis provided." },
                            fontSize = 12.sp, color = textW, lineHeight = 17.sp
                        )
                    }
                }
            }

            // Action Buttons
            if (verification.status == "PENDING") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onApprove, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981).copy(0.9f))
                    ) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Approve", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onReject, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444).copy(0.9f))
                    ) {
                        Icon(Icons.Default.Cancel, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reject", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onGenerateLink, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF3B82F6)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6).copy(0.5f))
                    ) {
                        Icon(Icons.Default.Link, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy Link", fontSize = 12.sp)
                    }
                    if (onClear != null) {
                        OutlinedButton(
                            onClick = onClear, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(0.5f))
                        ) {
                            Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Delete", fontSize = 12.sp)
                        }
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBadge(verification.status, modifier = Modifier.weight(1f))
                    if (onClear != null) {
                        OutlinedButton(
                            onClick = onClear,
                            modifier = Modifier.height(38.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(0.5f))
                        ) {
                            Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Delete", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DarkDetailRow(icon: ImageVector, label: String, value: String, accentColor: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = accentColor, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 12.sp, color = Color(0xFF8896A5), modifier = Modifier.width(100.dp))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE8EAED))
    }
}

@Composable
fun DetailRowWithIcon(icon: ImageVector, label: String, value: String) {
    DarkDetailRow(icon, label, value, Color(0xFF3B82F6))
}

@Composable
fun RecommendationBadge(recommendation: String) {
    val colors = when (recommendation) {
        "PAID" -> Triple(Color(0xFF10B981), Color(0xFF10B981).copy(0.15f), "✓")
        "MANUAL_REVIEW" -> Triple(Color(0xFFF59E0B), Color(0xFFF59E0B).copy(0.15f), "⚠")
        "REJECTED" -> Triple(Color(0xFFEF4444), Color(0xFFEF4444).copy(0.15f), "✗")
        else -> Triple(Color(0xFF8896A5), Color(0xFF8896A5).copy(0.15f), "?")
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.second,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.first.copy(0.4f))
    ) {
        Text(
            text = "${colors.third} $recommendation",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.first
        )
    }
}

@Composable
fun StatusBadge(status: String, modifier: Modifier = Modifier) {
    val (textColor, bgColor, label) = when (status) {
        "APPROVED"      -> Triple(Color(0xFF10B981), Color(0xFF10B981).copy(0.12f), "✓ Approved")
        "MANUAL_REVIEW" -> Triple(Color(0xFFF59E0B), Color(0xFFF59E0B).copy(0.12f), "⚠ Review Required")
        "REJECTED"      -> Triple(Color(0xFFEF4444), Color(0xFFEF4444).copy(0.12f), "✗ Rejected")
        "PROCESSED"     -> Triple(Color(0xFF3B82F6), Color(0xFF3B82F6).copy(0.12f), "✓ Processed")
        else            -> Triple(Color(0xFF8896A5), Color(0xFF1A2030), status)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, textColor.copy(0.4f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(vertical = 10.dp),
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textColor,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .background(Brush.radialGradient(listOf(Color(0xFF3B82F6).copy(0.2f), Color.Transparent)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Payment, null, modifier = Modifier.size(46.dp), tint = Color(0xFF3B82F6))
            }
            Text("No Payment Verifications", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE8EAED))
            Text(
                "Share your verification link with customers\nto receive payment screenshot submissions.",
                fontSize = 13.sp, color = Color(0xFF8896A5), textAlign = TextAlign.Center, lineHeight = 19.sp
            )
        }
    }
}

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Payment Verification", text)
    clipboard.setPrimaryClip(clip)
}

/**
 * Custom Field data class
 */
data class CustomField(
    val fieldName: String,
    val fieldDescription: String
)
