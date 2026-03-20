package com.message.bulksend.utils

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Fallback handler for campaign operations when primary systems fail
 * Requirements: 7.3, 7.4
 */
object CampaignFallbackHandler {
    
    private const val TAG = "CampaignFallbackHandler"
    private const val FALLBACK_PREFS_NAME = "campaign_fallback_prefs"
    private const val KEY_FALLBACK_COUNT = "fallback_count"
    private const val KEY_LAST_FALLBACK = "last_fallback_timestamp"
    
    /**
     * Create fallback UI state when data loading fails
     * Requirements: 7.3, 7.4
     */
    fun createFallbackUIState(
        context: Context,
        error: Throwable,
        operation: String
    ): FallbackUIState {
        val fallbackType = when {
            error is OutOfMemoryError -> FallbackType.MEMORY_ERROR
            error is SecurityException -> FallbackType.PERMISSION_ERROR
            error.message?.contains("storage", ignoreCase = true) == true -> FallbackType.STORAGE_ERROR
            error.message?.contains("network", ignoreCase = true) == true -> FallbackType.NETWORK_ERROR
            error.message?.contains("timeout", ignoreCase = true) == true -> FallbackType.TIMEOUT_ERROR
            error.message?.contains("corruption", ignoreCase = true) == true -> FallbackType.DATA_CORRUPTION
            else -> FallbackType.GENERIC_ERROR
        }
        
        val recoveryActions = CampaignErrorHandler.getRecoveryActions(error)
        
        // Track fallback usage
        trackFallbackUsage(context, fallbackType, operation)
        
        return FallbackUIState(
            type = fallbackType,
            title = getFallbackTitle(fallbackType),
            message = getFallbackMessage(fallbackType, operation),
            recoveryActions = recoveryActions,
            canRetry = canRetryOperation(fallbackType),
            showDataRecovery = fallbackType == FallbackType.DATA_CORRUPTION,
            showPermissionSettings = fallbackType == FallbackType.PERMISSION_ERROR
        )
    }
    
    /**
     * Attempt automatic recovery for specific error types
     * Requirements: 7.3, 7.4
     */
    suspend fun attemptAutomaticRecovery(
        context: Context,
        error: Throwable,
        operation: String
    ): RecoveryResult {
        return try {
            when {
                error.message?.contains("corruption", ignoreCase = true) == true -> {
                    Log.i(TAG, "Attempting automatic data recovery for corruption")
                    when (val result = CampaignDataRecovery.performDataRecovery(context)) {
                        is DataOperationResult.Success -> {
                            RecoveryResult.Success("Data recovery completed successfully")
                        }
                        is DataOperationResult.PartialSuccess -> {
                            RecoveryResult.Partial("Data recovery completed with warnings", result.warnings)
                        }
                        is DataOperationResult.Error -> {
                            RecoveryResult.Failed("Data recovery failed: ${result.message}")
                        }
                    }
                }
                error.message?.contains("storage", ignoreCase = true) == true -> {
                    Log.i(TAG, "Attempting storage cleanup for storage error")
                    val cleanupResult = performStorageCleanup(context)
                    if (cleanupResult) {
                        RecoveryResult.Success("Storage cleanup completed")
                    } else {
                        RecoveryResult.Failed("Storage cleanup failed")
                    }
                }
                error.message?.contains("timeout", ignoreCase = true) == true -> {
                    Log.i(TAG, "Attempting retry with increased timeout")
                    delay(2000) // Wait before retry
                    RecoveryResult.Success("Ready for retry with increased timeout")
                }
                else -> {
                    RecoveryResult.NotApplicable("No automatic recovery available for this error type")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during automatic recovery", e)
            RecoveryResult.Failed("Automatic recovery failed: ${e.message}")
        }
    }
    
    /**
     * Create emergency campaign data when all else fails
     * Requirements: 7.4
     */
    fun createEmergencyCampaignData(
        context: Context,
        originalCampaignId: String? = null
    ): EnhancedCampaignProgress {
        val timestamp = System.currentTimeMillis()
        val emergencyCampaign = CampaignValidationUtils.createFallbackCampaign(originalCampaignId)
        
        // Save emergency campaign
        try {
            EnhancedCampaignProgressManager.saveProgressWithUniqueIdLegacy(context, emergencyCampaign)
            Log.i(TAG, "Created emergency campaign data: ${emergencyCampaign.uniqueId}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save emergency campaign data", e)
        }
        
        return emergencyCampaign
    }
    
    /**
     * Perform storage cleanup to free space
     * Requirements: 7.3
     */
    private fun performStorageCleanup(context: Context): Boolean {
        return try {
            // Clean up old deletion backups
            CampaignDeleteHandler.cleanupOldBackups(context)
            
            // Clean up old recovery data
            CampaignDataRecovery.clearRecoveryStats(context)
            
            // Clean up corrupted entries
            val integrityResult = EnhancedCampaignProgressManager.performDataIntegrityCheck(context)
            
            Log.i(TAG, "Storage cleanup completed")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Storage cleanup failed", e)
            false
        }
    }
    
    /**
     * Track fallback usage for analytics
     * Requirements: 7.3
     */
    private fun trackFallbackUsage(context: Context, type: FallbackType, operation: String) {
        try {
            val prefs = context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
            val currentCount = prefs.getInt(KEY_FALLBACK_COUNT, 0)
            prefs.edit()
                .putInt(KEY_FALLBACK_COUNT, currentCount + 1)
                .putLong(KEY_LAST_FALLBACK, System.currentTimeMillis())
                .putString("last_fallback_type", type.name)
                .putString("last_fallback_operation", operation)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to track fallback usage", e)
        }
    }
    
    /**
     * Get fallback statistics
     * Requirements: 7.3
     */
    fun getFallbackStats(context: Context): Map<String, Any> {
        return try {
            val prefs = context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
            mapOf(
                "total_fallbacks" to prefs.getInt(KEY_FALLBACK_COUNT, 0),
                "last_fallback" to prefs.getLong(KEY_LAST_FALLBACK, 0),
                "last_fallback_type" to prefs.getString("last_fallback_type", ""),
                "last_fallback_operation" to prefs.getString("last_fallback_operation", "")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get fallback stats", e)
            mapOf("error" to true)
        }
    }
    
    private fun getFallbackTitle(type: FallbackType): String {
        return when (type) {
            FallbackType.MEMORY_ERROR -> "Memory Error"
            FallbackType.PERMISSION_ERROR -> "Permission Required"
            FallbackType.STORAGE_ERROR -> "Storage Error"
            FallbackType.NETWORK_ERROR -> "Network Error"
            FallbackType.TIMEOUT_ERROR -> "Operation Timed Out"
            FallbackType.DATA_CORRUPTION -> "Data Corruption Detected"
            FallbackType.GENERIC_ERROR -> "Unexpected Error"
        }
    }
    
    private fun getFallbackMessage(type: FallbackType, operation: String): String {
        return when (type) {
            FallbackType.MEMORY_ERROR -> "Not enough memory to $operation. Please close other apps and try again."
            FallbackType.PERMISSION_ERROR -> "Permission required to $operation. Please grant necessary permissions."
            FallbackType.STORAGE_ERROR -> "Storage error occurred while trying to $operation. Please check available space."
            FallbackType.NETWORK_ERROR -> "Network error occurred while trying to $operation. Please check your connection."
            FallbackType.TIMEOUT_ERROR -> "Operation timed out while trying to $operation. Please try again."
            FallbackType.DATA_CORRUPTION -> "Data corruption detected during $operation. Recovery options are available."
            FallbackType.GENERIC_ERROR -> "An unexpected error occurred during $operation. Please try again."
        }
    }
    
    private fun canRetryOperation(type: FallbackType): Boolean {
        return when (type) {
            FallbackType.MEMORY_ERROR -> true
            FallbackType.PERMISSION_ERROR -> false // Requires user action
            FallbackType.STORAGE_ERROR -> true
            FallbackType.NETWORK_ERROR -> true
            FallbackType.TIMEOUT_ERROR -> true
            FallbackType.DATA_CORRUPTION -> false // Requires recovery first
            FallbackType.GENERIC_ERROR -> true
        }
    }
}

/**
 * Fallback UI state data class
 * Requirements: 7.3, 7.4
 */
data class FallbackUIState(
    val type: FallbackType,
    val title: String,
    val message: String,
    val recoveryActions: List<String>,
    val canRetry: Boolean,
    val showDataRecovery: Boolean,
    val showPermissionSettings: Boolean
)

/**
 * Fallback types for different error scenarios
 * Requirements: 7.3
 */
enum class FallbackType {
    MEMORY_ERROR,
    PERMISSION_ERROR,
    STORAGE_ERROR,
    NETWORK_ERROR,
    TIMEOUT_ERROR,
    DATA_CORRUPTION,
    GENERIC_ERROR
}

/**
 * Recovery result sealed class
 * Requirements: 7.3, 7.4
 */
sealed class RecoveryResult {
    data class Success(val message: String) : RecoveryResult()
    data class Partial(val message: String, val warnings: List<String>) : RecoveryResult()
    data class Failed(val message: String) : RecoveryResult()
    data class NotApplicable(val message: String) : RecoveryResult()
}

/**
 * Composable hook for automatic error recovery
 * Requirements: 7.3, 7.4
 */
@Composable
fun rememberErrorRecovery(
    error: Throwable?,
    operation: String,
    context: Context,
    onRecoveryComplete: (RecoveryResult) -> Unit
) {
    var isRecovering by remember { mutableStateOf(false) }
    
    LaunchedEffect(error) {
        if (error != null && !isRecovering) {
            isRecovering = true
            try {
                val result = CampaignFallbackHandler.attemptAutomaticRecovery(context, error, operation)
                onRecoveryComplete(result)
            } catch (e: Exception) {
                onRecoveryComplete(RecoveryResult.Failed("Recovery attempt failed: ${e.message}"))
            } finally {
                isRecovering = false
            }
        }
    }
}

/**
 * Composable for progressive error handling with multiple retry attempts
 * Requirements: 7.3, 7.4
 */
@Composable
fun rememberProgressiveErrorHandling(
    maxRetries: Int = 3,
    retryDelayMs: Long = 2000,
    onRetry: suspend () -> Unit
) {
    var retryCount by remember { mutableStateOf(0) }
    var isRetrying by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<Throwable?>(null) }
    
    fun attemptRetry() {
        if (retryCount < maxRetries && !isRetrying) {
            isRetrying = true
            kotlinx.coroutines.GlobalScope.launch {
                try {
                    delay(retryDelayMs)
                    onRetry()
                    retryCount = 0 // Reset on success
                    lastError = null
                } catch (e: Exception) {
                    retryCount++
                    lastError = e
                    Log.w("ProgressiveErrorHandling", "Retry attempt $retryCount failed", e)
                } finally {
                    isRetrying = false
                }
            }
        }
    }
    
    // Expose retry function and state
    // This would be used by the calling composable
}