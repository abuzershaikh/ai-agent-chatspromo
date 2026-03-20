package com.message.bulksend.autorespond.aireply

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Product(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val price: String,
    val description: String = ""
)

data class BusinessProfile(
    val businessName: String = "",
    val ownerName: String = "",
    val businessType: String = "",
    val description: String = "",
    val products: List<Product> = emptyList(),
    val customInstructions: String = "",
    val customText: String = "",
    val useCustomTextOnly: Boolean = false
)

class AIBusinessDataManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("ai_business_data", Context.MODE_PRIVATE)
    
    fun saveBusinessProfile(provider: AIProvider, profile: BusinessProfile) {
        val json = JSONObject().apply {
            put("businessName", profile.businessName)
            put("ownerName", profile.ownerName)
            put("businessType", profile.businessType)
            put("description", profile.description)
            put("customInstructions", profile.customInstructions)
            put("customText", profile.customText)
            put("useCustomTextOnly", profile.useCustomTextOnly)
            
            val productsArray = JSONArray()
            profile.products.forEach { product ->
                productsArray.put(JSONObject().apply {
                    put("id", product.id)
                    put("name", product.name)
                    put("price", product.price)
                    put("description", product.description)
                })
            }
            put("products", productsArray)
        }
        
        prefs.edit().putString("business_${provider.name}", json.toString()).apply()
    }
    
    fun getBusinessProfile(provider: AIProvider): BusinessProfile {
        val jsonString = prefs.getString("business_${provider.name}", null) ?: return BusinessProfile()
        
        return try {
            val json = JSONObject(jsonString)
            val products = mutableListOf<Product>()
            
            if (json.has("products")) {
                val productsArray = json.getJSONArray("products")
                for (i in 0 until productsArray.length()) {
                    val productJson = productsArray.getJSONObject(i)
                    products.add(
                        Product(
                            id = productJson.getString("id"),
                            name = productJson.getString("name"),
                            price = productJson.getString("price"),
                            description = productJson.optString("description", "")
                        )
                    )
                }
            }
            
            BusinessProfile(
                businessName = json.optString("businessName", ""),
                ownerName = json.optString("ownerName", ""),
                businessType = json.optString("businessType", ""),
                description = json.optString("description", ""),
                products = products,
                customInstructions = json.optString("customInstructions", ""),
                customText = json.optString("customText", ""),
                useCustomTextOnly = json.optBoolean("useCustomTextOnly", false)
            )
        } catch (e: Exception) {
            BusinessProfile()
        }
    }
    
    /**
     * Build business context string for ChatsPromo AI Cloud Function
     */
    fun buildBusinessContext(provider: AIProvider): String {
        val profile = getBusinessProfile(provider)
        
        if (profile.useCustomTextOnly && profile.customText.isNotEmpty()) {
            return profile.customText
        }
        
        val contextBuilder = StringBuilder()
        
        if (profile.businessName.isNotEmpty()) {
            contextBuilder.append("Business: ${profile.businessName}\n")
        }
        if (profile.ownerName.isNotEmpty()) {
            contextBuilder.append("Owner: ${profile.ownerName}\n")
        }
        if (profile.businessType.isNotEmpty()) {
            contextBuilder.append("Type: ${profile.businessType}\n")
        }
        if (profile.description.isNotEmpty()) {
            contextBuilder.append("About: ${profile.description}\n")
        }
        
        if (profile.products.isNotEmpty()) {
            contextBuilder.append("\nProducts/Services:\n")
            profile.products.forEach { product ->
                contextBuilder.append("- ${product.name}: ${product.price}")
                if (product.description.isNotEmpty()) {
                    contextBuilder.append(" (${product.description})")
                }
                contextBuilder.append("\n")
            }
        }
        
        if (profile.customInstructions.isNotEmpty()) {
            contextBuilder.append("\nInstructions: ${profile.customInstructions}\n")
        }
        
        if (profile.customText.isNotEmpty()) {
            contextBuilder.append("\nAdditional: ${profile.customText}\n")
        }
        
        return contextBuilder.toString()
    }
    
    fun buildAIPrompt(provider: AIProvider, incomingMessage: String, senderName: String): String {
        val profile = getBusinessProfile(provider)
        
        if (profile.useCustomTextOnly && profile.customText.isNotEmpty()) {
            return profile.customText
        }
        
        val promptBuilder = StringBuilder()
        
        // Business context
        if (profile.businessName.isNotEmpty()) {
            promptBuilder.append("Business Name: ${profile.businessName}\n")
        }
        if (profile.ownerName.isNotEmpty()) {
            promptBuilder.append("Owner: ${profile.ownerName}\n")
        }
        if (profile.businessType.isNotEmpty()) {
            promptBuilder.append("Business Type: ${profile.businessType}\n")
        }
        if (profile.description.isNotEmpty()) {
            promptBuilder.append("About: ${profile.description}\n")
        }
        
        // Products
        if (profile.products.isNotEmpty()) {
            promptBuilder.append("\nProducts/Services:\n")
            profile.products.forEach { product ->
                promptBuilder.append("- ${product.name}: ${product.price}")
                if (product.description.isNotEmpty()) {
                    promptBuilder.append(" (${product.description})")
                }
                promptBuilder.append("\n")
            }
        }
        
        // Custom instructions
        if (profile.customInstructions.isNotEmpty()) {
            promptBuilder.append("\nInstructions: ${profile.customInstructions}\n")
        }
        
        // Custom text
        if (profile.customText.isNotEmpty()) {
            promptBuilder.append("\nAdditional Context:\n${profile.customText}\n")
        }
        
        // Message to reply to
        promptBuilder.append("\nCustomer Message: $incomingMessage\n")
        promptBuilder.append("Customer Name: $senderName\n")
        promptBuilder.append("\n=== IMPORTANT INSTRUCTIONS ===\n")
        promptBuilder.append("Generate ONLY the direct reply message that will be sent to the customer.\n")
        promptBuilder.append("DO NOT include:\n")
        promptBuilder.append("- Subject lines\n")
        promptBuilder.append("- Email headers\n")
        promptBuilder.append("- Meta descriptions like 'Here is a reply' or 'Of course'\n")
        promptBuilder.append("- Separators like *** or ---\n")
        promptBuilder.append("- Signatures (unless specifically in business data)\n")
        promptBuilder.append("\nStart directly with the greeting and reply content.\n")
        promptBuilder.append("Example format: 'Hi [Name], [direct answer to their question]...'\n")
        promptBuilder.append("\n=== TOOLS ===\n")
        promptBuilder.append("If the user asks for a payment link or wants to buy something where price is known, use this tag:\n")
        promptBuilder.append("[GENERATE-PAYMENT-LINK: amount, description]\n")
        promptBuilder.append("Example: [GENERATE-PAYMENT-LINK: 500, Consultation Fee]\n")
        promptBuilder.append("This will automatically generate a secure Razorpay link and send it to the user.\n")
        
        return promptBuilder.toString()
    }
}
