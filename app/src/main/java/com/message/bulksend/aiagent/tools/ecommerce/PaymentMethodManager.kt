package com.message.bulksend.aiagent.tools.ecommerce

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Payment Method Manager
 * Handles all payment method operations
 */
class PaymentMethodManager(private val context: Context) {
    
    private val database = PaymentMethodDatabase.getDatabase(context)
    private val dao = database.paymentMethodDao()
    private val flowModeManager = PaymentFlowModeManager(context)
    
    companion object {
        private const val TAG = "PaymentMethodManager"
        private const val QR_CODES_FOLDER = "payment_qr_codes"
    }
    
    /**
     * Get all payment methods
     */
    fun getAllPaymentMethods(): Flow<List<PaymentMethod>> {
        return dao.getAllPaymentMethods().map { entities ->
            entities.map { it.toPaymentMethod() }
        }
    }
    
    /**
     * Get enabled payment methods only
     */
    fun getEnabledPaymentMethods(): Flow<List<PaymentMethod>> {
        return dao.getEnabledPaymentMethods().map { entities ->
            entities
                .map { it.toPaymentMethod() }
                .filter { method -> isAllowedForCurrentMode(method.type) }
        }
    }
    
    /**
     * Get payment method by ID
     */
    suspend fun getPaymentMethodById(id: String): PaymentMethod? {
        return withContext(Dispatchers.IO) {
            dao.getPaymentMethodById(id)?.toPaymentMethod()
        }
    }
    
    /**
     * Add QR Code payment method
     */
    suspend fun addQRCodeMethod(
        name: String,
        qrCodeType: QRCodeType,
        qrCodeUri: Uri,
        fixedPrice: Double? = null,
        agentPriceField: String? = null
    ): Result<PaymentMethod> {
        return withContext(Dispatchers.IO) {
            try {
                val methodId = UUID.randomUUID().toString()
                
                // Copy QR code image to app storage
                val qrImagePath = copyQRCodeToStorage(qrCodeUri, methodId)
                
                val method = PaymentMethod(
                    id = methodId,
                    name = name,
                    type = PaymentMethodType.QR_CODE,
                    qrCodeType = qrCodeType,
                    qrCodeImagePath = qrImagePath,
                    qrCodeImageUri = Uri.fromFile(File(qrImagePath)),
                    fixedPrice = fixedPrice,
                    agentPriceField = agentPriceField,
                    isEnabled = true
                )
                
                dao.insertPaymentMethod(method.toEntity())
                enforceModeAfterMethodEnabled(method.type)
                Log.d(TAG, "QR Code method added: $name")
                Result.success(method)
            } catch (e: Exception) {
                Log.e(TAG, "Error adding QR code method", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Add UPI ID payment method
     */
    suspend fun addUPIMethod(
        name: String,
        upiId: String
    ): Result<PaymentMethod> {
        return withContext(Dispatchers.IO) {
            try {
                val method = PaymentMethod(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    type = PaymentMethodType.UPI_ID,
                    upiId = upiId,
                    isEnabled = true
                )
                
                dao.insertPaymentMethod(method.toEntity())
                enforceModeAfterMethodEnabled(method.type)
                Log.d(TAG, "UPI method added: $name")
                Result.success(method)
            } catch (e: Exception) {
                Log.e(TAG, "Error adding UPI method", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Add Razorpay payment method
     */
    suspend fun addRazorpayMethod(
        name: String,
        keyId: String,
        keySecret: String,
        webhookSecret: String? = null
    ): Result<PaymentMethod> {
        return withContext(Dispatchers.IO) {
            try {
                val method = PaymentMethod(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    type = PaymentMethodType.RAZORPAY,
                    razorpayKeyId = keyId,
                    razorpayKeySecret = keySecret,
                    razorpayWebhookSecret = webhookSecret,
                    isEnabled = true
                )
                
                dao.insertPaymentMethod(method.toEntity())
                enforceModeAfterMethodEnabled(method.type)
                Log.d(TAG, "Razorpay method added: $name")
                Result.success(method)
            } catch (e: Exception) {
                Log.e(TAG, "Error adding Razorpay method", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Add custom field group payment method
     */
    suspend fun addCustomGroupMethod(
        groupName: String,
        fields: List<CustomField>
    ): Result<PaymentMethod> {
        return withContext(Dispatchers.IO) {
            try {
                val method = PaymentMethod(
                    id = UUID.randomUUID().toString(),
                    name = groupName,
                    type = PaymentMethodType.CUSTOM_GROUP,
                    customGroupName = groupName,
                    customFields = fields,
                    isEnabled = true
                )
                
                dao.insertPaymentMethod(method.toEntity())
                enforceModeAfterMethodEnabled(method.type)
                Log.d(TAG, "Custom group added: $groupName")
                Result.success(method)
            } catch (e: Exception) {
                Log.e(TAG, "Error adding custom group", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Update payment method
     */
    suspend fun updatePaymentMethod(method: PaymentMethod): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                dao.updatePaymentMethod(method.toEntity())
                Log.d(TAG, "Payment method updated: ${method.id}")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating payment method", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Delete payment method
     */
    suspend fun deletePaymentMethod(id: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val method = dao.getPaymentMethodById(id)
                if (method != null) {
                    // Delete QR code image if exists
                    if (method.qrCodeImagePath != null) {
                        val file = File(method.qrCodeImagePath)
                        if (file.exists()) {
                            file.delete()
                        }
                    }
                    
                    dao.deletePaymentMethod(id)
                    Log.d(TAG, "Payment method deleted: $id")
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Payment method not found"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting payment method", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Toggle payment method enabled/disabled
     */
    suspend fun togglePaymentMethod(id: String, enabled: Boolean): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val method = dao.getPaymentMethodById(id)?.toPaymentMethod()
                    ?: return@withContext Result.failure(Exception("Payment method not found"))
                dao.togglePaymentMethod(id, enabled)
                if (enabled) {
                    enforceModeAfterMethodEnabled(method.type)
                } else {
                    syncFlowModeFromEnabledMethods()
                }
                Log.d(TAG, "Payment method toggled: $id -> $enabled")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling payment method", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Get payment methods count
     */
    suspend fun getEnabledCount(): Int {
        return withContext(Dispatchers.IO) {
            dao.getEnabledCount()
        }
    }
    
    /**
     * Copy QR code image to app storage
     */
    private fun copyQRCodeToStorage(sourceUri: Uri, methodId: String): String {
        val qrCodesDir = File(context.filesDir, QR_CODES_FOLDER)
        if (!qrCodesDir.exists()) {
            qrCodesDir.mkdirs()
        }
        
        val extension = context.contentResolver.getType(sourceUri)?.let { mimeType ->
            when {
                mimeType.startsWith("image/") -> ".${mimeType.substringAfter("image/")}"
                else -> ".jpg"
            }
        } ?: ".jpg"
        
        val destinationFile = File(qrCodesDir, "$methodId$extension")
        
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(destinationFile).use { output ->
                input.copyTo(output)
            }
        }
        
        return destinationFile.absolutePath
    }
    
    /**
     * Get payment methods for AI Agent context
     * Uses direct database query instead of Flow to avoid coroutine scope issues
     */
    suspend fun getPaymentMethodsForAI(): String {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("PaymentMethodManager", "🔍 Fetching payment methods for AI")
                
                // Direct database query instead of Flow to avoid scope issues
                val methodEntities = dao.getEnabledPaymentMethodsDirect()
                val methods =
                    methodEntities
                        .map { it.toPaymentMethod() }
                        .filter { method -> isAllowedForCurrentMode(method.type) }
                
                android.util.Log.d("PaymentMethodManager", "✅ Found ${methods.size} payment methods")
                
                if (methods.isEmpty()) {
                    android.util.Log.w("PaymentMethodManager", "⚠️ No payment methods configured")
                    return@withContext "No payment methods configured."
                }
                
                buildString {
                    appendLine("Available Payment Methods:")
                    appendLine("Active Mode: ${flowModeManager.getMode().name}")
                    appendLine()
                    methods.forEachIndexed { index, method ->
                        appendLine("${index + 1}. ${method.name} (ID: ${method.id})")
                        appendLine("   Type: ${method.type.displayName}")
                        when (method.type) {
                            PaymentMethodType.QR_CODE -> {
                                appendLine("   QR Type: ${method.qrCodeType?.displayName}")
                                if (method.fixedPrice != null) {
                                    appendLine("   Price: ₹${method.fixedPrice}")
                                }
                                if (method.agentPriceField != null) {
                                    appendLine("   Agent Field: ${method.agentPriceField}")
                                }
                                if (method.qrCodeImagePath != null) {
                                    val file = File(method.qrCodeImagePath)
                                    if (file.exists()) {
                                        appendLine("   ✅ QR Image: Available")
                                    } else {
                                        appendLine("   ❌ QR Image: Missing")
                                    }
                                }
                            }
                            PaymentMethodType.UPI_ID -> {
                                appendLine("   UPI ID: ${method.upiId}")
                            }
                            PaymentMethodType.RAZORPAY -> {
                                appendLine("   Razorpay Integration")
                            }
                            PaymentMethodType.PAYPAL -> {
                                appendLine("   PayPal: ${method.paypalEmail}")
                            }
                            PaymentMethodType.CUSTOM_GROUP -> {
                                appendLine("   ${method.customFields?.size ?: 0} fields")
                            }
                        }
                        appendLine()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PaymentMethodManager", "❌ Error fetching payment methods: ${e.message}", e)
                "No payment methods configured."
            }
        }
    }

    private suspend fun enforceModeAfterMethodEnabled(type: PaymentMethodType) {
        val enabledMethods = dao.getEnabledPaymentMethodsDirect()
        if (type == PaymentMethodType.RAZORPAY) {
            enabledMethods.forEach { entity ->
                val entityType =
                    runCatching { PaymentMethodType.valueOf(entity.type) }.getOrNull()
                if (entityType != null && entityType != PaymentMethodType.RAZORPAY) {
                    dao.togglePaymentMethod(entity.id, false)
                }
            }
            flowModeManager.setMode(PaymentFlowMode.RAZORPAY)
        } else {
            enabledMethods.forEach { entity ->
                val entityType =
                    runCatching { PaymentMethodType.valueOf(entity.type) }.getOrNull()
                if (entityType == PaymentMethodType.RAZORPAY) {
                    dao.togglePaymentMethod(entity.id, false)
                }
            }
            flowModeManager.setMode(PaymentFlowMode.MANUAL_QR_UPI_BANK)
        }
    }

    private suspend fun syncFlowModeFromEnabledMethods() {
        val enabledTypes =
            dao.getEnabledPaymentMethodsDirect().mapNotNull { entity ->
                runCatching { PaymentMethodType.valueOf(entity.type) }.getOrNull()
            }

        when {
            enabledTypes.any { it != PaymentMethodType.RAZORPAY } ->
                flowModeManager.setMode(PaymentFlowMode.MANUAL_QR_UPI_BANK)
            enabledTypes.any { it == PaymentMethodType.RAZORPAY } ->
                flowModeManager.setMode(PaymentFlowMode.RAZORPAY)
            else -> flowModeManager.setMode(PaymentFlowMode.MANUAL_QR_UPI_BANK)
        }
    }

    private fun isAllowedForCurrentMode(type: PaymentMethodType): Boolean {
        return when (flowModeManager.getMode()) {
            PaymentFlowMode.MANUAL_QR_UPI_BANK -> type != PaymentMethodType.RAZORPAY
            PaymentFlowMode.RAZORPAY -> type == PaymentMethodType.RAZORPAY
        }
    }
}

/**
 * Extension functions for conversion
 */
private fun PaymentMethodEntity.toPaymentMethod(): PaymentMethod {
    return PaymentMethod(
        id = id,
        name = name,
        type = PaymentMethodType.valueOf(type),
        isEnabled = isEnabled,
        createdAt = createdAt,
        qrCodeType = qrCodeType?.let { QRCodeType.valueOf(it) },
        qrCodeImagePath = qrCodeImagePath,
        qrCodeImageUri = qrCodeImagePath?.let { Uri.parse(it) },
        fixedPrice = fixedPrice,
        agentPriceField = agentPriceField,
        upiId = upiId,
        razorpayKeyId = razorpayKeyId,
        razorpayKeySecret = razorpayKeySecret,
        razorpayWebhookSecret = razorpayWebhookSecret,
        paypalEmail = paypalEmail,
        paypalClientId = paypalClientId,
        paypalClientSecret = paypalClientSecret,
        customGroupName = customGroupName,
        customFields = customFieldsJson?.let { parseCustomFields(it) }
    )
}

private fun PaymentMethod.toEntity(): PaymentMethodEntity {
    return PaymentMethodEntity(
        id = id,
        name = name,
        type = type.name,
        isEnabled = isEnabled,
        createdAt = createdAt,
        qrCodeType = qrCodeType?.name,
        qrCodeImagePath = qrCodeImagePath,
        fixedPrice = fixedPrice,
        agentPriceField = agentPriceField,
        upiId = upiId,
        razorpayKeyId = razorpayKeyId,
        razorpayKeySecret = razorpayKeySecret,
        razorpayWebhookSecret = razorpayWebhookSecret,
        paypalEmail = paypalEmail,
        paypalClientId = paypalClientId,
        paypalClientSecret = paypalClientSecret,
        customGroupName = customGroupName,
        customFieldsJson = customFields?.let { serializeCustomFields(it) }
    )
}

private fun parseCustomFields(json: String): List<CustomField> {
    return try {
        if (json.isBlank() || json == "[]") {
            return emptyList()
        }
        
        val fields = mutableListOf<CustomField>()
        
        // Remove outer brackets
        var content = json.trim()
        if (content.startsWith("[") && content.endsWith("]")) {
            content = content.substring(1, content.length - 1)
        }
        
        // Parse each field object
        var depth = 0
        var fieldStart = -1
        var inField = false
        
        for (i in content.indices) {
            val char = content[i]
            when (char) {
                '{' -> {
                    if (depth == 0) {
                        fieldStart = i
                        inField = true
                    }
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && inField) {
                        val fieldJson = content.substring(fieldStart, i + 1)
                        fields.add(parseCustomField(fieldJson))
                        inField = false
                    }
                }
            }
        }
        
        fields
    } catch (e: Exception) {
        emptyList()
    }
}

private fun parseCustomField(json: String): CustomField {
    var fieldName = ""
    var fieldValue = ""
    var fieldType = CustomFieldType.TEXT
    
    // Extract fieldName
    val namePattern = "\"fieldName\"\\s*:\\s*\"([^\"]*)\"".toRegex()
    namePattern.find(json)?.groupValues?.get(1)?.let { fieldName = it }
    
    // Extract fieldValue
    val valuePattern = "\"fieldValue\"\\s*:\\s*\"([^\"]*)\"".toRegex()
    valuePattern.find(json)?.groupValues?.get(1)?.let { fieldValue = it }
    
    // Extract fieldType
    val typePattern = "\"fieldType\"\\s*:\\s*\"([^\"]*)\"".toRegex()
    typePattern.find(json)?.groupValues?.get(1)?.let { typeStr ->
        try {
            fieldType = CustomFieldType.valueOf(typeStr)
        } catch (e: Exception) {
            fieldType = CustomFieldType.TEXT
        }
    }
    
    return CustomField(
        fieldName = fieldName,
        fieldValue = fieldValue,
        fieldType = fieldType
    )
}

private fun serializeCustomFields(fields: List<CustomField>): String {
    if (fields.isEmpty()) {
        return "[]"
    }
    
    val sb = StringBuilder()
    sb.append("[")
    
    fields.forEachIndexed { index, field ->
        if (index > 0) sb.append(",")
        sb.append("{\"fieldName\":\"${escapeJson(field.fieldName)}\",")
        sb.append("\"fieldValue\":\"${escapeJson(field.fieldValue)}\",")
        sb.append("\"fieldType\":\"${field.fieldType.name}\"}")
    }
    
    sb.append("]")
    return sb.toString()
}

private fun escapeJson(str: String): String {
    return str
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
