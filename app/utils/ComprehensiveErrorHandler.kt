package com.message.bulksend.utils

import android.content.Context
import android.widget.Toast
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Comprehensive error handling system for campaign operations
 * Requirements: 7.3, 7.4
 */
object ComprehensiveErrorHandler {
    
    private const val TAG = "ComprehensiveErrorHandler"
    
    // Error tracking
    private val errorCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val recentErrors = mutableListOf<ErrorRecord>()
    private val maxRecentErrors = 100
    
    // Retry configuration
    private const val MAX_RETRY_ATTEMPTS = 3
    private const val BASE_RETRY_DELAY = 1000L // 1 second
    
    /**
     * Error record for tracking and analysis
     */
    data class ErrorRecord(
        val timestamp: Long,
        val operation: String,
        val errorType: String,
        val message: String,
        val context: Map<String, Any> = emptyMap()
    )
    
    /**
     * Error severity levels
     */
    enum class ErrorSeverity {
        LOW,      // Minor issues, user can continue
        MEDIUM,   // Significant issues, some functionality affected
        HIGH,     // Major issues, core functionality affected
        CRITICAL  // System-breaking issues, app may need restart
    }
    
    /**
     * Comprehensive error handling with automatic recovery
     */
    suspend fun <T> handleWithRecovery(
        operation: String,
        context: Context? = null,
        maxRetries: Int = MAX_RETRY_ATTEMPTS,
        recoveryActions: List<suspend () -> Unit> = emptyList(),
        fallbackValue: T? = null,
        block: suspend () -> T
    ): Result<T> {
        var lastException: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                val result = block()
                
                // Reset error count on success
                errorCounts[operation]?.set(0)
                
                return Result.success(result)
            } catch (e: Exception) {
                lastException = e
                
                // Track error
                trackError(operation, e)
                
                // Log error with context
                logErrorWithContext(
                    operation = operation,
                    error = e,
                    attempt = attempt + 1,
                    maxRetries = maxRetries
                )
                
                // Show user feedback for first attempt
                if (attempt == 0 && context != null) {
                    showErrorFeedback(context, e, operation)
                }
                
                // Try recovery actions
                if (recoveryActions.isNotEmpty() && attempt < maxRetries - 1) {
                    try {
                        recoveryActions.getOrNull(attempt)?.invoke()
                    } catch (recoveryException: Exception) {
                        Log.w(TAG, "Recovery action failed for $operation", recoveryException)
                    }
                }
                
                // Wait before retry (exponential backoff)
                if (attempt < maxRetries - 1) {
                    val delay = BASE_RETRY_DELAY * (1L shl attempt) // 1s, 2s, 4s, etc.
                    delay(delay)
                }
            }
        }
        
        // All retries failed
        val finalException = lastException ?: Exception("Unknown error in $operation")
        
        // Try fallback value
        fallbackValue?.let { fallback ->
            Log.w(TAG, "Using fallback value for $operation after ${maxRetries} failed attempts")
            return Result.success(fallback)
        }
        
        // Final error handling
        context?.let { ctx ->
            handleFinalError(ctx, finalException, operation)
        }
        
        return Result.failure(finalException)
    }
    
    /**
     * Handle data operation results with comprehensive error handling
     */
    fun <T> handleDataOperationResult(
        context: Context,
        result: DataOperationResult<T>,
        operation: String,
        onSuccess: (T) -> Unit = {},
        onPartialSuccess: (T, List<String>) -> Unit = { data, warnings ->
            onSuccess(data)
            CampaignErrorHandler.handlePartialSuccess(context, 
                DataOperationResult.PartialSuccess(data, warnings), operation)
        },
        onError: (String, Throwable?) -> Unit = { message, cause ->
            CampaignErrorHandler.handleDataLoadingError(context, 
                cause ?: Exception(message), operation)
        }
    ) {
        when (result) {
            is DataOperationResult.Success -> {
                onSuccess(result.data)
            }
            is DataOperationResult.PartialSuccess -> {
                onPartialSuccess(result.data, result.warnings)
            }
            is DataOperationResult.Error -> {
                trackError(operation, Exception(result.message, result.cause))
                onError(result.message, result.cause)
            }
        }
    }
    
    /**
     * Validate and handle campaign data with comprehensive error recovery
     */
    suspend fun validateAndHandleCampaign(
        context: Context,
        campaign: EnhancedCampaignProgress,
        operation: String = "validate campaign"
    ): Result<EnhancedCampaignProgress> {
        return handleWithRecovery(
            operation = operation,
            context = context,
            recoveryActions = listOf(
                // Recovery action 1: Try to fix validation errors
                {
                    val recovered = CampaignValidationUtils.recoverCorruptedCampaign(campaign)
                    if (recovered != null) {
                        Log.i(TAG, "Successfully recovered campaign data for ${campaign.campaignName}")
                    }
                },
                // Recovery action 2: Create fallback campaign
                {
                    val fallback = CampaignValidationUtils.createFallbackCampaign(campaign.campaignId)
                    Log.w(TAG, "Created fallback campaign for ${campaign.campaignName}")
                }
            ),
            fallbackValue = CampaignValidationUtils.createFallbackCampaign(campaign.campaignId)
        ) {
            // Validate campaign
            when (val validation = CampaignValidationUtils.validateCampaignProgress(campaign)) {
                is ValidationResult.Valid -> campaign
                is ValidationResult.Invalid -> {
                    // Try to recover
                    val recovered = CampaignValidationUtils.recoverCorruptedCampaign(campaign)
                    if (recovered != null) {
                        // Validate recovered campaign
                        when (CampaignValidationUtils.validateCampaignProgress(recovered)) {
                            is ValidationResult.Valid -> recovered
                            is ValidationResult.Invalid -> throw ValidationException(
                                "Campaign validation failed even after recovery", validation.errors
                            )
                        }
                    } else {
                        throw ValidationException("Campaign validation failed", validation.errors)
                    }
                }
            }
        }
    }
    
    /**
     * Handle batch operations with partial failure support
     */
    suspend fun <T, R> handleBatchOperation(
        context: Context,
        items: List<T>,
        operation: String,
        batchSize: Int = 10,
        processor: suspend (T) -> R
    ): BatchOperationResult<R> {
        val results = mutableListOf<R>()
        val errors = mutableListOf<BatchError<T>>()
        var processedCount = 0
        
        try {
            items.chunked(batchSize).forEach { batch ->
                batch.forEach { item ->
                    try {
                        val result = processor(item)
                        results.add(result)
                        processedCount++
                    } catch (e: Exception) {
                        errors.add(BatchError(item, e))
                        trackError("$operation-batch", e)
                        Log.w(TAG, "Batch operation failed for item: $item", e)
                    }
                }
                
                // Small delay between batches to prevent overwhelming the system
                if (items.size > batchSize) {
                    delay(100)
                }
            }
            
            return BatchOperationResult(
                results = results,
                errors = errors,
                totalProcessed = processedCount,
                totalItems = items.size
            )
        } catch (e: Exception) {
            trackError(operation, e)
            CampaignErrorHandler.handleDataLoadingError(context, e, operation)
            throw e
        }
    }
    
    /**
     * Handle storage operations with automatic cleanup
     */
    suspend fun handleStorageOperation(
        context: Context,
        operation: String,
        cleanupOnFailure: Boolean = true,
        block: suspend () -> Unit
    ): Result<Unit> {
        return handleWithRecovery(
            operation = operation,
            context = context,
            recoveryActions = if (cleanupOnFailure) {
                listOf {
                    try {
                        // Attempt to clear corrupted data
                        clearCorruptedData(context)
                        Log.i(TAG, "Cleaned up corrupted data for $operation")
                    } catch (cleanupException: Exception) {
                        Log.w(TAG, "Cleanup failed for $operation", cleanupException)
                    }
                }
            } else emptyList()
        ) {
            block()
        }
    }
    
    /**
     * Get error statistics and health metrics
     */
    fun getErrorStatistics(): ErrorStatistics {
        synchronized(recentErrors) {
            val now = System.currentTimeMillis()
            val last24Hours = now - 24 * 60 * 60 * 1000L
            val lastHour = now - 60 * 60 * 1000L
            
            val recent24h = recentErrors.filter { it.timestamp >= last24Hours }
            val recent1h = recentErrors.filter { it.timestamp >= lastHour }
            
            val errorsByType = recent24h.groupingBy { it.errorType }.eachCount()
            val errorsByOperation = recent24h.groupingBy { it.operation }.eachCount()
            
            return ErrorStatistics(
                totalErrors24h = recent24h.size,
                totalErrors1h = recent1h.size,
                errorsByType = errorsByType,
                errorsByOperation = errorsByOperation,
                mostFrequentError = errorsByType.maxByOrNull { it.value }?.key,
                errorRate1h = recent1h.size / 60.0 // errors per minute
            )
        }
    }
    
    /**
     * Check system health based on error patterns
     */
    fun getSystemHealth(): SystemHealth {
        val stats = getErrorStatistics()
        
        val healthScore = when {
            stats.errorRate1h > 5.0 -> 0.2 // Very poor
            stats.errorRate1h > 2.0 -> 0.4 // Poor
            stats.errorRate1h > 1.0 -> 0.6 // Fair
            stats.errorRate1h > 0.5 -> 0.8 // Good
            else -> 1.0 // Excellent
        }
        
        val severity = when {
            healthScore <= 0.3 -> ErrorSeverity.CRITICAL
            healthScore <= 0.5 -> ErrorSeverity.HIGH
            healthScore <= 0.7 -> ErrorSeverity.MEDIUM
            else -> ErrorSeverity.LOW
        }
        
        val recommendations = generateHealthRecommendations(stats, severity)
        
        return SystemHealth(
            healthScore = healthScore,
            severity = severity,
            recommendations = recommendations,
            lastUpdated = System.currentTimeMillis()
        )
    }
    
    /**
     * Clear old error records to prevent memory leaks
     */
    fun cleanupErrorHistory() {
        synchronized(recentErrors) {
            val cutoff = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L // 7 days
            recentErrors.removeAll { it.timestamp < cutoff }
            
            if (recentErrors.size > maxRecentErrors) {
                val toRemove = recentErrors.size - maxRecentErrors
                repeat(toRemove) {
                    recentErrors.removeFirstOrNull()
                }
            }
        }
    }
    
    // Private helper methods
    
    private fun trackError(operation: String, error: Exception) {
        val errorType = error.javaClass.simpleName
        
        // Update error count
        errorCounts.computeIfAbsent(operation) { AtomicInteger(0) }.incrementAndGet()
        
        // Add to recent errors
        synchronized(recentErrors) {
            recentErrors.add(
                ErrorRecord(
                    timestamp = System.currentTimeMillis(),
                    operation = operation,
                    errorType = errorType,
                    message = error.message ?: "Unknown error",
                    context = mapOf(
                        "stackTrace" to error.stackTraceToString().take(500)
                    )
                )
            )
        }
    }
    
    private fun logErrorWithContext(
        operation: String,
        error: Exception,
        attempt: Int,
        maxRetries: Int
    ) {
        val context = mapOf(
            "operation" to operation,
            "attempt" to attempt,
            "maxRetries" to maxRetries,
            "errorType" to error.javaClass.simpleName
        )
        
        CampaignErrorHandler.logErrorWithContext(
            tag = TAG,
            message = "Error in $operation (attempt $attempt/$maxRetries)",
            error = error,
            context = context
        )
    }
    
    private fun showErrorFeedback(context: Context, error: Exception, operation: String) {
        val message = when (error) {
            is ValidationException -> "Data validation failed for $operation"
            is SecurityException -> "Permission denied for $operation"
            is OutOfMemoryError -> "Not enough memory for $operation"
            else -> "Error occurred during $operation"
        }
        
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun handleFinalError(context: Context, error: Exception, operation: String) {
        val severity = determineSeverity(error)
        
        when (severity) {
            ErrorSeverity.CRITICAL -> {
                CampaignErrorHandler.handleUnexpectedError(context, error, operation, true)
            }
            ErrorSeverity.HIGH -> {
                CampaignErrorHandler.handleDataLoadingError(context, error, operation)
            }
            else -> {
                Log.w(TAG, "Operation $operation failed after retries", error)
            }
        }
    }
    
    private fun determineSeverity(error: Exception): ErrorSeverity {
        return when (error) {
            is OutOfMemoryError -> ErrorSeverity.CRITICAL
            is SecurityException -> ErrorSeverity.HIGH
            is ValidationException -> ErrorSeverity.MEDIUM
            else -> ErrorSeverity.LOW
        }
    }
    
    private suspend fun clearCorruptedData(context: Context) {
        try {
            val result = EnhancedCampaignProgressManager.performDataIntegrityCheck(context)
            when (result) {
                is DataOperationResult.Success -> {
                    Log.i(TAG, "Data integrity check completed successfully")
                }
                is DataOperationResult.PartialSuccess -> {
                    Log.w(TAG, "Data integrity check completed with warnings: ${result.warnings}")
                }
                is DataOperationResult.Error -> {
                    Log.e(TAG, "Data integrity check failed: ${result.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear corrupted data", e)
        }
    }
    
    private fun generateHealthRecommendations(
        stats: ErrorStatistics,
        severity: ErrorSeverity
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        when (severity) {
            ErrorSeverity.CRITICAL -> {
                recommendations.add("Consider restarting the application")
                recommendations.add("Clear app cache and data")
                recommendations.add("Check available storage space")
            }
            ErrorSeverity.HIGH -> {
                recommendations.add("Review recent operations for issues")
                recommendations.add("Check app permissions")
                recommendations.add("Verify data integrity")
            }
            ErrorSeverity.MEDIUM -> {
                recommendations.add("Monitor error patterns")
                recommendations.add("Consider data cleanup")
            }
            ErrorSeverity.LOW -> {
                recommendations.add("System is operating normally")
            }
        }
        
        // Add specific recommendations based on error patterns
        stats.mostFrequentError?.let { errorType ->
            when (errorType) {
                "SecurityException" -> recommendations.add("Review and grant necessary permissions")
                "OutOfMemoryError" -> recommendations.add("Reduce data load or restart app")
                "ValidationException" -> recommendations.add("Check data quality and integrity")
            }
        }
        
        return recommendations
    }
}

/**
 * Custom exception for validation errors
 */
class ValidationException(
    message: String,
    val validationErrors: List<String> = emptyList()
) : Exception(message)

/**
 * Result class for batch operations
 */
data class BatchOperationResult<T>(
    val results: List<T>,
    val errors: List<BatchError<*>>,
    val totalProcessed: Int,
    val totalItems: Int
) {
    val successRate: Double = if (totalItems > 0) totalProcessed.toDouble() / totalItems else 0.0
    val hasErrors: Boolean = errors.isNotEmpty()
    val isPartialSuccess: Boolean = totalProcessed > 0 && errors.isNotEmpty()
}

/**
 * Error information for batch operations
 */
data class BatchError<T>(
    val item: T,
    val error: Exception
)

/**
 * Error statistics for system monitoring
 */
data class ErrorStatistics(
    val totalErrors24h: Int,
    val totalErrors1h: Int,
    val errorsByType: Map<String, Int>,
    val errorsByOperation: Map<String, Int>,
    val mostFrequentError: String?,
    val errorRate1h: Double
)

/**
 * System health information
 */
data class SystemHealth(
    val healthScore: Double, // 0.0 to 1.0
    val severity: ComprehensiveErrorHandler.ErrorSeverity,
    val recommendations: List<String>,
    val lastUpdated: Long
)