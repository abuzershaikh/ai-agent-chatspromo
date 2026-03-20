package com.message.bulksend.tools.invoicemaker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.tools.invoicemaker.database.InvoiceEntity
import com.message.bulksend.tools.invoicemaker.database.InvoiceRepository
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InvoiceMakerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            BulksendTestTheme {
                InvoiceMakerScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceMakerScreen(onBack: () -> Unit) {
    var currentScreen by remember { mutableStateOf<InvoiceScreen>(InvoiceScreen.Home) }
    
    when (currentScreen) {
        is InvoiceScreen.Home -> InvoiceHomeScreen(
            onBack = onBack,
            onCreateInvoice = { currentScreen = InvoiceScreen.Create() },
            onOpenSettings = { currentScreen = InvoiceScreen.Settings }
        )
        is InvoiceScreen.Create -> CreateInvoiceFullScreen(
            clientName = (currentScreen as InvoiceScreen.Create).clientName,
            clientPhone = (currentScreen as InvoiceScreen.Create).clientPhone,
            onBack = { currentScreen = InvoiceScreen.Home },
            onInvoiceSaved = { currentScreen = InvoiceScreen.Home },
            onPreview = { invoice -> currentScreen = InvoiceScreen.Preview(invoice) }
        )
        is InvoiceScreen.Preview -> InvoicePreviewScreen(
            invoice = (currentScreen as InvoiceScreen.Preview).invoice,
            onBack = { currentScreen = InvoiceScreen.Home },
            onSave = { updatedInvoice -> 
                // Save the invoice and go back to home
                currentScreen = InvoiceScreen.Home 
            }
        )
        is InvoiceScreen.Settings -> InvoiceSettingsFullScreen(
            onBack = { currentScreen = InvoiceScreen.Home }
        )
    }
}

sealed class InvoiceScreen {
    object Home : InvoiceScreen()
    data class Create(val clientName: String = "", val clientPhone: String = "") : InvoiceScreen()
    data class Preview(val invoice: InvoiceDataTool) : InvoiceScreen()
    object Settings : InvoiceScreen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceHomeScreen(
    onBack: () -> Unit,
    onCreateInvoice: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { InvoiceSettingsManagerTool(context) }
    val repository = remember { InvoiceRepository(context) }
    val settings = remember { settingsManager.getSettings() }
    val currencySymbol = settings.currencySymbol
    val scope = rememberCoroutineScope()
    
    val invoices by repository.allInvoices.collectAsState(initial = emptyList())
    val totalCount by repository.totalInvoiceCount.collectAsState(initial = 0)
    val thisMonthAmount by repository.getThisMonthAmount().collectAsState(initial = 0.0)
    
    val currencyFormat = remember { DecimalFormat("#,##0") }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    
    var showDeleteDialog by remember { mutableStateOf<InvoiceEntity?>(null) }
    
    val primaryColor = Color(0xFF6366F1)
    val secondaryColor = Color(0xFF8B5CF6)
    
    Scaffold(
        containerColor = Color(0xFFF8FAFC),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateInvoice,
                containerColor = primaryColor,
                contentColor = Color.White,
                modifier = Modifier.shadow(12.dp, RoundedCornerShape(16.dp))
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Create Invoice", fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // Header
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(listOf(primaryColor, secondaryColor, Color(0xFFA855F7))),
                            shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                        )
                ) {
                    Box(modifier = Modifier.size(150.dp).offset(x = (-30).dp, y = (-30).dp).background(Color.White.copy(alpha = 0.1f), CircleShape))
                    Box(modifier = Modifier.size(100.dp).align(Alignment.TopEnd).offset(x = 30.dp, y = 50.dp).background(Color.White.copy(alpha = 0.08f), CircleShape))
                    
                    Column(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(20.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack, modifier = Modifier.size(44.dp).background(Color.White.copy(alpha = 0.2f), CircleShape)) {
                                Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                            }
                            IconButton(onClick = onOpenSettings, modifier = Modifier.size(44.dp).background(Color.White.copy(alpha = 0.2f), CircleShape)) {
                                Icon(Icons.Default.Settings, null, tint = Color.White)
                            }
                        }
                        
                        Spacer(Modifier.height(24.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(56.dp).background(Color.White, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Receipt, null, tint = primaryColor, modifier = Modifier.size(32.dp))
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("Invoice Maker", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Create professional invoices", fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f))
                            }
                        }
                        
                        Spacer(Modifier.height(24.dp))
                        
                        // Stats Row with real data
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MiniStatCard(title = "Total", value = totalCount.toString(), icon = Icons.Default.Receipt, modifier = Modifier.weight(1f))
                            MiniStatCard(title = "This Month", value = "$currencySymbol${currencyFormat.format(thisMonthAmount)}", icon = Icons.Default.TrendingUp, modifier = Modifier.weight(1f))
                        }
                        
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            
            // Quick Actions
            item {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Quick Actions", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionCard(title = "New Invoice", subtitle = "Create now", icon = Icons.Default.Add, gradient = listOf(Color(0xFF10B981), Color(0xFF059669)), onClick = onCreateInvoice, modifier = Modifier.weight(1f))
                        ActionCard(title = "Settings", subtitle = "Business info", icon = Icons.Default.Settings, gradient = listOf(primaryColor, secondaryColor), onClick = onOpenSettings, modifier = Modifier.weight(1f))
                    }
                }
            }
            
            // Recent Invoices Section
            item {
                Text("Recent Invoices", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B), modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(12.dp))
            }
            
            if (invoices.isEmpty()) {
                // Empty State
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier.size(80.dp).background(Brush.linearGradient(listOf(primaryColor.copy(alpha = 0.1f), secondaryColor.copy(alpha = 0.1f))), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Receipt, null, tint = primaryColor, modifier = Modifier.size(40.dp))
                            }
                            Spacer(Modifier.height(20.dp))
                            Text("No invoices yet", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                            Spacer(Modifier.height(8.dp))
                            Text("Create your first invoice to get started", fontSize = 14.sp, color = Color(0xFF64748B))
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = onCreateInvoice, colors = ButtonDefaults.buttonColors(containerColor = primaryColor), shape = RoundedCornerShape(12.dp), modifier = Modifier.height(48.dp)) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Create Invoice", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            } else {
                // Invoice List
                items(invoices, key = { it.id }) { invoice ->
                    InvoiceListItem(
                        invoice = invoice,
                        currencySymbol = invoice.currencySymbol,
                        dateFormat = dateFormat,
                        currencyFormat = currencyFormat,
                        onDelete = { showDeleteDialog = invoice },
                        onStatusChange = { newStatus ->
                            scope.launch {
                                repository.updateStatus(invoice.id, newStatus)
                            }
                        },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )
                }
            }
            
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
    
    // Delete Confirmation Dialog
    showDeleteDialog?.let { invoice ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Invoice?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete invoice ${invoice.invoiceNumber}? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repository.deleteInvoice(invoice.id)
                            showDeleteDialog = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun InvoiceListItem(
    invoice: InvoiceEntity,
    currencySymbol: String,
    dateFormat: SimpleDateFormat,
    currencyFormat: DecimalFormat,
    onDelete: () -> Unit,
    onStatusChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (invoice.status) {
        "PAID" -> Color(0xFF10B981)  // Green for PAID
        "SENT" -> Color(0xFF3B82F6)  // Blue for SENT
        "CANCELLED" -> Color(0xFFEF4444)  // Red for CANCELLED
        else -> Color(0xFFF59E0B)  // Orange for CREATED/PENDING
    }
    
    var showStatusDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Invoice Icon
            Box(
                modifier = Modifier.size(48.dp).background(Color(0xFF6366F1).copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Receipt, null, tint = Color(0xFF6366F1), modifier = Modifier.size(24.dp))
            }
            
            Spacer(Modifier.width(12.dp))
            
            // Invoice Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(invoice.invoiceNumber, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = statusColor.copy(alpha = 0.1f), 
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.clickable { showStatusDialog = true }
                    ) {
                        Text(
                            invoice.status, 
                            fontSize = 10.sp, 
                            fontWeight = FontWeight.Bold, 
                            color = statusColor, 
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(invoice.clientName, fontSize = 14.sp, color = Color(0xFF64748B), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(dateFormat.format(Date(invoice.invoiceDate)), fontSize = 12.sp, color = Color(0xFF94A3B8))
            }
            
            // Amount & Delete
            Column(horizontalAlignment = Alignment.End) {
                Text("$currencySymbol${currencyFormat.format(invoice.totalAmount)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                Spacer(Modifier.height(4.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
    
    // Status Change Dialog
    if (showStatusDialog) {
        StatusChangeDialog(
            currentStatus = invoice.status,
            onStatusSelected = { newStatus ->
                onStatusChange(newStatus)
                showStatusDialog = false
            },
            onDismiss = { showStatusDialog = false }
        )
    }
}

@Composable
fun MiniStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(title, fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradient: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(120.dp)
            .shadow(8.dp, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(gradient))
        ) {
            // Decorative circle
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 20.dp, y = (-20).dp)
                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
            )
            
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Column {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@Composable
fun FeatureCard(
    icon: ImageVector,
    title: String,
    description: String,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                Text(description, fontSize = 13.sp, color = Color(0xFF64748B))
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFCBD5E1))
        }
    }
}

@Composable
fun StatusChangeDialog(
    currentStatus: String,
    onStatusSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val statusOptions = listOf(
        "CREATED" to Color(0xFFF59E0B),  // Orange
        "SENT" to Color(0xFF3B82F6),     // Blue
        "PAID" to Color(0xFF10B981),     // Green
        "CANCELLED" to Color(0xFFEF4444) // Red
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Change Status", 
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            ) 
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Select new status:",
                    fontSize = 14.sp,
                    color = Color(0xFF64748B)
                )
                statusOptions.forEach { (status, color) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStatusSelected(status) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (status == currentStatus) 
                                color.copy(alpha = 0.1f) 
                            else 
                                Color(0xFFF8FAFC)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(color, CircleShape)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                status,
                                fontSize = 16.sp,
                                fontWeight = if (status == currentStatus) FontWeight.Bold else FontWeight.Medium,
                                color = if (status == currentStatus) color else Color(0xFF1E293B)
                            )
                            if (status == currentStatus) {
                                Spacer(Modifier.weight(1f))
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = color,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF64748B))
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp)
    )
}
