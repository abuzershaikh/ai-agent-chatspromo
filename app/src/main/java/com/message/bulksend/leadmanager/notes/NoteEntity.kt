package com.message.bulksend.leadmanager.notes

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.message.bulksend.leadmanager.database.entities.LeadEntity

/**
 * Note types for categorization
 */
enum class NoteType(val displayName: String, val icon: String, val color: Long) {
    GENERAL("General Note", "note", 0xFF3B82F6),
    CALL_LOG("Call Log", "phone", 0xFF10B981),
    MEETING("Meeting Notes", "groups", 0xFF8B5CF6),
    EMAIL("Email Summary", "email", 0xFFEC4899),
    TASK("Task/Action Item", "task", 0xFFF59E0B),
    IMPORTANT("Important", "star", 0xFFEF4444),
    FOLLOW_UP("Follow-up Note", "schedule", 0xFF06B6D4),
    DEAL("Deal/Negotiation", "handshake", 0xFF22C55E),
    FEEDBACK("Customer Feedback", "feedback", 0xFFFF6B6B),
    INTERNAL("Internal Note", "lock", 0xFF64748B)
}

/**
 * Note priority levels
 */
enum class NotePriority(val displayName: String, val color: Long) {
    LOW("Low", 0xFF10B981),
    NORMAL("Normal", 0xFF3B82F6),
    HIGH("High", 0xFFF59E0B),
    URGENT("Urgent", 0xFFEF4444)
}

/**
 * Note entity for storing lead notes with timeline support
 */
@Entity(
    tableName = "lead_notes",
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
data class NoteEntity(
    @PrimaryKey
    val id: String,
    val leadId: String,
    val title: String,
    val content: String,
    val noteType: NoteType = NoteType.GENERAL,
    val priority: NotePriority = NotePriority.NORMAL,
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val createdBy: String = "User", // For future multi-user support
    val attachments: String = "", // JSON array of attachment URIs
    val tags: String = "", // JSON array of tags
    val isArchived: Boolean = false,
    val parentNoteId: String? = null // For threaded/reply notes
)

/**
 * UI Model for Note with additional computed properties
 */
data class Note(
    val id: String,
    val leadId: String,
    val title: String,
    val content: String,
    val noteType: NoteType = NoteType.GENERAL,
    val priority: NotePriority = NotePriority.NORMAL,
    val isPinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val createdBy: String = "User",
    val attachments: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val isArchived: Boolean = false,
    val parentNoteId: String? = null,
    val replies: List<Note> = emptyList() // Child notes/replies
) {
    val isEdited: Boolean get() = updatedAt > createdAt + 1000 // 1 second buffer
    val hasAttachments: Boolean get() = attachments.isNotEmpty()
    val isReply: Boolean get() = parentNoteId != null
}

/**
 * Note group by date for timeline display
 */
data class NoteGroup(
    val date: Long,
    val dateLabel: String,
    val notes: List<Note>
)
