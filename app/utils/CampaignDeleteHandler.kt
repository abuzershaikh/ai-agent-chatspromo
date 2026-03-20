package com.message.bulksend.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Enhanced campaign deletion handler with comprehensive error handling and UI integration
 * Requirements: 4.3, 7.2, 7.3
 */
object CampaignDeleteHandler {
    
    private const val TAG = "CampaignDeleteHandler"
    
    /**
     * Delete campaign with comprehensive error handling and validation
     * Requirements: 4.3, 7.2, 7.3
     */
    suspend fun deleteCampaignSafely(
        context: Context,
        uniqueId: String,
        campaignName: String = "Unknown Campaign"
    ): DataOperationResult<Unit> = withContext(Dispatchers.IO) {
        try {
            // Input validation
            if (uniqueId.isBlank()) {
                return@withContext DataOperationResult.Error<Unit>("Campaign ID cannot be empty")
            }
            
            // Verify campaign exists before deletion
            when (val existingResult = EnhancedCampaignProgressManager.getCampaignByUniqueId(context, uniqueId)) {
                is DataOperationResult.Success -> {
                    if (existingResult.data == null) {
                        return@withContext DataOperationResult.Error<Unit>("Campaign not found: $campaignName")
                    }
                    
                    // Perform the deletion
                    when (val deleteResult = EnhancedCampaignProgressManager.deleteProgress(context, uniqueId)) {
                        is DeleteResult.Success -> {
                            Log.i(TAG, "Successfully deleted campaign: $campaignName (ID: $uniqueId)")
                            
                            // Verify deletion was successful
                            when (val verifyResult = EnhancedCampaignProgressManager.getCampaignByUniqueId(context, uniqueId)) {
                                is DataOperationResult.Success -> {
                                    if (verifyResult.data == null) {
                                        DataOperationResult.Success(Unit)
                                    } else {
                                        DataOperationResult.Error<Unit>("Campaign deletion verification failed: campaign still exists")
                                    }
                                }
                                is DataOperationResult.Error -> {
                                    // This is expected if the campaign was deleted successfully
                                    DataOperationResult.Success(Unit)
                                }
                                is DataOperationResult.PartialSuccess -> {
                                    if (verifyResult.data == null) {
                                        DataOperationResult.Success(Unit)
                                    } else {
                                        DataOperationResult.Error<Unit>("Campaign deletion verification failed: campaign still exists")
                                    }
                                }
                            }
                        }
                        is DeleteResult.Error -> {
                            Log.e(TAG, "Failed to delete campaign: $campaignName (ID: $uniqueId) - ${deleteResult.message}")
                            DataOperationResult.Error<Unit>("Failed to delete campaign: ${deleteResult.message}")
                        }
                    }
                }
                is DataOperationResult.Error -> {
                    Log.e(TAG, "Error verifying campaign existence before deletion: ${existingResult.message}")
                    DataOperationResult.Error<Unit>("Failed to verify campaign existence: ${existingResult.message}")
                }
                is DataOperationResult.PartialSuccess -> {
                    if (existingResult.data == null) {
                        DataOperationResult.Error<Unit>("Campaign not found: $campaignName")
                    } else {
                        // Proceed with deletion despite warnings
                        when (val deleteResult = EnhancedCampaignProgressManager.deleteProgress(context, uniqueId)) {
                            is DeleteResult.Success -> {
                                Log.i(TAG, "Successfully deleted campaign with warnings: $campaignName (ID: $uniqueId)")
                                DataOperationResult.PartialSuccess(Unit, existingResult.warnings)
                            }
                            is DeleteResult.Error -> {
                                Log.e(TAG, "Failed to delete campaign: $campaignName (ID: $uniqueId) - ${deleteResult.message}")
                                DataOperationResult.Error<Unit>("Failed to delete campaign: ${deleteResult.message}")
                            }
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception during campaign deletion", e)
            DataOperationResult.Error<Unit>("Permission denied: Cannot delete campaign data")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during campaign deletion", e)
            DataOperationResult.Error<Unit>("Unexpected error during deletion: ${e.message}", e)
        }
    }
    
    /**
     * Handle delete campaign with UI integration and error handling
     * Requirements: 4.3, 4.4, 7.3
     */
    fun handleDeleteCampaign(
        context: Context,
        uniqueId: String,
        campaignName: String = "Unknown Campaign",
        onSuccess: () -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        kotlinx.coroutines.GlobalScope.launch {
            try {
                when (val result = deleteCampaignSafely(context, uniqueId, campaignName)) {
                    is DataOperationResult.Success -> {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context, 
                                "Campaign '$campaignName' deleted successfully", 
                                Toast.LENGTH_SHORT
                            ).show()
                            onSuccess()
                        }
                    }
                    is DataOperationResult.PartialSuccess -> {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context, 
                                "Campaign '$campaignName' deleted with warnings", 
                                Toast.LENGTH_LONG
                            ).show()
                            onSuccess()
                        }
                    }
                    is DataOperationResult.Error -> {
                        withContext(Dispatchers.Main) {
                            val errorMessage = "Failed to delete '$campaignName': ${result.message}"
                            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                            onError?.invoke(errorMessage)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errorMessage = "Unexpected error deleting '$campaignName': ${e.message}"
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    onError?.invoke(errorMessage)
                }
            }
        }
    }
    
    /**
     * Delete multiple campaigns with batch error handling
     * Requirements: 4.3, 7.3
     */
    suspend fun deleteCampaignsBatch(
        context: Context,
        campaigns: List<Pair<String, String>> // List of (uniqueId, campaignName) pairs
    ): DataOperationResult<BatchDeleteResult> = withContext(Dispatchers.IO) {
        try {
            if (campaigns.isEmpty()) {
                return@withContext DataOperationResult.Error<BatchDeleteResult>("No campaigns provided for deletion")
            }
            
            var successCount = 0
            var failureCount = 0
            val failedCampaigns = mutableListOf<String>()
            val warnings = mutableListOf<String>()
            
            campaigns.forEach { (uniqueId, campaignName) ->
                when (val result = deleteCampaignSafely(context, uniqueId, campaignName)) {
                    is DataOperationResult.Success -> {
                        successCount++
                    }
                    is DataOperationResult.PartialSuccess -> {
                        successCount++
                        warnings.addAll(result.warnings)
                    }
                    is DataOperationResult.Error -> {
                        failureCount++
                        failedCampaigns.add(campaignName)
                        warnings.add("Failed to delete '$campaignName': ${result.message}")
                    }
                }
            }
            
            val batchResult = BatchDeleteResult(
                totalAttempted = campaigns.size,
                successCount = successCount,
                failureCount = failureCount,
                failedCampaigns = failedCampaigns
            )
            
            Log.i(TAG, "Batch deletion completed: $batchResult")
            
            when {
                failureCount == 0 && warnings.isEmpty() -> DataOperationResult.Success(batchResult)
                failureCount == 0 && warnings.isNotEmpty() -> DataOperationResult.PartialSuccess(batchResult, warnings)
                failureCount < campaigns.size -> DataOperationResult.PartialSuccess(batchResult, warnings)
                else -> DataOperationResult.Error<BatchDeleteResult>("All campaign deletions failed")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Batch deletion failed completely", e)
            DataOperationResult.Error<BatchDeleteResult>("Batch deletion failed: ${e.message}", e)
        }
    }
    
    /**
     * Validate campaign before deletion
     * Requirements: 7.3
     */
    private suspend fun validateCampaignForDeletion(
        context: Context,
        uniqueId: String
    ): DataOperationResult<EnhancedCampaignProgress> = withContext(Dispatchers.IO) {
        try {
            // Check if campaign exists
            when (val result = EnhancedCampaignProgressManager.getCampaignByUniqueId(context, uniqueId)) {
                is DataOperationResult.Success -> {
                    if (result.data == null) {
                        DataOperationResult.Error<EnhancedCampaignProgress>("Campaign not found")
                    } else {
                        // Check if campaign is currently running
                        if (result.data.isRunning) {
                            DataOperationResult.Error<EnhancedCampaignProgress>("Cannot delete running campaign. Please stop the campaign first.")
                        } else {
                            DataOperationResult.Success(result.data)
                        }
                    }
                }
                is DataOperationResult.Error -> result
                is DataOperationResult.PartialSuccess -> {
                    if (result.data == null) {
                        DataOperationResult.Error<EnhancedCampaignProgress>("Campaign not found")
                    } else if (result.data.isRunning) {
                        DataOperationResult.Error<EnhancedCampaignProgress>("Cannot delete running campaign. Please stop the campaign first.")
                    } else {
                        result
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating campaign for deletion", e)
            DataOperationResult.Error<EnhancedCampaignProgress>("Validation failed: ${e.message}", e)
        }
    }
    
    /**
     * Create backup before deletion (optional safety feature)
     * Requirements: 7.3, 7.4
     */
    suspend fun createDeletionBackup(
        context: Context,
        uniqueId: String
    ): DataOperationResult<String> = withContext(Dispatchers.IO) {
        try {
            when (val result = EnhancedCampaignProgressManager.getCampaignByUniqueId(context, uniqueId)) {
                is DataOperationResult.Success -> {
                    if (result.data == null) {
                        DataOperationResult.Error<String>("Campaign not found for backup")
                    } else {
                        val backupKey = "backup_${uniqueId}_${System.currentTimeMillis()}"
                        val backupPrefs = context.getSharedPreferences("campaign_deletion_backups", Context.MODE_PRIVATE)
                        val gson = com.google.gson.Gson()
                        val backupJson = gson.toJson(result.data)
                        
                        val success = backupPrefs.edit().putString(backupKey, backupJson).commit()
                        if (success) {
                            Log.i(TAG, "Created deletion backup: $backupKey")
                            DataOperationResult.Success(backupKey)
                        } else {
                            DataOperationResult.Error<String>("Failed to create backup")
                        }
                    }
                }
                is DataOperationResult.Error -> DataOperationResult.Error<String>("Failed to retrieve campaign for backup: ${result.message}")
                is DataOperationResult.PartialSuccess -> {
                    if (result.data == null) {
                        DataOperationResult.Error<String>("Campaign not found for backup")
                    } else {
                        val backupKey = "backup_${uniqueId}_${System.currentTimeMillis()}"
                        val backupPrefs = context.getSharedPreferences("campaign_deletion_backups", Context.MODE_PRIVATE)
                        val gson = com.google.gson.Gson()
                        val backupJson = gson.toJson(result.data)
                        
                        val success = backupPrefs.edit().putString(backupKey, backupJson).commit()
                        if (success) {
                            Log.i(TAG, "Created deletion backup with warnings: $backupKey")
                            DataOperationResult.PartialSuccess(backupKey, result.warnings)
                        } else {
                            DataOperationResult.Error<String>("Failed to create backup")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating deletion backup", e)
            DataOperationResult.Error<String>("Backup creation failed: ${e.message}", e)
        }
    }
    
    /**
     * Clean up old deletion backups
     * Requirements: 7.3
     */
    fun cleanupOldBackups(context: Context, maxAgeMillis: Long = 7 * 24 * 60 * 60 * 1000L) { // 7 days default
        try {
            val backupPrefs = context.getSharedPreferences("campaign_deletion_backups", Context.MODE_PRIVATE)
            val currentTime = System.currentTimeMillis()
            val keysToRemove = mutableListOf<String>()
            
            backupPrefs.all.forEach { (key, _) ->
                if (key.startsWith("backup_")) {
                    try {
                        val timestampStr = key.substringAfterLast("_")
                        val timestamp = timestampStr.toLongOrNull()
                        if (timestamp != null && (currentTime - timestamp) > maxAgeMillis) {
                            keysToRemove.add(key)
                        }
                    } catch (e: Exception) {
                        // If we can't parse the timestamp, remove the backup
                        keysToRemove.add(key)
                    }
                }
            }
            
            if (keysToRemove.isNotEmpty()) {
                val editor = backupPrefs.edit()
                keysToRemove.forEach { key ->
                    editor.remove(key)
                }
                editor.apply()
                Log.i(TAG, "Cleaned up ${keysToRemove.size} old deletion backups")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up old backups", e)
        }
    }
}

/**
 * Result data class for batch deletion operations
 */
data class BatchDeleteResult(
    val totalAttempted: Int,
    val successCount: Int,
    val failureCount: Int,
    val failedCampaigns: List<String>
)

/**
 * Composable for delete confirmation dialog with error handling
 * Requirements: 4.4, 6.2, 7.3
 */
@Composable
fun DeleteConfirmationDialog(
    campaignName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDeleting: Boolean = false
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        title = {
            Text(
                text = "Delete Campaign",
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Text(
                text = if (isDeleting) {
                    "Deleting '$campaignName'..."
                } else {
                    "Are you sure you want to delete '$campaignName'?\n\nThis action cannot be undone."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(
                    text = if (isDeleting) "DELETING..." else "DELETE",
                    color = MaterialTheme.colorScheme.onError
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isDeleting
            ) {
                Text("CANCEL")
            }
        }
    )
}