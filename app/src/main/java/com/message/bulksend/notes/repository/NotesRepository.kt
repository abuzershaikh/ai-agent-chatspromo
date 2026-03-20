package com.message.bulksend.notes.repository

import android.content.Context
import com.message.bulksend.notes.NoteCategory
import com.message.bulksend.notes.database.NoteEntity
import com.message.bulksend.notes.database.NotesDatabase
import kotlinx.coroutines.flow.Flow

class NotesRepository(context: Context) {
    
    private val noteDao = NotesDatabase.getDatabase(context).noteDao()
    
    fun getAllNotes(): Flow<List<NoteEntity>> = noteDao.getAllNotes()
    
    fun getNotesByCategory(category: NoteCategory): Flow<List<NoteEntity>> = 
        noteDao.getNotesByCategory(category.name)
    
    fun getFavoriteNotes(): Flow<List<NoteEntity>> = noteDao.getFavoriteNotes()
    
    fun getPinnedNotes(): Flow<List<NoteEntity>> = noteDao.getPinnedNotes()
    
    fun searchNotes(query: String): Flow<List<NoteEntity>> = noteDao.searchNotes(query)
    
    suspend fun getNoteById(id: Long): NoteEntity? = noteDao.getNoteById(id)
    
    suspend fun insertNote(note: NoteEntity): Long = noteDao.insertNote(note)
    
    suspend fun updateNote(note: NoteEntity) = noteDao.updateNote(note)
    
    suspend fun deleteNote(note: NoteEntity) = noteDao.deleteNote(note)
    
    suspend fun softDeleteNote(id: Long) = noteDao.softDeleteNote(id)
    
    suspend fun togglePinStatus(id: Long, isPinned: Boolean) = 
        noteDao.updatePinStatus(id, isPinned)
    
    suspend fun toggleFavoriteStatus(id: Long, isFavorite: Boolean) = 
        noteDao.updateFavoriteStatus(id, isFavorite)
    
    suspend fun deleteAllSoftDeletedNotes() = noteDao.deleteAllSoftDeletedNotes()
    
    suspend fun getNotesCount(): Int = noteDao.getNotesCount()
    
    suspend fun getNotesCountByCategory(category: NoteCategory): Int = 
        noteDao.getNotesCountByCategory(category.name)
}