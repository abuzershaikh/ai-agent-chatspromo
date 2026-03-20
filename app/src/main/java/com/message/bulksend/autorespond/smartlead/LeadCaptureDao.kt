package com.message.bulksend.autorespond.smartlead

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LeadCaptureDao {
    
    // Lead Capture Operations
    @Insert
    suspend fun insertLeadCapture(leadCapture: LeadCaptureEntity): Long
    
    @Update
    suspend fun updateLeadCapture(leadCapture: LeadCaptureEntity)
    
    @Query("SELECT * FROM lead_captures WHERE username = :username AND platform = :platform AND isCompleted = 0 LIMIT 1")
    suspend fun getActiveLeadCapture(username: String, platform: String): LeadCaptureEntity?
    
    @Query("SELECT * FROM lead_captures WHERE isCompleted = 1 ORDER BY timestamp DESC")
    fun getCompletedLeadCaptures(): Flow<List<LeadCaptureEntity>>
    
    @Query("SELECT COUNT(*) FROM lead_captures WHERE isCompleted = 1")
    suspend fun getCompletedCapturesCount(): Int
    
    @Query("SELECT COUNT(*) FROM lead_captures WHERE isCompleted = 0")
    suspend fun getActiveCapturesCount(): Int
    
    @Query("DELETE FROM lead_captures WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldCaptures(beforeTimestamp: Long)
    
    // Settings Operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSettings(settings: LeadCaptureSettingsEntity)
    
    @Query("SELECT * FROM lead_capture_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): LeadCaptureSettingsEntity?
    
    @Query("UPDATE lead_capture_settings SET isEnabled = :enabled WHERE id = 1")
    suspend fun updateEnabledStatus(enabled: Boolean)
}