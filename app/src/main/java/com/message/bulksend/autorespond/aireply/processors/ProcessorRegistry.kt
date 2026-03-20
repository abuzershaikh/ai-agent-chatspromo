package com.message.bulksend.autorespond.aireply.processors

import android.content.Context

/**
 * Registry for managing template processors
 * Makes it easy to add new templates
 */
class ProcessorRegistry(private val context: Context) {
    
    private val processors = mutableMapOf<String, TemplateProcessor>()
    
    init {
        // Register default processors with error handling
        try {
            registerProcessor(GeneralProcessor(context))
            android.util.Log.d("ProcessorRegistry", "✅ GeneralProcessor registered")
        } catch (e: Exception) {
            android.util.Log.e("ProcessorRegistry", "❌ Failed to register GeneralProcessor: ${e.message}")
        }
        
        try {
            registerProcessor(ClinicProcessor(context))
            android.util.Log.d("ProcessorRegistry", "✅ ClinicProcessor registered")
        } catch (e: Exception) {
            android.util.Log.e("ProcessorRegistry", "❌ Failed to register ClinicProcessor: ${e.message}")
        }
        
        try {
            registerProcessor(EcommerceProcessor(context))
            android.util.Log.d("ProcessorRegistry", "✅ EcommerceProcessor registered")
        } catch (e: Exception) {
            android.util.Log.e("ProcessorRegistry", "❌ Failed to register EcommerceProcessor: ${e.message}")
        }

        try {
            registerProcessor(CustomTemplateProcessor(context))
            android.util.Log.d("ProcessorRegistry", "✅ CustomTemplateProcessor registered")
        } catch (e: Exception) {
            android.util.Log.e("ProcessorRegistry", "❌ Failed to register CustomTemplateProcessor: ${e.message}")
        }
        
        // Ensure at least GeneralProcessor is available
        if (!processors.containsKey("GENERAL")) {
            android.util.Log.e("ProcessorRegistry", "⚠️ CRITICAL: GeneralProcessor not available, creating fallback")
            processors["GENERAL"] = object : TemplateProcessor {
                override fun getTemplateType() = "GENERAL"
                override suspend fun generateContext(senderPhone: String) = ""
                override suspend fun processResponse(response: String, message: String, senderPhone: String, senderName: String) = response
            }
        }
    }
    
    /**
     * Register a new processor
     * Call this to add support for new templates
     */
    fun registerProcessor(processor: TemplateProcessor) {
        processors[processor.getTemplateType()] = processor
        android.util.Log.d("ProcessorRegistry", "✅ Registered: ${processor.getTemplateType()}")
    }
    
    /**
     * Get processor for template type
     * Returns GeneralProcessor if template not found
     * Never returns null - always returns a valid processor
     */
    fun getProcessor(templateType: String): TemplateProcessor {
        val processor = processors[templateType] ?: processors["GENERAL"]
        if (processor == null) {
            android.util.Log.e("ProcessorRegistry", "⚠️ No processor found for $templateType, creating emergency fallback")
            return object : TemplateProcessor {
                override fun getTemplateType() = "GENERAL"
                override suspend fun generateContext(senderPhone: String) = ""
                override suspend fun processResponse(response: String, message: String, senderPhone: String, senderName: String) = response
            }
        }
        return processor
    }
    
    /**
     * Get all registered template types
     */
    fun getRegisteredTemplates(): List<String> {
        return processors.keys.toList()
    }
}
