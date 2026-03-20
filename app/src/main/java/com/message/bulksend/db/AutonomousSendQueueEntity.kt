package com.message.bulksend.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "autonomous_send_queue",
    indices = [
        Index(value = ["campaignId"]),
        Index(value = ["campaignId", "status"]),
        Index(value = ["campaignId", "plannedTimeMillis"])
    ]
)
data class AutonomousSendQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: String,
    val contactNumber: String,
    val contactName: String,
    val plannedTimeMillis: Long,
    val dayIndex: Int,
    val hourOfDay: Int,
    val status: String = "queued", // queued, sent, failed
    val retryCount: Int = 0,
    val lastError: String? = null,
    val sentTimeMillis: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
