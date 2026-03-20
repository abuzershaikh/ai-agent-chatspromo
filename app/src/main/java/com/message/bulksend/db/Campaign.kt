package com.message.bulksend.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.message.bulksend.data.ContactStatus

/**
 * Database mein 'campaigns' table ko represent karta hai.
 * Ismein campaign se judi saari jaankari save hoti hai.
 */
@Entity(tableName = "campaigns")
data class Campaign(
    @PrimaryKey val id: String,
    val groupId: String, // Group ID or unique identifier for sheet campaign
    val campaignName: String,
    val message: String,
    val timestamp: Long,
    val totalContacts: Int,
    val contactStatuses: List<ContactStatus>,
    val isStopped: Boolean,
    val isRunning: Boolean,
    // Campaign type (e.g., "BULKSEND", "BULKTEXT", "SHEETSSEND")
    val campaignType: String,
    // Sheet Campaign specific fields
    val sheetFileName: String? = null,
    val countryCode: String? = null,
    val sheetDataJson: String? = null, // Sheet data in JSON format
    val sheetUrl: String? = null, // URL for sheet loading
    // Media attachment path (stored in app's internal storage)
    val mediaPath: String? = null
) {
    // Inhein database mein save nahi kiya jayega. Yeh `contactStatuses` se calculate honge.
    val sentCount: Int
        get() = contactStatuses.count { it.status == "sent" }

    val failedCount: Int
        get() = contactStatuses.count { it.status == "failed" }
}

