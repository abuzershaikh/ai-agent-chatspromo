package com.message.bulksend.autorespond.menureply

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Submenu Screen
 */
class SubmenuViewModel(application: Application) : AndroidViewModel(application) {
    
    private val menuReplyManager = MenuReplyManager(application)
    
    private val _state = MutableStateFlow(SubmenuState())
    val state: StateFlow<SubmenuState> = _state.asStateFlow()
    
    /**
     * Set parent ID for submenu
     */
    fun setParentId(parentId: String) {
        _state.value = _state.value.copy(parentId = parentId)
    }
    
    /**
     * Load submenu items for the parent
     */
    fun loadSubmenuItems() {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true)
                
                val parentId = _state.value.parentId
                if (parentId.isNotEmpty()) {
                    val submenuItems = menuReplyManager.getChildrenByParentId(parentId)
                        .mapIndexed { index, item ->
                            item.copy(orderIndex = index)
                        }
                    
                    _state.value = _state.value.copy(
                        submenuItems = submenuItems,
                        isLoading = false,
                        error = null
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Parent ID not set"
                    )
                }
                
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
    
    /**
     * Add new submenu item
     */
    fun addSubmenuItem(title: String, description: String, hasSubmenu: Boolean) {
        viewModelScope.launch {
            try {
                val parentId = _state.value.parentId
                if (parentId.isEmpty()) {
                    _state.value = _state.value.copy(error = "Parent ID not set")
                    return@launch
                }
                
                val currentItems = _state.value.submenuItems
                val newItem = MenuItem(
                    title = title,
                    description = description,
                    orderIndex = currentItems.size,
                    hasSubmenu = hasSubmenu,
                    parentId = parentId
                )
                
                menuReplyManager.addMenuItem(newItem)
                
                _state.value = _state.value.copy(
                    submenuItems = currentItems + newItem
                )
                
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }
    
    /**
     * Update existing submenu item
     */
    fun updateSubmenuItem(item: MenuItem) {
        viewModelScope.launch {
            try {
                menuReplyManager.updateMenuItem(item)
                
                val updatedItems = _state.value.submenuItems.map { existingItem ->
                    if (existingItem.id == item.id) item else existingItem
                }
                
                _state.value = _state.value.copy(submenuItems = updatedItems)
                
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }
    
    /**
     * Delete submenu item
     */
    fun deleteSubmenuItem(itemId: String) {
        viewModelScope.launch {
            try {
                menuReplyManager.deleteMenuItem(itemId)
                
                val updatedItems = _state.value.submenuItems.filter { it.id != itemId }
                    .mapIndexed { index, item ->
                        item.copy(orderIndex = index)
                    }
                
                _state.value = _state.value.copy(submenuItems = updatedItems)
                
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}

/**
 * State for Submenu Screen
 */
data class SubmenuState(
    val parentId: String = "",
    val submenuItems: List<MenuItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)