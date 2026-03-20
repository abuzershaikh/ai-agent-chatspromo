package com.message.bulksend.autorespond.smartlead

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.message.bulksend.autorespond.database.MessageDatabase

class LeadCaptureManager(private val context: Context) {
    
    private val leadCaptureDao = MessageDatabase.getDatabase(context).leadCaptureDao()
    private val gson = Gson()
    
    companion object {
        const val TAG = "LeadCaptureManager"
        const val PLATFORM_INSTAGRAM = "instagram"
        const val PLATFORM_WHATSAPP = "whatsapp"
        
        const val STATUS_STARTED = "STARTED"
        const val STATUS_NAME_CAPTURED = "NAME_CAPTURED"
        const val STATUS_MOBILE_CAPTURED = "MOBILE_CAPTURED"
        const val STATUS_CUSTOM_FIELDS = "CUSTOM_FIELDS"
        const val STATUS_COMPLETED = "COMPLETED"
    }
    
    /**
     * Check if lead capture is enabled
     */
    suspend fun isLeadCaptureEnabled(): Boolean {
        val settings = leadCaptureDao.getSettings()
        return settings?.isEnabled == true
    }
    
    /**
     * Check if user is new (no previous completed capture)
     */
    suspend fun isNewUser(username: String, platform: String): Boolean {
        val existingCapture = leadCaptureDao.getActiveLeadCapture(username, platform)
        return existingCapture == null
    }
    
    /**
     * Start lead capture process for new user
     */
    suspend fun startLeadCapture(username: String, platform: String): String? {
        try {
            if (!isLeadCaptureEnabled()) {
                Log.d(TAG, "Lead capture is disabled")
                return null
            }
            
            // Check if already in progress
            val existingCapture = leadCaptureDao.getActiveLeadCapture(username, platform)
            if (existingCapture != null) {
                Log.d(TAG, "Lead capture already in progress for $username")
                return processExistingCapture(existingCapture, "")
            }
            
            val settings = leadCaptureDao.getSettings() ?: return null
            
            // Create new lead capture
            val leadCapture = LeadCaptureEntity(
                username = username,
                platform = platform,
                captureStatus = STATUS_STARTED,
                currentStep = 0,
                lastQuestionSent = settings.nameQuestion
            )
            
            leadCaptureDao.insertLeadCapture(leadCapture)
            Log.d(TAG, "Started lead capture for $username on $platform")
            
            // Return welcome message + name question
            return "${settings.welcomeMessage}\n\n${settings.nameQuestion}"
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting lead capture: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Process user response in lead capture flow
     */
    suspend fun processLeadCaptureResponse(username: String, platform: String, response: String): String? {
        try {
            val leadCapture = leadCaptureDao.getActiveLeadCapture(username, platform) ?: return null
            return processExistingCapture(leadCapture, response)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing lead capture response: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Process existing capture with user response
     */
    private suspend fun processExistingCapture(leadCapture: LeadCaptureEntity, response: String): String? {
        val settings = leadCaptureDao.getSettings() ?: return null
        val customFields = parseCustomFields(settings.customFields)
        
        when (leadCapture.currentStep) {
            0 -> { // Name step
                if (response.isNotBlank()) {
                    val updatedCapture = leadCapture.copy(
                        name = response.trim(),
                        captureStatus = STATUS_NAME_CAPTURED,
                        currentStep = 1,
                        lastQuestionSent = settings.mobileQuestion,
                        lastResponseReceived = response
                    )
                    leadCaptureDao.updateLeadCapture(updatedCapture)
                    Log.d(TAG, "Name captured: ${response.trim()}")
                    return settings.mobileQuestion
                } else {
                    return settings.nameQuestion
                }
            }
            
            1 -> { // Mobile step
                if (response.isNotBlank()) {
                    val updatedCapture = leadCapture.copy(
                        mobileNumber = response.trim(),
                        captureStatus = STATUS_MOBILE_CAPTURED,
                        currentStep = 2,
                        lastResponseReceived = response
                    )
                    
                    if (customFields.isNotEmpty()) {
                        // Move to first custom field
                        val firstCustomField = customFields[0]
                        val updatedWithCustom = updatedCapture.copy(
                            captureStatus = STATUS_CUSTOM_FIELDS,
                            lastQuestionSent = firstCustomField.question
                        )
                        leadCaptureDao.updateLeadCapture(updatedWithCustom)
                        Log.d(TAG, "Mobile captured: ${response.trim()}, moving to custom fields")
                        return firstCustomField.question
                    } else {
                        // No custom fields, complete capture
                        return completeCapture(updatedCapture, settings)
                    }
                } else {
                    return settings.mobileQuestion
                }
            }
            
            else -> { // Custom fields step (2+)
                if (response.isNotBlank()) {
                    val customFieldIndex = leadCapture.currentStep - 2
                    val customFieldResponses = parseCustomFieldResponses(leadCapture.customFields).toMutableMap()
                    
                    if (customFieldIndex < customFields.size) {
                        val currentField = customFields[customFieldIndex]
                        customFieldResponses[currentField.label] = response.trim()
                        
                        val updatedCapture = leadCapture.copy(
                            customFields = gson.toJson(customFieldResponses),
                            currentStep = leadCapture.currentStep + 1,
                            lastResponseReceived = response
                        )
                        
                        val nextFieldIndex = customFieldIndex + 1
                        if (nextFieldIndex < customFields.size) {
                            // Move to next custom field
                            val nextField = customFields[nextFieldIndex]
                            val updatedWithNext = updatedCapture.copy(
                                lastQuestionSent = nextField.question
                            )
                            leadCaptureDao.updateLeadCapture(updatedWithNext)
                            Log.d(TAG, "Custom field captured: ${currentField.label} = ${response.trim()}")
                            return nextField.question
                        } else {
                            // All custom fields completed
                            return completeCapture(updatedCapture, settings)
                        }
                    }
                }
                
                // If we reach here, ask current custom field again
                val customFieldIndex = leadCapture.currentStep - 2
                if (customFieldIndex < customFields.size) {
                    return customFields[customFieldIndex].question
                }
            }
        }
        
        return null
    }
    
    /**
     * Complete the lead capture process
     */
    private suspend fun completeCapture(leadCapture: LeadCaptureEntity, settings: LeadCaptureSettingsEntity): String {
        val completedCapture = leadCapture.copy(
            captureStatus = STATUS_COMPLETED,
            isCompleted = true
        )
        leadCaptureDao.updateLeadCapture(completedCapture)
        
        // Save to Lead Manager if available
        saveToLeadManager(completedCapture)
        
        Log.d(TAG, "Lead capture completed for ${leadCapture.username}")
        return settings.completionMessage
    }
    
    /**
     * Save captured lead to Lead Manager
     */
    private fun saveToLeadManager(leadCapture: LeadCaptureEntity) {
        try {
            val leadManager = com.message.bulksend.leadmanager.LeadManager(context)
            val customFieldResponses = parseCustomFieldResponses(leadCapture.customFields)
            
            // Create lead with captured information
            val phoneNumber = if (leadCapture.platform == PLATFORM_INSTAGRAM) {
                "instagram_${leadCapture.username}"
            } else {
                leadCapture.mobileNumber
            }
            
            val notes = buildString {
                append("Lead captured via ${leadCapture.platform.capitalize()}\n")
                append("Username: ${leadCapture.username}\n")
                if (leadCapture.mobileNumber.isNotBlank()) {
                    append("Mobile: ${leadCapture.mobileNumber}\n")
                }
                if (customFieldResponses.isNotEmpty()) {
                    append("\nCustom Fields:\n")
                    customFieldResponses.forEach { (label, value) ->
                        append("$label: $value\n")
                    }
                }
            }
            
            leadManager.addLeadFromAutoRespond(
                senderName = leadCapture.name.ifBlank { leadCapture.username },
                senderPhone = phoneNumber,
                messageText = "Lead captured via Smart Auto Lead Capture",
                matchedKeyword = "smart_lead_capture"
            )
            
            Log.d(TAG, "Lead saved to Lead Manager: ${leadCapture.name}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to Lead Manager: ${e.message}", e)
        }
    }
    
    /**
     * Get or create default settings
     */
    suspend fun getSettings(): LeadCaptureSettingsEntity {
        return leadCaptureDao.getSettings() ?: run {
            val defaultSettings = LeadCaptureSettingsEntity()
            leadCaptureDao.insertOrUpdateSettings(defaultSettings)
            defaultSettings
        }
    }
    
    /**
     * Update settings
     */
    suspend fun updateSettings(settings: LeadCaptureSettingsEntity) {
        leadCaptureDao.insertOrUpdateSettings(settings)
    }
    
    /**
     * Parse custom fields from JSON
     */
    private fun parseCustomFields(json: String): List<CustomFieldConfig> {
        return try {
            if (json.isBlank()) return emptyList()
            val type = object : TypeToken<List<CustomFieldConfig>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing custom fields: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Parse custom field responses from JSON
     */
    private fun parseCustomFieldResponses(json: String): Map<String, String> {
        return try {
            if (json.isBlank()) return emptyMap()
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing custom field responses: ${e.message}")
            emptyMap()
        }
    }
    
    /**
     * Get statistics
     */
    suspend fun getStatistics(): LeadCaptureStatistics {
        return LeadCaptureStatistics(
            totalCompleted = leadCaptureDao.getCompletedCapturesCount(),
            totalActive = leadCaptureDao.getActiveCapturesCount()
        )
    }
}

data class LeadCaptureStatistics(
    val totalCompleted: Int,
    val totalActive: Int
)