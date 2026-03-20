package com.message.bulksend.autorespond.ai.data.repo

import android.content.Context
import com.message.bulksend.autorespond.ai.data.db.AIAgentDatabase
import com.message.bulksend.autorespond.ai.data.model.UserProfile
import com.message.bulksend.autorespond.ai.payment.PaymentSheetManager
import com.message.bulksend.autorespond.database.ProductDao
import com.message.bulksend.autorespond.database.Product
import com.message.bulksend.tablesheet.data.repository.TableSheetRepository
import com.message.bulksend.autorespond.database.MessageRepository
import com.message.bulksend.autorespond.database.MessageEntity
import java.util.Locale

class AIAgentRepository(
    private val context: Context,
    private val productDao: ProductDao, // Needs to be injected or retrieved from existing DB
    private val tableSheetRepository: TableSheetRepository
) {
    private val userProfileDao = AIAgentDatabase.getDatabase(context).userProfileDao()
    private val messageRepository = MessageRepository(context)

    // User Profile Operations
    suspend fun getUserProfile(phoneNumber: String): UserProfile? = userProfileDao.getUserProfile(phoneNumber)
    
    suspend fun saveUserProfile(profile: UserProfile) {
        userProfileDao.insertUserProfile(profile)
    }
    
    suspend fun updateUserName(phoneNumber: String, name: String) {
        // Check if profile exists, if not create
        val profile = userProfileDao.getUserProfile(phoneNumber)
        if (profile == null) {
            userProfileDao.insertUserProfile(UserProfile(phoneNumber = phoneNumber, name = name))
        } else {
            userProfileDao.updateUserName(phoneNumber, name)
        }
    }

    // Product Operations
    suspend fun searchProducts(query: String): List<Product> = productDao.searchProducts(query)
    
    suspend fun getAllVisibleProducts(): List<Product> = productDao.getVisibleProductsSync()

    // Memory Operations
    suspend fun getConversationHistory(phoneNumber: String, limit: Int = 10): List<MessageEntity> {
         return messageRepository.getRecentMessagesSync(phoneNumber, limit) 
    }

    // Table Sheet Operations - AI Agent data + Payment folder lookup
    suspend fun searchTableSheets(
        phoneNumber: String,
        query: String,
        tableNameFilter: String? = null,
        allowedFolders: List<String> = listOf("AI Agent Data Sheet", PaymentSheetManager.PAYMENT_FOLDER_NAME)
    ): List<String> {
        val results = mutableListOf<String>()

        try {
            val database = com.message.bulksend.tablesheet.data.TableSheetDatabase.getDatabase(context)
            val folderDao = database.folderDao()
            val tableDao = database.tableDao()
            val rowDao = database.rowDao()
            val columnDao = database.columnDao()
            val cellDao = database.cellDao()

            val normalizedTableFilter = tableNameFilter?.trim().orEmpty()
            val allowedTableIds = mutableSetOf<Long>()
            val tableNameById = mutableMapOf<Long, String>()
            val folderNameByTableId = mutableMapOf<Long, String>()

            val effectiveFolders = if (allowedFolders.isEmpty()) {
                listOf("AI Agent Data Sheet", PaymentSheetManager.PAYMENT_FOLDER_NAME)
            } else {
                allowedFolders
            }

            effectiveFolders.forEach { folderName ->
                val folder = folderDao.getFolderByNameSync(folderName) ?: return@forEach
                tableDao.getTablesByFolderIdSync(folder.id).forEach { table ->
                    if (normalizedTableFilter.isNotBlank() &&
                        !table.name.equals(normalizedTableFilter, ignoreCase = true)
                    ) {
                        return@forEach
                    }
                    allowedTableIds.add(table.id)
                    tableNameById[table.id] = table.name
                    folderNameByTableId[table.id] = folder.name
                }
            }

            if (allowedTableIds.isEmpty()) return emptyList()

            val rowIdsToSearch = mutableSetOf<Long>()

            val sanitizedPhone = sanitizePhone(phoneNumber)
            if (sanitizedPhone.isNotBlank()) {
                rowIdsToSearch.addAll(cellDao.findRowIdsByValue(sanitizedPhone))
            }

            buildQueryTokens(query).forEach { token ->
                rowIdsToSearch.addAll(cellDao.findRowIdsByValue(token))
            }

            if (rowIdsToSearch.isEmpty()) return emptyList()

            val rowData = tableSheetRepository.getCellsByRowIds(rowIdsToSearch.toList().take(120))
            val groupedRows = rowData.groupBy { it.rowId }
            val addedRows = mutableSetOf<Long>()

            groupedRows.forEach { (rowId, cells) ->
                try {
                    if (rowId in addedRows) return@forEach
                    val row = rowDao.getRowByIdSync(rowId) ?: return@forEach
                    val tableId = row.tableId
                    if (tableId !in allowedTableIds) return@forEach

                    val columns = columnDao.getColumnsByTableIdSync(tableId)
                    val formattedData =
                        cells.mapNotNull { cell ->
                            val column = columns.find { it.id == cell.columnId }
                            if (column != null && cell.value.isNotBlank()) {
                                "${column.name}=${cell.value}"
                            } else {
                                null
                            }
                        }.joinToString(", ")

                    if (formattedData.isBlank()) return@forEach

                    val tableName = tableNameById[tableId] ?: (tableDao.getTableByIdSync(tableId)?.name ?: "Unknown")
                    val folderName = folderNameByTableId[tableId] ?: "Data"
                    results.add("[$folderName > $tableName] $formattedData")
                    addedRows.add(rowId)
                } catch (e: Exception) {
                    android.util.Log.e("AIAgentRepo", "Error processing sheet row: ${e.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AIAgentRepo", "searchTableSheets failed: ${e.message}", e)
        }

        return results.take(20)
    }

    private fun sanitizePhone(raw: String): String {
        val digits = raw.replace(Regex("[^0-9]"), "")
        if (digits.isBlank()) return ""
        return if (digits.length > 10) digits.takeLast(10) else digits
    }

    private fun buildQueryTokens(query: String): List<String> {
        if (query.isBlank()) return emptyList()

        val stopWords =
            setOf(
                "payment",
                "verify",
                "verification",
                "status",
                "check",
                "link",
                "send",
                "bhejo",
                "karo",
                "karna",
                "mujhe",
                "mera",
                "meri",
                "hai",
                "pls",
                "please",
                "for",
                "with",
                "from",
                "the",
                "and",
                "to"
            )

        val tokens =
            Regex("[^a-zA-Z0-9@._-]+")
                .split(query.lowercase(Locale.ROOT))
                .filter { it.isNotBlank() }
                .filter { token ->
                    if (token in stopWords) return@filter false
                    if (token.length >= 4) return@filter true
                    token.any { it.isDigit() } && token.length >= 2
                }
                .toMutableList()

        val queryDigits = query.replace(Regex("[^0-9]"), "")
        if (queryDigits.length >= 6) {
            tokens.add(queryDigits)
            if (queryDigits.length > 10) {
                tokens.add(queryDigits.takeLast(10))
            }
        }

        return tokens.distinct().take(8)
    }
    
    // NEW: Auto-enrich profile from sheet data
    suspend fun enrichProfileFromSheet(phoneNumber: String) {
        try {
            // Get existing profile or create new
            val profile = getUserProfile(phoneNumber) ?: UserProfile(phoneNumber = phoneNumber)
            
            // Search sheet data
            val sheetData = searchTableSheets(phoneNumber, "")
            
            if (sheetData.isNotEmpty()) {
                // Parse first match (most relevant)
                val data = parseSheetData(sheetData.first())
                
                // Enrich profile with sheet data
                val enrichedProfile = profile.copy(
                    name = data["Name"] ?: data["name"] ?: profile.name,
                    email = data["Email"] ?: data["email"] ?: profile.email,
                    address = data["City"] ?: data["city"] ?: data["Address"] ?: data["address"] ?: profile.address,
                    leadTier = data["Tier"] ?: data["tier"] ?: profile.leadTier,
                    customData = mergeCustomData(profile.customData, data),
                    updatedAt = System.currentTimeMillis()
                )
                
                saveUserProfile(enrichedProfile)
            }
        } catch (e: Exception) {
            android.util.Log.e("AIAgentRepo", "Profile enrichment failed: ${e.message}")
        }
    }
    
    private fun parseSheetData(sheetMatch: String): Map<String, String> {
        // Parse "[TableName] Name=Rahul, Phone=9876543210, City=Mumbai"
        return try {
            // Remove table name prefix if present
            val dataString = if (sheetMatch.contains("]")) {
                sheetMatch.substringAfter("] ")
            } else {
                sheetMatch.substringAfter("Customer Match: ")
            }
            
            dataString
                .split(", ")
                .mapNotNull { pair ->
                    val parts = pair.split("=", limit = 2)
                    if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
                }
                .toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    private fun mergeCustomData(existingJson: String, newData: Map<String, String>): String {
        return try {
            val existing = org.json.JSONObject(existingJson)
            newData.forEach { (key, value) ->
                // Only add if not already a standard field
                if (key !in listOf("Name", "name", "Email", "email", "City", "city", "Tier", "tier", "Phone", "phone")) {
                    existing.put(key, value)
                }
            }
            existing.toString()
        } catch (e: Exception) {
            org.json.JSONObject(newData).toString()
        }
    }
}
