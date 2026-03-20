package com.message.bulksend.aiagent.tools.ecommerce

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import coil.compose.rememberAsyncImagePainter
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.launch
import java.io.File
class PaymentMethodActivity : ComponentActivity() {
    
    private lateinit var paymentManager: PaymentMethodManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        paymentManager = PaymentMethodManager(this)
        
        setContent {
            BulksendTestTheme {
                PaymentMethodScreen(
                    paymentManager = paymentManager,
                    onBackPressed = { finish() }
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentMethodScreen(
    paymentManager: PaymentMethodManager,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var verifyByAgent by remember { mutableStateOf(false) }
    
    // Load all payment methods from database
    val allPaymentMethods by paymentManager.getAllPaymentMethods().collectAsState(initial = emptyList())
    
    // QR Code states (3 slots) - Load from database
    val qrCodeMethods = allPaymentMethods.filter { it.type == PaymentMethodType.QR_CODE }
    var qrCode1 by remember { mutableStateOf<PaymentMethod?>(null) }
    var qrCode2 by remember { mutableStateOf<PaymentMethod?>(null) }
    var qrCode3 by remember { mutableStateOf<PaymentMethod?>(null) }
    
    // Load QR codes from database when allPaymentMethods changes
    LaunchedEffect(qrCodeMethods) {
        if (qrCodeMethods.isNotEmpty()) {
            qrCode1 = qrCodeMethods.getOrNull(0)
            qrCode2 = qrCodeMethods.getOrNull(1)
            qrCode3 = qrCodeMethods.getOrNull(2)
        }
    }
    
    // Razorpay state
    var razorpayMethod by remember { mutableStateOf<PaymentMethod?>(null) }
    
    // PayPal state
    var paypalMethod by remember { mutableStateOf<PaymentMethod?>(null) }
    
    // UPI state - load from database
    val upiMethod = allPaymentMethods.find { it.type == PaymentMethodType.UPI_ID }
    
    // Custom groups
    val customGroupsList = allPaymentMethods.filter { it.type == PaymentMethodType.CUSTOM_GROUP }
    
    // Dialog states
    var showUpiDialog by remember { mutableStateOf(false) }
    var showCustomGroupDialog by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<PaymentMethod?>(null) }
    
    // Dialog inputs
    var newUpiName by remember { mutableStateOf("") }
    var newUpiId by remember { mutableStateOf("") }
    
    var newGroupName by remember { mutableStateOf("") }
    var editingGroupName by remember { mutableStateOf("") }
    var editingFields by remember { mutableStateOf(listOf<CustomField>()) }

    // QR Code Picking State
    var showQrDialog by remember { mutableStateOf(false) }
    var pendingQrUri by remember { mutableStateOf<Uri?>(null) }
    var activeQrSlot by remember { mutableStateOf(0) }
    var newQrName by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingQrUri = uri
            showQrDialog = true
        }
    }

    
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0f0c29),
            Color(0xFF302b63),
            Color(0xFF24243e),
            Color(0xFF0f0c29)
        )
    )
    
    Scaffold(
        topBar = {
            PaymentMethodTopBar(onBackPressed = onBackPressed)
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Verify by Agent Card
                item {
                    VerifyByAgentCard(
                        enabled = verifyByAgent,
                        onToggle = { verifyByAgent = it }
                    )
                }
                
                // Section: QR Codes
                item {
                    SectionHeader(
                        title = "QR Code Payments",
                        subtitle = "Add up to 3 QR codes",
                        icon = Icons.Outlined.QrCode2,
                        color = Color(0xFF10B981)
                    )
                }
                
                // QR Code Slots - Horizontal Row with Scroll
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QRCodeSquareCard(
                            slotNumber = 1,
                            qrCode = qrCode1,
                            onAddClick = { 
                                activeQrSlot = 1
                                imagePickerLauncher.launch("image/*")
                            },
                            onRemoveClick = { 
                                qrCode1?.let { method ->
                                    scope.launch {
                                        paymentManager.deletePaymentMethod(method.id)
                                        qrCode1 = null
                                    }
                                }
                            },
                            onToggle = { enabled ->
                                qrCode1?.let { method ->
                                    scope.launch {
                                        paymentManager.togglePaymentMethod(method.id, enabled)
                                    }
                                }
                            },
                            modifier = Modifier.width(140.dp)
                        )
                        
                        QRCodeSquareCard(
                            slotNumber = 2,
                            qrCode = qrCode2,
                            onAddClick = { 
                                activeQrSlot = 2
                                imagePickerLauncher.launch("image/*")
                            },
                            onRemoveClick = { 
                                qrCode2?.let { method ->
                                    scope.launch {
                                        paymentManager.deletePaymentMethod(method.id)
                                        qrCode2 = null
                                    }
                                }
                            },
                            onToggle = { enabled ->
                                qrCode2?.let { method ->
                                    scope.launch {
                                        paymentManager.togglePaymentMethod(method.id, enabled)
                                    }
                                }
                            },
                            modifier = Modifier.width(140.dp)
                        )
                        
                        QRCodeSquareCard(
                            slotNumber = 3,
                            qrCode = qrCode3,
                            onAddClick = { 
                                activeQrSlot = 3
                                imagePickerLauncher.launch("image/*")
                            },
                            onRemoveClick = { 
                                qrCode3?.let { method ->
                                    scope.launch {
                                        paymentManager.deletePaymentMethod(method.id)
                                        qrCode3 = null
                                    }
                                }
                            },
                            onToggle = { enabled ->
                                qrCode3?.let { method ->
                                    scope.launch {
                                        paymentManager.togglePaymentMethod(method.id, enabled)
                                    }
                                }
                            },
                            modifier = Modifier.width(140.dp)
                        )
                    }
                }
                
                // Divider
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = Color(0xFF64748B).copy(alpha = 0.3f)
                    )
                }
                
                // Razorpay Card
                item {
                    RazorpayCard(
                        razorpayMethod = razorpayMethod,
                        onSetupClick = {
                            val intent = Intent(context, RazorpayConfigActivity::class.java)
                            context.startActivity(intent)
                        },
                        onToggle = { enabled ->
                            // Disabled for now
                        }
                    )
                }
                
                // Divider
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = Color(0xFF64748B).copy(alpha = 0.3f)
                    )
                }
                
                // PayPal Card
                item {
                    PayPalCard(
                        paypalMethod = paypalMethod,
                        onSetupClick = {
                            Toast.makeText(context, "PayPal integration coming soon", Toast.LENGTH_SHORT).show()
                        },
                        onToggle = { enabled ->
                            // Disabled for now
                        }
                    )
                }
                
                // Divider
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = Color(0xFF64748B).copy(alpha = 0.3f)
                    )
                }
                
                // UPI ID Card
                item {
                    UPICard(
                        upiMethod = upiMethod,
                        onAddClick = { showUpiDialog = true },
                        onToggle = { enabled ->
                            upiMethod?.let {
                                scope.launch {
                                    paymentManager.togglePaymentMethod(it.id, enabled)
                                }
                            }
                        }
                    )
                }
                
                // Divider
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = Color(0xFF64748B).copy(alpha = 0.3f)
                    )
                }
                
                // Section: Custom Field Groups
                item {
                    SectionHeader(
                        title = "Custom Payment Methods",
                        subtitle = "Bank details, international payments, etc.",
                        icon = Icons.Outlined.AccountBalance,
                        color = Color(0xFFEC4899)
                    )
                }
                
                // Custom Groups List
                customGroupsList.forEach { group ->
                    item {
                        CustomGroupCard(
                            group = group,
                            onEditClick = {
                                editingGroup = group
                                editingGroupName = group.customGroupName ?: group.name
                                editingFields = group.customFields ?: emptyList()
                                showCustomGroupDialog = true
                            },
                            onDeleteClick = {
                                scope.launch {
                                    paymentManager.deletePaymentMethod(group.id)
                                }
                            },
                            onToggle = { enabled ->
                                scope.launch {
                                    paymentManager.togglePaymentMethod(group.id, enabled)
                                }
                            }
                        )
                    }
                }
                
                // Add Custom Group Button
                item {
                    AddCustomGroupButton(
                        onClick = { showCustomGroupDialog = true }
                    )
                }
                
                // Bottom Spacer
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
            
            // UPI Dialog
            if (showUpiDialog) {
                AddUPIDialog(
                    name = newUpiName,
                    upiId = newUpiId,
                    onNameChange = { newUpiName = it },
                    onUpiIdChange = { newUpiId = it },
                    onDismiss = { showUpiDialog = false },
                    onConfirm = {
                        if (newUpiName.isNotBlank() && newUpiId.isNotBlank()) {
                            scope.launch {
                                paymentManager.addUPIMethod(newUpiName, newUpiId)
                                showUpiDialog = false
                                newUpiName = ""
                                newUpiId = ""
                            }
                        }
                    }
                )
            }
            
            // Custom Group Dialog
            if (showCustomGroupDialog) {
                val isEditMode = editingGroup != null
                AddCustomGroupDialog(
                    groupName = if (isEditMode) editingGroupName else newGroupName,
                    onGroupNameChange = { 
                        if (isEditMode) editingGroupName = it else newGroupName = it 
                    },
                    onDismiss = { 
                        showCustomGroupDialog = false
                        editingGroup = null
                        editingGroupName = ""
                        editingFields = emptyList()
                    },
                    onConfirm = { fields ->
                        if (isEditMode && editingGroup != null) {
                            // Edit mode - update existing group
                            if (editingGroupName.isNotBlank() && fields.isNotEmpty()) {
                                scope.launch {
                                    val updatedMethod = editingGroup!!.copy(
                                        name = editingGroupName,
                                        customGroupName = editingGroupName,
                                        customFields = fields
                                    )
                                    paymentManager.updatePaymentMethod(updatedMethod)
                                    showCustomGroupDialog = false
                                    editingGroup = null
                                    editingGroupName = ""
                                    editingFields = emptyList()
                                }
                            }
                        } else {
                            // Add mode - create new group
                            if (newGroupName.isNotBlank() && fields.isNotEmpty()) {
                                scope.launch {
                                    paymentManager.addCustomGroupMethod(newGroupName, fields)
                                    showCustomGroupDialog = false
                                    newGroupName = ""
                                }
                            }
                        }
                    },
                    isEditMode = isEditMode,
                    initialFields = if (isEditMode) editingFields else emptyList()
                )
            }
            // Custom Group Dialog


            // Add QR Dialog
            if (showQrDialog && pendingQrUri != null) {
                AddQRDialog(
                    imageUri = pendingQrUri!!,
                    name = newQrName,
                    onNameChange = { newQrName = it },
                    onDismiss = { 
                        showQrDialog = false 
                        pendingQrUri = null
                        newQrName = ""
                    },
                    onConfirm = {
                        if (newQrName.isNotBlank()) {
                            scope.launch {
                                val result = paymentManager.addQRCodeMethod(
                                    name = newQrName,
                                    qrCodeType = QRCodeType.UNLIMITED, // Default to Unlimited/Any Amount
                                    qrCodeUri = pendingQrUri!!
                                )
                                result.onSuccess { method ->
                                    // Update local state based on slot
                                    when (activeQrSlot) {
                                        1 -> qrCode1 = method
                                        2 -> qrCode2 = method
                                        3 -> qrCode3 = method
                                    }
                                    Toast.makeText(context, "QR Code Added", Toast.LENGTH_SHORT).show()
                                }.onFailure {
                                    Toast.makeText(context, "Failed to add QR", Toast.LENGTH_SHORT).show()
                                }
                                showQrDialog = false
                                pendingQrUri = null
                                newQrName = ""
                            }
                        }
                    }
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentMethodTopBar(onBackPressed: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF1a1a2e),
                        Color(0xFF16213e),
                        Color(0xFF0f3460)
                    )
                )
            )
    ) {
        Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackPressed) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            
            Icon(
                Icons.Outlined.Payment,
                contentDescription = "Payment",
                tint = Color(0xFF10B981),
                modifier = Modifier.size(28.dp)
            )
            
            Spacer(Modifier.width(12.dp))
            
            Text(
                "Payment Methods",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color.White
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
            )
            Text(
                subtitle,
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
            )
        }
    }
}

@Composable
fun VerifyByAgentCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E).copy(alpha = 0.9f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF6366F1).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.VerifiedUser,
                    contentDescription = "Verify",
                    tint = Color(0xFF6366F1),
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Verify Payment by AI Agent",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "AI Agent will verify payment screenshots",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )
            }
            
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF6366F1),
                    uncheckedThumbColor = Color(0xFF64748B),
                    uncheckedTrackColor = Color(0xFF334155)
                )
            )
        }
    }
}


@Composable
fun QRCodeSquareCard(
    slotNumber: Int,
    qrCode: PaymentMethod?,
    onAddClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(160.dp), // Fixed height for better layout
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E).copy(alpha = 0.9f)),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (qrCode != null) Color(0xFF10B981).copy(alpha = 0.3f) else Color(0xFF64748B).copy(alpha = 0.2f)
        )
    ) {
        if (qrCode == null) {
            // Empty slot - show add button
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onAddClick),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.AddPhotoAlternate,
                            contentDescription = "Add QR",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "QR $slotNumber",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Text(
                        "Tap to add",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        } else {
            // QR code added - show image and controls
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // QR Code Image
                if (qrCode.qrCodeImagePath != null) {
                    Image(
                        painter = rememberAsyncImagePainter(File(qrCode.qrCodeImagePath)),
                        contentDescription = "QR Code",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                
                Spacer(Modifier.height(6.dp))
                
                // Name
                Text(
                    qrCode.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                
                // Price or Type
                if (qrCode.fixedPrice != null) {
                    Text(
                        "₹${qrCode.fixedPrice}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF10B981)
                    )
                } else {
                    Text(
                        qrCode.qrCodeType?.displayName ?: "",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                
                Spacer(Modifier.height(6.dp))
                
                // Actions Row - centered
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Delete button
                    IconButton(
                        onClick = onRemoveClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFFF6B6B),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    Spacer(Modifier.width(16.dp))
                    
                    // Switch - larger and centered
                    Switch(
                        checked = qrCode.isEnabled,
                        onCheckedChange = onToggle,
                        modifier = Modifier.size(40.dp),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF10B981),
                            uncheckedThumbColor = Color(0xFF64748B),
                            uncheckedTrackColor = Color(0xFF334155)
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun QRCodeSlotCard(
    slotNumber: Int,
    qrCode: PaymentMethod?,
    onAddClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E).copy(alpha = 0.9f)),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (qrCode != null) Color(0xFF10B981).copy(alpha = 0.3f) else Color(0xFF64748B).copy(alpha = 0.2f)
        )
    ) {
        if (qrCode == null) {
            // Empty slot - show add button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAddClick)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.AddPhotoAlternate,
                            contentDescription = "Add QR",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "QR Code Slot $slotNumber",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    Text(
                        "Tap to add QR code",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        } else {
            // QR code added - show details
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    // QR Code Image
                    if (qrCode.qrCodeImagePath != null) {
                        Image(
                            painter = rememberAsyncImagePainter(File(qrCode.qrCodeImagePath)),
                            contentDescription = "QR Code",
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFF10B981).copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    
                    Spacer(Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            qrCode.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            qrCode.qrCodeType?.displayName ?: "",
                            fontSize = 13.sp,
                            color = Color(0xFF10B981)
                        )
                        if (qrCode.fixedPrice != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "₹${qrCode.fixedPrice}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                        if (qrCode.agentPriceField != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Agent Field: ${qrCode.agentPriceField}",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                    
                    Switch(
                        checked = qrCode.isEnabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF10B981),
                            uncheckedThumbColor = Color(0xFF64748B),
                            uncheckedTrackColor = Color(0xFF334155)
                        )
                    )
                }
                
                Spacer(Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onRemoveClick) {
                        Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(18.dp), tint = Color(0xFFFF6B6B))
                        Spacer(Modifier.width(4.dp))
                        Text("Remove", color = Color(0xFFFF6B6B))
                    }
                }
            }
        }
    }
}

@Composable
fun RazorpayCard(
    razorpayMethod: PaymentMethod?,
    onSetupClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E).copy(alpha = 0.9f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSetupClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF59E0B).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.CreditCard,
                    contentDescription = "Razorpay",
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Razorpay",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Coming Soon", // Changed from "Tap to setup" logic
                    fontSize = 13.sp,
                    color = Color(0xFFF59E0B)
                )
            }
            
            // Toggle removed for now or disabled
            /*
            if (razorpayMethod != null) {
                Switch(
                    checked = razorpayMethod.isEnabled,
                    onCheckedChange = onToggle,

                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFF59E0B),
                        uncheckedThumbColor = Color(0xFF64748B),
                        uncheckedTrackColor = Color(0xFF334155)
                    )
                )
            } else {
                Icon(
                    Icons.Outlined.ArrowForward,
                    contentDescription = "Setup",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(20.dp)
                )
            }
            */
        }
    }
}

@Composable
fun PayPalCard(
    paypalMethod: PaymentMethod?,
    onSetupClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E).copy(alpha = 0.9f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0070BA).copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSetupClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0070BA).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.AccountBalanceWallet,
                    contentDescription = "PayPal",
                    tint = Color(0xFF0070BA),
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "PayPal",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Coming Soon",
                    fontSize = 13.sp,
                    color = Color(0xFF0070BA)
                )
            }
            
            // Disabled toggle
            /*
            if (paypalMethod != null) {
                Switch(
                    checked = paypalMethod.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF0070BA),
                        uncheckedThumbColor = Color(0xFF64748B),
                        uncheckedTrackColor = Color(0xFF334155)
                    )
                )
            } else {
                Icon(
                    Icons.Outlined.ArrowForward,
                    contentDescription = "Setup",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(20.dp)
                )
            }
            */
        }
    }
}


@Composable
fun UPICard(
    upiMethod: PaymentMethod?,
    onAddClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E).copy(alpha = 0.9f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onAddClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF6366F1).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.AccountBalance,
                    contentDescription = "UPI",
                    tint = Color(0xFF6366F1),
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "UPI ID",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    upiMethod?.upiId ?: "Tap to add UPI ID",
                    fontSize = 13.sp,
                    color = if (upiMethod != null) Color(0xFF10B981) else Color(0xFF94A3B8)
                )
            }
            
            if (upiMethod != null) {
                Switch(
                    checked = upiMethod.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF6366F1),
                        uncheckedThumbColor = Color(0xFF64748B),
                        uncheckedTrackColor = Color(0xFF334155)
                    )
                )
            } else {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Add",
                    tint = Color(0xFF6366F1),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun CustomGroupCard(
    group: PaymentMethod,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E).copy(alpha = 0.9f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEC4899).copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFEC4899).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Folder,
                        contentDescription = "Group",
                        tint = Color(0xFFEC4899),
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        group.customGroupName ?: "Custom Group",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${group.customFields?.size ?: 0} fields",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                
                Switch(
                    checked = group.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFEC4899),
                        uncheckedThumbColor = Color(0xFF64748B),
                        uncheckedTrackColor = Color(0xFF334155)
                    )
                )
            }
            
            // Show fields
            if (group.customFields != null && group.customFields.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFF64748B).copy(alpha = 0.2f))
                Spacer(Modifier.height(12.dp))
                
                group.customFields.forEach { field ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            field.fieldName,
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            field.fieldValue,
                            fontSize = 13.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onEditClick) {
                    Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit")
                }
                TextButton(onClick = onDeleteClick) {
                    Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(18.dp), tint = Color(0xFFFF6B6B))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete", color = Color(0xFFFF6B6B))
                }
            }
        }
    }
}

@Composable
fun AddCustomGroupButton(
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E).copy(alpha = 0.9f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEC4899).copy(alpha = 0.3f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Add",
                    tint = Color(0xFFEC4899),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Add Custom Payment Method",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun AddUPIDialog(
    name: String,
    upiId: String,
    onNameChange: (String) -> Unit,
    onUpiIdChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Add UPI Payment",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Display Name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color(0xFF64748B),
                        focusedLabelColor = Color(0xFF6366F1),
                        unfocusedLabelColor = Color(0xFF64748B)
                    )
                )
                
                Spacer(Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = upiId,
                    onValueChange = onUpiIdChange,
                    label = { Text("UPI ID (e.g. name@upi)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color(0xFF64748B),
                        focusedLabelColor = Color(0xFF6366F1),
                        unfocusedLabelColor = Color(0xFF64748B)
                    )
                )
                
                Spacer(Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFF94A3B8))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}

@Composable
fun AddCustomGroupDialog(
    groupName: String,
    onGroupNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (List<CustomField>) -> Unit,
    isEditMode: Boolean = false,
    initialFields: List<CustomField> = emptyList()
) {
    var fields by remember { mutableStateOf(initialFields) }
    var newFieldName by remember { mutableStateOf("") }
    var newFieldValue by remember { mutableStateOf("") }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    if (isEditMode) "Edit Custom Payment Method" else "Add Custom Payment Method",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                
                Spacer(Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = groupName,
                    onValueChange = onGroupNameChange,
                    label = { Text("Group Name (e.g. Bank Transfer)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFEC4899),
                        unfocusedBorderColor = Color(0xFF64748B),
                        focusedLabelColor = Color(0xFFEC4899),
                        unfocusedLabelColor = Color(0xFF64748B)
                    )
                )
                
                Spacer(Modifier.height(16.dp))
                
                Text("Fields:", color = Color(0xFF94A3B8), fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                
                // Field Input list
                LazyColumn(
                    modifier = Modifier.heightIn(max = 150.dp)
                ) {
                    items(fields.size) { index ->
                        val field = fields[index]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(field.fieldName, color = Color(0xFF94A3B8), fontSize = 12.sp)
                                Text(field.fieldValue, color = Color.White, fontSize = 14.sp)
                            }
                            IconButton(onClick = { 
                                fields = fields.toMutableList().apply { removeAt(index) } 
                            }) {
                                Icon(Icons.Outlined.Delete, null, tint = Color(0xFFFF6B6B))
                            }
                        }
                        HorizontalDivider(color = Color(0xFF64748B).copy(alpha = 0.2f))
                    }
                }
                
                // Add field input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = newFieldName,
                            onValueChange = { newFieldName = it },
                            label = { Text("Label") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle.Default.copy(fontSize = 12.sp),
                             colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFEC4899),
                                unfocusedBorderColor = Color(0xFF64748B),
                                focusedLabelColor = Color(0xFFEC4899),
                                unfocusedLabelColor = Color(0xFF64748B)
                            )
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = newFieldValue,
                            onValueChange = { newFieldValue = it },
                            label = { Text("Value") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle.Default.copy(fontSize = 12.sp),
                             colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFEC4899),
                                unfocusedBorderColor = Color(0xFF64748B),
                                focusedLabelColor = Color(0xFFEC4899),
                                unfocusedLabelColor = Color(0xFF64748B)
                            )
                        )
                    }
                    IconButton(onClick = {
                        if (newFieldName.isNotBlank() && newFieldValue.isNotBlank()) {
                            fields = fields + CustomField(fieldName = newFieldName, fieldValue = newFieldValue)
                            newFieldName = ""
                            newFieldValue = ""
                        }
                    }) {
                        Icon(Icons.Filled.Add, null, tint = Color(0xFFEC4899))
                    }
                }
                
                Spacer(Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFF94A3B8))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(fields) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899))
                    ) {
                        Text("Save Group")
                    }
                }
            }
        }
    }
}

@Composable
fun AddQRDialog(
    imageUri: Uri,
    name: String,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Add QR Code",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(Modifier.height(16.dp))
                
                // Show Image Preview
                Image(
                    painter = rememberAsyncImagePainter(imageUri),
                    contentDescription = "QR Preview",
                    modifier = Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF10B981).copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                
                Spacer(Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Display Name (e.g. GPay - John)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF10B981),
                        unfocusedBorderColor = Color(0xFF64748B),
                        focusedLabelColor = Color(0xFF10B981),
                        unfocusedLabelColor = Color(0xFF64748B)
                    )
                )
                
                Spacer(Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFF94A3B8))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text("Save QR Code")
                    }
                }
            }
        }
    }
}
