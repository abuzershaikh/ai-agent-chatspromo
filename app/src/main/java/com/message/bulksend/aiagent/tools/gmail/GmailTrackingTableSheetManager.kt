package com.message.bulksend.aiagent.tools.gmail

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.message.bulksend.autorespond.ai.history.AIAgentHistoryManager
import com.message.bulksend.tablesheet.data.TableSheetDatabase
import com.message.bulksend.tablesheet.data.models.CellModel
import com.message.bulksend.tablesheet.data.models.ColumnModel
import com.message.bulksend.tablesheet.data.models.ColumnType
import com.message.bulksend.tablesheet.data.models.FolderModel
import com.message.bulksend.tablesheet.data.models.RowModel
import com.message.bulksend.tablesheet.data.models.TableModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class GmailTrackingTableSheetManager(context: Context) {

    companion object {
        private const val TAG = "GmailTrackingSheet"
        const val SHEET_NAME = "Gmail Tracking History"

        @Volatile
        private var sharedListener: ListenerRegistration? = null

        @Volatile
        private var sharedListenerUserId: String = ""
    }

    private data class ColumnSpec(
        val name: String,
        val type: String,
        val selectOptions: String? = null
    )

    private val appContext = context.applicationContext
    private val database = TableSheetDatabase.getDatabase(appContext)
    private val folderDao = database.folderDao()
    private val tableDao = database.tableDao()
    private val columnDao = database.columnDao()
    private val rowDao = database.rowDao()
    private val cellDao = database.cellDao()
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance("chatspromoweb")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val columnSpecs = listOf(
        ColumnSpec("Tracking ID", ColumnType.STRING),
        ColumnSpec("Status", ColumnType.STRING),
        ColumnSpec("Opened", ColumnType.SELECT, """["YES","NO"]"""),
        ColumnSpec("Open Count", ColumnType.INTEGER),
        ColumnSpec("Recipient Name", ColumnType.STRING),
        ColumnSpec("Recipient Phone", ColumnType.PHONE),
        ColumnSpec("Recipient Email", ColumnType.EMAIL),
        ColumnSpec("Conversation Name", ColumnType.STRING),
        ColumnSpec("Conversation Phone", ColumnType.PHONE),
        ColumnSpec("Subject", ColumnType.STRING),
        ColumnSpec("Sent At", ColumnType.STRING),
        ColumnSpec("Updated At", ColumnType.STRING),
        ColumnSpec("First Opened At", ColumnType.STRING),
        ColumnSpec("Last Opened At", ColumnType.STRING),
        ColumnSpec("Gmail Message ID", ColumnType.STRING),
        ColumnSpec("Thread ID", ColumnType.STRING),
        ColumnSpec("Draft ID", ColumnType.STRING),
        ColumnSpec("Mode", ColumnType.STRING),
        ColumnSpec("To", ColumnType.STRING),
        ColumnSpec("Cc", ColumnType.STRING),
        ColumnSpec("Bcc", ColumnType.STRING),
        ColumnSpec("Tracking URL", ColumnType.STRING),
        ColumnSpec("Body Preview", ColumnType.STRING)
    )

    suspend fun initializeSheetSystem() = withContext(Dispatchers.IO) {
        try {
            ensureSheet()
        } catch (e: Exception) {
            Log.e(TAG, "initializeSheetSystem failed: ${e.message}", e)
        }
    }

    fun startRealtimeSync() {
        val userId = auth.currentUser?.uid.orEmpty().trim()
        if (userId.isBlank()) return

        synchronized(GmailTrackingTableSheetManager::class.java) {
            if (sharedListener != null && sharedListenerUserId == userId) return

            sharedListener?.remove()
            sharedListenerUserId = userId
            sharedListener =
                firestore.collection("users_config")
                    .document(userId)
                    .collection("gmail_history")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e(TAG, "Realtime sync listener failed: ${error.message}", error)
                            return@addSnapshotListener
                        }

                        val docs = snapshot?.documents.orEmpty()
                        if (docs.isEmpty()) return@addSnapshotListener

                        scope.launch {
                            docs.forEach { doc ->
                                syncHistoryMap(doc.data.orEmpty())
                            }
                        }
                    }
        }
    }

    suspend fun syncHistoryPayload(payload: JSONObject): Int = withContext(Dispatchers.IO) {
        var updated = 0

        payload.optJSONArray("history")?.let { updated += syncHistoryArray(it) }
        payload.optJSONObject("history")?.let {
            if (syncHistoryObject(it)) updated++
        }

        updated
    }

    suspend fun syncHistoryArray(items: JSONArray): Int = withContext(Dispatchers.IO) {
        var updated = 0
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            if (syncHistoryObject(item)) {
                updated++
            }
        }
        updated
    }

    suspend fun syncHistoryObject(item: JSONObject): Boolean = withContext(Dispatchers.IO) {
        syncHistoryMap(jsonObjectToMap(item))
    }

    private suspend fun syncHistoryMap(item: Map<String, Any?>): Boolean {
        val trackingId = readString(item["trackingId"])
        if (trackingId.isBlank()) return false

        val sheet = ensureSheet()
        val columns = columnDao.getColumnsByTableIdSync(sheet.id).sortedBy { it.orderIndex }
        val columnsByName = columns.associateBy { it.name }
        val values = buildRowValues(item)

        val trackingColumn = columnsByName["Tracking ID"] ?: return false
        val existingCell = cellDao.findCellsByColumnAndValue(trackingColumn.id, trackingId).firstOrNull()
        val rowId =
            existingCell?.rowId ?: rowDao.insertRow(
                RowModel(
                    tableId = sheet.id,
                    orderIndex = (rowDao.getMaxOrderIndex(sheet.id) ?: -1) + 1
                )
            )

        values.forEach { (name, value) ->
            val column = columnsByName[name] ?: return@forEach
            val existing = cellDao.getCell(rowId, column.id)
            if (existing == null) {
                cellDao.insertCell(CellModel(rowId = rowId, columnId = column.id, value = value))
            } else if (existing.value != value) {
                cellDao.updateCell(existing.copy(value = value))
            }
        }

        val rowCount = rowDao.getRowCountSync(sheet.id)
        tableDao.updateRowCount(sheet.id, rowCount)
        tableDao.updateTable(sheet.copy(rowCount = rowCount, updatedAt = System.currentTimeMillis()))
        return true
    }

    private suspend fun ensureSheet(): TableModel {
        val folder = ensureFolder()
        val existing = tableDao.getTablesByFolderIdSync(folder.id).firstOrNull { it.name == SHEET_NAME }
        if (existing != null) {
            ensureColumns(existing)
            return existing
        }

        val tableId =
            tableDao.insertTable(
                TableModel(
                    name = SHEET_NAME,
                    description = "Auto-synced Gmail send/open tracking history",
                    folderId = folder.id,
                    rowCount = 0,
                    columnCount = columnSpecs.size
                )
            )

        columnSpecs.forEachIndexed { index, spec ->
            columnDao.insertColumn(
                ColumnModel(
                    tableId = tableId,
                    name = spec.name,
                    type = spec.type,
                    orderIndex = index,
                    selectOptions = spec.selectOptions
                )
            )
        }

        return tableDao.getTableById(tableId)!!
    }

    private suspend fun ensureFolder(): FolderModel {
        return folderDao.getFolderByName(AIAgentHistoryManager.HISTORY_FOLDER_NAME)
            ?: run {
                val folderId =
                    folderDao.insertFolder(
                        FolderModel(
                            name = AIAgentHistoryManager.HISTORY_FOLDER_NAME,
                            colorHex = "#EA4335"
                        )
                    )
                folderDao.getFolderById(folderId)!!
            }
    }

    private suspend fun ensureColumns(sheet: TableModel) {
        val columns = columnDao.getColumnsByTableIdSync(sheet.id).sortedBy { it.orderIndex }
        columnSpecs.forEachIndexed { index, spec ->
            val existing = columns.getOrNull(index)
            if (existing == null) {
                columnDao.insertColumn(
                    ColumnModel(
                        tableId = sheet.id,
                        name = spec.name,
                        type = spec.type,
                        orderIndex = index,
                        selectOptions = spec.selectOptions
                    )
                )
            } else if (
                existing.name != spec.name ||
                    existing.type != spec.type ||
                    existing.selectOptions != spec.selectOptions
            ) {
                columnDao.updateColumn(
                    existing.copy(
                        name = spec.name,
                        type = spec.type,
                        selectOptions = spec.selectOptions
                    )
                )
            }
        }
        tableDao.updateColumnCount(sheet.id, columnSpecs.size)
    }

    private fun buildRowValues(item: Map<String, Any?>): Map<String, String> {
        return linkedMapOf(
            "Tracking ID" to readString(item["trackingId"]),
            "Status" to readString(item["status"]),
            "Opened" to if (readBoolean(item["opened"]) || readInt(item["openCount"]) > 0) "YES" else "NO",
            "Open Count" to readInt(item["openCount"]).toString(),
            "Recipient Name" to readString(item["recipientName"]),
            "Recipient Phone" to readString(item["recipientPhone"]),
            "Recipient Email" to readString(item["recipientEmail"]),
            "Conversation Name" to readString(item["conversationName"]),
            "Conversation Phone" to readString(item["conversationPhone"]),
            "Subject" to readString(item["subject"]),
            "Sent At" to readString(item["sentAt"]),
            "Updated At" to readString(item["updatedAt"]),
            "First Opened At" to readString(item["firstOpenedAt"]),
            "Last Opened At" to readString(item["lastOpenedAt"]),
            "Gmail Message ID" to readString(item["gmailMessageId"]),
            "Thread ID" to readString(item["threadId"]),
            "Draft ID" to readString(item["draftId"]),
            "Mode" to readString(item["mode"]),
            "To" to readList(item["to"]),
            "Cc" to readList(item["cc"]),
            "Bcc" to readList(item["bcc"]),
            "Tracking URL" to readString(item["trackingUrl"]),
            "Body Preview" to readString(item["bodyPreview"])
        )
    }

    private fun jsonObjectToMap(json: JSONObject): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = when (val value = json.opt(key)) {
                JSONObject.NULL -> null
                is JSONObject -> jsonObjectToMap(value)
                is JSONArray -> (0 until value.length()).map { index -> value.opt(index) }
                else -> value
            }
        }
        return map
    }

    private fun readString(value: Any?): String {
        return when (value) {
            null -> ""
            is String -> value
            is Number -> value.toString()
            is Boolean -> if (value) "true" else "false"
            is List<*> -> value.joinToString(", ") { readString(it) }
            else -> value.toString()
        }.trim()
    }

    private fun readList(value: Any?): String {
        return when (value) {
            is List<*> -> value.joinToString(", ") { readString(it) }
            else -> readString(value)
        }
    }

    private fun readBoolean(value: Any?): Boolean {
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value.equals("true", true) || value == "1" || value.equals("yes", true)
            else -> false
        }
    }

    private fun readInt(value: Any?): Int {
        return when (value) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: 0
            else -> 0
        }
    }
}
