package com.message.bulksend.autorespond.menureply

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manager class for Menu Reply functionality
 * Handles menu creation, storage, and reply generation
 */
class MenuReplyManager(private val context: Context) {
    
    companion object {
        const val TAG = "MenuReplyManager"
        private const val PREFS_NAME = "menu_reply_prefs"
        private const val KEY_ROOT_MESSAGE = "root_message"
        private const val KEY_IS_ENABLED = "is_enabled"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val database = MenuReplyDatabase.getDatabase(context)
    private val menuDao = database.menuItemDao()
    private val configDao = database.menuConfigDao()
    private val userContextDao = database.userMenuContextDao()
    private val settingsManager = MenuReplySettingsManager(context)
    
    private val inactiveContextRetentionMs = 24 * 60 * 60 * 1000L
    
    /**
     * Save root message
     */
    fun saveRootMessage(message: String) {
        prefs.edit().putString(KEY_ROOT_MESSAGE, message).apply()
        Log.d(TAG, "Root message saved: $message")
    }
    
    /**
     * Get root message
     */
    fun getRootMessage(): String {
        return prefs.getString(KEY_ROOT_MESSAGE, "select option") ?: "select option"
    }
    
    /**
     * Enable/disable menu reply
     */
    fun setMenuReplyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IS_ENABLED, enabled).apply()
        Log.d(TAG, "Menu reply ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Check if menu reply is enabled
     */
    fun isMenuReplyEnabled(): Boolean {
        return prefs.getBoolean(KEY_IS_ENABLED, false)
    }
    
    /**
     * Add new menu item
     */
    suspend fun addMenuItem(item: MenuItem): String = withContext(Dispatchers.IO) {
        try {
            menuDao.insertMenuItem(item)
            Log.d(TAG, "Menu item added: ${item.title}")
            item.id
        } catch (e: Exception) {
            Log.e(TAG, "Error adding menu item: ${e.message}")
            throw e
        }
    }
    
    /**
     * Update menu item
     */
    suspend fun updateMenuItem(item: MenuItem) = withContext(Dispatchers.IO) {
        try {
            val updatedItem = item.copy(updatedAt = System.currentTimeMillis())
            menuDao.updateMenuItem(updatedItem)
            Log.d(TAG, "Menu item updated: ${item.title}")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating menu item: ${e.message}")
            throw e
        }
    }
    
    /**
     * Delete menu item and its children
     */
    suspend fun deleteMenuItem(itemId: String): Unit = withContext(Dispatchers.IO) {
        try {
            // Delete children first
            val children = menuDao.getChildrenByParentId(itemId)
            children.forEach { child ->
                deleteMenuItemRecursive(child.id) // Recursive delete
            }
            
            // Delete the item itself
            menuDao.deleteMenuItem(itemId)
            Log.d(TAG, "Menu item deleted: $itemId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting menu item: ${e.message}")
            throw e
        }
    }
    
    /**
     * Helper function for recursive deletion
     */
    private suspend fun deleteMenuItemRecursive(itemId: String) {
        val children = menuDao.getChildrenByParentId(itemId)
        children.forEach { child ->
            deleteMenuItemRecursive(child.id)
        }
        menuDao.deleteMenuItem(itemId)
    }
    
    /**
     * Get all root menu items (parentId is null)
     */
    suspend fun getRootMenuItems(): List<MenuItem> = withContext(Dispatchers.IO) {
        try {
            menuDao.getRootMenuItems()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting root menu items: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get children of a menu item
     */
    suspend fun getChildrenByParentId(parentId: String): List<MenuItem> = withContext(Dispatchers.IO) {
        try {
            menuDao.getChildrenByParentId(parentId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting children: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get menu item by ID
     */
    suspend fun getMenuItemById(itemId: String): MenuItem? = withContext(Dispatchers.IO) {
        try {
            menuDao.getMenuItemById(itemId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting menu item: ${e.message}")
            null
        }
    }
    
    /**
     * Build complete menu tree
     */
    suspend fun getMenuTree(): MenuTree = withContext(Dispatchers.IO) {
        try {
            val rootItems = getRootMenuItems()
            val rootItemsWithChildren = rootItems.map { buildMenuItemWithChildren(it) }
            
            MenuTree(
                rootMessage = getRootMessage(),
                rootItems = rootItemsWithChildren,
                isEnabled = isMenuReplyEnabled()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error building menu tree: ${e.message}")
            MenuTree()
        }
    }
    
    /**
     * Build menu item with its children recursively
     */
    private suspend fun buildMenuItemWithChildren(item: MenuItem): MenuItemWithChildren {
        val children = if (item.hasSubmenu) {
            val childItems = getChildrenByParentId(item.id)
            childItems.map { buildMenuItemWithChildren(it) }
        } else {
            emptyList()
        }
        
        return MenuItemWithChildren(item, children)
    }
    
    /**
     * Generate menu text for display
     */
    suspend fun generateMenuText(parentId: String? = null): String = withContext(Dispatchers.IO) {
        try {
            generateMenuTextForced(parentId)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating menu text: ${e.message}")
            "Error generating menu"
        }
    }
    
    /**
     * Process incoming message and find matching menu option
     */
    suspend fun processMenuSelection(message: String, currentParentId: String? = null): MenuSelectionResult = withContext(Dispatchers.IO) {
        try {
            if (!isMenuReplyEnabled()) {
                return@withContext MenuSelectionResult.NotEnabled
            }
            
            val items = if (currentParentId == null) {
                getRootMenuItems()
            } else {
                getChildrenByParentId(currentParentId)
            }
            
            if (items.isEmpty()) {
                return@withContext MenuSelectionResult.NoOptions
            }
            
            // Try to parse number selection (1, 2, 3, etc.)
            val trimmedMessage = message.trim()
            val selectedIndex = try {
                trimmedMessage.toInt() - 1 // Convert to 0-based index
            } catch (e: NumberFormatException) {
                -1
            }
            
            // Check if valid selection
            if (selectedIndex >= 0 && selectedIndex < items.size) {
                val selectedItem = items[selectedIndex]
                
                if (selectedItem.hasSubmenu) {
                    // Generate submenu
                    val submenuText = generateMenuText(selectedItem.id)
                    return@withContext MenuSelectionResult.Submenu(selectedItem, submenuText)
                } else {
                    // Final selection
                    return@withContext MenuSelectionResult.FinalSelection(selectedItem)
                }
            }
            
            // Only accept number input - no text matching
            // No match found
            return@withContext MenuSelectionResult.NoMatch
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing menu selection: ${e.message}")
            MenuSelectionResult.Error(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Get all menu items (for export/backup)
     */
    suspend fun getAllMenuItems(): List<MenuItem> = withContext(Dispatchers.IO) {
        try {
            menuDao.getAllMenuItems()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all menu items: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Clear all menu items
     */
    suspend fun clearAllMenuItems() = withContext(Dispatchers.IO) {
        try {
            menuDao.deleteAllMenuItems()
            Log.d(TAG, "All menu items cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing menu items: ${e.message}")
            throw e
        }
    }
    
    /**
     * Import menu items (for restore/backup)
     */
    suspend fun importMenuItems(items: List<MenuItem>) = withContext(Dispatchers.IO) {
        try {
            menuDao.insertMenuItems(items)
            Log.d(TAG, "Imported ${items.size} menu items")
        } catch (e: Exception) {
            Log.e(TAG, "Error importing menu items: ${e.message}")
            throw e
        }
    }
    
    /**
     * Find matching menu reply for incoming message
     * This is called from WhatsAppNotificationListener
     * Now uses Room DB for user context tracking
     */
    suspend fun findMenuReply(incomingMessage: String, currentContext: MenuContext? = null): MenuReplyResult = withContext(Dispatchers.IO) {
        try {
            if (!isMenuReplyEnabled()) {
                return@withContext MenuReplyResult.NotEnabled
            }
            
            // If no current context, start from root
            val parentId = currentContext?.currentParentId
            val result = processMenuSelection(incomingMessage, parentId)
            
            when (result) {
                is MenuSelectionResult.Submenu -> {
                    // User selected a submenu option, return the submenu text
                    val newContext = MenuContext(
                        currentParentId = result.selectedItem.id,
                        breadcrumb = (currentContext?.breadcrumb ?: emptyList()) + result.selectedItem.title,
                        lastInteractionTime = System.currentTimeMillis()
                    )
                    MenuReplyResult.MenuResponse(result.submenuText, newContext)
                }
                is MenuSelectionResult.FinalSelection -> {
                    // User made final selection, return confirmation or action
                    val confirmationMessage = "You selected: ${result.selectedItem.title}"
                    MenuReplyResult.FinalResponse(
                        result.selectedItem.responseMessage.ifEmpty {
                            "You selected: ${result.selectedItem.title}\n\nSend your menu keyword again or reply menu to open the menu."
                        }
                    )
                }
                MenuSelectionResult.GoBack -> {
                    // Go back handled in findMenuReplyForUser
                    val menuText = generateMenuText(parentId)
                    val context = currentContext ?: MenuContext()
                    MenuReplyResult.MenuResponse(menuText, context)
                }
                MenuSelectionResult.NoMatch -> {
                    // No match found, return current menu options
                    val menuText = generateMenuText(parentId)
                    val context = currentContext ?: MenuContext()
                    MenuReplyResult.MenuResponse(menuText, context)
                }
                MenuSelectionResult.NoOptions -> {
                    MenuReplyResult.NoOptions
                }
                MenuSelectionResult.NotEnabled -> {
                    MenuReplyResult.NotEnabled
                }
                is MenuSelectionResult.Error -> {
                    MenuReplyResult.Error(result.message)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error finding menu reply: ${e.message}")
            MenuReplyResult.Error(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Find matching menu reply for incoming message WITH USER CONTEXT TRACKING
     * This is the main function called from WhatsAppNotificationListener
     * @param userId - Phone number or Instagram username to track context
     * @param incomingMessage - The message sent by user
     * @param forceEnabled - Skip isMenuReplyEnabled check without resetting active context
     */
    suspend fun findMenuReplyForUser(userId: String, incomingMessage: String, forceEnabled: Boolean = false): MenuReplyResult = withContext(Dispatchers.IO) {
        try {
            // Skip enabled check if forceEnabled (triggered from keyword)
            if (!forceEnabled && !isMenuReplyEnabled()) {
                Log.d(TAG, "Menu reply not enabled")
                return@withContext MenuReplyResult.NotEnabled
            }
            
            // Clean old inactive contexts first
            cleanExpiredContexts()
            
            // Get user's current context from DB
            var userContext = userContextDao.getUserContext(userId)
            val trimmedMessage = incomingMessage.trim()
            val trimmedMessageLower = trimmedMessage.lowercase()
            val resetToRootCommand = trimmedMessageLower == "0" || trimmedMessageLower == "menu" || trimmedMessageLower == "back" || trimmedMessageLower == "start"
            val restartMenuCommand = trimmedMessageLower == "menu" || trimmedMessageLower == "start"
            
            if (userContext != null && userContext.isActive && isUserContextExpired(userContext)) {
                Log.d(TAG, "User context timed out for $userId, waiting for keyword restart")
                userContextDao.markContextTimedOut(userId, System.currentTimeMillis())
                userContext = userContextDao.getUserContext(userId)
            }
            
            if (restartMenuCommand && userContext?.requiresKeywordRestart == true) {
                Log.d(TAG, "User $userId restarting menu from root")
                return@withContext startFreshMenuForUser(userId)
            }
            
            if (userContext?.requiresKeywordRestart == true) {
                Log.d(TAG, "User $userId timed out - silently waiting for keyword/menu restart")
                return@withContext MenuReplyResult.NoOptions
            }
            
            if (userContext != null && !userContext.isActive) {
                if (restartMenuCommand) {
                    Log.d(TAG, "User $userId manually restarted completed menu flow")
                    return@withContext startFreshMenuForUser(userId)
                }
                Log.d(TAG, "User $userId completed menu flow - ignoring message: $trimmedMessage")
                return@withContext MenuReplyResult.NoOptions
            }
            
            if (userContext == null) {
                Log.d(TAG, "User $userId is new, starting menu flow")
                return@withContext startFreshMenuForUser(userId)
            }
            
            if (resetToRootCommand) {
                Log.d(TAG, "User $userId reset active menu flow to root")
                return@withContext startFreshMenuForUser(userId)
            }
            
            // Get current parent ID from context
            val currentParentId = userContext?.currentParentId
            val currentBreadcrumb = userContext?.breadcrumb?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
            
            Log.d(TAG, "User $userId - Current parent: $currentParentId, Breadcrumb: $currentBreadcrumb")
            
            // Process menu selection (force enabled)
            val result = processMenuSelectionForced(incomingMessage, currentParentId)
            
            when (result) {
                is MenuSelectionResult.Submenu -> {
                    // User selected a submenu option
                    val newBreadcrumb = (currentBreadcrumb + result.selectedItem.title).joinToString(",")
                    
                    // Update user context in DB
                    userContextDao.insertOrUpdateContext(
                        UserMenuContext(
                            userId = userId,
                            currentParentId = result.selectedItem.id,
                            breadcrumb = newBreadcrumb,
                            lastInteractionTime = System.currentTimeMillis(),
                            isActive = true
                        )
                    )
                    
                    Log.d(TAG, "User $userId moved to submenu: ${result.selectedItem.title}")
                    
                    val newContext = MenuContext(
                        currentParentId = result.selectedItem.id,
                        breadcrumb = currentBreadcrumb + result.selectedItem.title,
                        lastInteractionTime = System.currentTimeMillis()
                    )
                    MenuReplyResult.MenuResponse(result.submenuText, newContext)
                }
                
                is MenuSelectionResult.GoBack -> {
                    // User selected "Go Back" - navigate to parent menu
                    if (currentParentId != null) {
                        // Get current menu item to find its parent
                        val currentMenuItem = menuDao.getMenuItemById(currentParentId)
                        val parentOfCurrentId = currentMenuItem?.parentId
                        
                        // Generate parent menu text
                        val parentMenuText = generateMenuTextForced(parentOfCurrentId)
                        
                        // Update breadcrumb - remove last item
                        val newBreadcrumb = if (currentBreadcrumb.isNotEmpty()) {
                            currentBreadcrumb.dropLast(1).joinToString(",")
                        } else ""
                        
                        // Update user context
                        userContextDao.insertOrUpdateContext(
                            UserMenuContext(
                                userId = userId,
                                currentParentId = parentOfCurrentId,
                                breadcrumb = newBreadcrumb,
                                lastInteractionTime = System.currentTimeMillis(),
                                isActive = true
                            )
                        )
                        
                        Log.d(TAG, "User $userId went back to parent menu: $parentOfCurrentId")
                        
                        val newContext = MenuContext(
                            currentParentId = parentOfCurrentId,
                            breadcrumb = currentBreadcrumb.dropLast(1),
                            lastInteractionTime = System.currentTimeMillis()
                        )
                        MenuReplyResult.MenuResponse(parentMenuText, newContext)
                    } else {
                        // Already at root, show root menu
                        val menuText = generateMenuTextForced(null)
                        MenuReplyResult.MenuResponse(menuText, MenuContext())
                    }
                }
                
                is MenuSelectionResult.FinalSelection -> {
                    // User made final selection - reset context
                    userContextDao.resetUserContext(userId, System.currentTimeMillis())
                    
                    Log.d(TAG, "User $userId final selection: ${result.selectedItem.title}")
                    
                    // Use responseMessage if set, otherwise default confirmation
                    val confirmationMessage = if (result.selectedItem.responseMessage.isNotEmpty()) {
                        result.selectedItem.responseMessage
                    } else {
                        "✅ You selected: ${result.selectedItem.title}\n\nReply 0 or 'menu' to start again."
                    }
                    MenuReplyResult.FinalResponse(
                        result.selectedItem.responseMessage.ifEmpty {
                            "You selected: ${result.selectedItem.title}\n\nSend your menu keyword again or reply menu to open the menu."
                        }
                    )
                }
                
                MenuSelectionResult.NoMatch -> {
                    // No match found - handle based on settings
                    val settings = settingsManager.getSettings()
                    
                    Log.d(TAG, "User $userId - No match for: $incomingMessage")
                    
                    if (!settings.defaultReplyEnabled) {
                        // Don't send any reply for invalid option
                        return@withContext MenuReplyResult.NoOptions
                    }
                    
                    var replyText = ""
                    val newParentId: String?
                    val newBreadcrumb: List<String>
                    
                    when (settings.defaultReplyType) {
                        DefaultReplyType.MAIN_MENU -> {
                            // Reset to main menu and update context
                            newParentId = null
                            newBreadcrumb = emptyList()
                            val mainMenuText = generateMenuTextForced(null)
                            replyText = "❌ Invalid option.\n\n$mainMenuText"
                        }
                        DefaultReplyType.SAME_MENU -> {
                            // Show same menu again, keep context
                            newParentId = currentParentId
                            newBreadcrumb = currentBreadcrumb
                            val menuText = generateMenuTextForced(currentParentId)
                            replyText = "❌ Invalid option. Please select from below:\n\n$menuText"
                        }
                        DefaultReplyType.CUSTOM_MESSAGE -> {
                            // Show custom message, keep context
                            newParentId = currentParentId
                            newBreadcrumb = currentBreadcrumb
                            replyText = settings.customReplyMessage
                        }
                    }
                    
                    replyText = replyText
                        .replace(
                            Regex("^[^A-Za-z]*Invalid option\\. Please select from below:"),
                            "Invalid option. Reply with one of the options below:"
                        )
                        .replace(Regex("^[^A-Za-z]*Invalid option\\."), "Invalid option.")
                        .replace("âŒ Invalid option. Please select from below:", "Invalid option. Reply with one of the options below.")
                        .replace("âŒ Invalid option.", "Invalid option.")
                    
                    // Update user context in DB
                    userContextDao.insertOrUpdateContext(
                        UserMenuContext(
                            userId = userId,
                            currentParentId = newParentId,
                            breadcrumb = newBreadcrumb.joinToString(","),
                            lastInteractionTime = System.currentTimeMillis(),
                            isActive = true
                        )
                    )
                    
                    val context = MenuContext(
                        currentParentId = newParentId,
                        breadcrumb = newBreadcrumb,
                        lastInteractionTime = System.currentTimeMillis()
                    )
                    MenuReplyResult.MenuResponse(replyText, context)
                }
                
                MenuSelectionResult.NoOptions -> {
                    Log.d(TAG, "No menu options configured")
                    MenuReplyResult.NoOptions
                }
                
                MenuSelectionResult.NotEnabled -> {
                    MenuReplyResult.NotEnabled
                }
                
                is MenuSelectionResult.Error -> {
                    MenuReplyResult.Error(result.message)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error finding menu reply for user $userId: ${e.message}")
            MenuReplyResult.Error(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Generate menu text (forced - no enabled check)
     * Auto-adds navigation options based on settings:
     * - "Previous Menu" option in submenus (last number) - if enabled
     * - "Main Menu" option (0) in all menus - if enabled
     */
    private suspend fun generateMenuTextForced(parentId: String? = null): String {
        val settings = settingsManager.getSettings()
        
        val items = if (parentId == null) {
            menuDao.getRootMenuItems()
        } else {
            menuDao.getChildrenByParentId(parentId)
        }
        
        if (items.isEmpty()) {
            // If no children, check if parent has submenuMessage
            if (parentId != null) {
                val parentItem = menuDao.getMenuItemById(parentId)
                if (parentItem != null && parentItem.submenuMessage.isNotEmpty()) {
                    return formatHeader(parentItem.submenuMessage, settings)
                }
            }
            return formatHeader(if (parentId == null) getRootMessage() else "No options available", settings)
        }
        
        // Get message - use parent's submenuMessage if in submenu
        val message = if (parentId == null) {
            getRootMessage()
        } else {
            val parentItem = menuDao.getMenuItemById(parentId)
            if (parentItem != null && parentItem.submenuMessage.isNotEmpty()) {
                parentItem.submenuMessage
            } else {
                "Select an option:"
            }
        }
        
        val optionItems = items.mapIndexed { index, item ->
            "${index + 1}. ${item.title}"
        }
        
        // Build navigation options
        val navigationItems = mutableListOf<String>()
        
        // Add "Previous Menu" option only in submenus (not root) - if enabled
        if (parentId != null && settings.showPreviousMenuOption) {
            val goBackNumber = items.size + 1
            navigationItems += "$goBackNumber. ${if (settings.showEmojis) "< " else ""}Previous menu"
        }
        
        // Add "Main Menu" option - if enabled
        if (settings.showMainMenuOption) {
            navigationItems += "0. ${if (settings.showEmojis) "# " else ""}Main menu"
        }
        
        return buildMenuText(message, optionItems, navigationItems, settings)
    }
    
    /**
     * Process menu selection (forced - no enabled check)
     * Handles navigation options:
     * - Last number in submenu = Go Back to previous menu
     * - 0 = Main Menu (handled in findMenuReplyForUser)
     */
    private suspend fun processMenuSelectionForced(message: String, currentParentId: String? = null): MenuSelectionResult {
        val settings = settingsManager.getSettings()
        val items = if (currentParentId == null) {
            menuDao.getRootMenuItems()
        } else {
            menuDao.getChildrenByParentId(currentParentId)
        }
        
        if (items.isEmpty()) {
            return MenuSelectionResult.NoOptions
        }
        
        // Try to parse number selection (1, 2, 3, etc.)
        val trimmedMessage = message.trim()
        val selectedNumber = try {
            trimmedMessage.toInt()
        } catch (e: NumberFormatException) {
            -1
        }
        
        // Check if "Go Back" option selected (last number in submenu)
        if (currentParentId != null && settings.showPreviousMenuOption && selectedNumber == items.size + 1) {
            Log.d(TAG, "Go Back selected, returning to previous menu")
            return MenuSelectionResult.GoBack
        }
        
        val selectedIndex = selectedNumber - 1 // Convert to 0-based index
        
        // Check if valid selection
        if (selectedIndex >= 0 && selectedIndex < items.size) {
            val selectedItem = items[selectedIndex]
            
            Log.d(TAG, "Selected item: ${selectedItem.title}, hasSubmenu: ${selectedItem.hasSubmenu}, id: ${selectedItem.id}")
            
            if (selectedItem.hasSubmenu) {
                // Check if submenu has children
                val submenuChildren = menuDao.getChildrenByParentId(selectedItem.id)
                
                Log.d(TAG, "Submenu children count: ${submenuChildren.size} for item: ${selectedItem.title}")
                
                if (submenuChildren.isEmpty()) {
                    // No children - treat submenuMessage as final response
                    // This handles the case where user added submenu but only put a message, no options
                    Log.d(TAG, "Submenu has no children, treating as final selection with submenuMessage: ${selectedItem.submenuMessage}")
                    return MenuSelectionResult.FinalSelection(selectedItem.copy(
                        responseMessage = if (selectedItem.submenuMessage.isNotEmpty()) {
                            selectedItem.submenuMessage
                        } else {
                            selectedItem.responseMessage
                        }
                    ))
                }
                
                // Generate submenu
                val submenuText = generateMenuTextForced(selectedItem.id)
                return MenuSelectionResult.Submenu(selectedItem, submenuText)
            } else {
                // Final selection
                Log.d(TAG, "Final selection (no submenu): ${selectedItem.title}, responseMessage: ${selectedItem.responseMessage}")
                return MenuSelectionResult.FinalSelection(selectedItem)
            }
        }
        
        // Only accept number input - no text matching
        // If user sends non-number text, return NoMatch
        Log.d(TAG, "Invalid input (not a valid number): $trimmedMessage")
        
        // No match found
        return MenuSelectionResult.NoMatch
    }
    
    /**
     * Get user's current menu context from DB
     */
    suspend fun getUserContext(userId: String): UserMenuContext? = withContext(Dispatchers.IO) {
        try {
            userContextDao.getUserContext(userId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user context: ${e.message}")
            null
        }
    }
    
    /**
     * Reset user's menu context (back to root)
     */
    suspend fun resetUserContext(userId: String) = withContext(Dispatchers.IO) {
        try {
            userContextDao.resetUserContext(userId, System.currentTimeMillis())
            Log.d(TAG, "User context reset for: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting user context: ${e.message}")
        }
    }
    
    /**
     * Check if user context has expired
     */
    private fun isUserContextExpired(context: UserMenuContext): Boolean {
        return (System.currentTimeMillis() - context.lastInteractionTime) > settingsManager.getMenuTimeoutMillis()
    }
    
    /**
     * Clean expired contexts from DB
     */
    private suspend fun cleanExpiredContexts() {
        try {
            val expiryTime = System.currentTimeMillis() - inactiveContextRetentionMs
            userContextDao.deleteInactiveContexts(expiryTime)
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning expired contexts: ${e.message}")
        }
    }
    
    /**
     * Get initial menu for new conversation
     */
    suspend fun getInitialMenu(): String = withContext(Dispatchers.IO) {
        generateMenuText(null)
    }
    
    /**
     * Reset menu context (for new conversation or timeout)
     */
    fun resetMenuContext(): MenuContext {
        return MenuContext()
    }
    
    /**
     * Check if menu context has expired (30 minutes timeout)
     */
    fun isMenuContextExpired(context: MenuContext): Boolean {
        val timeoutMs = settingsManager.getMenuTimeoutMillis()
        return (System.currentTimeMillis() - context.lastInteractionTime) > timeoutMs
    }
    
    private suspend fun startFreshMenuForUser(userId: String): MenuReplyResult.MenuResponse {
        val menuText = generateMenuTextForced(null)
        userContextDao.insertOrUpdateContext(
            UserMenuContext(
                userId = userId,
                currentParentId = null,
                breadcrumb = "",
                lastInteractionTime = System.currentTimeMillis(),
                isActive = true,
                requiresKeywordRestart = false
            )
        )
        return MenuReplyResult.MenuResponse(menuText, MenuContext())
    }
    
    private fun buildSessionExpiredMessage(): String {
        val timeoutSeconds = settingsManager.getMenuTimeoutSeconds()
        return "Menu session timed out after ${formatTimeoutLabel(timeoutSeconds)}.\n\nSend your menu keyword again or reply menu to reopen the options."
    }
    
    private fun formatTimeoutLabel(seconds: Int): String {
        return if (seconds >= 60 && seconds % 60 == 0) {
            val minutes = seconds / 60
            "$minutes minute" + if (minutes == 1) "" else "s"
        } else {
            "$seconds seconds"
        }
    }
    
    private fun buildMenuText(
        message: String,
        optionItems: List<String>,
        navigationItems: List<String>,
        settings: MenuReplySettings
    ): String {
        val sections = mutableListOf<String>()
        sections += formatHeader(message, settings)

        val menuLines = mutableListOf<String>()
        if (settings.showSelectionHint) {
            menuLines += "Reply with a number to continue."
        }
        menuLines += optionItems
        menuLines += navigationItems

        sections += menuLines.joinToString("\n")

        return sections
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }
    
    private fun formatHeader(message: String, settings: MenuReplySettings): String {
        val cleanMessage = message.trim().ifEmpty { "Select an option" }
        return if (settings.boldHeader && !(cleanMessage.startsWith("*") && cleanMessage.endsWith("*"))) {
            "*$cleanMessage*"
        } else {
            cleanMessage
        }
    }
}

/**
 * Result of menu selection processing
 */
sealed class MenuSelectionResult {
    object NotEnabled : MenuSelectionResult()
    object NoOptions : MenuSelectionResult()
    object NoMatch : MenuSelectionResult()
    object GoBack : MenuSelectionResult() // User selected "Go Back" option
    data class Submenu(val selectedItem: MenuItem, val submenuText: String) : MenuSelectionResult()
    data class FinalSelection(val selectedItem: MenuItem) : MenuSelectionResult()
    data class Error(val message: String) : MenuSelectionResult()
}
