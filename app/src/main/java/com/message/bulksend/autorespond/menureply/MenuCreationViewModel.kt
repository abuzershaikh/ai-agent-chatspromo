package com.message.bulksend.autorespond.menureply

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Menu Creation Screen
 */
class MenuCreationViewModel(application: Application) : AndroidViewModel(application) {
    
    private val menuReplyManager = MenuReplyManager(application)
    
    private val _state = MutableStateFlow(MenuCreationState())
    val state: StateFlow<MenuCreationState> = _state.asStateFlow()
    
    init {
        loadMenuItems()
    }
    
    /**
     * Load existing menu items
     */
    fun loadMenuItems() {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true)
                
                val rootMessage = menuReplyManager.getRootMessage()
                val menuItems = menuReplyManager.getRootMenuItems()
                
                _state.value = _state.value.copy(
                    rootMessage = rootMessage,
                    menuItems = menuItems.mapIndexed { index, item ->
                        item.copy(orderIndex = index)
                    },
                    isLoading = false,
                    error = null
                )
                
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
    
    /**
     * Update root message
     */
    fun updateRootMessage(message: String) {
        _state.value = _state.value.copy(rootMessage = message)
    }
    
    /**
     * Add new menu item
     */
    fun addMenuItem(title: String, description: String, hasSubmenu: Boolean) {
        viewModelScope.launch {
            try {
                val currentItems = _state.value.menuItems
                val newItem = MenuItem(
                    title = title,
                    description = description,
                    orderIndex = currentItems.size,
                    hasSubmenu = hasSubmenu,
                    parentId = null // Root level item
                )
                
                menuReplyManager.addMenuItem(newItem)
                
                _state.value = _state.value.copy(
                    menuItems = currentItems + newItem
                )
                
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }
    
    /**
     * Update existing menu item
     */
    fun updateMenuItem(item: MenuItem) {
        viewModelScope.launch {
            try {
                menuReplyManager.updateMenuItem(item)
                
                val updatedItems = _state.value.menuItems.map { existingItem ->
                    if (existingItem.id == item.id) item else existingItem
                }
                
                _state.value = _state.value.copy(menuItems = updatedItems)
                
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }
    
    /**
     * Delete menu item
     */
    fun deleteMenuItem(itemId: String) {
        viewModelScope.launch {
            try {
                menuReplyManager.deleteMenuItem(itemId)
                
                val updatedItems = _state.value.menuItems.filter { it.id != itemId }
                    .mapIndexed { index, item ->
                        item.copy(orderIndex = index)
                    }
                
                _state.value = _state.value.copy(menuItems = updatedItems)
                
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }
    
    /**
     * Toggle submenu for an item
     */
    fun toggleSubmenu(itemId: String) {
        viewModelScope.launch {
            try {
                val item = _state.value.menuItems.find { it.id == itemId }
                if (item != null) {
                    val updatedItem = item.copy(hasSubmenu = !item.hasSubmenu)
                    updateMenuItem(updatedItem)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }
    
    /**
     * Save menu configuration
     */
    fun saveMenu() {
        viewModelScope.launch {
            try {
                // Save root message
                menuReplyManager.saveRootMessage(_state.value.rootMessage)
                
                // Enable menu reply
                menuReplyManager.setMenuReplyEnabled(true)
                
                _state.value = _state.value.copy(error = null)
                
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }
    
    /**
     * Generate preview text for message bubble
     */
    fun generatePreviewText(): String {
        val rootMessage = _state.value.rootMessage.ifEmpty { "select option" }
        val items = _state.value.menuItems
        
        if (items.isEmpty()) {
            return rootMessage
        }
        
        val options = items.mapIndexed { index, item ->
            "${index + 1}. ${item.title}"
        }.joinToString("\n")
        
        return "$rootMessage\n$options"
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}