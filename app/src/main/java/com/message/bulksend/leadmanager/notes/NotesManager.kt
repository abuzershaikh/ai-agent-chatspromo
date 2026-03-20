package com.message.bulksend.leadmanager.notes

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.message.bulksend.leadmanager.database.LeadManagerDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manager class for Notes operations
 * Handles all note-related business logic
 */
class NotesManager(context: Context) {
    
    private val database = LeadManagerDatabase.getDatabase(context)
    private val noteDao = database.noteDao()
    private val gson = Gson()
    
    /**
     * Get all notes for a lead as Flow (reactive)
     */
    fun getNotesForLead(leadId: String): Flow<List<Note>> {
        return noteDao.getNotesForLead(leadId).map { entities ->
            entities.map { it.toNote() }
        }
    }
    
    /**
     * Get all notes for a lead as list with replies
     */
    suspend fun getNotesForLeadList(leadId: String): List<Note> {
        val notes = noteDao.getNotesForLeadList(leadId)
        return notes.map { entity ->
            val replies = noteDao.getRepliesForNote(entity.id).map { it.toNote() }
            entity.toNote().copy(replies = replies)
        }
    }
    
    /**
     * Get notes grouped by date for timeline display
     */
    suspend fun getNotesGroupedByDate(leadId: String): List<NoteGroup> {
        val notes = getNotesForLeadList(leadId)
        return groupNotesByDate(notes)
    }
    
    /**
     * Get recent notes (for preview in lead detail)
     */
    suspend fun getRecentNotes(leadId: String, limit: Int = 3): List<Note> {
        return noteDao.getRecentNotes(leadId, limit).map { it.toNote() }
    }
    
    /**
     * Get notes count
     */
    suspend fun getNotesCount(leadId: String): Int {
        return noteDao.getNotesCount(leadId)
    }
    
    /**
     * Get single note by ID
     */
    suspend fun getNoteById(id: String): Note? {
        val entity = noteDao.getNoteById(id) ?: return null
        val replies = noteDao.getRepliesForNote(id).map { it.toNote() }
        return entity.toNote().copy(replies = replies)
    }
    
    /**
     * Add a new note
     */
    suspend fun addNote(
        leadId: String,
        title: String,
        content: String,
        noteType: NoteType = NoteType.GENERAL,
        priority: NotePriority = NotePriority.NORMAL,
        tags: List<String> = emptyList(),
        attachments: List<String> = emptyList(),
        parentNoteId: String? = null
    ): Note {
        val now = System.currentTimeMillis()
        val entity = NoteEntity(
            id = UUID.randomUUID().toString(),
            leadId = leadId,
            title = title,
            content = content,
            noteType = noteType,
            priority = priority,
            isPinned = false,
            createdAt = now,
            updatedAt = now,
            tags = gson.toJson(tags),
            attachments = gson.toJson(attachments),
            parentNoteId = parentNoteId
        )
        noteDao.insertNote(entity)
        return entity.toNote()
    }
    
    /**
     * Update existing note
     */
    suspend fun updateNote(
        id: String,
        title: String? = null,
        content: String? = null,
        noteType: NoteType? = null,
        priority: NotePriority? = null,
        tags: List<String>? = null,
        attachments: List<String>? = null
    ): Note? {
        val existing = noteDao.getNoteById(id) ?: return null
        val updated = existing.copy(
            title = title ?: existing.title,
            content = content ?: existing.content,
            noteType = noteType ?: existing.noteType,
            priority = priority ?: existing.priority,
            tags = if (tags != null) gson.toJson(tags) else existing.tags,
            attachments = if (attachments != null) gson.toJson(attachments) else existing.attachments,
            updatedAt = System.currentTimeMillis()
        )
        noteDao.updateNote(updated)
        return updated.toNote()
    }
    
    /**
     * Toggle pin status
     */
    suspend fun togglePin(id: String) {
        val note = noteDao.getNoteById(id) ?: return
        noteDao.togglePin(id, !note.isPinned)
    }
    
    /**
     * Archive note (soft delete)
     */
    suspend fun archiveNote(id: String) {
        noteDao.archiveNote(id)
    }
    
    /**
     * Restore archived note
     */
    suspend fun restoreNote(id: String) {
        noteDao.restoreNote(id)
    }
    
    /**
     * Delete note permanently
     */
    suspend fun deleteNote(id: String) {
        noteDao.deleteNoteById(id)
    }
    
    /**
     * Search notes
     */
    suspend fun searchNotes(leadId: String, query: String): List<Note> {
        return noteDao.searchNotes(leadId, query).map { it.toNote() }
    }
    
    /**
     * Get notes by type
     */
    suspend fun getNotesByType(leadId: String, noteType: NoteType): List<Note> {
        return noteDao.getNotesByType(leadId, noteType).map { it.toNote() }
    }
    
    /**
     * Get pinned notes
     */
    suspend fun getPinnedNotes(leadId: String): List<Note> {
        return noteDao.getPinnedNotes(leadId).map { it.toNote() }
    }
    
    /**
     * Get archived notes
     */
    suspend fun getArchivedNotes(leadId: String): List<Note> {
        return noteDao.getArchivedNotes(leadId).map { it.toNote() }
    }
    
    /**
     * Add reply to a note
     */
    suspend fun addReply(parentNoteId: String, content: String): Note? {
        val parent = noteDao.getNoteById(parentNoteId) ?: return null
        return addNote(
            leadId = parent.leadId,
            title = "Reply",
            content = content,
            noteType = parent.noteType,
            parentNoteId = parentNoteId
        )
    }
    
    /**
     * Quick add note (simplified)
     */
    suspend fun quickAddNote(leadId: String, content: String, noteType: NoteType = NoteType.GENERAL): Note {
        val title = when (noteType) {
            NoteType.CALL_LOG -> "Call Log"
            NoteType.MEETING -> "Meeting Notes"
            NoteType.EMAIL -> "Email Summary"
            NoteType.TASK -> "Task"
            NoteType.IMPORTANT -> "Important Note"
            NoteType.FOLLOW_UP -> "Follow-up Note"
            NoteType.DEAL -> "Deal Update"
            NoteType.FEEDBACK -> "Customer Feedback"
            NoteType.INTERNAL -> "Internal Note"
            else -> "Note"
        }
        return addNote(leadId, title, content, noteType)
    }
    
    /**
     * Group notes by date
     */
    private fun groupNotesByDate(notes: List<Note>): List<NoteGroup> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val displayFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
        val today = dateFormat.format(Date())
        val yesterday = dateFormat.format(Date(System.currentTimeMillis() - 86400000))
        
        return notes.groupBy { note ->
            dateFormat.format(Date(note.createdAt))
        }.map { (dateStr, groupNotes) ->
            val dateLabel = when (dateStr) {
                today -> "Today"
                yesterday -> "Yesterday"
                else -> displayFormat.format(dateFormat.parse(dateStr)!!)
            }
            NoteGroup(
                date = groupNotes.first().createdAt,
                dateLabel = dateLabel,
                notes = groupNotes.sortedByDescending { it.createdAt }
            )
        }.sortedByDescending { it.date }
    }
    
    /**
     * Convert entity to model
     */
    private fun NoteEntity.toNote(): Note {
        val tagsList: List<String> = if (tags.isNotEmpty()) {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                gson.fromJson(tags, type) ?: emptyList()
            } catch (e: Exception) { emptyList() }
        } else emptyList()
        
        val attachmentsList: List<String> = if (attachments.isNotEmpty()) {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                gson.fromJson(attachments, type) ?: emptyList()
            } catch (e: Exception) { emptyList() }
        } else emptyList()
        
        return Note(
            id = id,
            leadId = leadId,
            title = title,
            content = content,
            noteType = noteType,
            priority = priority,
            isPinned = isPinned,
            createdAt = createdAt,
            updatedAt = updatedAt,
            createdBy = createdBy,
            attachments = attachmentsList,
            tags = tagsList,
            isArchived = isArchived,
            parentNoteId = parentNoteId
        )
    }
}
