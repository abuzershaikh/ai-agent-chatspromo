package com.message.bulksend.autorespond.backup

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.message.bulksend.autorespond.AutoRespondManager
import com.message.bulksend.autorespond.database.MessageDatabase
import com.message.bulksend.autorespond.database.MessageEntity
import com.message.bulksend.autorespond.keywordreply.KeywordReplyData
import com.message.bulksend.autorespond.keywordreply.KeywordReplyManager
import com.message.bulksend.autorespond.settings.AutoReplySettingsManager
import com.message.bulksend.autorespond.settings.ReplyPriority
import com.message.bulksend.autorespond.sheetreply.SpreadsheetData
import com.message.bulksend.autorespond.sheetreply.SpreadsheetReplyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * AutoRespond Backup Manager
 * Handles backup and restore of all AutoRespond data to Firestore
 * Independent from main app's SyncManager
 */
class AutoRespondBackupManager(private val context: Context) {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val prefs = context.getSharedPreferences("autorespond_backup_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "AutoRespondBackup"
        private const val COLLECTION_BACKUPS = "autorespond_backups"
        private const val PREF_LAST_BACKUP = "last_backup_at"
        private const val PREF_LAST_RESTORE = "last_restore_at"
        private const val PREF_AUTO_BACKUP_ENABLED = "auto_backup_enabled"
        private const val PREF_LAST_AUTO_BACKUP = "last_auto_backup_at"
        private const val AUTO_BACKUP_INTERVAL = 24 * 60 * 60 * 1000L // 24 hours
        private const val BATCH_SIZE = 450
    }

    // ==================== Email Utilities ====================

    /**
     * Sanitize email for Firestore document ID
     */
    private fun sanitizeEmail(email: String): String {
        return email.trim().lowercase()
            .replace(".", "_")
            .replace("@", "_at_")
    }

    /**
     * Get current user's document ID (sanitized email)
     */
    private fun getUserDocId(): String? {
        val email = auth.currentUser?.email ?: return null
        return sanitizeEmail(email)
    }

    /**
     * Get current user's email
     */
    fun getCurrentUserEmail(): String? {
        return auth.currentUser?.email
    }

    /**
     * Check if user is logged in
     */
    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    // ==================== Auto Backup Settings ====================

    /**
     * Check if auto backup is enabled
     */
    fun isAutoBackupEnabled(): Boolean {
        return prefs.getBoolean(PREF_AUTO_BACKUP_ENABLED, false)
    }

    /**
     * Enable or disable auto backup
     */
    fun setAutoBackupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_AUTO_BACKUP_ENABLED, enabled).apply()
        Log.d(TAG, "Auto backup ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Get last auto backup time
     */
    fun getLastAutoBackupTime(): Long {
        return prefs.getLong(PREF_LAST_AUTO_BACKUP, 0)
    }

    /**
     * Check if auto backup is needed (24 hours passed)
     */
    fun isAutoBackupNeeded(): Boolean {
        if (!isAutoBackupEnabled()) return false
        if (!isUserLoggedIn()) return false
        
        val lastAutoBackup = getLastAutoBackupTime()
        val now = System.currentTimeMillis()
        return (now - lastAutoBackup) >= AUTO_BACKUP_INTERVAL
    }

    // ==================== Backup Functions ====================

    /**
     * Perform full backup of all AutoRespond data
     */
    suspend fun performBackup(onProgress: (String) -> Unit = {}): BackupResult {
        return withContext(Dispatchers.IO) {
            try {
                val userDocId = getUserDocId()
                    ?: return@withContext BackupResult(false, "User not logged in", error = "Please login first")

                val userEmail = auth.currentUser?.email ?: ""
                val basePath = "$COLLECTION_BACKUPS/$userDocId"
                var totalItems = 0

                onProgress("Starting backup...")

                // 1. Save user info
                onProgress("Saving user info...")
                firestore.collection(COLLECTION_BACKUPS)
                    .document(userDocId)
                    .set(mapOf(
                        "email" to userEmail,
                        "displayName" to (auth.currentUser?.displayName ?: ""),
                        "lastBackupAt" to System.currentTimeMillis(),
                        "appVersion" to getAppVersion(),
                        "deviceModel" to Build.MODEL,
                        "backupVersion" to 1
                    ), SetOptions.merge())
                    .await()

                // 2. Backup Keyword Replies
                onProgress("Backing up keyword replies...")
                val keywordManager = KeywordReplyManager(context)
                val keywordReplies = keywordManager.getAllReplies()
                if (keywordReplies.isNotEmpty()) {
                    backupKeywordReplies(basePath, keywordReplies)
                    totalItems += keywordReplies.size
                }
                Log.d(TAG, "Backed up ${keywordReplies.size} keyword replies")

                // 3. Backup Spreadsheets
                onProgress("Backing up spreadsheets...")
                val spreadsheetManager = SpreadsheetReplyManager(context)
                val spreadsheets = spreadsheetManager.getAllSpreadsheets()
                if (spreadsheets.isNotEmpty()) {
                    backupSpreadsheets(basePath, spreadsheets)
                    totalItems += spreadsheets.size
                }
                Log.d(TAG, "Backed up ${spreadsheets.size} spreadsheets")

                // 4. Backup Message Logs
                onProgress("Backing up message logs...")
                val messageDao = MessageDatabase.getDatabase(context).messageDao()
                val messageLogs = messageDao.getAllMessages().first()
                val recentLogs = messageLogs.take(1000) // Limit to 1000 messages
                if (recentLogs.isNotEmpty()) {
                    backupMessageLogs(basePath, recentLogs)
                    totalItems += recentLogs.size
                }
                Log.d(TAG, "Backed up ${recentLogs.size} message logs")

                // 5. Backup Settings
                onProgress("Backing up settings...")
                backupSettings(basePath)
                totalItems++

                // Update local backup timestamp
                val now = System.currentTimeMillis()
                prefs.edit()
                    .putLong(PREF_LAST_BACKUP, now)
                    .putLong(PREF_LAST_AUTO_BACKUP, now)
                    .apply()

                onProgress("Backup complete!")
                Log.d(TAG, "Backup completed successfully. Total items: $totalItems")

                BackupResult(
                    success = true,
                    message = "Backup successful!",
                    itemsBackedUp = totalItems
                )

            } catch (e: Exception) {
                Log.e(TAG, "Backup failed", e)
                BackupResult(
                    success = false,
                    message = "Backup failed",
                    error = e.message
                )
            }
        }
    }

    private suspend fun backupKeywordReplies(basePath: String, replies: List<KeywordReplyData>) {
        val batches = replies.chunked(BATCH_SIZE)
        for (batch in batches) {
            val writeBatch = firestore.batch()
            batch.forEach { reply ->
                val docRef = firestore.collection("$basePath/keyword_replies").document(reply.id)
                writeBatch.set(docRef, mapOf(
                    "id" to reply.id,
                    "incomingKeyword" to reply.incomingKeyword,
                    "replyMessage" to reply.replyMessage,
                    "replyOption" to reply.replyOption,
                    "matchOption" to reply.matchOption,
                    "sendEmail" to reply.sendEmail,
                    "isEnabled" to reply.isEnabled,
                    "createdAt" to reply.createdAt
                ))
            }
            writeBatch.commit().await()
        }
    }

    private suspend fun backupSpreadsheets(basePath: String, spreadsheets: List<SpreadsheetData>) {
        val writeBatch = firestore.batch()
        spreadsheets.forEach { sheet ->
            val docRef = firestore.collection("$basePath/spreadsheets").document(sheet.id)
            writeBatch.set(docRef, mapOf(
                "id" to sheet.id,
                "name" to sheet.name,
                "url" to sheet.url,
                "type" to sheet.type,
                "addedTime" to sheet.addedTime
            ))
        }
        writeBatch.commit().await()
    }

    private suspend fun backupMessageLogs(basePath: String, logs: List<MessageEntity>) {
        val batches = logs.chunked(BATCH_SIZE)
        for (batch in batches) {
            val writeBatch = firestore.batch()
            batch.forEach { log ->
                val docRef = firestore.collection("$basePath/message_logs").document(log.id.toString())
                writeBatch.set(docRef, mapOf(
                    "id" to log.id,
                    "srNo" to log.srNo,
                    "phoneNumber" to log.phoneNumber,
                    "senderName" to log.senderName,
                    "incomingMessage" to log.incomingMessage,
                    "outgoingMessage" to log.outgoingMessage,
                    "status" to log.status,
                    "timestamp" to log.timestamp,
                    "dateTime" to log.dateTime,
                    "notificationKey" to log.notificationKey
                ))
            }
            writeBatch.commit().await()
        }
    }

    private suspend fun backupSettings(basePath: String) {
        val settingsManager = AutoReplySettingsManager(context)
        val autoRespondManager = AutoRespondManager(context)

        firestore.collection("$basePath/settings").document("auto_reply")
            .set(mapOf(
                "keywordReplyEnabled" to settingsManager.isKeywordReplyEnabled(),
                "aiReplyEnabled" to settingsManager.isAIReplyEnabled(),
                "spreadsheetReplyEnabled" to settingsManager.isSpreadsheetReplyEnabled(),
                "replyPriority" to settingsManager.getReplyPriority().name,
                "whatsAppEnabled" to settingsManager.isWhatsAppEnabled(),
                "whatsAppBusinessEnabled" to settingsManager.isWhatsAppBusinessEnabled(),
                "autoRespondEnabled" to autoRespondManager.isAutoRespondEnabled(),
                "responseMessage" to autoRespondManager.getResponseMessage()
            ))
            .await()
    }


    // ==================== Restore Functions ====================

    /**
     * Restore all AutoRespond data from Firestore
     */
    suspend fun performRestore(onProgress: (String) -> Unit = {}): RestoreResult {
        return withContext(Dispatchers.IO) {
            try {
                val userDocId = getUserDocId()
                    ?: return@withContext RestoreResult(false, "User not logged in", error = "Please login first")

                val basePath = "$COLLECTION_BACKUPS/$userDocId"

                onProgress("Checking backup...")

                // Check if backup exists
                val userDoc = firestore.collection(COLLECTION_BACKUPS)
                    .document(userDocId)
                    .get()
                    .await()

                if (!userDoc.exists()) {
                    return@withContext RestoreResult(
                        success = false,
                        message = "No backup found",
                        error = "No backup found for this account"
                    )
                }

                var keywordCount = 0
                var spreadsheetCount = 0
                var messageLogCount = 0
                var settingsRestored = false

                // 1. Restore Keyword Replies
                onProgress("Restoring keyword replies...")
                keywordCount = restoreKeywordReplies(basePath)
                Log.d(TAG, "Restored $keywordCount keyword replies")

                // 2. Restore Spreadsheets
                onProgress("Restoring spreadsheets...")
                spreadsheetCount = restoreSpreadsheets(basePath)
                Log.d(TAG, "Restored $spreadsheetCount spreadsheets")

                // 3. Restore Message Logs
                onProgress("Restoring message logs...")
                messageLogCount = restoreMessageLogs(basePath)
                Log.d(TAG, "Restored $messageLogCount message logs")

                // 4. Restore Settings
                onProgress("Restoring settings...")
                settingsRestored = restoreSettings(basePath)
                Log.d(TAG, "Settings restored: $settingsRestored")

                // Update local restore timestamp
                prefs.edit().putLong(PREF_LAST_RESTORE, System.currentTimeMillis()).apply()

                onProgress("Restore complete!")

                RestoreResult(
                    success = true,
                    message = "Restore successful!",
                    keywordRepliesRestored = keywordCount,
                    spreadsheetsRestored = spreadsheetCount,
                    messageLogsRestored = messageLogCount,
                    settingsRestored = settingsRestored
                )

            } catch (e: Exception) {
                Log.e(TAG, "Restore failed", e)
                RestoreResult(
                    success = false,
                    message = "Restore failed",
                    error = e.message
                )
            }
        }
    }

    private suspend fun restoreKeywordReplies(basePath: String): Int {
        val snapshot = firestore.collection("$basePath/keyword_replies").get().await()
        if (snapshot.isEmpty) return 0

        val keywordManager = KeywordReplyManager(context)
        var count = 0

        snapshot.documents.forEach { doc ->
            try {
                val reply = KeywordReplyData(
                    id = doc.getString("id") ?: return@forEach,
                    incomingKeyword = doc.getString("incomingKeyword") ?: "",
                    replyMessage = doc.getString("replyMessage") ?: "",
                    replyOption = doc.getString("replyOption") ?: "",
                    matchOption = doc.getString("matchOption") ?: "exact",
                    sendEmail = doc.getBoolean("sendEmail") ?: false,
                    isEnabled = doc.getBoolean("isEnabled") ?: true,
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                )
                keywordManager.saveKeywordReply(reply)
                count++
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring keyword reply: ${e.message}")
            }
        }
        return count
    }

    private suspend fun restoreSpreadsheets(basePath: String): Int {
        val snapshot = firestore.collection("$basePath/spreadsheets").get().await()
        if (snapshot.isEmpty) return 0

        val spreadsheetManager = SpreadsheetReplyManager(context)
        val restoredSheets = snapshot.documents.mapNotNull { doc ->
            try {
                SpreadsheetData(
                    id = doc.getString("id") ?: return@mapNotNull null,
                    name = doc.getString("name") ?: "",
                    url = doc.getString("url") ?: "",
                    type = doc.getString("type") ?: "excel_file",
                    addedTime = doc.getLong("addedTime") ?: System.currentTimeMillis()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring spreadsheet: ${e.message}")
                null
            }
        }

        spreadsheetManager.replaceAllSpreadsheets(restoredSheets)
        return restoredSheets.size
    }

    private suspend fun restoreMessageLogs(basePath: String): Int {
        val snapshot = firestore.collection("$basePath/message_logs").get().await()
        if (snapshot.isEmpty) return 0

        val messageDao = MessageDatabase.getDatabase(context).messageDao()
        var count = 0

        snapshot.documents.forEach { doc ->
            try {
                val message = MessageEntity(
                    id = (doc.getLong("id") ?: 0).toInt(),
                    srNo = (doc.getLong("srNo") ?: 0).toInt(),
                    phoneNumber = doc.getString("phoneNumber") ?: "",
                    senderName = doc.getString("senderName") ?: "",
                    incomingMessage = doc.getString("incomingMessage") ?: "",
                    outgoingMessage = doc.getString("outgoingMessage") ?: "",
                    status = doc.getString("status") ?: "PENDING",
                    timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                    dateTime = doc.getString("dateTime") ?: "",
                    notificationKey = doc.getString("notificationKey") ?: ""
                )
                messageDao.insertMessage(message)
                count++
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring message log: ${e.message}")
            }
        }
        return count
    }

    private suspend fun restoreSettings(basePath: String): Boolean {
        return try {
            val doc = firestore.collection("$basePath/settings")
                .document("auto_reply")
                .get()
                .await()

            if (!doc.exists()) return false

            val settingsManager = AutoReplySettingsManager(context)
            val autoRespondManager = AutoRespondManager(context)

            settingsManager.setKeywordReplyEnabled(doc.getBoolean("keywordReplyEnabled") ?: true)
            settingsManager.setAIReplyEnabled(doc.getBoolean("aiReplyEnabled") ?: false)
            settingsManager.setSpreadsheetReplyEnabled(doc.getBoolean("spreadsheetReplyEnabled") ?: false)
            
            val priorityName = doc.getString("replyPriority") ?: ReplyPriority.KEYWORD_FIRST.name
            try {
                settingsManager.setReplyPriority(ReplyPriority.valueOf(priorityName))
            } catch (e: Exception) {
                settingsManager.setReplyPriority(ReplyPriority.KEYWORD_FIRST)
            }

            settingsManager.setWhatsAppEnabled(doc.getBoolean("whatsAppEnabled") ?: true)
            settingsManager.setWhatsAppBusinessEnabled(doc.getBoolean("whatsAppBusinessEnabled") ?: true)
            autoRespondManager.setAutoRespondEnabled(doc.getBoolean("autoRespondEnabled") ?: false)
            
            val responseMessage = doc.getString("responseMessage") ?: ""
            if (responseMessage.isNotEmpty()) {
                autoRespondManager.saveResponseMessage(responseMessage)
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring settings: ${e.message}")
            false
        }
    }


    // ==================== Status Functions ====================

    /**
     * Get backup status from Firestore
     */
    suspend fun getBackupStatus(): BackupStatus {
        return withContext(Dispatchers.IO) {
            try {
                val userDocId = getUserDocId() ?: return@withContext BackupStatus()

                val userDoc = firestore.collection(COLLECTION_BACKUPS)
                    .document(userDocId)
                    .get()
                    .await()

                if (!userDoc.exists()) {
                    return@withContext BackupStatus(hasBackup = false)
                }

                val basePath = "$COLLECTION_BACKUPS/$userDocId"

                // Get counts
                val keywordCount = firestore.collection("$basePath/keyword_replies")
                    .get().await().size()
                val spreadsheetCount = firestore.collection("$basePath/spreadsheets")
                    .get().await().size()
                val messageLogCount = firestore.collection("$basePath/message_logs")
                    .get().await().size()
                val settingsDoc = firestore.collection("$basePath/settings")
                    .document("auto_reply").get().await()

                BackupStatus(
                    hasBackup = true,
                    lastBackupAt = userDoc.getLong("lastBackupAt"),
                    lastRestoreAt = prefs.getLong(PREF_LAST_RESTORE, 0).takeIf { it > 0 },
                    keywordRepliesCount = keywordCount,
                    spreadsheetsCount = spreadsheetCount,
                    messageLogsCount = messageLogCount,
                    settingsBackedUp = settingsDoc.exists(),
                    userEmail = userDoc.getString("email"),
                    deviceModel = userDoc.getString("deviceModel"),
                    appVersion = userDoc.getString("appVersion")
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error getting backup status", e)
                BackupStatus()
            }
        }
    }

    /**
     * Get last backup time from local prefs
     */
    fun getLastBackupTime(): Long {
        return prefs.getLong(PREF_LAST_BACKUP, 0)
    }

    /**
     * Get last restore time from local prefs
     */
    fun getLastRestoreTime(): Long {
        return prefs.getLong(PREF_LAST_RESTORE, 0)
    }

    /**
     * Delete all backup data from Firestore
     */
    suspend fun deleteBackup(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val userDocId = getUserDocId() ?: return@withContext false
                val basePath = "$COLLECTION_BACKUPS/$userDocId"

                // Delete all subcollections
                deleteCollection("$basePath/keyword_replies")
                deleteCollection("$basePath/spreadsheets")
                deleteCollection("$basePath/message_logs")
                deleteCollection("$basePath/settings")
                deleteCollection("$basePath/ai_config")
                deleteCollection("$basePath/ai_business")

                // Delete main document
                firestore.collection(COLLECTION_BACKUPS)
                    .document(userDocId)
                    .delete()
                    .await()

                Log.d(TAG, "Backup deleted successfully")
                true

            } catch (e: Exception) {
                Log.e(TAG, "Error deleting backup", e)
                false
            }
        }
    }

    private suspend fun deleteCollection(path: String) {
        try {
            val docs = firestore.collection(path).get().await()
            val batch = firestore.batch()
            docs.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            if (docs.size() > 0) {
                batch.commit().await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting collection $path: ${e.message}")
        }
    }

    /**
     * Perform auto backup if needed (called from Activity)
     */
    suspend fun performAutoBackupIfNeeded(onProgress: (String) -> Unit = {}): BackupResult? {
        if (!isAutoBackupNeeded()) {
            Log.d(TAG, "Auto backup not needed")
            return null
        }

        Log.d(TAG, "Performing auto backup...")
        return performBackup(onProgress)
    }

    private fun getAppVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }
}
