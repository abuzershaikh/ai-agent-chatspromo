package com.message.bulksend.leadmanager.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.message.bulksend.leadmanager.model.LeadPriority
import com.message.bulksend.leadmanager.model.LeadStatus

@Entity(tableName = "leads")
data class LeadEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val phoneNumber: String,
    val email: String = "",
    val countryCode: String = "",
    val countryIso: String = "",
    val alternatePhone: String = "",
    val status: LeadStatus,
    val source: String,
    val lastMessage: String,
    val timestamp: Long,
    val category: String = "General",
    val notes: String = "",
    val priority: LeadPriority = LeadPriority.MEDIUM,
    val tags: String = "", // JSON string of tags list
    val product: String = "",
    val leadScore: Int = 50,
    val nextFollowUpDate: Long? = null,
    val isFollowUpCompleted: Boolean = false
)