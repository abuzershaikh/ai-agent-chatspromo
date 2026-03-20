package com.message.bulksend.autorespond.aireply.processors

import android.content.Context
import com.message.bulksend.autorespond.ai.ecommerce.OrderManager
import com.message.bulksend.autorespond.ai.settings.AIAgentAdvancedSettings

/**
 * Processor for ECOMMERCE template
 * Handles order management and address collection
 */
class EcommerceProcessor(private val context: Context) : TemplateProcessor {
    
    private val orderManager = OrderManager(context)
    private val advancedSettings = AIAgentAdvancedSettings(context)
    
    override fun getTemplateType(): String = "ECOMMERCE"
    
    override suspend fun generateContext(senderPhone: String): String {
        // Add ecommerce-specific context if needed
        return ""
    }
    
    override suspend fun processResponse(
        response: String,
        message: String,
        senderPhone: String,
        senderName: String
    ): String {
        var cleanResponse = response
        
        // Check for pending orders and address collection
        if (advancedSettings.enableEcommerceMode && advancedSettings.autoAskAddress) {
            try {
                if (orderManager.hasPendingOrder(senderPhone)) {
                    // User has pending order - check if they provided address
                    if (message.length > 20 && !message.contains("buy", ignoreCase = true)) {
                        // Likely an address - complete the order
                        val orderDetails = orderManager.getPendingOrderDetails(senderPhone)
                        if (orderDetails != null) {
                            orderManager.completeOrder(
                                phoneNumber = senderPhone,
                                address = message,
                                notes = "Order completed via AI Agent"
                            )
                            android.util.Log.d("EcommerceProcessor", "✅ Order completed for $senderPhone")
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("EcommerceProcessor", "❌ Order processing failed: ${e.message}")
            }
        }
        
        return cleanResponse
    }
}
