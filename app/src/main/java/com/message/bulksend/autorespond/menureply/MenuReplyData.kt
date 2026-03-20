package com.message.bulksend.autorespond.menureply

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Data class for Menu Item
 */
@Entity(tableName = "menu_items")
data class MenuItem(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val parentId: String? = null, // null for root items
    val title: String,
    val description: String = "",
    val responseMessage: String = "", // Message to show when this item is selected (if no submenu)
    val submenuMessage: String = "select option", // Message to show at top of submenu
    val orderIndex: Int = 0,
    val hasSubmenu: Boolean = false,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Data class for Menu Tree structure
 */
data class MenuTree(
    val rootMessage: String = "select option",
    val rootItems: List<MenuItemWithChildren> = emptyList(),
    val isEnabled: Boolean = true
)

/**
 * Menu item with its children for tree display
 */
data class MenuItemWithChildren(
    val item: MenuItem,
    val children: List<MenuItemWithChildren> = emptyList()
)

/**
 * Menu creation state for UI
 */
data class MenuCreationState(
    val rootMessage: String = "select option",
    val menuItems: List<MenuItem> = emptyList(),
    val currentParentId: String? = null,
    val previewText: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * Tree view state for UI
 */
data class TreeViewState(
    val menuTree: MenuTree? = null,
    val expandedNodes: Set<String> = emptySet(),
    val selectedNodeId: String? = null,
    val isLoading: Boolean = false
)

/**
 * Dialog states for UI
 */
data class DialogState(
    val showAddItemDialog: Boolean = false,
    val showEditItemDialog: Boolean = false,
    val showDeleteConfirmDialog: Boolean = false,
    val editingItem: MenuItem? = null
)

/**
 * Menu reply configuration
 */
@Entity(tableName = "menu_reply_config")
data class MenuReplyConfig(
    @PrimaryKey
    val id: String = "default",
    val rootMessage: String = "select option",
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Menu context for tracking user's position in menu tree (in-memory)
 */
data class MenuContext(
    val currentParentId: String? = null,
    val breadcrumb: List<String> = emptyList(),
    val lastInteractionTime: Long = System.currentTimeMillis()
)

/**
 * User Menu Context Entity - Tracks each user's position in menu tree (Room DB)
 */
@Entity(tableName = "user_menu_context")
data class UserMenuContext(
    @PrimaryKey
    val userId: String, // Phone number or Instagram username
    val currentParentId: String? = null, // Current menu level (null = root)
    val breadcrumb: String = "", // Comma-separated path like "Option1,SubOption2"
    val lastInteractionTime: Long = System.currentTimeMillis(),
    val isActive: Boolean = true, // Is user currently in menu flow
    val requiresKeywordRestart: Boolean = false // Session timed out and needs keyword/menu trigger
)

/**
 * Result of menu reply processing
 */
sealed class MenuReplyResult {
    object NotEnabled : MenuReplyResult()
    object NoOptions : MenuReplyResult()
    data class MenuResponse(val menuText: String, val context: MenuContext) : MenuReplyResult()
    data class FinalResponse(val responseText: String) : MenuReplyResult()
    data class SessionExpired(val message: String) : MenuReplyResult()
    data class Error(val message: String) : MenuReplyResult()
}
