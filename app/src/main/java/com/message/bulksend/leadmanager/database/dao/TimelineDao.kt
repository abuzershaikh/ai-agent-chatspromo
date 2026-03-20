package com.message.bulksend.leadmanager.database.dao

import androidx.room.*
import com.message.bulksend.leadmanager.database.entities.TimelineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimelineDao {
    
    @Query("SELECT * FROM timeline_entries WHERE leadId = :leadId ORDER BY timestamp DESC")
    fun getTimelineForLead(leadId: String): Flow<List<TimelineEntity>>
    
    @Query("SELECT * FROM timeline_entries WHERE leadId = :leadId ORDER BY timestamp DESC")
    suspend fun getTimelineForLeadList(leadId: String): List<TimelineEntity>
    
    @Query("SELECT * FROM timeline_entries WHERE id = :id")
    suspend fun getTimelineEntryById(id: String): TimelineEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimelineEntry(entry: TimelineEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimelineEntries(entries: List<TimelineEntity>)
    
    @Update
    suspend fun updateTimelineEntry(entry: TimelineEntity)
    
    @Query("DELETE FROM timeline_entries WHERE id = :id")
    suspend fun deleteTimelineEntryById(id: String)
    
    @Query("DELETE FROM timeline_entries WHERE leadId = :leadId")
    suspend fun deleteAllTimelineForLead(leadId: String)
    
    @Query("SELECT COUNT(*) FROM timeline_entries WHERE leadId = :leadId")
    suspend fun getTimelineCountForLead(leadId: String): Int
    
    @Query("SELECT * FROM timeline_entries ORDER BY timestamp DESC")
    suspend fun getAllTimelineList(): List<TimelineEntity>
    
    @Query("SELECT * FROM timeline_entries WHERE id = :id")
    suspend fun getTimelineById(id: String): TimelineEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimeline(entry: TimelineEntity)
}
