package com.message.bulksend.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.message.bulksend.contactmanager.Contact

@Entity(tableName = "scheduled_campaigns")
data class ScheduledCampaign(
    @PrimaryKey
    val id: String,
    val campaignType: String, // TEXT, MEDIA, TEXT_AND_MEDIA, SHEET
    val campaignName: String,
    val scheduledTime: Long, // Unix timestamp
    val status: String, // SCHEDULED, RUNNING, COMPLETED, CANCELLED, FAILED
    val createdTime: Long = System.currentTimeMillis(),
    
    // Campaign data as JSON
    val campaignDataJson: String,
    
    // Quick access fields for UI
    val contactCount: Int = 0,
    val groupId: String? = null,
    val groupName: String? = null
)

// Data class for campaign configuration
data class CampaignData(
    // Common fields for all campaigns
    val campaignName: String,
    val countryCode: String,
    val selectedGroupId: String? = null,
    val selectedContacts: List<Contact>? = null,
    val delaySettings: String = "Fixed (5 sec)",
    val uniqueIdEnabled: Boolean = false,
    val whatsAppPreference: String = "WhatsApp",
    
    // Text-specific fields
    val messageText: String? = null,
    
    // Media-specific fields  
    val mediaUri: String? = null,
    val mediaPath: String? = null,
    val captionText: String? = null,
    
    // Text+Media specific
    val sendOrder: String? = null, // TEXT_FIRST, MEDIA_FIRST
    
    // Sheet-specific fields
    val sheetUrl: String? = null,
    val sheetFileName: String? = null,
    val sheetDataJson: String? = null,
    val templateMessage: String? = null
)

enum class CampaignType(val displayName: String) {
    TEXT("Text Campaign"),
    MEDIA("Media Campaign"), 
    TEXT_AND_MEDIA("Text + Media"),
    SHEET("Sheet Campaign")
}

enum class ScheduleStatus(val displayName: String) {
    SCHEDULED("Scheduled"),
    RUNNING("Running"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    FAILED("Failed")
}

// Extension functions for easy conversion
fun CampaignData.toJson(): String = Gson().toJson(this)

fun String.toCampaignData(): CampaignData? {
    return try {
        Gson().fromJson(this, CampaignData::class.java)
    } catch (e: Exception) {
        null
    }
}