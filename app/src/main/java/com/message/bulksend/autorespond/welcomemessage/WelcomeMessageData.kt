package com.message.bulksend.autorespond.welcomemessage

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.message.bulksend.autorespond.documentreply.DocumentFile

/**
 * Entity to track which users have received welcome message
 */
@Entity(tableName = "welcome_message_sent")
data class WelcomeMessageSent(
    @PrimaryKey
    val oderId: String, // Phone number or user ID
    val sentAt: Long = System.currentTimeMillis(),
    val messageCount: Int = 0 // How many welcome messages sent
)

/**
 * Entity for welcome messages (multiple messages support)
 */
@Entity(tableName = "welcome_messages")
data class WelcomeMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val message: String,
    val orderIndex: Int = 0, // Order in which messages are sent
    val delayMs: Long = 0, // Delay before sending this message (ms)
    val isEnabled: Boolean = true,
    val selectedDocumentsJson: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getSelectedDocuments(): List<DocumentFile> {
        return WelcomeMessageDocumentCodec.deserialize(selectedDocumentsJson)
    }

    fun hasSelectedDocuments(): Boolean = selectedDocumentsJson.isNotBlank() && getSelectedDocuments().isNotEmpty()

    fun withSelectedDocuments(documents: List<DocumentFile>): WelcomeMessage {
        return copy(selectedDocumentsJson = WelcomeMessageDocumentCodec.serialize(documents))
    }
}

/**
 * Welcome message settings
 */
data class WelcomeMessageSettings(
    val isEnabled: Boolean = false,
    val sendMultiple: Boolean = false, // true = send all messages, false = send only first
    val delayBetweenMessages: Long = 1000L, // Delay between multiple messages (ms)
    val onlyNewContacts: Boolean = true // Only send to contacts not in phone
)

object WelcomeMessageDocumentCodec {
    private val gson = Gson()

    fun serialize(documents: List<DocumentFile>): String {
        if (documents.isEmpty()) return ""
        return gson.toJson(documents)
    }

    fun deserialize(json: String): List<DocumentFile> {
        if (json.isBlank()) return emptyList()

        return try {
            val type = object : TypeToken<List<DocumentFile>>() {}.type
            gson.fromJson<List<DocumentFile>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
