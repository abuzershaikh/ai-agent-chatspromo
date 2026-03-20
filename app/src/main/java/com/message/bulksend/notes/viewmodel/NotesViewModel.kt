package com.message.bulksend.notes.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.message.bulksend.notes.database.NoteEntity
import com.message.bulksend.notes.repository.NotesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotesViewModel(application: Application) : AndroidViewModel(application) {
    
    val repository = NotesRepository(application)
    
    private val _notes = MutableStateFlow<List<NoteEntity>>(emptyList())
    val notes: StateFlow<List<NoteEntity>> = _notes.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    init {
        loadNotes()
    }
    
    private fun loadNotes() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getAllNotes().collect { notesList ->
                _notes.value = notesList
                _isLoading.value = false
            }
        }
    }
    
    fun insertNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.insertNote(note)
        }
    }
    
    fun updateNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.updateNote(note)
        }
    }
    
    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            repository.softDeleteNote(noteId)
        }
    }
    
    fun toggleFavorite(noteId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.toggleFavoriteStatus(noteId, isFavorite)
        }
    }
    
    fun togglePin(noteId: Long, isPinned: Boolean) {
        viewModelScope.launch {
            repository.togglePinStatus(noteId, isPinned)
        }
    }
    
    fun searchNotes(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                loadNotes()
            } else {
                repository.searchNotes(query).collect { searchResults ->
                    _notes.value = searchResults
                }
            }
        }
    }
    
    fun getNotesByCategory(category: com.message.bulksend.notes.NoteCategory) {
        viewModelScope.launch {
            repository.getNotesByCategory(category).collect { categoryNotes ->
                _notes.value = categoryNotes
            }
        }
    }
    
    fun getFavoriteNotes() {
        viewModelScope.launch {
            repository.getFavoriteNotes().collect { favoriteNotes ->
                _notes.value = favoriteNotes
            }
        }
    }
}