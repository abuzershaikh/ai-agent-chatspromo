package com.message.bulksend.utils

import android.content.Context
import android.util.Log
import android.widget.Toast

/**
 * Comprehensive error handling utilities for Enhanced Campaign Status
 * Requirements: 7.3, 7.4 - Error handling and recovery mechanisms
 */
object CampaignErrorHandler {
    
    private const val TAG = "CampaignErrorHandler"
    
    // Error categories
    enum class ErrorCategory {
        VALIDATION,
        STORAGE,
        MIGRATION,
        CORRUPTION,
        NETWORK,
        PERMISSION,
        UNKNOWN
    }
    
    // Error severity levels
    enum class ErrorSeverity {
        LOW,      // Warning, operation can continue
        MEDIUM,   // Error, operation failed but recoverable
        HIGH,     // Critical error, requires user intervention
        CRITICAL  // System error, app functionality compromised
    }
    
    /**
     * Handle data operation errors with appropriate user feedback
     */
    fun handleDataOperationError(
        context: Context,
        result: DataOperationResult.Error<*>,
        operation: String
    ) {
        val errorInfo = categorizeError(result.message, result.cause)
        
        logError(operation, result.message, result.cause, errorInfo.severity)
        
        val userMessage = getUserFriendlyMessage(errorInfo.category, operation)
        showUserFeedback(context, userMessage, errorInfo.severity)
        
        // Attempt recovery if possible
        if (errorInfo.severity != ErrorSeverity.CRITICAL) {
            suggestRecoveryAction(context, errorInfo.category, operation)
        }
    }
    
    /**
     * Handle delete operation errors
     */
    fun handleDeleteError(
        context: Context,
        result: DeleteResult.Error,
        campaignName: String
    ) {
        val errorInfo = categorizeError(result.message, null)
        
        logError("Delete Campaign", result.message, null, errorInfo.severity)
        
        val userMessage = when (errorInfo.category) {
            ErrorCategory.STORAGE -> "Failed to delete '$campaignName'. Storage error occurred."
            ErrorCategory.VALIDATION -> "Cannot delete '$campaignName'. Campaign data is invalid."
            else -> "Failed to delete '$campaignName'. Please try again."
        }
        
        showUserFeedback(context, userMessage, errorInfo.severity)
    }
    
    /**
     * Handle validation errors
     */
    fun handleValidationError(
        context: Context,
        result: ValidationResult.Invalid,
        operation: String
    ) {
        val errorMessage = "Validation failed: ${result.errors.joinToString(", ")}"
        
        logError(operation, errorMessage, null, ErrorSeverity.MEDIUM)
        
        val userMessage = when {
            result.errors.any { it.contains("empty") } -> 
                "Required information is missing. Please check your campaign data."
            result.errors.any { it.contains("exceed") } -> 
                "Some values are too large. Please reduce the size of your data."
            result.errors.any { it.contains("negative") } -> 
                "Invalid numbers detected. Please check your campaign statistics."
            else -> 
                "Campaign data is invalid. Please review and correct the information."
        }
        
        showUserFeedback(context, userMessage, ErrorSeverity.MEDIUM)
    }
    
    /**
     * Handle migration errors
     */
    fun handleMigrationError(
        context: Context,
        error: String,
        cause: Throwable?
    ) {
        val errorInfo = categorizeError(error, cause)
        
        logError("Migration", error, cause, errorInfo.severity)
        
        val userMessage = when (errorInfo.category) {
            ErrorCategory.CORRUPTION -> 
                "Some campaign data could not be migrated due to corruption. Valid campaigns have been preserved."
            ErrorCategory.STORAGE -> 
                "Migration failed due to storage issues. Please ensure sufficient storage space."
            else -> 
                "Campaign data migration encountered issues. Some data may need to be recreated."
        }
        
        showUserFeedback(context, userMessage, errorInfo.severity)
    }
    
    /**
     * Handle data corruption errors
     */
    fun handleDataCorruption(
        context: Context,
        corruptedCount: Int,
        totalCount: Int,
        recoveredCount: Int = 0
    ) {
        val severity = when {
            corruptedCount == totalCount -> ErrorSeverity.HIGH
            corruptedCount > totalCount / 2 -> ErrorSeverity.MEDIUM
            else -> ErrorSeverity.LOW
        }
        
        logError(
            "Data Corruption", 
            "Found $corruptedCount corrupted campaigns out of $totalCount total, recovered $recoveredCount", 
            null, 
            severity
        )
        
        val userMessage = when {
            recoveredCount > 0 -> 
                "Found $corruptedCount corrupted campaigns. Successfully recovered $recoveredCount campaigns."
            corruptedCount == 1 -> 
                "Found 1 corrupted campaign that has been removed."
            else -> 
                "Found $corruptedCount corrupted campaigns that have been removed."
        }
        
        if (severity >= ErrorSeverity.MEDIUM) {
            showUserFeedback(context, userMessage, severity)
        }
    }
    
    /**
     * Handle performance issues
     */
    fun handlePerformanceIssue(
        context: Context,
        operation: String,
        executionTime: Long,
        threshold: Long
    ) {
        if (executionTime > threshold) {
            val severity = when {
                executionTime > threshold * 3 -> ErrorSeverity.HIGH
                executionTime > threshold * 2 -> ErrorSeverity.MEDIUM
                else -> ErrorSeverity.LOW
            }
            
            logError(
                "Performance", 
                "$operation took ${executionTime}ms (threshold: ${threshold}ms)", 
                null, 
                severity
            )
            
            if (severity >= ErrorSeverity.MEDIUM) {
                val userMessage = "Operation is taking longer than expected. This may be due to a large amount of data."
                showUserFeedback(context, userMessage, severity)
            }
        }
    }
    
    /**
     * Categorize error based on message and cause
     */
    private fun categorizeError(message: String, cause: Throwable?): ErrorInfo {
        val category = when {
            message.contains("validation", ignoreCase = true) || 
            message.contains("invalid", ignoreCase = true) -> ErrorCategory.VALIDATION
            
            message.contains("storage", ignoreCase = true) || 
            message.contains("SharedPreferences", ignoreCase = true) ||
            message.contains("commit", ignoreCase = true) -> ErrorCategory.STORAGE
            
            message.contains("migration", ignoreCase = true) -> ErrorCategory.MIGRATION
            
            message.contains("corrupt", ignoreCase = true) || 
            message.contains("malformed", ignoreCase = true) ||
            message.contains("json", ignoreCase = true) -> ErrorCategory.CORRUPTION
            
            message.contains("network", ignoreCase = true) || 
            message.contains("connection", ignoreCase = true) -> ErrorCategory.NETWORK
            
            message.contains("permission", ignoreCase = true) || 
            message.contains("access", ignoreCase = true) -> ErrorCategory.PERMISSION
            
            else -> ErrorCategory.UNKNOWN
        }
        
        val severity = when (category) {
            ErrorCategory.VALIDATION -> ErrorSeverity.MEDIUM
            ErrorCategory.STORAGE -> ErrorSeverity.HIGH
            ErrorCategory.MIGRATION -> ErrorSeverity.MEDIUM
            ErrorCategory.CORRUPTION -> ErrorSeverity.MEDIUM
            ErrorCategory.NETWORK -> ErrorSeverity.LOW
            ErrorCategory.PERMISSION -> ErrorSeverity.HIGH
            ErrorCategory.UNKNOWN -> when {
                cause is OutOfMemoryError -> ErrorSeverity.CRITICAL
                cause is SecurityException -> ErrorSeverity.HIGH
                message.contains("critical", ignoreCase = true) -> ErrorSeverity.CRITICAL
                else -> ErrorSeverity.MEDIUM
            }
        }
        
        return ErrorInfo(category, severity)
    }
    
    /**
     * Get user-friendly error message
     */
    private fun getUserFriendlyMessage(category: ErrorCategory, operation: String): String {
        return when (category) {
            ErrorCategory.VALIDATION -> 
                "The campaign data is not valid. Please check the information and try again."
            
            ErrorCategory.STORAGE -> 
                "Unable to save campaign data. Please check available storage space and try again."
            
            ErrorCategory.MIGRATION -> 
                "There was an issue updating your campaign data. Some campaigns may need to be recreated."
            
            ErrorCategory.CORRUPTION -> 
                "Some campaign data appears to be corrupted and has been cleaned up."
            
            ErrorCategory.NETWORK -> 
                "Network connection issue. Please check your internet connection and try again."
            
            ErrorCategory.PERMISSION -> 
                "Permission denied. Please check app permissions and try again."
            
            ErrorCategory.UNKNOWN -> 
                "An unexpected error occurred during $operation. Please try again."
        }
    }
    
    /**
     * Show appropriate user feedback based on severity
     */
    private fun showUserFeedback(context: Context, message: String, severity: ErrorSeverity) {
        when (severity) {
            ErrorSeverity.LOW -> {
                // Log only, no user notification for low severity
                Log.i(TAG, "Low severity issue: $message")
            }
            
            ErrorSeverity.MEDIUM -> {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
            
            ErrorSeverity.HIGH, ErrorSeverity.CRITICAL -> {
                Toast.makeText(context, "⚠️ $message", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * Suggest recovery actions to user
     */
    private fun suggestRecoveryAction(context: Context, category: ErrorCategory, operation: String) {
        val suggestion = when (category) {
            ErrorCategory.STORAGE -> 
                "Try freeing up storage space or restarting the app."
            
            ErrorCategory.CORRUPTION -> 
                "Your data has been automatically cleaned up. You may need to recreate some campaigns."
            
            ErrorCategory.VALIDATION -> 
                "Please review your campaign information and ensure all required fields are filled correctly."
            
            ErrorCategory.MIGRATION -> 
                "Try restarting the app to complete the data update process."
            
            else -> null
        }
        
        if (suggestion != null) {
            // Show suggestion after a delay to avoid overwhelming the user
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                Toast.makeText(context, "💡 $suggestion", Toast.LENGTH_LONG).show()
            }, 2000)
        }
    }
    
    /**
     * Log error with appropriate level
     */
    private fun logError(operation: String, message: String, cause: Throwable?, severity: ErrorSeverity) {
        val logMessage = "[$operation] $message"
        
        when (severity) {
            ErrorSeverity.LOW -> Log.i(TAG, logMessage, cause)
            ErrorSeverity.MEDIUM -> Log.w(TAG, logMessage, cause)
            ErrorSeverity.HIGH, ErrorSeverity.CRITICAL -> Log.e(TAG, logMessage, cause)
        }
    }
    
    /**
     * Create error report for debugging
     */
    fun createErrorReport(
        operation: String,
        error: String,
        cause: Throwable?,
        context: Map<String, Any> = emptyMap()
    ): String {
        val errorInfo = categorizeError(error, cause)
        
        return buildString {
            appendLine("=== CAMPAIGN ERROR REPORT ===")
            appendLine("Operation: $operation")
            appendLine("Error: $error")
            appendLine("Category: ${errorInfo.category}")
            appendLine("Severity: ${errorInfo.severity}")
            appendLine("Timestamp: ${System.currentTimeMillis()}")
            
            if (cause != null) {
                appendLine("Cause: ${cause.javaClass.simpleName}: ${cause.message}")
                appendLine("Stack trace: ${cause.stackTraceToString()}")
            }
            
            if (context.isNotEmpty()) {
                appendLine("Context:")
                context.forEach { (key, value) ->
                    appendLine("  $key: $value")
                }
            }
            
            appendLine("=== END REPORT ===")
        }
    }
    
    /**
     * Handle data loading errors
     */
    fun handleDataLoadingError(
        context: Context,
        error: Throwable,
        operation: String
    ): String {
        val errorInfo = categorizeError(error.message ?: "Unknown error", error)
        
        logError(operation, error.message ?: "Unknown error", error, errorInfo.severity)
        
        val userMessage = when (errorInfo.category) {
            ErrorCategory.STORAGE -> "Unable to load campaign data due to storage issues."
            ErrorCategory.CORRUPTION -> "Some campaign data is corrupted and has been cleaned up."
            ErrorCategory.PERMISSION -> "Permission denied. Please check app permissions."
            else -> "Failed to load campaign data. Please try again."
        }
        
        showUserFeedback(context, userMessage, errorInfo.severity)
        
        return userMessage
    }
    
    /**
     * Handle partial success results
     */
    fun handlePartialSuccess(
        context: Context,
        result: DataOperationResult.PartialSuccess<*>,
        operation: String
    ) {
        val warningMessage = "Operation completed with warnings: ${result.warnings.joinToString(", ")}"
        
        logError(operation, warningMessage, null, ErrorSeverity.LOW)
        
        if (result.warnings.any { it.contains("corrupted", ignoreCase = true) }) {
            showUserFeedback(context, "Some corrupted data was found and cleaned up.", ErrorSeverity.LOW)
        }
    }
    
    /**
     * Handle unexpected errors with retry option
     */
    fun handleUnexpectedError(
        context: Context,
        error: Throwable,
        operation: String,
        showRetryOption: Boolean = false
    ): String {
        val errorInfo = categorizeError(error.message ?: "Unexpected error", error)
        
        logError(operation, error.message ?: "Unexpected error", error, errorInfo.severity)
        
        val userMessage = when (error) {
            is OutOfMemoryError -> "Not enough memory available. Please close other apps and try again."
            is SecurityException -> "Permission denied. Please check app permissions in device settings."
            else -> "An unexpected error occurred. Please try again."
        }
        
        showUserFeedback(context, userMessage, errorInfo.severity)
        
        if (showRetryOption) {
            suggestRecoveryAction(context, errorInfo.category, operation)
        }
        
        return userMessage
    }

    /**
     * Check system health and report issues
     */
    fun performHealthCheck(context: Context): HealthCheckResult {
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        try {
            // Check available memory
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val usedMemory = totalMemory - freeMemory
            val memoryUsagePercent = (usedMemory * 100) / maxMemory
            
            if (memoryUsagePercent > 90) {
                issues.add("High memory usage: ${memoryUsagePercent}%")
            } else if (memoryUsagePercent > 75) {
                warnings.add("Elevated memory usage: ${memoryUsagePercent}%")
            }
            
            // Check storage space
            val internalStorage = context.filesDir
            val freeSpace = internalStorage.freeSpace
            val totalSpace = internalStorage.totalSpace
            val storageUsagePercent = ((totalSpace - freeSpace) * 100) / totalSpace
            
            if (storageUsagePercent > 95) {
                issues.add("Very low storage space: ${100 - storageUsagePercent}% free")
            } else if (storageUsagePercent > 85) {
                warnings.add("Low storage space: ${100 - storageUsagePercent}% free")
            }
            
            // Check SharedPreferences accessibility
            try {
                val testPrefs = context.getSharedPreferences("health_check_test", Context.MODE_PRIVATE)
                testPrefs.edit().putString("test", "test").commit()
                testPrefs.edit().remove("test").commit()
            } catch (e: Exception) {
                issues.add("SharedPreferences access error: ${e.message}")
            }
            
            // Check data integrity
            try {
                val scanResult = CampaignDataRecovery.scanForCorruption(context)
                when (scanResult) {
                    is DataOperationResult.Success -> {
                        if (scanResult.data.corruptedCampaigns > 0) {
                            if (scanResult.data.corruptionRate > 0.5f) {
                                issues.add("High data corruption rate: ${String.format("%.1f%%", scanResult.data.corruptionRate * 100)}")
                            } else {
                                warnings.add("Some data corruption detected: ${scanResult.data.corruptedCampaigns} campaigns")
                            }
                        }
                    }
                    is DataOperationResult.Error -> {
                        warnings.add("Could not check data integrity: ${scanResult.message}")
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                warnings.add("Data integrity check failed: ${e.message}")
            }
            
        } catch (e: Exception) {
            issues.add("Health check failed: ${e.message}")
        }
        
        return HealthCheckResult(
            isHealthy = issues.isEmpty(),
            issues = issues,
            warnings = warnings
        )
    }
    
    /**
     * Perform automatic error recovery
     */
    fun performAutomaticRecovery(context: Context): DataOperationResult<AutoRecoveryReport> {
        return try {
            val recoveryReport = AutoRecoveryReport()
            
            // Step 1: Health check
            val healthCheck = performHealthCheck(context)
            recoveryReport.healthCheckPassed = healthCheck.isHealthy
            recoveryReport.healthIssues = healthCheck.issues
            recoveryReport.healthWarnings = healthCheck.warnings
            
            // Step 2: Data corruption scan and recovery
            val corruptionScan = CampaignDataRecovery.scanForCorruption(context)
            when (corruptionScan) {
                is DataOperationResult.Success -> {
                    if (corruptionScan.data.corruptedCampaigns > 0) {
                        // Perform data recovery
                        val dataRecovery = CampaignDataRecovery.performDataRecovery(context)
                        when (dataRecovery) {
                            is DataOperationResult.Success -> {
                                recoveryReport.dataRecoveryPerformed = true
                                recoveryReport.dataRecoverySuccess = true
                                recoveryReport.recoveredCampaigns = dataRecovery.data.recoveredCampaigns
                                recoveryReport.removedCampaigns = dataRecovery.data.removedCampaigns
                            }
                            is DataOperationResult.PartialSuccess -> {
                                recoveryReport.dataRecoveryPerformed = true
                                recoveryReport.dataRecoverySuccess = true
                                recoveryReport.recoveredCampaigns = dataRecovery.data.recoveredCampaigns
                                recoveryReport.removedCampaigns = dataRecovery.data.removedCampaigns
                                recoveryReport.recoveryWarnings = dataRecovery.warnings
                            }
                            is DataOperationResult.Error -> {
                                recoveryReport.dataRecoveryPerformed = true
                                recoveryReport.dataRecoverySuccess = false
                                recoveryReport.recoveryErrors.add("Data recovery failed: ${dataRecovery.message}")
                            }
                        }
                    }
                }
                is DataOperationResult.Error -> {
                    recoveryReport.recoveryErrors.add("Corruption scan failed: ${corruptionScan.message}")
                }
                else -> {}
            }
            
            // Step 3: Memory optimization if needed
            if (healthCheck.issues.any { it.contains("memory", ignoreCase = true) }) {
                try {
                    CampaignPerformanceOptimizer.clearAllCaches()
                    System.gc()
                    recoveryReport.memoryOptimizationPerformed = true
                } catch (e: Exception) {
                    recoveryReport.recoveryErrors.add("Memory optimization failed: ${e.message}")
                }
            }
            
            // Step 4: Storage cleanup if needed
            if (healthCheck.issues.any { it.contains("storage", ignoreCase = true) }) {
                try {
                    cleanupTemporaryData(context)
                    recoveryReport.storageCleanupPerformed = true
                } catch (e: Exception) {
                    recoveryReport.recoveryErrors.add("Storage cleanup failed: ${e.message}")
                }
            }
            
            recoveryReport.success = recoveryReport.recoveryErrors.isEmpty()
            
            if (recoveryReport.recoveryWarnings.isNotEmpty()) {
                DataOperationResult.PartialSuccess(recoveryReport, recoveryReport.recoveryWarnings)
            } else {
                DataOperationResult.Success(recoveryReport)
            }
            
        } catch (e: Exception) {
            DataOperationResult.Error("Automatic recovery failed: ${e.message}", e)
        }
    }
    
    /**
     * Clean up temporary data to free storage space
     */
    private fun cleanupTemporaryData(context: Context) {
        try {
            // Clear expired cache entries
            CampaignPerformanceOptimizer.clearExpiredCache()
            
            // Clear old performance metrics
            PerformanceMonitor.clearMetrics()
            
            // Clear old recovery statistics if they're very old
            val recoveryStats = CampaignDataRecovery.getRecoveryStatistics(context)
            val oneMonthAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            if (recoveryStats.lastRecoveryTimestamp < oneMonthAgo) {
                CampaignDataRecovery.clearRecoveryStatistics(context)
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Error during temporary data cleanup", e)
        }
    }
    
    /**
     * Handle critical system errors that may require app restart
     */
    fun handleCriticalError(
        context: Context,
        error: Throwable,
        operation: String
    ): CriticalErrorResponse {
        val errorInfo = categorizeError(error.message ?: "Critical error", error)
        
        logError(operation, error.message ?: "Critical error", error, ErrorSeverity.CRITICAL)
        
        val response = when (error) {
            is OutOfMemoryError -> {
                CriticalErrorResponse(
                    requiresRestart = true,
                    userMessage = "The app is running out of memory and needs to restart. Please close other apps and try again.",
                    recoveryActions = listOf(
                        "Clear app cache",
                        "Free up device memory",
                        "Restart the app"
                    )
                )
            }
            is SecurityException -> {
                CriticalErrorResponse(
                    requiresRestart = false,
                    userMessage = "Permission denied. Please check app permissions in device settings.",
                    recoveryActions = listOf(
                        "Check app permissions",
                        "Grant required permissions",
                        "Restart the app if needed"
                    )
                )
            }
            else -> {
                CriticalErrorResponse(
                    requiresRestart = errorInfo.severity == ErrorSeverity.CRITICAL,
                    userMessage = "A critical error occurred. The app may need to restart.",
                    recoveryActions = listOf(
                        "Try the operation again",
                        "Restart the app if problems persist",
                        "Contact support if the issue continues"
                    )
                )
            }
        }
        
        // Show critical error feedback
        showUserFeedback(context, "🚨 ${response.userMessage}", ErrorSeverity.CRITICAL)
        
        return response
    }
    
    /**
     * Validate system state before critical operations
     */
    fun validateSystemState(context: Context): SystemStateValidation {
        val healthCheck = performHealthCheck(context)
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Check if system is in a state to perform operations
        if (healthCheck.issues.any { it.contains("memory", ignoreCase = true) }) {
            issues.add("High memory usage may cause operations to fail")
        }
        
        if (healthCheck.issues.any { it.contains("storage", ignoreCase = true) }) {
            issues.add("Low storage space may prevent data saving")
        }
        
        if (healthCheck.issues.any { it.contains("SharedPreferences", ignoreCase = true) }) {
            issues.add("Data storage system is not accessible")
        }
        
        warnings.addAll(healthCheck.warnings)
        
        return SystemStateValidation(
            isValid = issues.isEmpty(),
            canPerformOperations = issues.isEmpty() || issues.none { 
                it.contains("SharedPreferences", ignoreCase = true) 
            },
            issues = issues,
            warnings = warnings,
            recommendations = generateSystemRecommendations(issues, warnings)
        )
    }
    
    /**
     * Generate system recommendations based on issues
     */
    private fun generateSystemRecommendations(issues: List<String>, warnings: List<String>): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (issues.any { it.contains("memory", ignoreCase = true) } || 
            warnings.any { it.contains("memory", ignoreCase = true) }) {
            recommendations.add("Close other apps to free up memory")
            recommendations.add("Restart the app to clear memory usage")
        }
        
        if (issues.any { it.contains("storage", ignoreCase = true) } || 
            warnings.any { it.contains("storage", ignoreCase = true) }) {
            recommendations.add("Free up storage space on your device")
            recommendations.add("Delete unnecessary files or apps")
        }
        
        if (issues.any { it.contains("SharedPreferences", ignoreCase = true) }) {
            recommendations.add("Restart the app to restore data access")
            recommendations.add("Check app permissions in device settings")
        }
        
        return recommendations
    }
    
    /**
     * Error information data class
     */
    private data class ErrorInfo(
        val category: ErrorCategory,
        val severity: ErrorSeverity
    )
}

/**
 * Health check result data class
 */
data class HealthCheckResult(
    val isHealthy: Boolean,
    val issues: List<String>,
    val warnings: List<String>
)

/**
 * Automatic recovery report data class
 */
data class AutoRecoveryReport(
    var healthCheckPassed: Boolean = false,
    var healthIssues: List<String> = emptyList(),
    var healthWarnings: List<String> = emptyList(),
    var dataRecoveryPerformed: Boolean = false,
    var dataRecoverySuccess: Boolean = false,
    var recoveredCampaigns: Int = 0,
    var removedCampaigns: Int = 0,
    var memoryOptimizationPerformed: Boolean = false,
    var storageCleanupPerformed: Boolean = false,
    var recoveryWarnings: List<String> = emptyList(),
    var recoveryErrors: MutableList<String> = mutableListOf(),
    var success: Boolean = false
)

/**
 * Critical error response data class
 */
data class CriticalErrorResponse(
    val requiresRestart: Boolean,
    val userMessage: String,
    val recoveryActions: List<String>
)

/**
 * System state validation data class
 */
data class SystemStateValidation(
    val isValid: Boolean,
    val canPerformOperations: Boolean,
    val issues: List<String>,
    val warnings: List<String>,
    val recommendations: List<String>
)