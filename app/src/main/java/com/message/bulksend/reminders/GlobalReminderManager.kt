package com.message.bulksend.reminders

import android.content.Context
import com.message.bulksend.tablesheet.data.models.ColumnType
import com.message.bulksend.tablesheet.data.repository.TableSheetRepository
import com.message.bulksend.tablesheet.data.TableSheetDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GlobalReminderManager(private val context: Context) {

    private val database = TableSheetDatabase.getDatabase(context)
    private val repository = TableSheetRepository(
        database.tableDao(), 
        database.columnDao(), 
        database.rowDao(), 
        database.cellDao(),
        database.folderDao()
    )

    companion object {
        private const val FOLDER_NAME = "AI Reminders"
        private const val SHEET_NAME = "Unified Reminders"
        
        // Column Names
        const val COL_ID = "ID"
        const val COL_PHONE = "Phone"
        const val COL_NAME = "Name"
        const val COL_DATE = "Date" // YYYY-MM-DD
        const val COL_TIME = "Time" // HH:mm
        const val COL_CONTEXT = "AI Context" // Prompt
        const val COL_OWNER_MESSAGE = "Owner Message" // Exact outgoing message from owner
        const val COL_STATUS = "Status" // PENDING, SENT, FAILED
        const val COL_TEMPLATE = "Template" // CLINIC, GENERAL
        
        // Status values
        const val STATUS_PENDING = "PENDING"
        const val STATUS_SENT = "SENT"
        const val STATUS_FAILED = "FAILED"
        
        private const val WORK_NAME = "GlobalReminderCheckWork"
        
        fun scheduleWorker(context: Context) {
            try {
                android.util.Log.d("GlobalReminderManager", "Scheduling reminder worker...")
                
                val constraints = androidx.work.Constraints.Builder()
                    .setRequiresBatteryNotLow(false) // Allow even on low battery
                    .build()
                
                val workRequest = androidx.work.PeriodicWorkRequestBuilder<ReminderCheckWorker>(
                    15, java.util.concurrent.TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .addTag("REMINDER_WORKER")
                    .build()

                androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
                
                android.util.Log.d("GlobalReminderManager", "✅ Reminder worker scheduled successfully")
            } catch (e: Exception) {
                android.util.Log.e("GlobalReminderManager", "❌ Failed to schedule worker: ${e.message}", e)
            }
        }
        
        fun cancelWorker(context: Context) {
            try {
                androidx.work.WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                android.util.Log.d("GlobalReminderManager", "Reminder worker cancelled")
            } catch (e: Exception) {
                android.util.Log.e("GlobalReminderManager", "Failed to cancel worker: ${e.message}")
            }
        }
        
        fun checkWorkerStatus(context: Context): String {
            return try {
                val workInfos = androidx.work.WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(WORK_NAME).get()
                
                if (workInfos.isEmpty()) {
                    "❌ Worker not scheduled"
                } else {
                    val info = workInfos[0]
                    "✅ Worker Status: ${info.state.name}"
                }
            } catch (e: Exception) {
                "❌ Error checking status: ${e.message}"
            }
        }
    }

    /**
     * Initializes the Folder and Sheet if they don't exist.
     * Returns the Table ID.
     */
    suspend fun getOrCreateReminderTable(): Long = withContext(Dispatchers.IO) {
        android.util.Log.d("GlobalReminderManager", "getOrCreateReminderTable called")
        // 1. Get or Create Folder
        val folderId = repository.createFolderIfNotExists(FOLDER_NAME)
        android.util.Log.d("GlobalReminderManager", "Folder ID: $folderId")
        
        // 2. Check if Table exists in this folder
        // Using DAO directly for synchronous lookup by folder
        val tables = database.tableDao().getTablesByFolderIdSync(folderId)
        val existingTable = tables.find { it.name == SHEET_NAME }
        
        if (existingTable != null) {
            ensureReminderSchema(existingTable.id)
            android.util.Log.d("GlobalReminderManager", "Found existing table: ${existingTable.id}")
            return@withContext existingTable.id
        }
        
        android.util.Log.d("GlobalReminderManager", "Creating new table...")
        // 3. Create Table if not exists
        val headers = listOf(COL_ID, COL_PHONE, COL_NAME, COL_DATE, COL_TIME, COL_CONTEXT, COL_OWNER_MESSAGE, COL_STATUS, COL_TEMPLATE)
        
        // Creating table using import logic to setup columns easily
        val tableId = repository.createTableFromImport(
            name = SHEET_NAME,
            description = "AI Agent Scheduled Reminders",
            headers = headers,
            rows = emptyList(), // No rows initially
            folderId = folderId
        )
        android.util.Log.d("GlobalReminderManager", "Created table with ID: $tableId")
        ensureReminderSchema(tableId)
        
        // Set favorite
        repository.updateTableFavorite(tableId, true)
        
        return@withContext tableId
    }

    private suspend fun ensureReminderSchema(tableId: Long) {
        var columns = repository.getColumnsByTableIdSync(tableId)
        val columnNames = columns.map { it.name }.toSet()

        if (!columnNames.contains(COL_OWNER_MESSAGE)) {
            repository.addColumn(tableId, COL_OWNER_MESSAGE, ColumnType.STRING)
            columns = repository.getColumnsByTableIdSync(tableId)
        }

        // Update column types for better UI in TableSheet
        columns.forEach { col ->
            val newCol = when(col.name) {
                 COL_PHONE -> col.copy(type = ColumnType.PHONE)
                 COL_DATE -> col.copy(type = ColumnType.DATE)
                 COL_TIME -> col.copy(type = ColumnType.TIME)
                 COL_STATUS -> col.copy(type = ColumnType.SELECT, selectOptions = "[\"$STATUS_PENDING\",\"$STATUS_SENT\",\"$STATUS_FAILED\"]") // JSON array for options? Or CSV? Assuming CSV/String based on typical Select impl
                 else -> null
            }
            if (newCol != null) {
                repository.updateColumn(newCol)
            }
        }
    }

    /**
     * Adds a new reminder to the sheet.
     */
    suspend fun addReminder(
        phone: String,
        name: String,
        date: String, // YYYY-MM-DD
        time: String, // HH:mm
        prompt: String,
        ownerMessage: String = "",
        templateType: String = "GENERAL"
    ): Long = withContext(Dispatchers.IO) {
        android.util.Log.d("GlobalReminderManager", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        android.util.Log.d("GlobalReminderManager", "📝 ADDING REMINDER TO SHEET")
        android.util.Log.d("GlobalReminderManager", "📞 Phone: $phone")
        android.util.Log.d("GlobalReminderManager", "👤 Name: $name")
        android.util.Log.d("GlobalReminderManager", "📅 Date: $date")
        android.util.Log.d("GlobalReminderManager", "⏰ Time: $time")
        android.util.Log.d("GlobalReminderManager", "💬 Owner Message: ${ownerMessage.take(120)}")
        android.util.Log.d("GlobalReminderManager", "📋 Template: $templateType")
        
        val tableId = getOrCreateReminderTable()
        
        // Create a new row
        val rowId = repository.addRow(tableId)
        // Note: addRow might already add empty cells, we just need to update them
        
        // Get columns to map values
        val columns = repository.getColumnsByTableIdSync(tableId)
        // Create map of Name -> ID
        val colMap = columns.associate { it.name to it.id }
        
        // Insert/Update values
        val uniqueId = System.currentTimeMillis().toString()
        
        // Helper to update safely
        suspend fun update(colName: String, value: String) {
            colMap[colName]?.let { colId -> 
                repository.updateCellValue(rowId, colId, value) 
            }
        }
        
        android.util.Log.d("GlobalReminderManager", "💾 Saving to row: $rowId")
        update(COL_ID, uniqueId)
        update(COL_PHONE, phone)
        update(COL_NAME, name)
        update(COL_DATE, date)
        update(COL_TIME, time)
        update(COL_CONTEXT, prompt)
        update(COL_OWNER_MESSAGE, ownerMessage)
        update(COL_STATUS, STATUS_PENDING)
        update(COL_TEMPLATE, templateType)
        
        repository.refreshTableTimestamp(tableId)
        
        // Ensure worker is scheduled
        scheduleWorker(context)
        
        android.util.Log.d("GlobalReminderManager", "✅ Reminder saved successfully!")
        android.util.Log.d("GlobalReminderManager", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        
        return@withContext rowId
    }

    data class ReminderItem(
        val rowId: Long,
        val id: String,
        val phone: String,
        val name: String,
        val date: String,
        val time: String,
        val context: String,
        val ownerMessage: String,
        val status: String,
        val template: String
    )

    private data class DateParts(val year: Int, val month: Int, val day: Int)
    private data class TimeParts(val hour: Int, val minute: Int)

    /**
     * Gets all reminders that are PENDING and due/overdue.
     */
    suspend fun getDueReminders(): List<ReminderItem> = withContext(Dispatchers.IO) {
        android.util.Log.d("GlobalReminderManager", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        android.util.Log.d("GlobalReminderManager", "🔍 CHECKING FOR DUE REMINDERS")
        
        val tableId = getOrCreateReminderTable()
        android.util.Log.d("GlobalReminderManager", "📋 Table ID: $tableId")
        
        val rows = repository.getRowsByTableIdSync(tableId)
        android.util.Log.d("GlobalReminderManager", "📊 Total rows in table: ${rows.size}")
        
        val columns = repository.getColumnsByTableIdSync(tableId)
        val colMap = columns.associate { it.name to it.id }
        
        val dueReminders = mutableListOf<ReminderItem>()
        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val nowFormatted = dateFormat.format(java.util.Date(now))
        
        android.util.Log.d("GlobalReminderManager", "⏰ Current time: $nowFormatted")
        android.util.Log.d("GlobalReminderManager", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        var pendingCount = 0
        var sentCount = 0
        var failedCount = 0
        
        for ((index, row) in rows.withIndex()) {
            val cells = repository.getCellsByRowIds(listOf(row.id))
            val cellMap = cells.associate { it.columnId to it.value }
            
            val status = cellMap[colMap[COL_STATUS]] ?: ""
            val dateStr = cellMap[colMap[COL_DATE]] ?: ""
            val timeStr = cellMap[colMap[COL_TIME]] ?: ""
            val name = cellMap[colMap[COL_NAME]] ?: ""
            
            when (status) {
                STATUS_PENDING -> pendingCount++
                STATUS_SENT -> sentCount++
                STATUS_FAILED -> failedCount++
            }
            
            android.util.Log.d("GlobalReminderManager", "📝 Row ${index + 1}: Status=$status, Date=$dateStr, Time=$timeStr, Name=$name")
            
            if (status == STATUS_PENDING) {
                if (dateStr.isNotBlank() && timeStr.isNotBlank()) {
                    try {
                        val dueTime = parseReminderDueTime(dateStr, timeStr)
                        if (dueTime == null) {
                            android.util.Log.e(
                                "GlobalReminderManager",
                                "   ❌ Invalid date/time values: date='$dateStr', time='$timeStr'"
                            )
                            continue
                        }
                        val dueFormatted = dateFormat.format(java.util.Date(dueTime))
                        val isDue = dueTime <= now
                        
                        android.util.Log.d("GlobalReminderManager", "   ⏰ Due time: $dueFormatted")
                        android.util.Log.d("GlobalReminderManager", "   ✅ Is due: $isDue (${if (isDue) "WILL SEND" else "NOT YET"})")
                        
                        if (isDue) {
                            // It's due!
                            dueReminders.add(ReminderItem(
                                rowId = row.id,
                                id = cellMap[colMap[COL_ID]] ?: "",
                                phone = cellMap[colMap[COL_PHONE]] ?: "",
                                name = name,
                                date = dateStr,
                                time = timeStr,
                                context = cellMap[colMap[COL_CONTEXT]] ?: "",
                                ownerMessage = cellMap[colMap[COL_OWNER_MESSAGE]] ?: "",
                                status = status,
                                template = cellMap[colMap[COL_TEMPLATE]] ?: "GENERAL"
                            ))
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("GlobalReminderManager", "   ❌ Invalid date format: ${e.message}")
                    }
                } else {
                    android.util.Log.w("GlobalReminderManager", "   ⚠️ Missing date or time")
                }
            }
        }
        
        android.util.Log.d("GlobalReminderManager", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        android.util.Log.d("GlobalReminderManager", "📊 SUMMARY:")
        android.util.Log.d("GlobalReminderManager", "   Total reminders: ${rows.size}")
        android.util.Log.d("GlobalReminderManager", "   Pending: $pendingCount")
        android.util.Log.d("GlobalReminderManager", "   Sent: $sentCount")
        android.util.Log.d("GlobalReminderManager", "   Failed: $failedCount")
        android.util.Log.d("GlobalReminderManager", "   Due now: ${dueReminders.size}")
        android.util.Log.d("GlobalReminderManager", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        
        return@withContext dueReminders
    }

    private fun parseReminderDueTime(dateValue: String, timeValue: String): Long? {
        val dateText = dateValue.trim()
        val timeText = timeValue.trim()
        if (dateText.isBlank() || timeText.isBlank()) return null

        // Primary format used by addReminder/AI ("yyyy-MM-dd" + "HH:mm")
        parseDateWithPattern("$dateText $timeText", "yyyy-MM-dd HH:mm")?.let { return it.time }

        val dateParts = parseDateParts(dateText) ?: return null
        val timeParts = parseTimeParts(timeText) ?: return null

        return Calendar.getInstance().apply {
            set(Calendar.YEAR, dateParts.year)
            set(Calendar.MONTH, dateParts.month)
            set(Calendar.DAY_OF_MONTH, dateParts.day)
            set(Calendar.HOUR_OF_DAY, timeParts.hour)
            set(Calendar.MINUTE, timeParts.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun parseDateParts(raw: String): DateParts? {
        raw.toLongOrNull()?.let { millis ->
            val cal = Calendar.getInstance().apply { timeInMillis = millis }
            return DateParts(
                year = cal.get(Calendar.YEAR),
                month = cal.get(Calendar.MONTH),
                day = cal.get(Calendar.DAY_OF_MONTH)
            )
        }

        val patterns = listOf(
            "yyyy-MM-dd",
            "dd/MM/yyyy",
            "d/M/yyyy",
            "MM/dd/yyyy",
            "M/d/yyyy"
        )

        patterns.forEach { pattern ->
            val parsed = parseDateWithPattern(raw, pattern)
            if (parsed != null) {
                val cal = Calendar.getInstance().apply { time = parsed }
                return DateParts(
                    year = cal.get(Calendar.YEAR),
                    month = cal.get(Calendar.MONTH),
                    day = cal.get(Calendar.DAY_OF_MONTH)
                )
            }
        }

        return null
    }

    private fun parseTimeParts(raw: String): TimeParts? {
        raw.toLongOrNull()?.let { millis ->
            val cal = Calendar.getInstance().apply { timeInMillis = millis }
            return TimeParts(
                hour = cal.get(Calendar.HOUR_OF_DAY),
                minute = cal.get(Calendar.MINUTE)
            )
        }

        val patterns = listOf(
            "HH:mm",
            "H:mm",
            "hh:mm a",
            "h:mm a",
            "hh:mma",
            "h:mma"
        )

        patterns.forEach { pattern ->
            val parsed = parseDateWithPattern(raw, pattern)
            if (parsed != null) {
                val cal = Calendar.getInstance().apply { time = parsed }
                return TimeParts(
                    hour = cal.get(Calendar.HOUR_OF_DAY),
                    minute = cal.get(Calendar.MINUTE)
                )
            }
        }

        return null
    }

    private fun parseDateWithPattern(value: String, pattern: String): Date? {
        return try {
            SimpleDateFormat(pattern, Locale.getDefault()).apply {
                isLenient = false
            }.parse(value)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun updateReminderStatus(rowId: Long, newStatus: String) = withContext(Dispatchers.IO) {
        val tableId = getOrCreateReminderTable()
        val columns = repository.getColumnsByTableIdSync(tableId)
        val statusColId = columns.find { it.name == COL_STATUS }?.id
        
        if (statusColId != null) {
            repository.updateCellValue(rowId, statusColId, newStatus)
            repository.refreshTableTimestamp(tableId)
        }
    }
}
