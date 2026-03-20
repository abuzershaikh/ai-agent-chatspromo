package com.message.bulksend.leadmanager.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Timeline event types
 */
enum class TimelineEventType {
    LEAD_CREATED,
    STATUS_CHANGED,
    NOTE_ADDED,
    FOLLOWUP_SCHEDULED,
    FOLLOWUP_COMPLETED,
    CALL_MADE,
    MESSAGE_SENT,
    EMAIL_SENT,
    MEETING_SCHEDULED,
    PRODUCT_ASSIGNED,
    IMAGE_ADDED,
    CUSTOM_EVENT
}

/**
 * Timeline entry entity for tracking all lead activities
 */
@Entity(
    tableName = "timeline_entries",
    foreignKeys = [
        ForeignKey(
            entity = LeadEntity::class,
            parentColumns = ["id"],
            childColumns = ["leadId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["leadId"])]
)
data class TimelineEntity(
    @PrimaryKey
    val id: String,
    val leadId: String,
    val eventType: TimelineEventType,
    val title: String,
    val description: String = "",
    val timestamp: Long,
    val imageUri: String? = null,
    val metadata: String = "", // JSON for extra data
    val iconType: String = "default"
)
