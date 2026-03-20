package com.message.bulksend.notes.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    
    @Query("SELECT * FROM notes WHERE isDeleted = 0 ORDER BY isPinned DESC, lastModified DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>
    
    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND category = :category ORDER BY isPinned DESC, lastModified DESC")
    fun getNotesByCategory(category: String): Flow<List<NoteEntity>>
    
    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND isFavorite = 1 ORDER BY isPinned DESC, lastModified DESC")
    fun getFavoriteNotes(): Flow<List<NoteEntity>>
    
    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND isPinned = 1 ORDER BY lastModified DESC")
    fun getPinnedNotes(): Flow<List<NoteEntity>>
    
    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%') ORDER BY isPinned DESC, lastModified DESC")
    fun searchNotes(query: String): Flow<List<NoteEntity>>
    
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Long): NoteEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long
    
    @Update
    suspend fun updateNote(note: NoteEntity)
    
    @Delete
    suspend fun deleteNote(note: NoteEntity)
    
    @Query("UPDATE notes SET isDeleted = 1, lastModified = :timestamp WHERE id = :id")
    suspend fun softDeleteNote(id: Long, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE notes SET isPinned = :isPinned, lastModified = :timestamp WHERE id = :id")
    suspend fun updatePinStatus(id: Long, isPinned: Boolean, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE notes SET isFavorite = :isFavorite, lastModified = :timestamp WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Long, isFavorite: Boolean, timestamp: Long = System.currentTimeMillis())
    
    @Query("DELETE FROM notes WHERE isDeleted = 1")
    suspend fun deleteAllSoftDeletedNotes()
    
    @Query("SELECT COUNT(*) FROM notes WHERE isDeleted = 0")
    suspend fun getNotesCount(): Int
    
    @Query("SELECT COUNT(*) FROM notes WHERE isDeleted = 0 AND category = :category")
    suspend fun getNotesCountByCategory(category: String): Int
}