package com.message.bulksend.aiagent.tools.woocommerce

import android.content.Context
import android.util.Log
import com.message.bulksend.aiagent.tools.reverseai.ReverseAIManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WooCommerce AI Integration
 *
 * Exposes WooCommerce functionality as AI Agent tools.
 * The AI can call these to:
 *   - Check if WooCommerce is set up
 *   - Fetch recent orders
 *   - Get details of a specific order
 *   - Get total order count and revenue summary
 */
class WooCommerceAIIntegration(private val context: Context) {

    private val tag = "WooCommerceAI"
    private val manager = WooCommerceManager(context)

    /**
     * Returns list of all registered tool names this integration provides.
     */
    fun getToolNames(): List<String> = listOf(
        "check_woocommerce_setup",
        "get_woocommerce_orders",
        "get_woocommerce_order_detail",
        "get_woocommerce_summary"
    )

    /**
     * Check if WooCommerce is connected.
     */
    suspend fun checkSetup(): String = withContext(Dispatchers.IO) {
        try {
            val config = manager.getConfig()
            if (config == null) {
                "WooCommerce is not connected. Open AI Agent -> WooCommerce Setup to connect it."
            } else {
                val ownerAssistantPhone = ReverseAIManager(context).ownerPhoneNumber.trim()
                "WooCommerce connected!\n" +
                    "Site: ${config.siteUrl}\n" +
                    "Webhook URL: ${config.webhookUrl}\n" +
                    "Last order at: ${config.lastOrderAt.ifBlank { "No orders yet" }}\n" +
                    "Owner Assistant Number: ${ownerAssistantPhone.ifBlank { "Not set in Reverse AI" }}"
            }
        } catch (e: Exception) {
            Log.e(tag, "checkSetup error: ${e.message}", e)
            "Error while checking WooCommerce status: ${e.message}"
        }
    }

    /**
     * Get recent WooCommerce orders (up to 10).
     */
    suspend fun getRecentOrders(limit: Int = 10): String = withContext(Dispatchers.IO) {
        try {
            val isSetup = manager.isSetupDone()
            if (!isSetup) {
                return@withContext "WooCommerce is not connected yet. Please complete setup first."
            }

            val orders = manager.fetchOrdersFlat().take(limit)
            if (orders.isEmpty()) {
                return@withContext "No WooCommerce orders received yet."
            }

            val sb = StringBuilder("Recent WooCommerce Orders (${orders.size}):\n\n")
            orders.forEachIndexed { i, order ->
                sb.append("${i + 1}. Order #${order.orderId}\n")
                sb.append("   Customer: ${order.customerName} (${order.customerPhone})\n")
                sb.append("   Items: ${order.items}\n")
                sb.append("   Total: ${order.currency} ${order.total}\n")
                sb.append("   Status: ${order.status}\n")
                sb.append("   Received: ${order.receivedAt.take(16).replace("T", " ")}\n\n")
            }
            sb.toString()
        } catch (e: Exception) {
            Log.e(tag, "getRecentOrders error: ${e.message}", e)
            "Error while fetching orders: ${e.message}"
        }
    }

    /**
     * Get details of a specific order by ID.
     */
    suspend fun getOrderDetail(orderId: String): String = withContext(Dispatchers.IO) {
        try {
            val orders = manager.fetchOrdersFlat()
            val order = orders.find { it.orderId == orderId }
                ?: return@withContext "Order #$orderId not found."

            """
Order #${order.orderId} details:
------------------------
Customer: ${order.customerName}
Email: ${order.customerEmail}
Phone: ${order.customerPhone}
Items: ${order.items}
Total: ${order.currency} ${order.total}
Status: ${order.status}
Site: ${order.siteUrl}
Received at: ${order.receivedAt.take(19).replace("T", " ")}
            """.trimIndent()
        } catch (e: Exception) {
            Log.e(tag, "getOrderDetail error: ${e.message}", e)
            "Error while fetching order detail: ${e.message}"
        }
    }

    /**
     * Get summary: total orders, total revenue.
     */
    suspend fun getOrdersSummary(): String = withContext(Dispatchers.IO) {
        try {
            val isSetup = manager.isSetupDone()
            if (!isSetup) {
                return@withContext "WooCommerce is not connected yet."
            }

            val orders = manager.fetchOrdersFlat()
            if (orders.isEmpty()) {
                return@withContext "No orders received yet."
            }

            val totalOrders = orders.size
            val totalRevenue = orders.sumOf { it.total.toDoubleOrNull() ?: 0.0 }
            val currency = orders.firstOrNull()?.currency ?: "INR"

            val statusBreakdown = orders.groupBy { it.status }
                .entries.joinToString("\n") { (status, list) -> "  $status: ${list.size} orders" }

            """
WooCommerce Summary:
--------------------
Total Orders: $totalOrders
Total Revenue: $currency ${String.format("%.2f", totalRevenue)}

Status breakdown:
$statusBreakdown

Last order: #${orders.first().orderId} by ${orders.first().customerName}
            """.trimIndent()
        } catch (e: Exception) {
            Log.e(tag, "getOrdersSummary error: ${e.message}", e)
            "Error while fetching summary: ${e.message}"
        }
    }

    /**
     * Main tool dispatcher called by AI Agent with tool name and args.
     */
    suspend fun executeTool(toolName: String, args: Map<String, String> = emptyMap()): String {
        return when (toolName) {
            "check_woocommerce_setup" -> checkSetup()
            "get_woocommerce_orders" -> {
                val limit = args["limit"]?.toIntOrNull() ?: 10
                getRecentOrders(limit)
            }
            "get_woocommerce_order_detail" -> {
                val orderId = args["orderId"] ?: args["order_id"] ?: ""
                if (orderId.isBlank()) "orderId is required." else getOrderDetail(orderId)
            }
            "get_woocommerce_summary" -> getOrdersSummary()
            else -> "Unknown WooCommerce tool: $toolName"
        }
    }
}
