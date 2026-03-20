package com.message.bulksend.aiagent.tools.agentform.models

enum class FieldType(val label: String, val isVerificationType: Boolean = false) {
    TEXT("Text Input"),
    NUMBER("Number Input"),
    PHONE("Phone Number"), // Mobile
    EMAIL("Email Address"),
    CONTACT_PICKER("Contact Select", isVerificationType = true),
    LOCATION("Location (Google Maps)", isVerificationType = true),
    MEDIA("File/Media Upload"),
    DATE("Date Picker"),
    TIME("Time Picker"),
    SELECT("Dropdown Selection"),
    GOOGLE_AUTH("Google Sign-In (Required)", isVerificationType = true); 
}
