package com.message.bulksend.aiagent.tools.ecommerce

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import com.google.firebase.auth.FirebaseAuth

class RazorpayConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BulksendTestTheme {
                RazorpayConfigScreen(onBackPressed = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RazorpayConfigScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val paymentManager = remember { RazorPaymentManager(context) }

    // Credentials State
    var keyId by remember { mutableStateOf(paymentManager.getRazorpayKeyId() ?: "") }
    var keySecret by remember { mutableStateOf(paymentManager.getRazorpaySecret() ?: "") }
    var email by remember { mutableStateOf(paymentManager.getUserEmail() ?: "") }
    
    // UI State
    var isCredentialsExpanded by remember { mutableStateOf(keyId.isBlank()) }
    var isPostPaymentExpanded by remember { mutableStateOf(false) }
    var webhookUrl by remember { mutableStateOf(paymentManager.getWebhookUrl() ?: "") }
    var isLoading by remember { mutableStateOf(false) }

    // Redirect Settings State
    var redirectMode by remember { mutableStateOf(paymentManager.getRedirectMode()) }
    var customUrl by remember { mutableStateOf(paymentManager.getCustomRedirectUrl()) }
    var whatsappNumber by remember { mutableStateOf(paymentManager.getWhatsAppNumber()) }
    var whatsappMessage by remember { mutableStateOf(paymentManager.getWhatsAppMessage()) }
    
    // Webhook Settings State
    var isWebhookExpanded by remember { mutableStateOf(false) }

    // Auto-fetch email
    LaunchedEffect(Unit) {
        if (email.isBlank()) {
            val firebaseEmail = FirebaseAuth.getInstance().currentUser?.email
            if (!firebaseEmail.isNullOrBlank()) email = firebaseEmail
        }
    }
    
    val workerUrl = "https://razorpay-webhook-worker.aawuazer.workers.dev" 
    
    // Payment links state
    val paymentLinks = remember { mutableStateOf<List<RazorPaymentManager.PaymentLinkInfo>>(emptyList()) }
    
    LaunchedEffect(email) {
        if (email.isNotBlank()) {
            paymentManager.getPaymentLinksFlow(email).collect { links ->
                paymentLinks.value = links
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Razorpay Setup", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F3460))
            )
        },
        containerColor = Color(0xFF0F0C29)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // --- SECTION 1: CREDENTIALS (Collapsible) ---
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().clickable { isCredentialsExpanded = !isCredentialsExpanded }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Save, contentDescription = null, tint = Color(0xFF10B981))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "API Credentials",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                if (isCredentialsExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color.Gray
                            )
                        }

                        if (isCredentialsExpanded) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Enter your Razorpay API Keys to enable payment links.",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(8.dp))

                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("Merchant Email (ID)") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = textFieldColors()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = keyId,
                                onValueChange = { keyId = it },
                                label = { Text("Key ID") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = textFieldColors()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = keySecret,
                                onValueChange = { keySecret = it },
                                label = { Text("Key Secret") },
                                modifier = Modifier.fillMaxWidth(),
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                colors = textFieldColors()
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    if (keyId.isBlank() || keySecret.isBlank() || email.isBlank()) {
                                        Toast.makeText(context, "Fill all fields", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isLoading = true
                                    scope.launch {
                                        paymentManager.saveRazorpayCredentials(keyId, keySecret)
                                        paymentManager.saveUserEmail(email)
                                        val success = paymentManager.registerWithWorker(workerUrl, email, keyId, keySecret)
                                        isLoading = false
                                        if (success) {
                                            Toast.makeText(context, "Registered!", Toast.LENGTH_SHORT).show()
                                            isCredentialsExpanded = false
                                        } else {
                                            Toast.makeText(context, "Registration Failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                enabled = !isLoading
                            ) {
                                if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                else Text("Save & Register")
                            }
                        }
                    }
                }
            }

            // --- SECTION 2: REDIRECT SETTINGS ---
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isPostPaymentExpanded = !isPostPaymentExpanded }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Post-Payment Action", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("Where should the user go after paying?", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            }
                            Icon(
                                imageVector = if (isPostPaymentExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Expand/Collapse",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        if (isPostPaymentExpanded) {
                            Spacer(Modifier.height(12.dp))

                            RedirectOption(
                                selected = redirectMode == "THANK_YOU",
                                onClick = { redirectMode = "THANK_YOU" },
                                text = "Thank You Page (Default)"
                            )
                            RedirectOption(
                                selected = redirectMode == "URL",
                                onClick = { redirectMode = "URL" },
                                text = "Redirect to Website"
                            )
                            RedirectOption(
                                selected = redirectMode == "WHATSAPP",
                                onClick = { redirectMode = "WHATSAPP" },
                                text = "Redirect to WhatsApp"
                            )

                            Spacer(Modifier.height(16.dp))

                            if (redirectMode == "URL") {
                                OutlinedTextField(
                                    value = customUrl,
                                    onValueChange = { customUrl = it },
                                    label = { Text("Enter Website URL (https://...)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = textFieldColors()
                                )
                            } else if (redirectMode == "WHATSAPP") {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "WhatsApp Number",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "Info",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Add your number to redirect user after successful payment",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            color = Color(0xFF1E293B),
                                            shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp),
                                            modifier = Modifier.height(56.dp)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(horizontal = 12.dp)
                                            ) {
                                                Text(
                                                    "+91",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp
                                                )
                                            }
                                        }
                                        
                                        OutlinedTextField(
                                            value = whatsappNumber,
                                            onValueChange = { 
                                                if (it.all { char -> char.isDigit() } && it.length <= 10) {
                                                    whatsappNumber = it
                                                }
                                            },
                                            placeholder = { Text("9876543210", color = Color(0xFF64748B)) },
                                            modifier = Modifier.weight(1f),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = Color(0xFF10B981),
                                                unfocusedBorderColor = Color(0xFF334155),
                                                cursorColor = Color(0xFF10B981)
                                            ),
                                            shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                                        )
                                    }
                                }
                                
                                Spacer(Modifier.height(16.dp))
                                
                                OutlinedTextField(
                                    value = whatsappMessage,
                                    onValueChange = { whatsappMessage = it },
                                    label = { Text("Pre-filled Message") },
                                    placeholder = { Text("Payment successful! 🎉", color = Color(0xFF64748B)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedLabelColor = Color(0xFF10B981),
                                        unfocusedLabelColor = Color(0xFF94A3B8),
                                        focusedBorderColor = Color(0xFF10B981),
                                        unfocusedBorderColor = Color(0xFF334155)
                                    )
                                )
                            }

                            if (isPostPaymentExpanded && redirectMode != "THANK_YOU") {
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        paymentManager.saveRedirectSettings(redirectMode, customUrl, whatsappNumber, whatsappMessage)
                                        Toast.makeText(context, "Redirect Settings Saved", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                                ) {
                                    Text("Save Redirect Settings")
                                }
                            }
                        }
                    }
                }
            }

            // --- SECTION 3: WEBHOOK SETUP ---
            if (keyId.isNotBlank() && keySecret.isNotBlank()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isWebhookExpanded = !isWebhookExpanded }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Webhook Setup", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text("Configure payment notifications", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                }
                                Icon(
                                    imageVector = if (isWebhookExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Expand/Collapse",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            if (isWebhookExpanded) {
                                Spacer(Modifier.height(16.dp))

                                if (webhookUrl.isNotBlank()) {
                                    Text(
                                        "Your Webhook URL",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    
                                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F3460)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(webhookUrl))
                                                Toast.makeText(context, "Webhook URL Copied!", Toast.LENGTH_SHORT).show()
                                            }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp)
                                        ) {
                                            Text(
                                                webhookUrl,
                                                color = Color(0xFF10B981),
                                                fontSize = 12.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "COPY",
                                                color = Color(0xFF10B981),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    
                                    Spacer(Modifier.height(16.dp))
                                }

                                Text(
                                    "Setup Instructions",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(Modifier.height(8.dp))

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        WebhookStep(
                                            number = "1",
                                            title = "Go to Razorpay Dashboard",
                                            description = "Log in to your Razorpay account and navigate to Settings → Webhooks"
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        WebhookStep(
                                            number = "2",
                                            title = "Add Webhook URL",
                                            description = "Click 'Add New Webhook' and paste the URL above"
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        WebhookStep(
                                            number = "3",
                                            title = "Select Events",
                                            description = "Select all payment link events and payment.authorized event"
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        WebhookStep(
                                            number = "4",
                                            title = "Add Webhook Secret",
                                            description = "Copy the webhook secret and save it securely"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Payment History Section
            if (email.isNotBlank()) {
                item {
                    Text(
                        "Payment History",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (paymentLinks.value.isEmpty()) {
                    item {
                        Text(
                            "No payment links generated yet.",
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp
                        )
                    }
                } else {
                    items(paymentLinks.value) { link ->
                        PaymentHistoryItem(link)
                    }
                }
            }
        }
    }
}

@Composable
fun RedirectOption(selected: Boolean, onClick: () -> Unit, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF10B981), unselectedColor = Color.Gray)
        )
        Text(text, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
fun WebhookStep(number: String, title: String, description: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = Color(0xFF0F3460),
            shape = RoundedCornerShape(50),
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    number,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = Color(0xFF0F3460),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                color = Color(0xFF64748B),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedLabelColor = Color(0xFF10B981),
    unfocusedLabelColor = Color(0xFF94A3B8),
    focusedBorderColor = Color(0xFF10B981),
    unfocusedBorderColor = Color(0xFF334155)
)

@Composable
fun PaymentHistoryItem(link: RazorPaymentManager.PaymentLinkInfo) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "₹${link.amount.toInt()}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                
                val statusColor = when(link.status.lowercase()) {
                    "paid" -> Color(0xFF10B981)
                    "created" -> Color(0xFFF59E0B)
                    "expired" -> Color(0xFFEF4444)
                    "failed" -> Color(0xFFEF4444)
                    else -> Color.Gray
                }
                
                Text(
                    text = if (link.status.equals("created", true)) "PENDING" else link.status.uppercase(),
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = link.description,
                color = Color(0xFFCBD5E1),
                fontSize = 14.sp
            )

            if (!link.customerName.isNullOrBlank() || !link.customerContact.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(8.dp))
                
                if (!link.customerName.isNullOrBlank()) {
                    Text(
                        text = "Name: ${link.customerName}",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
                if (!link.customerContact.isNullOrBlank()) {
                    Text(
                        text = "Phone: ${link.customerContact}",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Created: ${link.createdAt}",
                color = Color(0xFF64748B),
                fontSize = 10.sp
            )
        }
    }
}
