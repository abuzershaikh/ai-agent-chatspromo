package com.message.bulksend.utils

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

/**
 * Data recovery utilities for corrupted campaign data
 * Requirements: 7.3, 7.4 - Error handling and recovery mechanisms
 */
object CampaignDataRecovery {
    
    private const val TAG = "CampaignDataRecovery"
    private const val RECOVERY_PREFS_NAME = "campaign_recovery_prefs"
    private const val KEY_RECOVERY_COUNT = "recovery_count"
    private const val KEY_LAST_RECOVERY = "last_recovery_timestamp"
    
    private val gson = Gson()

    /**
     * Perform comprehensive data recovery
     */
    fun performDataRecovery(context: Context): DataOperationResult<RecoveryReport> {
        return try {
            Log.i(TAG, "Starting data recovery process")
            
            val recoveryReport = RecoveryReport()
            val prefs = context.getSharedPreferences("enhanced_campaign_progress_prefs", Context.MODE_PRIVATE)
            val allData = prefs.all
            
            var totalCampaigns = 0
            var corruptedCampaigns = 0
            var recoveredCampaigns = 0
            var removedCampaigns = 0
            val warnings = mutableListOf<String>()
            
            val keysToRemove = mutableListOf<String>()
            val campaignsToSave = mutableListOf<Pair<String, EnhancedCampaignProgress>>()
            
            // Process each campaign entry
            allData.forEach { (key, value) ->
                if (key.startsWith("campaign_") && value is String) {
                    totalCampaigns++
                    
                    try {
                        val campaign = gson.fromJson(value, EnhancedCampaignProgress::class.java)
                        
                        // Validate the campaign
                        when (val validation = CampaignValidationUtils.validateCampaignProgress(campaign)) {
                            is ValidationResult.Valid -> {
                                // Campaign is valid, no action needed
                            }
                            is ValidationResult.Invalid -> {
                                corruptedCampaigns++
                                
                                // Attempt recovery
                                val recovered = CampaignValidationUtils.recoverCorruptedCampaign(campaign)
                                if (recovered != null) {
                                    recoveredCampaigns++
                                    campaignsToSave.add(key to recovered)
                                    warnings.add("Recovered campaign: ${campaign.campaignName}")
                                    Log.i(TAG, "Successfully recovered campaign: ${campaign.campaignName}")
                                } else {
                                    removedCampaigns++
                                    keysToRemove.add(key)
                                    warnings.add("Removed unrecoverable campaign: ${campaign.campaignName}")
                                    Log.w(TAG, "Could not recover campaign: ${campaign.campaignName}")
                                }
                            }
                        }
                    } catch (e: JsonSyntaxException) {
                        corruptedCampaigns++
                        removedCampaigns++
                        keysToRemove.add(key)
                        warnings.add("Removed campaign with invalid JSON format")
                        Log.w(TAG, "Removed campaign with invalid JSON: $key", e)
                    } catch (e: Exception) {
                        corruptedCampaigns++
                        removedCampaigns++
                        keysToRemove.add(key)
                        warnings.add("Removed campaign due to unexpected error: ${e.message}")
                        Log.e(TAG, "Unexpected error processing campaign: $key", e)
                    }
                }
            }
            
            // Apply recovery changes
            val editor = prefs.edit()
            
            // Save recovered campaigns
            campaignsToSave.forEach { (key, campaign) ->
                val jsonString = gson.toJson(campaign)
                editor.putString(key, jsonString)
            }
            
            // Remove unrecoverable campaigns
            keysToRemove.forEach { key ->
                editor.remove(key)
            }
            
            // Commit changes
            val success = editor.commit()
            
            if (success) {
                // Update recovery statistics
                updateRecoveryStatistics(context, recoveredCampaigns + removedCampaigns)
                
                recoveryReport.apply {
                    this.totalCampaigns = totalCampaigns
                    this.corruptedCampaigns = corruptedCampaigns
                    this.recoveredCampaigns = recoveredCampaigns
                    this.removedCampaigns = removedCampaigns
                    this.warnings = warnings
                    this.success = true
                }
                
                Log.i(TAG, "Data recovery completed successfully: $recoveryReport")
                
                if (warnings.isNotEmpty()) {
                    DataOperationResult.PartialSuccess(recoveryReport, warnings)
                } else {
                    DataOperationResult.Success(recoveryReport)
                }
            } else {
                DataOperationResult.Error("Failed to save recovery changes to storage")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Data recovery failed", e)
            DataOperationResult.Error("Data recovery failed: ${e.message}", e)
        }
    }
    
    /**
     * Perform quick corruption scan without recovery
     */
    fun scanForCorruption(context: Context): DataOperationResult<CorruptionScanResult> {
        return try {
            val prefs = context.getSharedPreferences("enhanced_campaign_progress_prefs", Context.MODE_PRIVATE)
            val allData = prefs.all
            
            var totalCampaigns = 0
            var corruptedCampaigns = 0
            val corruptionIssues = mutableListOf<String>()
            
            allData.forEach { (key, value) ->
                if (key.startsWith("campaign_") && value is String) {
                    totalCampaigns++
                    
                    try {
                        val campaign = gson.fromJson(value, EnhancedCampaignProgress::class.java)
                        
                        when (val validation = CampaignValidationUtils.validateCampaignProgress(campaign)) {
                            is ValidationResult.Invalid -> {
                                corruptedCampaigns++
                                corruptionIssues.add("${campaign.campaignName}: ${validation.errors.joinToString(", ")}")
                            }
                            is ValidationResult.Valid -> {
                                // Campaign is valid
                            }
                        }
                    } catch (e: JsonSyntaxException) {
                        corruptedCampaigns++
                        corruptionIssues.add("$key: Invalid JSON format")
                    } catch (e: Exception) {
                        corruptedCampaigns++
                        corruptionIssues.add("$key: ${e.message}")
                    }
                }
            }
            
            val scanResult = CorruptionScanResult(
                totalCampaigns = totalCampaigns,
                corruptedCampaigns = corruptedCampaigns,
                corruptionIssues = corruptionIssues
            )
            
            DataOperationResult.Success(scanResult)
            
        } catch (e: Exception) {
            DataOperationResult.Error("Corruption scan failed: ${e.message}", e)
        }
    }
    
    /**
     * Backup current data before recovery
     */
    fun createBackup(context: Context): DataOperationResult<String> {
        return try {
            val prefs = context.getSharedPreferences("enhanced_campaign_progress_prefs", Context.MODE_PRIVATE)
            val backupPrefs = context.getSharedPreferences("campaign_backup_${System.currentTimeMillis()}", Context.MODE_PRIVATE)
            
            val allData = prefs.all
            val editor = backupPrefs.edit()
            
            allData.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                }
            }
            
            val success = editor.commit()
            
            if (success) {
                val backupName = backupPrefs.toString()
                DataOperationResult.Success(backupName)
            } else {
                DataOperationResult.Error("Failed to create backup")
            }
            
        } catch (e: Exception) {
            DataOperationResult.Error("Backup creation failed: ${e.message}", e)
        }
    }
    
    /**
     * Get recovery statistics
     */
    fun getRecoveryStatistics(context: Context): RecoveryStatistics {
        val prefs = context.getSharedPreferences(RECOVERY_PREFS_NAME, Context.MODE_PRIVATE)
        
        return RecoveryStatistics(
            totalRecoveries = prefs.getInt(KEY_RECOVERY_COUNT, 0),
            lastRecoveryTimestamp = prefs.getLong(KEY_LAST_RECOVERY, 0)
        )
    }
    
    /**
     * Update recovery statistics
     */
    private fun updateRecoveryStatistics(context: Context, recoveredCount: Int) {
        val prefs = context.getSharedPreferences(RECOVERY_PREFS_NAME, Context.MODE_PRIVATE)
        val currentCount = prefs.getInt(KEY_RECOVERY_COUNT, 0)
        
        prefs.edit()
            .putInt(KEY_RECOVERY_COUNT, currentCount + recoveredCount)
            .putLong(KEY_LAST_RECOVERY, System.currentTimeMillis())
            .apply()
    }
    
    /**
     * Clear recovery statistics
     */
    fun clearRecoveryStatistics(context: Context) {
        val prefs = context.getSharedPreferences(RECOVERY_PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}

/**
 * Recovery report data class
 */
data class RecoveryReport(
    var totalCampaigns: Int = 0,
    var corruptedCampaigns: Int = 0,
    var recoveredCampaigns: Int = 0,
    var removedCampaigns: Int = 0,
    var warnings: List<String> = emptyList(),
    var success: Boolean = false
) {
    val recoveryRate: Float
        get() = if (corruptedCampaigns > 0) {
            recoveredCampaigns.toFloat() / corruptedCampaigns.toFloat()
        } else {
            1.0f
        }
    
    override fun toString(): String {
        return "RecoveryReport(total=$totalCampaigns, corrupted=$corruptedCampaigns, recovered=$recoveredCampaigns, removed=$removedCampaigns, rate=${String.format("%.1f%%", recoveryRate * 100)})"
    }
}

/**
 * Corruption scan result data class
 */
data class CorruptionScanResult(
    val totalCampaigns: Int,
    val corruptedCampaigns: Int,
    val corruptionIssues: List<String>
) {
    val corruptionRate: Float
        get() = if (totalCampaigns > 0) {
            corruptedCampaigns.toFloat() / totalCampaigns.toFloat()
        } else {
            0.0f
        }
}

/**
 * Recovery statistics data class
 */
data class RecoveryStatistics(
    val totalRecoveries: Int,
    val lastRecoveryTimestamp: Long
)