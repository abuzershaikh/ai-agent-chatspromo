package com.message.bulksend.leadmanager.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.message.bulksend.leadmanager.model.FollowUpType

@Entity(
    tableName = "follow_ups",
    foreignKeys = [
        ForeignKey(
            entity = LeadEntity::class,
            parentColumns = ["id"],
            childColumns = ["leadId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [androidx.room.Index(value = ["leadId"])]
)
data class FollowUpEntity(
    @PrimaryKey
    val id: String,
    val leadId: String,
    val title: String,
    val description: String = "",
    val scheduledDate: Long,
    val scheduledTime: String, // Format: "HH:mm"
    val type: FollowUpType,
    val isCompleted: Boolean = false,
    val completedDate: Long? = null,
    val notes: String = "",
    val reminderMinutes: Int = 15
)