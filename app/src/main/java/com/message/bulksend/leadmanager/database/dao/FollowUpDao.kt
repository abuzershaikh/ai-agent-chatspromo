package com.message.bulksend.leadmanager.database.dao

import androidx.room.*
import com.message.bulksend.leadmanager.database.entities.FollowUpEntity
import com.message.bulksend.leadmanager.model.FollowUpType
import kotlinx.coroutines.flow.Flow

@Dao
interface FollowUpDao {
    
    @Query("SELECT * FROM follow_ups ORDER BY scheduledDate DESC")
    fun getAllFollowUps(): Flow<List<FollowUpEntity>>
    
    @Query("SELECT * FROM follow_ups WHERE leadId = :leadId ORDER BY scheduledDate DESC")
    suspend fun getFollowUpsByLeadId(leadId: String): List<FollowUpEntity>
    
    @Query("SELECT * FROM follow_ups WHERE id = :id")
    suspend fun getFollowUpById(id: String): FollowUpEntity?
    
    @Query("SELECT * FROM follow_ups WHERE isCompleted = 0 ORDER BY scheduledDate ASC")
    suspend fun getPendingFollowUps(): List<FollowUpEntity>
    
    @Query("SELECT * FROM follow_ups WHERE isCompleted = 1 ORDER BY completedDate DESC")
    suspend fun getCompletedFollowUps(): List<FollowUpEntity>
    
    @Query("SELECT * FROM follow_ups WHERE scheduledDate BETWEEN :startDate AND :endDate ORDER BY scheduledDate ASC")
    suspend fun getFollowUpsByDateRange(startDate: Long, endDate: Long): List<FollowUpEntity>
    
    @Query("SELECT * FROM follow_ups WHERE type = :type ORDER BY scheduledDate DESC")
    suspend fun getFollowUpsByType(type: FollowUpType): List<FollowUpEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFollowUp(followUp: FollowUpEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFollowUps(followUps: List<FollowUpEntity>)
    
    @Update
    suspend fun updateFollowUp(followUp: FollowUpEntity)
    
    @Delete
    suspend fun deleteFollowUp(followUp: FollowUpEntity)
    
    @Query("DELETE FROM follow_ups WHERE id = :id")
    suspend fun deleteFollowUpById(id: String)
    
    @Query("DELETE FROM follow_ups WHERE leadId = :leadId")
    suspend fun deleteFollowUpsByLeadId(leadId: String)
    
    @Query("DELETE FROM follow_ups")
    suspend fun deleteAllFollowUps()
    
    // Today's follow-ups
    @Query("""
        SELECT * FROM follow_ups 
        WHERE date(scheduledDate/1000, 'unixepoch') = date('now') 
        AND isCompleted = 0 
        ORDER BY scheduledDate ASC
    """)
    suspend fun getTodayFollowUps(): List<FollowUpEntity>
    
    // Overdue follow-ups
    @Query("""
        SELECT * FROM follow_ups 
        WHERE scheduledDate < :currentTime 
        AND isCompleted = 0 
        ORDER BY scheduledDate ASC
    """)
    suspend fun getOverdueFollowUps(currentTime: Long = System.currentTimeMillis()): List<FollowUpEntity>
    
    // Upcoming follow-ups
    @Query("""
        SELECT * FROM follow_ups 
        WHERE scheduledDate > :currentTime 
        AND isCompleted = 0 
        ORDER BY scheduledDate ASC
    """)
    suspend fun getUpcomingFollowUps(currentTime: Long = System.currentTimeMillis()): List<FollowUpEntity>
    
    @Query("SELECT * FROM follow_ups ORDER BY scheduledDate DESC")
    suspend fun getAllFollowUpsList(): List<FollowUpEntity>
    
    @Query("SELECT COUNT(*) FROM follow_ups")
    suspend fun getFollowUpsCount(): Int
}