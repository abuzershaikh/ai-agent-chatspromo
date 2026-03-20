package com.message.bulksend.autorespond.backup

/**
 * Data classes for AutoRespond Backup System
 */

/**
 * Backup status information
 */
data class BackupStatus(
    val hasBackup: Boolean = false,
    val lastBackupAt: Long? = null,
    val lastRestoreAt: Long? = null,
    val keywordRepliesCount: Int = 0,
    val spreadsheetsCount: Int = 0,
    val messageLogsCount: Int = 0,
    val settingsBackedUp: Boolean = false,
    val aiConfigBackedUp: Boolean = false,
    val userEmail: String? = null,
    val deviceModel: String? = null,
    val appVersion: String? = null
)

/**
 * Backup result
 */
data class BackupResult(
    val success: Boolean,
    val message: String,
    val itemsBackedUp: Int = 0,
    val error: String? = null
)

/**
 * Restore result
 */
data class RestoreResult(
    val success: Boolean,
    val message: String,
    val keywordRepliesRestored: Int = 0,
    val spreadsheetsRestored: Int = 0,
    val messageLogsRestored: Int = 0,
    val settingsRestored: Boolean = false,
    val error: String? = null
)
