package com.message.bulksend.leadmanager.model

import com.message.bulksend.leadmanager.database.entities.TimelineEventType

/**
 * Timeline entry model for UI
 */
data class TimelineEntry(
    val id: String,
    val leadId: String,
    val eventType: TimelineEventType,
    val title: String,
    val description: String = "",
    val timestamp: Long,
    val imageUri: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val iconType: String = "default"
)

/**
 * Timeline group by date
 */
data class TimelineGroup(
    val date: Long,
    val dateLabel: String,
    val entries: List<TimelineEntry>
)
