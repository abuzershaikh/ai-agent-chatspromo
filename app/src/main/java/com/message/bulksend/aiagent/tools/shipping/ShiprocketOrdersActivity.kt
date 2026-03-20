package com.message.bulksend.aiagent.tools.shipping

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.ui.theme.BulksendTestTheme

/**
 * Shows Shiprocket orders saved in Firestore.
 */
class ShiprocketOrdersActivity : ComponentActivity() {

    private lateinit var manager: ShiprocketManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        manager = ShiprocketManager(this)

        setContent {
            BulksendTestTheme {
                ShiprocketOrdersScreen(
                    manager = manager,
                    onBackPressed = { finish() },
                    onSetupClick = { startActivity(Intent(this, ShiprocketSetupActivity::class.java)) }
                )
            }
        }
    }
}

@Composable
fun ShiprocketOrdersScreen(
    manager: ShiprocketManager,
    onBackPressed: () -> Unit,
    onSetupClick: () -> Unit
) {
    var orders by remember { mutableStateOf<List<ShiprocketOrderLog>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSetupDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isSetupDone = manager.isSetupDone()
        orders = manager.fetchMyOrders()
        isLoading = false
    }

    val bg = Brush.verticalGradient(listOf(Color(0xFF0F0C29), Color(0xFF302B63), Color(0xFF24243E)))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackPressed) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                }
                Icon(
                    Icons.Outlined.LocalShipping,
                    null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Shiprocket Orders", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                    Text("Firestore -> chatspromoweb", fontSize = 11.sp, color = Color(0xFF94A3B8))
                }
                IconButton(onClick = onSetupClick) {
                    Icon(Icons.Outlined.Settings, null, tint = Color(0xFF94A3B8))
                }
            }

            if (!isSetupDone) {
                Spacer(Modifier.height(40.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Outlined.LinkOff, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Shiprocket Not Connected", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Connect your dedicated Shiprocket API user first.",
                        fontSize = 14.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onSetupClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Set Up Shiprocket")
                    }
                }
                return@Column
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF10B981))
                }
                return@Column
            }

            if (orders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Inbox, null, tint = Color(0xFF64748B), modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No orders yet", fontSize = 16.sp, color = Color(0xFF94A3B8))
                        Text("Place your first order with AI Agent", fontSize = 13.sp, color = Color(0xFF64748B))
                    }
                }
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(orders) { order ->
                    ShiprocketOrderCard(order = order)
                }
            }
        }
    }
}

@Composable
fun ShiprocketOrderCard(order: ShiprocketOrderLog) {
    val statusColor = when {
        order.status.contains("Delivered", ignoreCase = true) -> Color(0xFF10B981)
        order.status.contains("Transit", ignoreCase = true) -> Color(0xFF3B82F6)
        order.status.contains("Out for", ignoreCase = true) -> Color(0xFFF59E0B)
        order.status.contains("Cancel", ignoreCase = true) -> Color(0xFFEF4444)
        else -> Color(0xFF94A3B8)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E).copy(alpha = 0.9f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        order.customerName.ifEmpty { "Unknown" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    Text(
                        order.customerPhone,
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(order.status, fontSize = 11.sp, color = statusColor, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.QrCodeScanner, null, tint = Color(0xFF6366F1), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("AWB: ${order.awb.ifEmpty { "Pending" }}", fontSize = 12.sp, color = Color(0xFF94A3B8))
            }

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Inventory2, null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    order.items.ifEmpty { "No items" },
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Rs.${order.amount}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF10B981)
                )
                Text(
                    order.orderPlacedAt.ifBlank { order.timestamp },
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}
