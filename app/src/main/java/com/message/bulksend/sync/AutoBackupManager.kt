package com.message.bulksend.sync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AutoBackupManager - Automatically backs up data when app opens
 * Checks if backup is needed and performs silent backup
 */
class AutoBackupManager(private val context: Context) {
    
    private val syncManager = SyncManager(context)
    private val prefs = context.getSharedPreferences("auto_backup_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val TAG = "AutoBackupManager"
        private const val PREF_LAST_AUTO_BACKUP = "last_auto_backup_at"
        private const val PREF_AUTO_BACKUP_ENABLED = "auto_backup_enabled"
        private const val AUTO_BACKUP_INTERVAL = 24 * 60 * 60 * 1000L // 24 hours in milliseconds
    }
    
    /**
     * Check if auto backup is enabled
     */
    fun isAutoBackupEnabled(): Boolean {
        return prefs.getBoolean(PREF_AUTO_BACKUP_ENABLED, true) // Default: enabled
    }
    
    /**
     * Enable or disable auto backup
     */
    fun setAutoBackupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_AUTO_BACKUP_ENABLED, enabled).apply()
        Log.d(TAG, "Auto backup ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Check if backup is needed
     * Returns true if:
     * 1. Auto backup is enabled
     * 2. User is logged in
     * 3. There are pending changes (any new/modified data)
     */
    suspend fun isBackupNeeded(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Check if auto backup is enabled
                if (!isAutoBackupEnabled()) {
                    Log.d(TAG, "Auto backup is disabled")
                    return@withContext false
                }
                
                // Check if user is logged in
                val status = syncManager.getSyncStatus()
                if (!status.isLoggedIn) {
                    Log.d(TAG, "User not logged in, skipping auto backup")
                    return@withContext false
                }
                
                // Check if there are pending changes (NO TIME CHECK - immediate backup)
                val hasPendingChanges = checkForPendingChanges()
                if (!hasPendingChanges) {
                    Log.d(TAG, "No pending changes, skipping backup")
                    return@withContext false
                }
                
                Log.d(TAG, "Backup needed! Found pending changes")
                true
                
            } catch (e: Exception) {
                Log.e(TAG, "Error checking if backup needed", e)
                false
            }
        }
    }
    
    /**
     * Check if there are any pending changes since last backup
     */
    private suspend fun checkForPendingChanges(): Boolean {
        return try {
            val db = com.message.bulksend.db.AppDatabase.getInstance(context)
            val lastBackupAt = prefs.getLong(PREF_LAST_AUTO_BACKUP, 0)
            
            // Check for new/modified groups
            val allGroups = db.contactGroupDao().getAllGroupsList()
            val pendingGroups = allGroups.filter { it.timestamp > lastBackupAt }
            
            // Check for new/modified campaigns
            val allCampaigns = db.campaignDao().getAllCampaigns()
            val pendingCampaigns = allCampaigns.filter { it.timestamp > lastBackupAt }
            
            val hasPending = pendingGroups.isNotEmpty() || pendingCampaigns.isNotEmpty()
            
            if (hasPending) {
                Log.d(TAG, "Found pending changes: ${pendingGroups.size} groups, ${pendingCampaigns.size} campaigns")
            }
            
            hasPending
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking pending changes", e)
            false
        }
    }
    
    /**
     * Perform automatic backup silently in background
     * Called when MainActivity opens
     */
    fun performAutoBackupIfNeeded(scope: CoroutineScope, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        scope.launch {
            try {
                // Check if backup is needed
                val needed = isBackupNeeded()
                if (!needed) {
                    onComplete(false, "No backup needed")
                    return@launch
                }
                
                Log.d(TAG, "Starting automatic backup...")
                
                // Perform backup silently
                val result = syncManager.backupPending { progress ->
                    Log.d(TAG, "Auto backup progress: $progress")
                }
                
                if (result.isSuccess) {
                    // Update last auto backup time
                    val now = System.currentTimeMillis()
                    prefs.edit().putLong(PREF_LAST_AUTO_BACKUP, now).apply()
                    
                    Log.d(TAG, "Auto backup completed successfully")
                    onComplete(true, "Auto backup completed")
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    Log.e(TAG, "Auto backup failed: $error")
                    onComplete(false, "Auto backup failed: $error")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during auto backup", e)
                onComplete(false, "Error: ${e.message}")
            }
        }
    }
    
    /**
     * Get last auto backup time
     */
    fun getLastAutoBackupTime(): Long {
        return prefs.getLong(PREF_LAST_AUTO_BACKUP, 0)
    }
    
    /**
     * Get time until next auto backup
     */
    fun getTimeUntilNextBackup(): Long {
        val lastBackup = getLastAutoBackupTime()
        if (lastBackup == 0L) return 0L
        
        val now = System.currentTimeMillis()
        val timeSinceLastBackup = now - lastBackup
        val timeUntilNext = AUTO_BACKUP_INTERVAL - timeSinceLastBackup
        
        return if (timeUntilNext > 0) timeUntilNext else 0L
    }
    
    /**
     * Force backup now (ignores time interval)
     */
    fun forceBackupNow(scope: CoroutineScope, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        scope.launch {
            try {
                Log.d(TAG, "Force backup initiated...")
                
                val result = syncManager.backupPending { progress ->
                    Log.d(TAG, "Force backup progress: $progress")
                }
                
                if (result.isSuccess) {
                    val now = System.currentTimeMillis()
                    prefs.edit().putLong(PREF_LAST_AUTO_BACKUP, now).apply()
                    
                    Log.d(TAG, "Force backup completed")
                    onComplete(true, "Backup completed")
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    Log.e(TAG, "Force backup failed: $error")
                    onComplete(false, "Backup failed: $error")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during force backup", e)
                onComplete(false, "Error: ${e.message}")
            }
        }
    }
}
