package com.message.bulksend.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledCampaignDao {
    
    @Query("SELECT * FROM scheduled_campaigns ORDER BY scheduledTime ASC")
    fun getAllScheduledCampaigns(): Flow<List<ScheduledCampaign>>
    
    @Query("SELECT * FROM scheduled_campaigns ORDER BY createdTime DESC")
    suspend fun getAllCampaigns(): List<ScheduledCampaign>
    
    @Query("UPDATE scheduled_campaigns SET status = 'COMPLETED' WHERE status = 'SCHEDULED' AND scheduledTime < :currentTime")
    suspend fun markOverdueCampaignsAsCompleted(currentTime: Long)
    
    @Query("SELECT * FROM scheduled_campaigns WHERE status = 'SCHEDULED' AND scheduledTime < :currentTime")
    suspend fun getOverdueCampaigns(currentTime: Long): List<ScheduledCampaign>
    
    @Query("SELECT * FROM scheduled_campaigns WHERE status = :status ORDER BY scheduledTime ASC")
    fun getCampaignsByStatus(status: String): Flow<List<ScheduledCampaign>>
    
    @Query("SELECT * FROM scheduled_campaigns WHERE id = :id")
    suspend fun getCampaignById(id: String): ScheduledCampaign?
    
    @Query("SELECT * FROM scheduled_campaigns WHERE status = 'SCHEDULED' AND scheduledTime <= :currentTime")
    suspend fun getDueCampaigns(currentTime: Long): List<ScheduledCampaign>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduledCampaign(campaign: ScheduledCampaign)
    
    @Update
    suspend fun updateScheduledCampaign(campaign: ScheduledCampaign)
    
    @Query("UPDATE scheduled_campaigns SET status = :status WHERE id = :id")
    suspend fun updateCampaignStatus(id: String, status: String)
    
    @Query("UPDATE scheduled_campaigns SET scheduledTime = :newTime WHERE id = :id")
    suspend fun updateScheduledTime(id: String, newTime: Long)
    
    @Query("DELETE FROM scheduled_campaigns WHERE id = :id")
    suspend fun deleteCampaign(id: String)
    
    @Query("DELETE FROM scheduled_campaigns WHERE status IN ('COMPLETED', 'CANCELLED', 'FAILED') AND createdTime < :cutoffTime")
    suspend fun deleteOldCampaigns(cutoffTime: Long)
}