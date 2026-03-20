package com.message.bulksend.autorespond.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Message tracking entity for Room Database
 */
@Entity(tableName = "message_logs")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    val srNo: Int = 0,
    val phoneNumber: String = "",
    val senderName: String = "",
    val incomingMessage: String = "",
    val outgoingMessage: String = "",
    val status: String = "PENDING", // PENDING, SENT, FAILED
    val timestamp: Long = System.currentTimeMillis(),
    val dateTime: String = "",
    val notificationKey: String = ""
)
