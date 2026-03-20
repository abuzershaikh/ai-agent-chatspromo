package com.message.bulksend.leadmanager.timeline

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.message.bulksend.leadmanager.database.LeadManagerDatabase
import com.message.bulksend.leadmanager.database.entities.TimelineEntity
import com.message.bulksend.leadmanager.database.entities.TimelineEventType
import com.message.bulksend.leadmanager.model.TimelineEntry
import com.message.bulksend.leadmanager.model.TimelineGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manager class for timeline operations
 */
class TimelineManager(context: Context) {
    
    private val database = LeadManagerDatabase.getDatabase(context)
    private val timelineDao = database.timelineDao()
    private val gson = Gson()
    
    /**
     * Get timeline entries for a lead as Flow
     */
    fun getTimelineForLead(leadId: String): Flow<List<TimelineEntry>> {
        return timelineDao.getTimelineForLead(leadId).map { entities ->
            entities.map { it.toModel() }
        }
    }
    
    /**
     * Get timeline entries grouped by date
     */
    fun getTimelineGroupedByDate(leadId: String): Flow<List<TimelineGroup>> {
        return timelineDao.getTimelineForLead(leadId).map { entities ->
            val entries = entities.map { it.toModel() }
            groupEntriesByDate(entries)
        }
    }
    
    /**
     * Get timeline entries as list
     */
    suspend fun getTimelineForLeadList(leadId: String): List<TimelineEntry> {
        return timelineDao.getTimelineForLeadList(leadId).map { it.toModel() }
    }
    
    /**
     * Add a timeline entry
     */
    suspend fun addTimelineEntry(
        leadId: String,
        eventType: TimelineEventType,
        title: String,
        description: String = "",
        imageUri: String? = null,
        metadata: Map<String, String> = emptyMap()
    ) {
        val entry = TimelineEntity(
            id = UUID.randomUUID().toString(),
            leadId = leadId,
            eventType = eventType,
            title = title,
            description = description,
            timestamp = System.currentTimeMillis(),
            imageUri = imageUri,
            metadata = gson.toJson(metadata),
            iconType = getIconTypeForEvent(eventType)
        )
        timelineDao.insertTimelineEntry(entry)
    }
    
    /**
     * Add lead created event
     */
    suspend fun addLeadCreatedEvent(leadId: String, leadName: String) {
        addTimelineEntry(
            leadId = leadId,
            eventType = TimelineEventType.LEAD_CREATED,
            title = "Lead Created",
            description = "Lead '$leadName' was created"
        )
    }
    
    /**
     * Add status changed event
     */
    suspend fun addStatusChangedEvent(leadId: String, oldStatus: String, newStatus: String) {
        addTimelineEntry(
            leadId = leadId,
            eventType = TimelineEventType.STATUS_CHANGED,
            title = "Status Changed",
            description = "Status changed from '$oldStatus' to '$newStatus'",
            metadata = mapOf("oldStatus" to oldStatus, "newStatus" to newStatus)
        )
    }
    
    /**
     * Add note event
     */
    suspend fun addNoteEvent(leadId: String, note: String) {
        addTimelineEntry(
            leadId = leadId,
            eventType = TimelineEventType.NOTE_ADDED,
            title = "Note Added",
            description = note
        )
    }
    
    /**
     * Add follow-up scheduled event
     */
    suspend fun addFollowUpScheduledEvent(leadId: String, followUpTitle: String, scheduledDate: Long) {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
        addTimelineEntry(
            leadId = leadId,
            eventType = TimelineEventType.FOLLOWUP_SCHEDULED,
            title = "Follow-up Scheduled",
            description = "$followUpTitle - ${dateFormat.format(Date(scheduledDate))}"
        )
    }
    
    /**
     * Add image event
     */
    suspend fun addImageEvent(leadId: String, imageUri: String, caption: String = "") {
        addTimelineEntry(
            leadId = leadId,
            eventType = TimelineEventType.IMAGE_ADDED,
            title = "Image Added",
            description = caption,
            imageUri = imageUri
        )
    }
    
    /**
     * Add custom event
     */
    suspend fun addCustomEvent(leadId: String, title: String, description: String) {
        addTimelineEntry(
            leadId = leadId,
            eventType = TimelineEventType.CUSTOM_EVENT,
            title = title,
            description = description
        )
    }
    
    /**
     * Delete timeline entry
     */
    suspend fun deleteTimelineEntry(id: String) {
        timelineDao.deleteTimelineEntryById(id)
    }
    
    /**
     * Group entries by date
     */
    private fun groupEntriesByDate(entries: List<TimelineEntry>): List<TimelineGroup> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val displayFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
        
        return entries.groupBy { entry ->
            dateFormat.format(Date(entry.timestamp))
        }.map { (_, groupEntries) ->
            val firstEntry = groupEntries.first()
            TimelineGroup(
                date = firstEntry.timestamp,
                dateLabel = displayFormat.format(Date(firstEntry.timestamp)),
                entries = groupEntries
            )
        }.sortedByDescending { it.date }
    }
    
    /**
     * Get icon type for event
     */
    private fun getIconTypeForEvent(eventType: TimelineEventType): String {
        return when (eventType) {
            TimelineEventType.LEAD_CREATED -> "person_add"
            TimelineEventType.STATUS_CHANGED -> "flag"
            TimelineEventType.NOTE_ADDED -> "note"
            TimelineEventType.FOLLOWUP_SCHEDULED -> "schedule"
            TimelineEventType.FOLLOWUP_COMPLETED -> "check_circle"
            TimelineEventType.CALL_MADE -> "phone"
            TimelineEventType.MESSAGE_SENT -> "message"
            TimelineEventType.EMAIL_SENT -> "email"
            TimelineEventType.MEETING_SCHEDULED -> "meeting"
            TimelineEventType.PRODUCT_ASSIGNED -> "inventory"
            TimelineEventType.IMAGE_ADDED -> "image"
            TimelineEventType.CUSTOM_EVENT -> "event"
        }
    }
    
    /**
     * Convert entity to model
     */
    private fun TimelineEntity.toModel(): TimelineEntry {
        val metadataMap: Map<String, String> = if (metadata.isNotEmpty()) {
            try {
                val type = object : TypeToken<Map<String, String>>() {}.type
                gson.fromJson(metadata, type) ?: emptyMap()
            } catch (e: Exception) {
                emptyMap()
            }
        } else emptyMap()
        
        return TimelineEntry(
            id = id,
            leadId = leadId,
            eventType = eventType,
            title = title,
            description = description,
            timestamp = timestamp,
            imageUri = imageUri,
            metadata = metadataMap,
            iconType = iconType
        )
    }
}
