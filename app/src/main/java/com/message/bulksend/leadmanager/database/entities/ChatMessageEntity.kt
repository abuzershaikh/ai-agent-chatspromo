package com.message.bulksend.leadmanager.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity to store chat messages from AutoRespond
 * Links to Lead via leadId foreign key
 */
@Entity(
    tableName = "chat_messages",
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
data class ChatMessageEntity(
    @PrimaryKey
    val id: String,
    val leadId: String,
    val senderName: String,
    val senderPhone: String,
    val messageText: String,
    val timestamp: Long,
    val isIncoming: Boolean = true, // true = received, false = sent (auto-reply)
    val isAutoReply: Boolean = false,
    val matchedKeyword: String? = null, // Which keyword triggered auto-reply
    val replyType: String = "manual", // manual, keyword, ai, spreadsheet
    val packageName: String = "", // com.whatsapp or com.whatsapp.w4b
    val isRead: Boolean = false
)
