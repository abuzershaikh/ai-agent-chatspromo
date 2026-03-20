package com.message.bulksend.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.message.bulksend.data.ContactStatus
import java.util.UUID

// Enhanced CampaignProgress with unique identification
data class EnhancedCampaignProgress(
    val uniqueId: String = UUID.randomUUID().toString(), // New unique identifier
    val campaignId: String,
    val campaignName: String,
    val campaignType: String,
    val message: String,
    val totalContacts: Int,
    val sentCount: Int,
    val failedCount: Int,
    val remainingCount: Int,
    val currentIndex: Int,
    val isRunning: Boolean,
    val isStopped: Boolean,
    val contactStatuses: List<ContactStatus> = emptyList(),
    val errorMessage: String? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(), // New field for creation tracking
    val pausedAt: Long? = null // New field for pause tracking
)

// Sorting options enum
enum class CampaignSortOption {
    DATE_NEWEST_FIRST,
    DATE_OLDEST_FIRST,
    CAMPAIGN_TYPE,
    PROGRESS_HIGH_TO_LOW,
    PROGRESS_LOW_TO_HIGH,
    NAME_A_TO_Z,
    NAME_Z_TO_A
}

// Delete result sealed class for error handling
sealed class DeleteResult {
    object Success : DeleteResult()
    data class Error(val message: String) : DeleteResult()
}

// Validation result sealed class for data validation
sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val errors: List<String>) : ValidationResult()
}

// Data operation result sealed class for comprehensive error handling
sealed class DataOperationResult<T> {
    data class Success<T>(val data: T) : DataOperationResult<T>()
    data class Error<T>(val message: String, val cause: Throwable? = null) : DataOperationResult<T>()
    data class PartialSuccess<T>(val data: T, val warnings: List<String>) : DataOperationResult<T>()
}

object EnhancedCampaignProgressManager {
    const val ENHANCED_PREFS_NAME = "enhanced_campaign_progress_prefs"
    private const val KEY_CAMPAIGN_PREFIX = "campaign_"
    private const val KEY_MIGRATION_FLAG = "migration_completed"
    private const val KEY_CORRUPTED_DATA_COUNT = "corrupted_data_count"
    private val gson = Gson()
    
    // Data validation constants
    private const val MAX_CAMPAIGN_NAME_LENGTH = 200
    private const val MAX_MESSAGE_LENGTH = 5000
    private const val MAX_CONTACTS = 10000
    private const val MIN_TIMESTAMP = 946684800000L // Jan 1, 2000

    /**
     * Validate campaign progress data for integrity and consistency
     * Requirements: 7.3
     */
    private fun validateCampaignProgress(progress: EnhancedCampaignProgress): ValidationResult {
        return CampaignValidationUtils.validateCampaignProgress(progress)
    }
    
    /**
     * Save campaign progress with unique ID using UUID-based keys and comprehensive validation
     * Requirements: 2.1, 2.2, 7.1, 7.3
     */
    fun saveProgressWithUniqueId(context: Context, progress: EnhancedCampaignProgress): DataOperationResult<Unit> {
        return try {
            // Validate data before saving
            when (val validation = validateCampaignProgress(progress)) {
                is ValidationResult.Valid -> {
                    val prefs = context.getSharedPreferences(ENHANCED_PREFS_NAME, Context.MODE_PRIVATE)
                    val jsonString = gson.toJson(progress)
                    val key = KEY_CAMPAIGN_PREFIX + progress.uniqueId
                    
                    val success = prefs.edit().putString(key, jsonString).commit()
                    if (success) {
                        DataOperationResult.Success(Unit)
                    } else {
                        DataOperationResult.Error("Failed to save campaign to SharedPreferences")
                    }
                }
                is ValidationResult.Invalid -> {
                    DataOperationResult.Error("Validation failed: ${validation.errors.joinToString(", ")}")
                }
            }
        } catch (e: Exception) {
            DataOperationResult.Error("Unexpected error while saving campaign: ${e.message}", e)
        }
    }
    
    /**
     * Legacy save method for backward compatibility
     * Requirements: 2.1, 2.2, 7.1
     */
    fun saveProgressWithUniqueIdLegacy(context: Context, progress: EnhancedCampaignProgress) {
        when (val result = saveProgressWithUniqueId(context, progress)) {
            is DataOperationResult.Success -> {
                // Success - no action needed
            }
            is DataOperationResult.Error -> {
                // Log error but don't throw to maintain backward compatibility
                android.util.Log.e("EnhancedCampaignProgressManager", "Save failed: ${result.message}", result.cause)
            }
            is DataOperationResult.PartialSuccess -> {
                // Log warnings
                android.util.Log.w("EnhancedCampaignProgressManager", "Save warnings: ${result.warnings.joinToString(", ")}")
            }
        }
    }

    /**
     * Delete campaign progress with error handling and SharedPreferences cleanup
     * Requirements: 4.3, 7.2
     */
    fun deleteProgress(context: Context, uniqueId: String): DeleteResult {
        return try {
            val prefs = context.getSharedPreferences(ENHANCED_PREFS_NAME, Context.MODE_PRIVATE)
            val key = KEY_CAMPAIGN_PREFIX + uniqueId
            
            // Check if campaign exists before deletion
            if (!prefs.contains(key)) {
                return DeleteResult.Error("Campaign not found")
            }
            
            // Remove the campaign from SharedPreferences
            val success = prefs.edit().remove(key).commit()
            
            if (success) {
                DeleteResult.Success
            } else {
                DeleteResult.Error("Failed to delete campaign from storage")
            }
        } catch (e: Exception) {
            DeleteResult.Error("Failed to delete campaign: ${e.message}")
        }
    }

    /**
     * Safely parse campaign data with error recovery
     * Requirements: 7.3, 7.4
     */
    private fun safeParseCampaign(key: String, jsonString: String, context: Context): EnhancedCampaignProgress? {
        return try {
            val progress = gson.fromJson(jsonString, EnhancedCampaignProgress::class.java)
            
            // Validate parsed data
            when (validateCampaignProgress(progress)) {
                is ValidationResult.Valid -> progress
                is ValidationResult.Invalid -> {
                    // Log validation errors and attempt recovery
                    android.util.Log.w("EnhancedCampaignProgressManager", 
                        "Invalid campaign data for key $key, attempting recovery")
                    
                    // Attempt to recover corrupted data
                    recoverCorruptedCampaign(progress, key, context)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("EnhancedCampaignProgressManager", 
                "Failed to parse campaign data for key $key", e)
            
            // Track corrupted data
            trackCorruptedData(context)
            
            // Attempt to clean up corrupted entry
            cleanupCorruptedEntry(key, context)
            
            null
        }
    }
    
    /**
     * Attempt to recover corrupted campaign data
     * Requirements: 7.3, 7.4
     */
    private fun recoverCorruptedCampaign(
        progress: EnhancedCampaignProgress, 
        key: String, 
        context: Context
    ): EnhancedCampaignProgress? {
        return try {
            val recovered = CampaignValidationUtils.recoverCorruptedCampaign(progress)
            if (recovered != null) {
                android.util.Log.i("EnhancedCampaignProgressManager", 
                    "Successfully recovered campaign data for key $key")
                saveProgressWithUniqueIdLegacy(context, recovered)
                recovered
            } else {
                android.util.Log.e("EnhancedCampaignProgressManager", 
                    "Failed to recover campaign data for key $key")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("EnhancedCampaignProgressManager", 
                "Error during campaign recovery for key $key", e)
            null
        }
    }
    
    /**
     * Track corrupted data occurrences
     * Requirements: 7.3
     */
    private fun trackCorruptedData(context: Context) {
        try {
            val prefs = context.getSharedPreferences(ENHANCED_PREFS_NAME, Context.MODE_PRIVATE)
            val currentCount = prefs.getInt(KEY_CORRUPTED_DATA_COUNT, 0)
            prefs.edit().putInt(KEY_CORRUPTED_DATA_COUNT, currentCount + 1).apply()
        } catch (e: Exception) {
            android.util.Log.e("EnhancedCampaignProgressManager", "Failed to track corrupted data", e)
        }
    }
    
    /**
     * Clean up corrupted data entry
     * Requirements: 7.3
     */
    private fun cleanupCorruptedEntry(key: String, context: Context) {
        try {
            val prefs = context.getSharedPreferences(ENHANCED_PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(key).apply()
            android.util.Log.i("EnhancedCampaignProgressManager", "Cleaned up corrupted entry: $key")
        } catch (e: Exception) {
            android.util.Log.e("EnhancedCampaignProgressManager", "Failed to cleanup corrupted entry: $key", e)
        }
    }
    
    /**
     * Retrieve only paused campaigns with comprehensive error handling and performance monitoring
     * Requirements: 2.3, 7.3, 7.4
     */
    fun getAllPausedCampaigns(context: Context): DataOperationResult<List<EnhancedCampaignProgress>> {
        return PerformanceMonitor.monitorOperation("getAllPausedCampaigns") {
            try {
            val prefs = context.getSharedPreferences(ENHANCED_PREFS_NAME, Context.MODE_PRIVATE)
            val pausedCampaigns = mutableListOf<EnhancedCampaignProgress>()
            val warnings = mutableListOf<String>()
            var corruptedCount = 0
            
            prefs.all.forEach { (key, value) ->
                if (key.startsWith(KEY_CAMPAIGN_PREFIX) && value is String) {
                    val progress = safeParseCampaign(key, value, context)
                    if (progress != null) {
                        // Only include campaigns that are stopped and have remaining contacts (paused)
                        if (progress.isStopped && progress.remainingCount > 0) {
                            pausedCampaigns.add(progress)
                        }
                    } else {
                        corruptedCount++
                    }
                }
            }
            
            if (corruptedCount > 0) {
                warnings.add("$corruptedCount corrupted campaign entries were found and cleaned up")
            }
            
            val sortedCampaigns = pausedCampaigns.sortedByDescending { it.lastUpdated }
            
            if (warnings.isNotEmpty()) {
                DataOperationResult.PartialSuccess(sortedCampaigns, warnings)
            } else {
                DataOperationResult.Success(sortedCampaigns)
            }
            } catch (e: Exception) {
                DataOperationResult.Error("Failed to retrieve paused campaigns: ${e.message}", e)
            }
        }
    }
    
    /**
     * Legacy method for backward compatibility
     * Requirements: 2.3
     */
    fun getAllPausedCampaignsLegacy(context: Context): List<EnhancedCampaignProgress> {
        return when (val result = getAllPausedCampaigns(context)) {
            is DataOperationResult.Success -> result.data
            is DataOperationResult.PartialSuccess -> {
                android.util.Log.w("EnhancedCampaignProgressManager", 
                    "Warnings during data retrieval: ${result.warnings.joinToString(", ")}")
                result.data
            }
            is DataOperationResult.Error -> {
                android.util.Log.e("EnhancedCampaignProgressManager", 
                    "Error retrieving paused campaigns: ${result.message}", result.cause)
                emptyList()
            }
        }
    }

    /**
     * Sort campaigns by different criteria
     * Requirements: 5.1, 5.2, 5.3, 5.4, 5.5
     */
    fun sortCampaigns(campaigns: List<EnhancedCampaignProgress>, sortOption: CampaignSortOption): List<EnhancedCampaignProgress> {
        return when (sortOption) {
            CampaignSortOption.DATE_NEWEST_FIRST -> campaigns.sortedByDescending { it.createdAt }
            CampaignSortOption.DATE_OLDEST_FIRST -> campaigns.sortedBy { it.createdAt }
            CampaignSortOption.CAMPAIGN_TYPE -> campaigns.sortedBy { it.campaignType }
            CampaignSortOption.PROGRESS_HIGH_TO_LOW -> campaigns.sortedByDescending { 
                if (it.totalContacts > 0) (it.sentCount.toDouble() / it.totalContacts.toDouble()) else 0.0 
            }
            CampaignSortOption.PROGRESS_LOW_TO_HIGH -> campaigns.sortedBy { 
                if (it.totalContacts > 0) (it.sentCount.toDouble() / it.totalContacts.toDouble()) else 0.0 
            }
            CampaignSortOption.NAME_A_TO_Z -> campaigns.sortedBy { it.campaignName.lowercase() }
            CampaignSortOption.NAME_Z_TO_A -> campaigns.sortedByDescending { it.campaignName.lowercase() }
        }
    }

    /**
     * Get all campaigns with comprehensive error handling
     * Requirements: 7.3, 7.4
     */
    fun getAllCampaigns(context: Context): DataOperationResult<List<EnhancedCampaignProgress>> {
        return try {
            val prefs = context.getSharedPreferences(ENHANCED_PREFS_NAME, Context.MODE_PRIVATE)
            val campaigns = mutableListOf<EnhancedCampaignProgress>()
            val warnings = mutableListOf<String>()
            var corruptedCount = 0
            
            prefs.all.forEach { (key, value) ->
                if (key.startsWith(KEY_CAMPAIGN_PREFIX) && value is String) {
                    val progress = safeParseCampaign(key, value, context)
                    if (progress != null) {
                        campaigns.add(progress)
                    } else {
                        corruptedCount++
                    }
                }
            }
            
            if (corruptedCount > 0) {
                warnings.add("$corruptedCount corrupted campaign entries were found and cleaned up")
            }
            
            val sortedCampaigns = campaigns.sortedByDescending { it.lastUpdated }
            
            if (warnings.isNotEmpty()) {
                DataOperationResult.PartialSuccess(sortedCampaigns, warnings)
            } else {
                DataOperationResult.Success(sortedCampaigns)
            }
        } catch (e: Exception) {
            DataOperationResult.Error("Failed to retrieve campaigns: ${e.message}", e)
        }
    }
    
    /**
     * Legacy method for backward compatibility
     */
    fun getAllCampaignsLegacy(context: Context): List<EnhancedCampaignProgress> {
        return when (val result = getAllCampaigns(context)) {
            is DataOperationResult.Success -> result.data
            is DataOperationResult.PartialSuccess -> {
                android.util.Log.w("EnhancedCampaignProgressManager", 
                    "Warnings during data retrieval: ${result.warnings.joinToString(", ")}")
                result.data
            }
            is DataOperationResult.Error -> {
                android.util.Log.e("EnhancedCampaignProgressManager", 
                    "Error retrieving campaigns: ${result.message}", result.cause)
                emptyList()
            }
        }
    }

    /**
     * Get campaign by unique ID with comprehensive error handling
     * Requirements: 7.3, 7.4
     */
    fun getCampaignByUniqueId(context: Context, uniqueId: String): DataOperationResult<EnhancedCampaignProgress?> {
        return try {
            if (uniqueId.isBlank()) {
                return DataOperationResult.Error("Unique ID cannot be empty")
            }
            
            val prefs = context.getSharedPreferences(ENHANCED_PREFS_NAME, Context.MODE_PRIVATE)
            val key = KEY_CAMPAIGN_PREFIX + uniqueId
            val jsonString = prefs.getString(key, null)
            
            if (jsonString != null) {
                val progress = safeParseCampaign(key, jsonString, context)
                if (progress != null) {
                    DataOperationResult.Success(progress)
                } else {
                    DataOperationResult.Error("Campaign data is corrupted and could not be recovered")
                }
            } else {
                DataOperationResult.Success(null) // Campaign not found, but this is not an error
            }
        } catch (e: Exception) {
            DataOperationResult.Error("Failed to retrieve campaign: ${e.message}", e)
        }
    }
    
    /**
     * Legacy method for backward compatibility
     */
    fun getCampaignByUniqueIdLegacy(context: Context, uniqueId: String): EnhancedCampaignProgress? {
        return when (val result = getCampaignByUniqueId(context, uniqueId)) {
            is DataOperationResult.Success -> result.data
            is DataOperationResult.PartialSuccess -> result.data
            is DataOperationResult.Error -> {
                android.util.Log.e("EnhancedCampaignProgressManager", 
                    "Error retrieving campaign: ${result.message}", result.cause)
                null
            }
        }
    }

    /**
     * Update existing campaign progress with validation
     * Requirements: 7.3
     */
    fun updateCampaignProgress(context: Context, progress: EnhancedCampaignProgress): DataOperationResult<Unit> {
        return try {
            // Verify campaign exists before updating
            when (val existingResult = getCampaignByUniqueId(context, progress.uniqueId)) {
                is DataOperationResult.Success -> {
                    if (existingResult.data == null) {
                        DataOperationResult.Error("Cannot update non-existent campaign with ID: ${progress.uniqueId}")
                    } else {
                        val updatedProgress = progress.copy(lastUpdated = System.currentTimeMillis())
                        saveProgressWithUniqueId(context, updatedProgress)
                    }
                }
                is DataOperationResult.Error -> {
                    DataOperationResult.Error("Failed to verify existing campaign: ${existingResult.message}", existingResult.cause)
                }
                is DataOperationResult.PartialSuccess -> {
                    // Proceed with update despite warnings
                    val updatedProgress = progress.copy(lastUpdated = System.currentTimeMillis())
                    saveProgressWithUniqueId(context, updatedProgress)
                }
            }
        } catch (e: Exception) {
            DataOperationResult.Error("Unexpected error during campaign update: ${e.message}", e)
        }
    }
    
    /**
     * Legacy method for backward compatibility
     */
    fun updateCampaignProgressLegacy(context: Context, progress: EnhancedCampaignProgress) {
        when (val result = updateCampaignProgress(context, progress)) {
            is DataOperationResult.Success -> {
                // Success - no action needed
            }
            is DataOperationResult.Error -> {
                android.util.Log.e("EnhancedCampaignProgressManager", 
                    "Update failed: ${result.message}", result.cause)
            }
            is DataOperationResult.PartialSuccess -> {
                android.util.Log.w("EnhancedCampaignProgressManager", 
                    "Update warnings: ${result.warnings.joinToString(", ")}")
            }
        }
    }

    /**
     * Check if migration has been completed
     */
    private fun isMigrationCompleted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(ENHANCED_PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_MIGRATION_FLAG, false)
    }

    /**
     * Mark migration as completed
     */
    private fun markMigrationCompleted(context: Context) {
        val prefs = context.getSharedPreferences(ENHANCED_PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_MIGRATION_FLAG, true).apply()
    }

    /**
     * Migrate legacy campaigns with comprehensive error handling and recovery
     * Requirements: 7.4, 2.4
     */
    fun migrateLegacyCampaigns(context: Context): DataOperationResult<Int> {
        // Skip if migration already completed
        if (isMigrationCompleted(context)) {
            return DataOperationResult.Success(0)
        }

        return try {
            val legacyCampaigns = CampaignProgressManager.getAllProgress(context)
            var successCount = 0
            var failureCount = 0
            val warnings = mutableListOf<String>()
            
            legacyCampaigns.forEach { legacy ->
                try {
                    val enhanced = EnhancedCampaignProgress(
                        uniqueId = UUID.randomUUID().toString(),
                        campaignId = legacy.campaignId.takeIf { it.isNotBlank() } ?: "legacy_${System.currentTimeMillis()}",
                        campaignName = legacy.campaignName.takeIf { it.isNotBlank() } ?: "Legacy Campaign",
                        campaignType = legacy.campaignType.takeIf { 
                            it in setOf("BULKSEND", "BULKTEXT", "TEXTMEDIA", "SHEETSEND", "FORMCAMPAIGN", "GOOGLEFORM") 
                        } ?: "BULKSEND",
                        message = legacy.message.take(MAX_MESSAGE_LENGTH),
                        totalContacts = maxOf(0, minOf(legacy.totalContacts, MAX_CONTACTS)),
                        sentCount = maxOf(0, minOf(legacy.sentCount, legacy.totalContacts)),
                        failedCount = maxOf(0, minOf(legacy.failedCount, legacy.totalContacts)),
                        remainingCount = maxOf(0, legacy.totalContacts - legacy.sentCount - legacy.failedCount),
                        currentIndex = maxOf(0, minOf(legacy.currentIndex, legacy.totalContacts)),
                        isRunning = legacy.isRunning,
                        isStopped = legacy.isStopped,
                        contactStatuses = legacy.contactStatuses,
                        errorMessage = legacy.errorMessage,
                        lastUpdated = if (legacy.lastUpdated >= MIN_TIMESTAMP) legacy.lastUpdated else System.currentTimeMillis(),
                        createdAt = if (legacy.lastUpdated >= MIN_TIMESTAMP) legacy.lastUpdated else System.currentTimeMillis(),
                        pausedAt = if (legacy.isStopped && legacy.lastUpdated >= MIN_TIMESTAMP) legacy.lastUpdated else null
                    )
                    
                    when (val saveResult = saveProgressWithUniqueId(context, enhanced)) {
                        is DataOperationResult.Success -> {
                            successCount++
                        }
                        is DataOperationResult.Error -> {
                            failureCount++
                            warnings.add("Failed to migrate campaign '${legacy.campaignName}': ${saveResult.message}")
                            android.util.Log.w("EnhancedCampaignProgressManager", 
                                "Migration failed for campaign ${legacy.campaignId}: ${saveResult.message}")
                        }
                        is DataOperationResult.PartialSuccess -> {
                            successCount++
                            warnings.addAll(saveResult.warnings)
                        }
                    }
                } catch (e: Exception) {
                    failureCount++
                    warnings.add("Exception during migration of campaign '${legacy.campaignName}': ${e.message}")
                    android.util.Log.e("EnhancedCampaignProgressManager", 
                        "Exception during migration of campaign ${legacy.campaignId}", e)
                }
            }
            
            // Mark migration as completed even if some campaigns failed
            // This prevents infinite retry loops
            markMigrationCompleted(context)
            
            android.util.Log.i("EnhancedCampaignProgressManager", 
                "Migration completed: $successCount successful, $failureCount failed")
            
            if (failureCount > 0) {
                DataOperationResult.PartialSuccess(successCount, warnings)
            } else {
                DataOperationResult.Success(successCount)
            }
        } catch (e: Exception) {
            android.util.Log.e("EnhancedCampaignProgressManager", "Migration failed completely", e)
            DataOperationResult.Error("Migration failed: ${e.message}", e)
        }
    }
    
    /**
     * Initialize the enhanced campaign progress manager and trigger migration if needed
     */
    fun initializeLegacy(context: Context) {
        try {
            // Trigger migration if not completed
            migrateLegacyCampaignsLegacy(context)
            
            // Perform data integrity check
            when (val result = performDataIntegrityCheck(context)) {
                is DataOperationResult.PartialSuccess -> {
                    android.util.Log.w("EnhancedCampaignProgressManager", 
                        "Initialization completed with warnings: ${result.warnings.joinToString(", ")}")
                }
                is DataOperationResult.Error -> {
                    android.util.Log.e("EnhancedCampaignProgressManager", 
                        "Initialization integrity check failed: ${result.message}", result.cause)
                }
                else -> {
                    android.util.Log.i("EnhancedCampaignProgressManager", "Initialization completed successfully")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("EnhancedCampaignProgressManager", "Initialization failed", e)
        }
    }

    /**
     * Legacy migration method for backward compatibility
     */
    fun migrateLegacyCampaignsLegacy(context: Context) {
        when (val result = migrateLegacyCampaigns(context)) {
            is DataOperationResult.Success -> {
                android.util.Log.i("EnhancedCampaignProgressManager", 
                    "Migration completed successfully: ${result.data} campaigns migrated")
            }
            is DataOperationResult.PartialSuccess -> {
                android.util.Log.w("EnhancedCampaignProgressManager", 
                    "Migration completed with warnings: ${result.data} campaigns migrated, warnings: ${result.warnings.joinToString(", ")}")
            }
            is DataOperationResult.Error -> {
                android.util.Log.e("EnhancedCampaignProgressManager", 
                    "Migration failed: ${result.message}", result.cause)
            }
        }
    }

    /**
     * Get data integrity statistics
     * Requirements: 7.3
     */
    fun getDataIntegrityStats(context: Context): Map<String, Int> {
        return try {
            val prefs = context.getSharedPreferences(ENHANCED_PREFS_NAME, Context.MODE_PRIVATE)
            val stats = mutableMapOf<String, Int>()
            
            var totalCampaigns = 0
            var validCampaigns = 0
            var corruptedCampaigns = 0
            
            prefs.all.forEach { (key, value) ->
                if (key.startsWith(KEY_CAMPAIGN_PREFIX) && value is String) {
                    totalCampaigns++
                    try {
                        val progress = gson.fromJson(value, EnhancedCampaignProgress::class.java)
                        when (validateCampaignProgress(progress)) {
                            is ValidationResult.Valid -> validCampaigns++
                            is ValidationResult.Invalid -> corruptedCampaigns++
                        }
                    } catch (e: Exception) {
                        corruptedCampaigns++
                    }
                }
            }
            
            stats["total_campaigns"] = totalCampaigns
            stats["valid_campaigns"] = validCampaigns
            stats["corrupted_campaigns"] = corruptedCampaigns
            stats["corrupted_data_count"] = prefs.getInt(KEY_CORRUPTED_DATA_COUNT, 0)
            stats["migration_completed"] = if (isMigrationCompleted(context)) 1 else 0
            
            stats
        } catch (e: Exception) {
            android.util.Log.e("EnhancedCampaignProgressManager", "Failed to get data integrity stats", e)
            mapOf("error" to 1)
        }
    }
    
    /**
     * Perform data integrity check and cleanup
     * Requirements: 7.3, 7.4
     */
    fun performDataIntegrityCheck(context: Context): DataOperationResult<Map<String, Int>> {
        return try {
            val prefs = context.getSharedPreferences(ENHANCED_PREFS_NAME, Context.MODE_PRIVATE)
            var totalChecked = 0
            var validCampaigns = 0
            var recoveredCampaigns = 0
            var removedCampaigns = 0
            val warnings = mutableListOf<String>()
            
            val keysToRemove = mutableListOf<String>()
            
            prefs.all.forEach { (key, value) ->
                if (key.startsWith(KEY_CAMPAIGN_PREFIX) && value is String) {
                    totalChecked++
                    try {
                        val progress = gson.fromJson(value, EnhancedCampaignProgress::class.java)
                        when (val validation = validateCampaignProgress(progress)) {
                            is ValidationResult.Valid -> {
                                validCampaigns++
                            }
                            is ValidationResult.Invalid -> {
                                // Attempt recovery
                                val recovered = recoverCorruptedCampaign(progress, key, context)
                                if (recovered != null) {
                                    recoveredCampaigns++
                                    warnings.add("Recovered corrupted campaign: ${progress.campaignName}")
                                } else {
                                    keysToRemove.add(key)
                                    warnings.add("Removed unrecoverable campaign: ${progress.campaignName}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        keysToRemove.add(key)
                        warnings.add("Removed corrupted entry: $key")
                    }
                }
            }
            
            // Remove corrupted entries
            val editor = prefs.edit()
            keysToRemove.forEach { key ->
                editor.remove(key)
                removedCampaigns++
            }
            editor.apply()
            
            val results = mapOf(
                "total_checked" to totalChecked,
                "valid_campaigns" to validCampaigns,
                "recovered_campaigns" to recoveredCampaigns,
                "removed_campaigns" to removedCampaigns
            )
            
            if (warnings.isNotEmpty()) {
                DataOperationResult.PartialSuccess(results, warnings)
            } else {
                DataOperationResult.Success(results)
            }
        } catch (e: Exception) {
            DataOperationResult.Error("Data integrity check failed: ${e.message}", e)
        }
    }
    
    /**
     * Clear all corrupted data and reset counters
     * Requirements: 7.3
     */
    fun clearCorruptedData(context: Context): DataOperationResult<Int> {
        return try {
            val prefs = context.getSharedPreferences(ENHANCED_PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            var removedCount = 0
            
            val keysToRemove = mutableListOf<String>()
            
            prefs.all.forEach { (key, value) ->
                if (key.startsWith(KEY_CAMPAIGN_PREFIX) && value is String) {
                    try {
                        val progress = gson.fromJson(value, EnhancedCampaignProgress::class.java)
                        when (validateCampaignProgress(progress)) {
                            is ValidationResult.Invalid -> {
                                keysToRemove.add(key)
                            }
                            is ValidationResult.Valid -> {
                                // Keep valid campaigns
                            }
                        }
                    } catch (e: Exception) {
                        keysToRemove.add(key)
                    }
                }
            }
            
            keysToRemove.forEach { key ->
                editor.remove(key)
                removedCount++
            }
            
            // Reset corrupted data counter
            editor.putInt(KEY_CORRUPTED_DATA_COUNT, 0)
            editor.apply()
            
            DataOperationResult.Success(removedCount)
        } catch (e: Exception) {
            DataOperationResult.Error("Failed to clear corrupted data: ${e.message}", e)
        }
    }
    
    /**
     * Initialize the manager and perform migration if needed with comprehensive error handling
     * Requirements: 7.4
     */
    fun initialize(context: Context): DataOperationResult<String> {
        return try {
            when (val migrationResult = migrateLegacyCampaigns(context)) {
                is DataOperationResult.Success -> {
                    DataOperationResult.Success("Initialization completed successfully. ${migrationResult.data} campaigns migrated.")
                }
                is DataOperationResult.PartialSuccess -> {
                    DataOperationResult.PartialSuccess(
                        "Initialization completed with warnings. ${migrationResult.data} campaigns migrated.",
                        migrationResult.warnings
                    )
                }
                is DataOperationResult.Error -> {
                    DataOperationResult.Error("Initialization failed: ${migrationResult.message}", migrationResult.cause)
                }
            }
        } catch (e: Exception) {
            DataOperationResult.Error("Unexpected error during initialization: ${e.message}", e)
        }
    }
    
    /**
     * Legacy initialization method for backward compatibility
     */
    fun initializeLegacy(context: Context) {
        when (val result = initialize(context)) {
            is DataOperationResult.Success -> {
                android.util.Log.i("EnhancedCampaignProgressManager", result.data)
            }
            is DataOperationResult.PartialSuccess -> {
                android.util.Log.w("EnhancedCampaignProgressManager", 
                    "${result.data} Warnings: ${result.warnings.joinToString(", ")}")
            }
            is DataOperationResult.Error -> {
                android.util.Log.e("EnhancedCampaignProgressManager", 
                    "Initialization failed: ${result.message}", result.cause)
            }
        }
    }
    
    /**
     * Perform comprehensive data integrity check and cleanup
     * Requirements: 7.3, 7.4
     */
    fun performDataIntegrityCheck(context: Context): DataOperationResult<String> {
        return try {
            val prefs = context.getSharedPreferences(ENHANCED_PREFS_NAME, Context.MODE_PRIVATE)
            var checkedCount = 0
            var fixedCount = 0
            var removedCount = 0
            val issues = mutableListOf<String>()
            
            prefs.all.forEach { (key, value) ->
                if (key.startsWith(KEY_CAMPAIGN_PREFIX) && value is String) {
                    checkedCount++
                    try {
                        val progress = gson.fromJson(value, EnhancedCampaignProgress::class.java)
                        when (val validation = validateCampaignProgress(progress)) {
                            is ValidationResult.Valid -> {
                                // Data is valid, no action needed
                            }
                            is ValidationResult.Invalid -> {
                                // Try to recover the data
                                val recovered = recoverCorruptedCampaign(progress, key, context)
                                if (recovered != null) {
                                    fixedCount++
                                    issues.add("Fixed corrupted campaign: ${progress.campaignName}")
                                } else {
                                    // Remove irreparable data
                                    cleanupCorruptedEntry(key, context)
                                    removedCount++
                                    issues.add("Removed irreparable campaign data: $key")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Remove completely corrupted entries
                        cleanupCorruptedEntry(key, context)
                        removedCount++
                        issues.add("Removed corrupted entry: $key")
                    }
                }
            }
            
            val summary = "Data integrity check completed. Checked: $checkedCount, Fixed: $fixedCount, Removed: $removedCount"
            
            if (issues.isNotEmpty()) {
                DataOperationResult.PartialSuccess(summary, issues)
            } else {
                DataOperationResult.Success(summary)
            }
        } catch (e: Exception) {
            DataOperationResult.Error("Data integrity check failed: ${e.message}", e)
        }
    }
    
    /**
     * Clear all campaign data - for testing purposes only
     * Requirements: Testing support
     */
    fun clearAllData(context: Context): DataOperationResult<String> {
        return try {
            val prefs = context.getSharedPreferences(ENHANCED_PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            
            // Remove all campaign-related keys
            prefs.all.keys.forEach { key ->
                if (key.startsWith(KEY_CAMPAIGN_PREFIX)) {
                    editor.remove(key)
                }
            }
            
            // Reset migration flag for testing
            editor.remove(KEY_MIGRATION_FLAG)
            editor.remove(KEY_CORRUPTED_DATA_COUNT)
            
            val success = editor.commit()
            if (success) {
                DataOperationResult.Success("All campaign data cleared successfully")
            } else {
                DataOperationResult.Error("Failed to clear campaign data")
            }
        } catch (e: Exception) {
            DataOperationResult.Error("Error clearing campaign data: ${e.message}", e)
        }
    }
    
    /**
     * Initialize the enhanced campaign progress manager with legacy migration
     * Requirements: 7.4, 2.4
     */
    fun initializeLegacy(context: Context) {
        try {
            // Trigger migration if not completed
            migrateLegacyCampaignsLegacy(context)
            
            // Clean up old deletion backups
            CampaignDeleteHandler.cleanupOldBackups(context)
            
            // Perform background data integrity check
            kotlinx.coroutines.GlobalScope.launch {
                try {
                    performDataIntegrityCheck(context)
                } catch (e: Exception) {
                    android.util.Log.w("EnhancedCampaignProgressManager", "Background integrity check failed", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("EnhancedCampaignProgressManager", "Initialization failed", e)
        }
    }
}