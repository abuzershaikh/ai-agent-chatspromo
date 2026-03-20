package com.message.bulksend.aiagent.tools.woocommerce

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.google.firebase.firestore.ListenerRegistration
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.launch

/**
 * WooCommerce Orders Activity
 *
 * Displays real-time WooCommerce orders received via webhook.
 * Uses Firestore snapshot listener for live updates.
 */
class WooCommerceOrdersActivity : ComponentActivity() {

    private val tag = "WooCommerceOrders"

    private lateinit var manager: WooCommerceManager
    private var ordersListener: ListenerRegistration? = null

    private var isLoading by mutableStateOf(true)
    private var orders by mutableStateOf<List<WooCommerceOrder>>(emptyList())
    private var emptyMessage by mutableStateOf("No orders yet. Waiting for WooCommerce webhooks...")
    private var isSetupDone by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        manager = WooCommerceManager(this)

        setContent {
            BulksendTestTheme {
                WooCommerceOrdersScreen(
                    isLoading = isLoading,
                    orders = orders,
                    emptyMessage = emptyMessage,
                    isSetupDone = isSetupDone,
                    onBackPressed = { finish() },
                    onSetupClick = {
                        startActivity(Intent(this, WooCommerceSetupActivity::class.java))
                    }
                )
            }
        }

        checkSetupAndLoad()
    }

    private fun checkSetupAndLoad() {
        isLoading = true

        lifecycleScope.launch {
            val setupDone = manager.isSetupDone()
            isSetupDone = setupDone

            if (!setupDone) {
                isLoading = false
                orders = emptyList()
                emptyMessage = "WooCommerce not connected. Tap Setup to configure."
                ordersListener?.remove()
                ordersListener = null
                return@launch
            }

            startOrdersListener()

            try {
                val initialOrders = manager.fetchOrdersFlat()
                orders = initialOrders
                emptyMessage = if (initialOrders.isEmpty()) {
                    "No orders yet. Waiting for WooCommerce webhooks..."
                } else {
                    ""
                }
            } catch (e: Exception) {
                Log.e(tag, "Load orders error: ${e.message}", e)
                Toast.makeText(this@WooCommerceOrdersActivity, "Error loading orders", Toast.LENGTH_SHORT).show()
                emptyMessage = "Error loading orders. Please try again."
            } finally {
                isLoading = false
            }
        }
    }

    private fun startOrdersListener() {
        ordersListener?.remove()
        ordersListener = manager.listenToOrders { newOrders ->
            isLoading = false
            orders = newOrders
            if (newOrders.isEmpty()) {
                emptyMessage = "No orders yet. Waiting for WooCommerce webhooks..."
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ordersListener?.remove()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WooCommerceOrdersScreen(
    isLoading: Boolean,
    orders: List<WooCommerceOrder>,
    emptyMessage: String,
    isSetupDone: Boolean,
    onBackPressed: () -> Unit,
    onSetupClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WooCommerce Orders", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onSetupClick) {
                        Icon(Icons.Outlined.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Setup")
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            orders.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                    if (!isSetupDone) {
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = onSetupClick) {
                            Text("Open Setup")
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = orders,
                        key = { "${it.orderId}_${it.receivedAt}" }
                    ) { order ->
                        WooOrderCard(order = order)
                    }
                }
            }
        }
    }
}

@Composable
private fun WooOrderCard(order: WooCommerceOrder) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "#${order.orderId}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${order.customerName} - ${order.customerPhone}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = order.items.ifBlank { "No items" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${order.currency} ${order.total}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = order.status.uppercase(),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Text(
                text = formatOrderTime(order.receivedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
        }
    }
}

private fun formatOrderTime(receivedAt: String): String {
    return if (receivedAt.isBlank()) "" else receivedAt.take(16).replace("T", " ")
}
