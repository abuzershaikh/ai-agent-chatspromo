package com.message.bulksend.autorespond.ai.history

import android.content.Context
import com.message.bulksend.autorespond.ai.intent.IntentResult
import com.message.bulksend.autorespond.ai.settings.AIAgentAdvancedSettings
import com.message.bulksend.tablesheet.data.TableSheetDatabase
import com.message.bulksend.tablesheet.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * AI Agent History Manager
 * Manages auto-creation and updating of history sheets
 */
class AIAgentHistoryManager(private val context: Context) {
    
    private val advancedSettings = AIAgentAdvancedSettings(context)
    private val database = TableSheetDatabase.getDatabase(context)
    private val folderDao = database.folderDao()
    private val tableDao = database.tableDao()
    private val columnDao = database.columnDao()
    private val rowDao = database.rowDao()
    private val cellDao = database.cellDao()
    
    companion object {
        const val HISTORY_FOLDER_NAME = "AI Agent History"
        const val INTENT_SHEET_NAME = "Intent Detection Log"
        const val PROFILE_SHEET_NAME = "User Profiles"
        const val CONVERSATION_SHEET_NAME = "Conversation Log"
    }

    /**
     * Initialize AI Agent History folder and sheets
     */
    suspend fun initializeHistorySystem() = withContext(Dispatchers.IO) {
        try {
            // Create folder if not exists
            val folder = ensureHistoryFolderExists()
            
            // Create sheets if enabled
            if (advancedSettings.autoCreateHistorySheets) {
                ensureIntentSheetExists(folder.id)
                ensureConversationSheetExists(folder.id)
            }
            
            if (advancedSettings.autoCreateProfileSheet) {
                ensureProfileSheetExists(folder.id)
            }
            
            android.util.Log.d("AIHistoryManager", "History system initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("AIHistoryManager", "Failed to initialize history system: ${e.message}")
        }
    }

    /**
     * Ensure "AI Agent History" folder exists
     */
    private suspend fun ensureHistoryFolderExists(): FolderModel {
        var folder = folderDao.getFolderByName(HISTORY_FOLDER_NAME)
        
        if (folder == null) {
            val folderId = folderDao.insertFolder(
                FolderModel(
                    name = HISTORY_FOLDER_NAME,
                    colorHex = "#9C27B0" // Purple color for history
                )
            )
            folder = folderDao.getFolderById(folderId)!!
            android.util.Log.d("AIHistoryManager", "Created history folder: $HISTORY_FOLDER_NAME")
        }
        
        return folder
    }

    /**
     * Ensure Intent Detection Log sheet exists
     * Pre-creation Strategy: Create with extra rows & columns
     */
    private suspend fun ensureIntentSheetExists(folderId: Long): TableModel {
        // Check if sheet already exists
        val existingTables = tableDao.getTablesByFolderIdSync(folderId)
        var intentSheet = existingTables.find { it.name == INTENT_SHEET_NAME }
        
        if (intentSheet != null) {
            // Sheet exists - just configure it
            configureIntentSheet(intentSheet)
            return intentSheet
        } else {
            // Create pre-built sheet with extra capacity
            return createPreBuiltIntentSheet(folderId)
        }
    }
    
    /**
     * Create pre-built sheet with 100 empty rows and 20 columns
     */
    private suspend fun createPreBuiltIntentSheet(folderId: Long): TableModel {
        val tableId = tableDao.insertTable(
            TableModel(
                name = INTENT_SHEET_NAME,
                description = "Auto-generated log of detected user intents",
                folderId = folderId,
                columnCount = 20, // 7 required + 13 extra
                rowCount = 100 // Pre-create 100 rows
            )
        )
        
        // Create 20 columns (7 named + 13 extra)
        val columnNames = listOf(
            "Timestamp", "Phone Number", "User Name", "Message", 
            "Intent", "Confidence", "Priority"
        )
        
        // Add named columns with proper types
        columnNames.forEachIndexed { index, name ->
            columnDao.insertColumn(
                ColumnModel(
                    tableId = tableId,
                    name = name,
                    type = when(name) {
                        "Phone Number" -> ColumnType.PHONE
                        "Confidence" -> ColumnType.INTEGER
                        "Priority" -> ColumnType.PRIORITY
                        else -> ColumnType.STRING
                    },
                    orderIndex = index
                )
            )
        }
        
        // Add 13 extra columns
        repeat(13) { i ->
            columnDao.insertColumn(
                ColumnModel(
                    tableId = tableId,
                    name = "Column ${i + 8}",
                    type = ColumnType.STRING,
                    orderIndex = 7 + i
                )
            )
        }
        
        // Pre-create 100 empty rows
        repeat(100) { i ->
            rowDao.insertRow(RowModel(tableId = tableId, orderIndex = i))
        }
        
        val intentSheet = tableDao.getTableById(tableId)!!
        android.util.Log.d("AIHistoryManager", "Pre-created intent sheet: 100 rows x 20 columns")
        return intentSheet
    }
    
    /**
     * Configure existing sheet (rename columns, set types)
     */
    private suspend fun configureIntentSheet(sheet: TableModel) {
        try {
            val columns = columnDao.getColumnsByTableIdSync(sheet.id)
            val columnConfig = mapOf(
                0 to Pair("Timestamp", ColumnType.STRING),
                1 to Pair("Phone Number", ColumnType.PHONE),
                2 to Pair("User Name", ColumnType.STRING),
                3 to Pair("Message", ColumnType.STRING),
                4 to Pair("Intent", ColumnType.STRING),
                5 to Pair("Confidence", ColumnType.INTEGER),
                6 to Pair("Priority", ColumnType.PRIORITY)
            )
            
            columnConfig.forEach { (index, config) ->
                if (index < columns.size) {
                    val column = columns[index]
                    columnDao.updateColumn(
                        column.copy(
                            name = config.first,
                            type = config.second
                        )
                    )
                }
            }
            
            android.util.Log.d("AIHistoryManager", "Configured intent sheet columns")
        } catch (e: Exception) {
            android.util.Log.e("AIHistoryManager", "Failed to configure sheet: ${e.message}")
        }
    }

    /**
     * Ensure User Profiles sheet exists (Pre-built)
     */
    private suspend fun ensureProfileSheetExists(folderId: Long): TableModel {
        val existingTables = tableDao.getTablesByFolderIdSync(folderId)
        var profileSheet = existingTables.find { it.name == PROFILE_SHEET_NAME }
        
        if (profileSheet != null) {
            configureProfileSheet(profileSheet)
            return profileSheet
        } else {
            return createPreBuiltProfileSheet(folderId)
        }
    }
    
    /**
     * Create pre-built profile sheet with 100 rows and 20 columns
     */
    private suspend fun createPreBuiltProfileSheet(folderId: Long): TableModel {
        val tableId = tableDao.insertTable(
            TableModel(
                name = PROFILE_SHEET_NAME,
                description = "Auto-generated user profile database",
                folderId = folderId,
                columnCount = 20, // 10 required + 10 extra
                rowCount = 100
            )
        )
        
        val columnConfig = listOf(
            Triple("Phone Number", ColumnType.PHONE, 0),
            Triple("Name", ColumnType.STRING, 1),
            Triple("Email", ColumnType.EMAIL, 2),
            Triple("City", ColumnType.STRING, 3),
            Triple("Lead Tier", ColumnType.SELECT, 4),
            Triple("Lead Score", ColumnType.INTEGER, 5),
            Triple("Total Messages", ColumnType.INTEGER, 6),
            Triple("Last Intent", ColumnType.STRING, 7),
            Triple("Last Contact", ColumnType.STRING, 8),
            Triple("Created At", ColumnType.STRING, 9)
        )
        
        columnConfig.forEach { (name, type, index) ->
            columnDao.insertColumn(
                ColumnModel(
                    tableId = tableId,
                    name = name,
                    type = type,
                    orderIndex = index,
                    selectOptions = if (name == "Lead Tier") """["COLD","WARM","HOT"]""" else null
                )
            )
        }
        
        // Add 10 extra columns
        repeat(10) { i ->
            columnDao.insertColumn(
                ColumnModel(
                    tableId = tableId,
                    name = "Column ${i + 11}",
                    type = ColumnType.STRING,
                    orderIndex = 10 + i
                )
            )
        }
        
        // Pre-create 100 empty rows
        repeat(100) { i ->
            rowDao.insertRow(RowModel(tableId = tableId, orderIndex = i))
        }
        
        val profileSheet = tableDao.getTableById(tableId)!!
        android.util.Log.d("AIHistoryManager", "Pre-created profile sheet: 100 rows x 20 columns")
        return profileSheet
    }
    
    /**
     * Configure existing profile sheet
     */
    private suspend fun configureProfileSheet(sheet: TableModel) {
        try {
            val columns = columnDao.getColumnsByTableIdSync(sheet.id)
            val columnConfig = mapOf(
                0 to Triple("Phone Number", ColumnType.PHONE, null),
                1 to Triple("Name", ColumnType.STRING, null),
                2 to Triple("Email", ColumnType.EMAIL, null),
                3 to Triple("City", ColumnType.STRING, null),
                4 to Triple("Lead Tier", ColumnType.SELECT, """["COLD","WARM","HOT"]"""),
                5 to Triple("Lead Score", ColumnType.INTEGER, null),
                6 to Triple("Total Messages", ColumnType.INTEGER, null),
                7 to Triple("Last Intent", ColumnType.STRING, null),
                8 to Triple("Last Contact", ColumnType.STRING, null),
                9 to Triple("Created At", ColumnType.STRING, null)
            )
            
            columnConfig.forEach { (index, config) ->
                if (index < columns.size) {
                    val column = columns[index]
                    columnDao.updateColumn(
                        column.copy(
                            name = config.first,
                            type = config.second,
                            selectOptions = config.third
                        )
                    )
                }
            }
            
            android.util.Log.d("AIHistoryManager", "Configured profile sheet columns")
        } catch (e: Exception) {
            android.util.Log.e("AIHistoryManager", "Failed to configure profile sheet: ${e.message}")
        }
    }

    /**
     * Ensure Conversation Log sheet exists (Pre-built)
     */
    private suspend fun ensureConversationSheetExists(folderId: Long): TableModel {
        val existingTables = tableDao.getTablesByFolderIdSync(folderId)
        var conversationSheet = existingTables.find { it.name == CONVERSATION_SHEET_NAME }
        
        if (conversationSheet != null) {
            configureConversationSheet(conversationSheet)
            return conversationSheet
        } else {
            return createPreBuiltConversationSheet(folderId)
        }
    }
    
    /**
     * Create pre-built conversation sheet with 200 rows and 15 columns
     */
    private suspend fun createPreBuiltConversationSheet(folderId: Long): TableModel {
        val tableId = tableDao.insertTable(
            TableModel(
                name = CONVERSATION_SHEET_NAME,
                description = "Auto-generated conversation history",
                folderId = folderId,
                columnCount = 15, // 6 required + 9 extra
                rowCount = 200 // More rows for conversations
            )
        )
        
        val columnConfig = listOf(
            Triple("Timestamp", ColumnType.STRING, 0),
            Triple("Phone Number", ColumnType.PHONE, 1),
            Triple("User Name", ColumnType.STRING, 2),
            Triple("User Message", ColumnType.STRING, 3),
            Triple("AI Reply", ColumnType.STRING, 4),
            Triple("Intent", ColumnType.STRING, 5)
        )
        
        columnConfig.forEach { (name, type, index) ->
            columnDao.insertColumn(
                ColumnModel(
                    tableId = tableId,
                    name = name,
                    type = type,
                    orderIndex = index
                )
            )
        }
        
        // Add 9 extra columns
        repeat(9) { i ->
            columnDao.insertColumn(
                ColumnModel(
                    tableId = tableId,
                    name = "Column ${i + 7}",
                    type = ColumnType.STRING,
                    orderIndex = 6 + i
                )
            )
        }
        
        // Pre-create 200 empty rows
        repeat(200) { i ->
            rowDao.insertRow(RowModel(tableId = tableId, orderIndex = i))
        }
        
        val conversationSheet = tableDao.getTableById(tableId)!!
        android.util.Log.d("AIHistoryManager", "Pre-created conversation sheet: 200 rows x 15 columns")
        return conversationSheet
    }
    
    /**
     * Configure existing conversation sheet
     */
    private suspend fun configureConversationSheet(sheet: TableModel) {
        try {
            val columns = columnDao.getColumnsByTableIdSync(sheet.id)
            val columnConfig = mapOf(
                0 to Pair("Timestamp", ColumnType.STRING),
                1 to Pair("Phone Number", ColumnType.PHONE),
                2 to Pair("User Name", ColumnType.STRING),
                3 to Pair("User Message", ColumnType.STRING),
                4 to Pair("AI Reply", ColumnType.STRING),
                5 to Pair("Intent", ColumnType.STRING)
            )
            
            columnConfig.forEach { (index, config) ->
                if (index < columns.size) {
                    val column = columns[index]
                    columnDao.updateColumn(
                        column.copy(
                            name = config.first,
                            type = config.second
                        )
                    )
                }
            }
            
            android.util.Log.d("AIHistoryManager", "Configured conversation sheet columns")
        } catch (e: Exception) {
            android.util.Log.e("AIHistoryManager", "Failed to configure conversation sheet: ${e.message}")
        }
    }

    /**
     * Log intent detection to sheet (thread-based: groups messages by user)
     */
    suspend fun logIntent(
        phoneNumber: String,
        userName: String,
        message: String,
        intentResult: IntentResult,
        priority: String
    ) = withContext(Dispatchers.IO) {
        if (!advancedSettings.autoSaveIntentHistory) return@withContext
        
        try {
            val folder = folderDao.getFolderByName(HISTORY_FOLDER_NAME) ?: return@withContext
            val sheet = ensureIntentSheetExists(folder.id)
            val columns = columnDao.getColumnsByTableIdSync(sheet.id)
            
            // Find existing row for this phone number
            val phoneColumn = columns.find { it.name == "Phone Number" }
            var targetRow: RowModel? = null
            
            if (phoneColumn != null) {
                val existingCells = cellDao.findCellsByColumnAndValue(phoneColumn.id, phoneNumber)
                if (existingCells.isNotEmpty()) {
                    // User already has a row - update it
                    targetRow = rowDao.getRowById(existingCells.first().rowId)
                }
            }
            
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            
            if (targetRow != null) {
                // UPDATE existing row - append new message
                updateThreadedRow(targetRow, columns, message, intentResult, priority, timestamp)
                android.util.Log.d("AIHistoryManager", "Updated thread for $phoneNumber")
            } else {
                // CREATE new row for this user
                targetRow = createThreadedRow(sheet.id, columns, phoneNumber, userName, message, intentResult, priority, timestamp)
                android.util.Log.d("AIHistoryManager", "Created new thread for $phoneNumber")
            }
            
        } catch (e: Exception) {
            android.util.Log.e("AIHistoryManager", "Failed to log intent: ${e.message}")
        }
    }
    
    /**
     * Create new threaded row for user
     */
    private suspend fun createThreadedRow(
        tableId: Long,
        columns: List<ColumnModel>,
        phoneNumber: String,
        userName: String,
        message: String,
        intentResult: IntentResult,
        priority: String,
        timestamp: String
    ): RowModel {
        // Find first empty row
        val allRows = rowDao.getRowsByTableIdSync(tableId)
        var emptyRow: RowModel? = null
        
        for (row in allRows) {
            val cells = cellDao.getCellsByRowIdSync(row.id)
            if (cells.isEmpty() || cells.all { it.value.isBlank() }) {
                emptyRow = row
                break
            }
        }
        
        // If no empty row, expand
        if (emptyRow == null) {
            val maxOrderIndex = rowDao.getMaxOrderIndex(tableId) ?: -1
            repeat(50) { i ->
                rowDao.insertRow(RowModel(tableId = tableId, orderIndex = maxOrderIndex + i + 1))
            }
            emptyRow = rowDao.getRowsByTableIdSync(tableId).first { row ->
                val cells = cellDao.getCellsByRowIdSync(row.id)
                cells.isEmpty() || cells.all { it.value.isBlank() }
            }
        }
        
        // Create initial cells
        val cellData = mapOf(
            "Timestamp" to timestamp,
            "Phone Number" to phoneNumber,
            "User Name" to userName,
            "Message" to message.take(500), // Allow longer for thread
            "Intent" to intentResult.intent,
            "Confidence" to String.format("%.2f", intentResult.confidence),
            "Priority" to priority
        )
        
        cellData.forEach { (columnName, value) ->
            val column = columns.find { it.name == columnName }
            if (column != null) {
                cellDao.insertCell(
                    CellModel(
                        rowId = emptyRow!!.id,
                        columnId = column.id,
                        value = value
                    )
                )
            }
        }
        
        return emptyRow!!
    }
    
    /**
     * Update existing threaded row - append new message
     */
    private suspend fun updateThreadedRow(
        row: RowModel,
        columns: List<ColumnModel>,
        newMessage: String,
        intentResult: IntentResult,
        priority: String,
        timestamp: String
    ) {
        // Update timestamp
        val timestampColumn = columns.find { it.name == "Timestamp" }
        if (timestampColumn != null) {
            val cell = cellDao.getCellSync(row.id, timestampColumn.id)
            if (cell != null) {
                cellDao.updateCell(cell.copy(value = timestamp))
            }
        }
        
        // Append to message thread
        val messageColumn = columns.find { it.name == "Message" }
        if (messageColumn != null) {
            val cell = cellDao.getCellSync(row.id, messageColumn.id)
            if (cell != null) {
                val existingMessages = cell.value
                val updatedMessages = if (existingMessages.isBlank()) {
                    newMessage.take(500)
                } else {
                    // Append with newline separator
                    val combined = "$existingMessages\n---\n$newMessage"
                    // Keep last 1000 chars to prevent cell overflow
                    if (combined.length > 1000) {
                        "...(earlier messages)\n" + combined.takeLast(900)
                    } else {
                        combined
                    }
                }
                cellDao.updateCell(cell.copy(value = updatedMessages))
            }
        }
        
        // Append to intent list
        val intentColumn = columns.find { it.name == "Intent" }
        if (intentColumn != null) {
            val cell = cellDao.getCellSync(row.id, intentColumn.id)
            if (cell != null) {
                val existingIntents = cell.value
                val updatedIntents = if (existingIntents.isBlank()) {
                    intentResult.intent
                } else {
                    // Add new intent if different from last
                    val intentList = existingIntents.split(", ")
                    if (intentList.lastOrNull() != intentResult.intent) {
                        "$existingIntents, ${intentResult.intent}"
                    } else {
                        existingIntents
                    }
                }
                cellDao.updateCell(cell.copy(value = updatedIntents))
            }
        }
        
        // Update confidence (average or latest)
        val confidenceColumn = columns.find { it.name == "Confidence" }
        if (confidenceColumn != null) {
            val cell = cellDao.getCellSync(row.id, confidenceColumn.id)
            if (cell != null) {
                cellDao.updateCell(cell.copy(value = String.format("%.2f", intentResult.confidence)))
            }
        }
        
        // Update priority (highest priority wins)
        val priorityColumn = columns.find { it.name == "Priority" }
        if (priorityColumn != null) {
            val cell = cellDao.getCellSync(row.id, priorityColumn.id)
            if (cell != null) {
                val existingPriority = cell.value
                val newPriority = when {
                    priority == "URGENT" || existingPriority == "URGENT" -> "URGENT"
                    priority == "HIGH" || existingPriority == "HIGH" -> "HIGH"
                    priority == "NORMAL" || existingPriority == "NORMAL" -> "NORMAL"
                    else -> "LOW"
                }
                cellDao.updateCell(cell.copy(value = newPriority))
            }
        }
    }

    /**
     * Log conversation to sheet (thread-based: groups messages by user)
     * Format: One row per user, messages append to same cell
     */
    suspend fun logConversation(
        phoneNumber: String,
        userName: String,
        userMessage: String,
        aiReply: String,
        intent: String
    ) = withContext(Dispatchers.IO) {
        if (!advancedSettings.autoSaveIntentHistory) return@withContext
        
        try {
            val folder = folderDao.getFolderByName(HISTORY_FOLDER_NAME) ?: return@withContext
            val sheet = ensureConversationSheetExists(folder.id)
            val columns = columnDao.getColumnsByTableIdSync(sheet.id)
            
            // Find existing row for this phone number
            val phoneColumn = columns.find { it.name == "Phone Number" }
            var targetRow: RowModel? = null
            
            if (phoneColumn != null) {
                val existingCells = cellDao.findCellsByColumnAndValue(phoneColumn.id, phoneNumber)
                if (existingCells.isNotEmpty()) {
                    // User already has a row - update it
                    targetRow = rowDao.getRowById(existingCells.first().rowId)
                }
            }
            
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            
            if (targetRow != null) {
                // UPDATE existing row - append new conversation
                updateConversationThread(targetRow, columns, userMessage, aiReply, intent, timestamp)
                android.util.Log.d("AIHistoryManager", "Updated conversation thread for $phoneNumber")
            } else {
                // CREATE new row for this user
                targetRow = createConversationRow(sheet.id, columns, phoneNumber, userName, userMessage, aiReply, intent, timestamp)
                android.util.Log.d("AIHistoryManager", "Created new conversation thread for $phoneNumber")
            }
            
            // Check if expansion needed
            checkAndExpandSheet(sheet.id)
            
        } catch (e: Exception) {
            android.util.Log.e("AIHistoryManager", "Failed to log conversation: ${e.message}")
        }
    }
    
    /**
     * Create new conversation row for user
     */
    private suspend fun createConversationRow(
        tableId: Long,
        columns: List<ColumnModel>,
        phoneNumber: String,
        userName: String,
        userMessage: String,
        aiReply: String,
        intent: String,
        timestamp: String
    ): RowModel {
        // Find first empty row
        val allRows = rowDao.getRowsByTableIdSync(tableId)
        var emptyRow: RowModel? = null
        
        for (row in allRows) {
            val cells = cellDao.getCellsByRowIdSync(row.id)
            if (cells.isEmpty() || cells.all { it.value.isBlank() }) {
                emptyRow = row
                break
            }
        }
        
        // If no empty row, expand
        if (emptyRow == null) {
            val maxOrderIndex = rowDao.getMaxOrderIndex(tableId) ?: -1
            repeat(50) { i ->
                rowDao.insertRow(RowModel(tableId = tableId, orderIndex = maxOrderIndex + i + 1))
            }
            emptyRow = rowDao.getRowsByTableIdSync(tableId).first { row ->
                val cells = cellDao.getCellsByRowIdSync(row.id)
                cells.isEmpty() || cells.all { it.value.isBlank() }
            }
        }
        
        // Format: [timestamp]\nUser: message\nAI: reply
        val conversationEntry = "[$timestamp]\nUser: ${userMessage.take(300)}\nAI: ${aiReply.take(300)}"
        
        // Create initial cells
        val cellData = mapOf(
            "Timestamp" to timestamp,
            "Phone Number" to phoneNumber,
            "User Name" to userName,
            "User Message" to conversationEntry,
            "AI Reply" to aiReply.take(500),
            "Intent" to intent
        )
        
        cellData.forEach { (columnName, value) ->
            val column = columns.find { it.name == columnName }
            if (column != null) {
                cellDao.insertCell(
                    CellModel(
                        rowId = emptyRow!!.id,
                        columnId = column.id,
                        value = value
                    )
                )
            }
        }
        
        return emptyRow!!
    }
    
    /**
     * Update existing conversation thread - append new messages
     */
    private suspend fun updateConversationThread(
        row: RowModel,
        columns: List<ColumnModel>,
        newUserMessage: String,
        newAiReply: String,
        intent: String,
        timestamp: String
    ) {
        // Update timestamp
        val timestampColumn = columns.find { it.name == "Timestamp" }
        if (timestampColumn != null) {
            val cell = cellDao.getCellSync(row.id, timestampColumn.id)
            if (cell != null) {
                cellDao.updateCell(cell.copy(value = timestamp))
            }
        }
        
        // Append to conversation thread in "User Message" column
        val messageColumn = columns.find { it.name == "User Message" }
        if (messageColumn != null) {
            val cell = cellDao.getCellSync(row.id, messageColumn.id)
            if (cell != null) {
                val existingConversation = cell.value
                
                // Format new entry
                val newEntry = "\n\n[$timestamp]\nUser: ${newUserMessage.take(300)}\nAI: ${newAiReply.take(300)}"
                
                // Append with thread format
                val updatedConversation = if (existingConversation.isBlank()) {
                    "[$timestamp]\nUser: ${newUserMessage.take(300)}\nAI: ${newAiReply.take(300)}"
                } else {
                    // Keep last 2000 chars to prevent overflow
                    val combined = existingConversation + newEntry
                    if (combined.length > 2000) {
                        "...(earlier messages)\n" + combined.takeLast(1800)
                    } else {
                        combined
                    }
                }
                
                cellDao.updateCell(cell.copy(value = updatedConversation))
            }
        }
        
        // Update AI Reply column with latest reply
        val replyColumn = columns.find { it.name == "AI Reply" }
        if (replyColumn != null) {
            val cell = cellDao.getCellSync(row.id, replyColumn.id)
            if (cell != null) {
                cellDao.updateCell(cell.copy(value = newAiReply.take(500)))
            }
        }
        
        // Append to intent list
        val intentColumn = columns.find { it.name == "Intent" }
        if (intentColumn != null) {
            val cell = cellDao.getCellSync(row.id, intentColumn.id)
            if (cell != null) {
                val existingIntents = cell.value
                val updatedIntents = if (existingIntents.isBlank()) {
                    intent
                } else {
                    // Add new intent if different from last
                    val intentList = existingIntents.split(", ")
                    if (intentList.lastOrNull() != intent) {
                        "$existingIntents, $intent"
                    } else {
                        existingIntents
                    }
                }
                cellDao.updateCell(cell.copy(value = updatedIntents))
            }
        }
    }
    
    /**
     * Check if sheet needs expansion (< 10 empty rows)
     */
    private suspend fun checkAndExpandSheet(tableId: Long) {
        try {
            val allRows = rowDao.getRowsByTableIdSync(tableId)
            var emptyRowCount = 0
            
            for (row in allRows) {
                val cells = cellDao.getCellsByRowIdSync(row.id)
                if (cells.isEmpty() || cells.all { it.value.isBlank() }) {
                    emptyRowCount++
                }
            }
            
            // If less than 10 empty rows, add 50 more
            if (emptyRowCount < 10) {
                val maxOrderIndex = rowDao.getMaxOrderIndex(tableId) ?: -1
                repeat(50) { i ->
                    rowDao.insertRow(RowModel(tableId = tableId, orderIndex = maxOrderIndex + i + 1))
                }
                android.util.Log.d("AIHistoryManager", "Auto-expanded sheet: added 50 rows (had $emptyRowCount empty)")
            }
        } catch (e: Exception) {
            android.util.Log.e("AIHistoryManager", "Failed to check/expand sheet: ${e.message}")
        }
    }

    /**
     * Update or create user profile in sheet
     */
    suspend fun updateProfileSheet(
        phoneNumber: String,
        name: String?,
        email: String?,
        city: String?,
        leadTier: String?,
        leadScore: Int,
        totalMessages: Int,
        lastIntent: String?
    ) = withContext(Dispatchers.IO) {
        if (!advancedSettings.autoCreateProfileSheet) return@withContext
        
        try {
            val folder = folderDao.getFolderByName(HISTORY_FOLDER_NAME) ?: return@withContext
            val sheet = ensureProfileSheetExists(folder.id)
            val columns = columnDao.getColumnsByTableIdSync(sheet.id)
            
            // Check if profile already exists
            val phoneColumn = columns.find { it.name == "Phone Number" }
            if (phoneColumn != null) {
                val existingCells = cellDao.findCellsByColumnAndValue(phoneColumn.id, phoneNumber)
                
                if (existingCells.isNotEmpty()) {
                    // Update existing row
                    val rowId = existingCells.first().rowId
                    updateProfileRow(sheet.id, rowId, columns, phoneNumber, name, email, city, leadTier, leadScore, totalMessages, lastIntent)
                } else {
                    // Create new row
                    createProfileRow(sheet.id, columns, phoneNumber, name, email, city, leadTier, leadScore, totalMessages, lastIntent)
                }
            }
            
            android.util.Log.d("AIHistoryManager", "Updated profile sheet for $phoneNumber")
        } catch (e: Exception) {
            android.util.Log.e("AIHistoryManager", "Failed to update profile sheet: ${e.message}")
        }
    }

    private suspend fun createProfileRow(
        tableId: Long,
        columns: List<ColumnModel>,
        phoneNumber: String,
        name: String?,
        email: String?,
        city: String?,
        leadTier: String?,
        leadScore: Int,
        totalMessages: Int,
        lastIntent: String?
    ) {
        val rowId = rowDao.insertRow(RowModel(tableId = tableId, orderIndex = 0))
        
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val cellData = mapOf(
            "Phone Number" to phoneNumber,
            "Name" to (name ?: "Unknown"),
            "Email" to (email ?: ""),
            "City" to (city ?: ""),
            "Lead Tier" to (leadTier ?: "COLD"),
            "Lead Score" to leadScore.toString(),
            "Total Messages" to totalMessages.toString(),
            "Last Intent" to (lastIntent ?: "UNKNOWN"),
            "Last Contact" to timestamp,
            "Created At" to timestamp
        )
        
        cellData.forEach { (columnName, value) ->
            val column = columns.find { it.name == columnName }
            if (column != null) {
                cellDao.insertCell(
                    CellModel(
                        rowId = rowId,
                        columnId = column.id,
                        value = value
                    )
                )
            }
        }
        
        tableDao.updateRowCount(tableId, rowDao.getRowCountSync(tableId))
    }

    private suspend fun updateProfileRow(
        tableId: Long,
        rowId: Long,
        columns: List<ColumnModel>,
        phoneNumber: String,
        name: String?,
        email: String?,
        city: String?,
        leadTier: String?,
        leadScore: Int,
        totalMessages: Int,
        lastIntent: String?
    ) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val updateData = mapOf(
            "Name" to (name ?: "Unknown"),
            "Email" to (email ?: ""),
            "City" to (city ?: ""),
            "Lead Tier" to (leadTier ?: "COLD"),
            "Lead Score" to leadScore.toString(),
            "Total Messages" to totalMessages.toString(),
            "Last Intent" to (lastIntent ?: "UNKNOWN"),
            "Last Contact" to timestamp
        )
        
        updateData.forEach { (columnName, value) ->
            val column = columns.find { it.name == columnName }
            if (column != null) {
                val existingCell = cellDao.getCellSync(rowId, column.id)
                if (existingCell != null) {
                    cellDao.updateCell(existingCell.copy(value = value))
                } else {
                    cellDao.insertCell(
                        CellModel(
                            rowId = rowId,
                            columnId = column.id,
                            value = value
                        )
                    )
                }
            }
        }
    }
}
