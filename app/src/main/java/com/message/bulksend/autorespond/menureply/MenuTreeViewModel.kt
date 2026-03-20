package com.message.bulksend.autorespond.menureply

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Menu Tree View Screen
 */
class MenuTreeViewModel(application: Application) : AndroidViewModel(application) {
    
    private val menuReplyManager = MenuReplyManager(application)
    
    private val _state = MutableStateFlow(TreeViewState())
    val state: StateFlow<TreeViewState> = _state.asStateFlow()
    
    init {
        loadMenuTree()
    }
    
    /**
     * Load complete menu tree
     */
    fun loadMenuTree() {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true)
                
                val menuTree = menuReplyManager.getMenuTree()
                
                _state.value = _state.value.copy(
                    menuTree = menuTree,
                    isLoading = false
                )
                
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    menuTree = null
                )
            }
        }
    }
    
    /**
     * Toggle node expansion in tree view
     */
    fun toggleNodeExpansion(nodeId: String) {
        val currentExpanded = _state.value.expandedNodes
        val newExpanded = if (currentExpanded.contains(nodeId)) {
            currentExpanded - nodeId
        } else {
            currentExpanded + nodeId
        }
        
        _state.value = _state.value.copy(expandedNodes = newExpanded)
    }
    
    /**
     * Select a node in tree view
     */
    fun selectNode(nodeId: String) {
        _state.value = _state.value.copy(selectedNodeId = nodeId)
    }
    
    /**
     * Clear node selection
     */
    fun clearSelection() {
        _state.value = _state.value.copy(selectedNodeId = null)
    }
    
    /**
     * Refresh menu tree
     */
    fun refreshMenuTree() {
        loadMenuTree()
    }
}