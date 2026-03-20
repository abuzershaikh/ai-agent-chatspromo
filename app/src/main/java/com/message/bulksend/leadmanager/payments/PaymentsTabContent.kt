package com.message.bulksend.leadmanager.payments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.leadmanager.payments.database.*
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentsTabContent(
    leadId: String,
    leadName: String = "",
    leadPhone: String = ""
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val paymentsManager = remember { PaymentsManager(context) }
    
    val payments by paymentsManager.getPaymentsForLead(leadId).collectAsState(initial = emptyList())
    
    var totalReceived by remember { mutableStateOf(0.0) }
    var totalGiven by remember { mutableStateOf(0.0) }
    var showAddPaymentDialog by remember { mutableStateOf(false) }
    var paymentTypeToAdd by remember { mutableStateOf(PaymentType.RECEIVED) }
    
    LaunchedEffect(payments) {
        totalReceived = paymentsManager.getTotalReceived(leadId)
        totalGiven = paymentsManager.getTotalGiven(leadId)
    }
    
    val currencyFormat = remember { DecimalFormat("#,##0") }
    
    // Add Payment Dialog
    if (showAddPaymentDialog) {
        AddPaymentDialog(
            paymentType = paymentTypeToAdd,
            onDismiss = { showAddPaymentDialog = false },
            onSave = { amount, description ->
                coroutineScope.launch {
                    paymentsManager.addPayment(leadId, amount, paymentTypeToAdd, description)
                }
                showAddPaymentDialog = false
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary Cards
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Received Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("₹ ${currencyFormat.format(totalReceived)}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Received", fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f))
                    }
                }
                // Given Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("₹ ${currencyFormat.format(totalGiven)}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Given", fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f))
                    }
                }
            }
        }
        
        // Balance Card
        item {
            val balance = totalReceived - totalGiven
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Balance", fontSize = 16.sp, color = Color(0xFF94A3B8))
                    Text(
                        "${if (balance >= 0) "+" else ""}₹ ${currencyFormat.format(balance)}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (balance >= 0) Color(0xFF10B981) else Color(0xFFEF4444)
                    )
                }
            }
        }
        
        // Payments List Header
        item {
            Text("PAYMENTS (${payments.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
        }
        
        if (payments.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AccountBalanceWallet, null, tint = Color(0xFF64748B), modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No payments yet", color = Color(0xFF94A3B8))
                        Spacer(Modifier.height(4.dp))
                        Text("Add received or given payments", fontSize = 12.sp, color = Color(0xFF64748B))
                    }
                }
            }
        } else {
            items(payments, key = { it.id }) { payment ->
                PaymentCard(
                    payment = payment,
                    onDelete = {
                        coroutineScope.launch { paymentsManager.deletePayment(payment.id) }
                    }
                )
            }
        }
        
        // Add Buttons
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { paymentTypeToAdd = PaymentType.RECEIVED; showAddPaymentDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("RECEIVED", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { paymentTypeToAdd = PaymentType.GIVEN; showAddPaymentDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Remove, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("GIVEN", fontWeight = FontWeight.Bold)
                }
            }
        }
        
        item { Spacer(Modifier.height(80.dp)) }
    }
}


@Composable
fun PaymentCard(payment: PaymentEntity, onDelete: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mma", Locale.getDefault()) }
    val currencyFormat = remember { DecimalFormat("#,##0") }
    val isReceived = payment.paymentType == PaymentType.RECEIVED
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isReceived) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f)
                ) {
                    Text(
                        "${if (isReceived) "+" else "-"} ₹ ${currencyFormat.format(payment.amount)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isReceived) Color(0xFF10B981) else Color(0xFFEF4444),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                if (payment.description.isNotEmpty()) {
                    Text(payment.description, fontSize = 13.sp, color = Color.White, modifier = Modifier.padding(top = 4.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Default.Schedule, null, tint = Color(0xFF64748B), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(dateFormat.format(Date(payment.timestamp)), fontSize = 11.sp, color = Color(0xFF64748B))
                }
            }
            Row {
                IconButton(onClick = { /* Share */ }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Share, null, tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, null, tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPaymentDialog(
    paymentType: PaymentType,
    onDismiss: () -> Unit,
    onSave: (Double, String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val isReceived = paymentType == PaymentType.RECEIVED
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isReceived) Icons.Default.Add else Icons.Default.Remove,
                    null,
                    tint = if (isReceived) Color(0xFF10B981) else Color(0xFFEF4444)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isReceived) "Add Received Payment" else "Add Given Payment",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) amount = it },
                    label = { Text("Amount (₹)", color = Color(0xFF94A3B8)) },
                    leadingIcon = { Text("₹", color = Color(0xFF64748B), fontWeight = FontWeight.Bold) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isReceived) Color(0xFF10B981) else Color(0xFFEF4444),
                        unfocusedBorderColor = Color(0xFF64748B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = if (isReceived) Color(0xFF10B981) else Color(0xFFEF4444)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isReceived) Color(0xFF10B981) else Color(0xFFEF4444),
                        unfocusedBorderColor = Color(0xFF64748B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = if (isReceived) Color(0xFF10B981) else Color(0xFFEF4444)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amountValue = amount.toDoubleOrNull() ?: 0.0
                    if (amountValue > 0) onSave(amountValue, description)
                },
                enabled = amount.isNotEmpty() && (amount.toDoubleOrNull() ?: 0.0) > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isReceived) Color(0xFF10B981) else Color(0xFFEF4444)
                ),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF94A3B8)) }
        },
        containerColor = Color(0xFF1a1a2e)
    )
}
