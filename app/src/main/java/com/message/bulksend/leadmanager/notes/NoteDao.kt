package com.message.bulksend.leadmanager.notes

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    
    // Get all notes for a lead (newest first, pinned at top)
    @Query("""
        SELECT * FROM lead_notes 
        WHERE leadId = :leadId AND isArchived = 0 AND parentNoteId IS NULL
        ORDER BY isPinned DESC, createdAt DESC
    """)
    fun getNotesForLead(leadId: String): Flow<List<NoteEntity>>
    
    @Query("""
        SELECT * FROM lead_notes 
        WHERE leadId = :leadId AND isArchived = 0 AND parentNoteId IS NULL
        ORDER BY isPinned DESC, createdAt DESC
    """)
    suspend fun getNotesForLeadList(leadId: String): List<NoteEntity>
    
    // Get replies for a note
    @Query("""
        SELECT * FROM lead_notes 
        WHERE parentNoteId = :noteId AND isArchived = 0
        ORDER BY createdAt ASC
    """)
    suspend fun getRepliesForNote(noteId: String): List<NoteEntity>
    
    // Get single note by ID
    @Query("SELECT * FROM lead_notes WHERE id = :id")
    suspend fun getNoteById(id: String): NoteEntity?
    
    // Get notes by type
    @Query("""
        SELECT * FROM lead_notes 
        WHERE leadId = :leadId AND noteType = :noteType AND isArchived = 0
        ORDER BY createdAt DESC
    """)
    suspend fun getNotesByType(leadId: String, noteType: NoteType): List<NoteEntity>
    
    // Get pinned notes
    @Query("""
        SELECT * FROM lead_notes 
        WHERE leadId = :leadId AND isPinned = 1 AND isArchived = 0
        ORDER BY createdAt DESC
    """)
    suspend fun getPinnedNotes(leadId: String): List<NoteEntity>
    
    // Search notes
    @Query("""
        SELECT * FROM lead_notes 
        WHERE leadId = :leadId AND isArchived = 0
        AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%')
        ORDER BY createdAt DESC
    """)
    suspend fun searchNotes(leadId: String, query: String): List<NoteEntity>
    
    // Get notes count for lead
    @Query("SELECT COUNT(*) FROM lead_notes WHERE leadId = :leadId AND isArchived = 0")
    suspend fun getNotesCount(leadId: String): Int
    
    // Get recent notes (last 5)
    @Query("""
        SELECT * FROM lead_notes 
        WHERE leadId = :leadId AND isArchived = 0 AND parentNoteId IS NULL
        ORDER BY createdAt DESC 
        LIMIT :limit
    """)
    suspend fun getRecentNotes(leadId: String, limit: Int = 5): List<NoteEntity>
    
    // Insert
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>)
    
    // Update
    @Update
    suspend fun updateNote(note: NoteEntity)
    
    // Toggle pin
    @Query("UPDATE lead_notes SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun togglePin(id: String, isPinned: Boolean, updatedAt: Long = System.currentTimeMillis())
    
    // Archive note
    @Query("UPDATE lead_notes SET isArchived = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun archiveNote(id: String, updatedAt: Long = System.currentTimeMillis())
    
    // Delete
    @Query("DELETE FROM lead_notes WHERE id = :id")
    suspend fun deleteNoteById(id: String)
    
    @Query("DELETE FROM lead_notes WHERE leadId = :leadId")
    suspend fun deleteAllNotesForLead(leadId: String)
    
    // Get archived notes
    @Query("""
        SELECT * FROM lead_notes 
        WHERE leadId = :leadId AND isArchived = 1
        ORDER BY updatedAt DESC
    """)
    suspend fun getArchivedNotes(leadId: String): List<NoteEntity>
    
    // Restore from archive
    @Query("UPDATE lead_notes SET isArchived = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restoreNote(id: String, updatedAt: Long = System.currentTimeMillis())
    
    // Get all notes for backup
    @Query("SELECT * FROM lead_notes ORDER BY createdAt DESC")
    suspend fun getAllNotesList(): List<NoteEntity>
    
    // Get total notes count
    @Query("SELECT COUNT(*) FROM lead_notes")
    suspend fun getNotesCount(): Int
}
