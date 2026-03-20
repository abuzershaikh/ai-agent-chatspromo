package com.message.bulksend.autorespond.smartlead

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lead_captures")
data class LeadCaptureEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val username: String,
    val platform: String, // "instagram" or "whatsapp"
    val captureStatus: String, // "STARTED", "NAME_CAPTURED", "MOBILE_CAPTURED", "CUSTOM_FIELDS", "COMPLETED"
    val currentStep: Int = 0, // 0=name, 1=mobile, 2+=custom fields
    val name: String = "",
    val mobileNumber: String = "",
    val customFields: String = "", // JSON string of custom field responses
    val timestamp: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = false,
    val lastQuestionSent: String = "",
    val lastResponseReceived: String = ""
)

@Entity(tableName = "lead_capture_settings")
data class LeadCaptureSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val isEnabled: Boolean = false,
    val nameQuestion: String = "What's your name?",
    val mobileQuestion: String = "Please share your mobile number",
    val customFields: String = "", // JSON string of custom fields
    val welcomeMessage: String = "Hi! I'd like to know more about you to provide better assistance.",
    val completionMessage: String = "Thank you! Your information has been saved. How can I help you today?"
)

data class CustomFieldConfig(
    val label: String,
    val question: String
)