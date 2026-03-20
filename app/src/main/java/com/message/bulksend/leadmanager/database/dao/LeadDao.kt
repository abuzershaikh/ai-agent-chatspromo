package com.message.bulksend.leadmanager.database.dao

import androidx.room.*
import com.message.bulksend.leadmanager.database.entities.LeadEntity
import com.message.bulksend.leadmanager.model.LeadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface LeadDao {
    
    @Query("SELECT * FROM leads ORDER BY timestamp DESC")
    fun getAllLeads(): Flow<List<LeadEntity>>
    
    @Query("SELECT * FROM leads ORDER BY timestamp DESC")
    suspend fun getAllLeadsList(): List<LeadEntity>
    
    @Query("SELECT * FROM leads WHERE status = :status ORDER BY timestamp DESC")
    suspend fun getLeadsByStatus(status: LeadStatus): List<LeadEntity>
    
    @Query("SELECT * FROM leads WHERE id = :id")
    suspend fun getLeadById(id: String): LeadEntity?
    
    @Query("SELECT * FROM leads WHERE name LIKE '%' || :query || '%' OR phoneNumber LIKE '%' || :query || '%'")
    suspend fun searchLeads(query: String): List<LeadEntity>
    
    @Query("SELECT * FROM leads WHERE phoneNumber = :phone OR phoneNumber LIKE '%' || :phone || '%' OR :phone LIKE '%' || phoneNumber || '%' LIMIT 1")
    suspend fun getLeadByPhone(phone: String): LeadEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLead(lead: LeadEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLeads(leads: List<LeadEntity>)
    
    @Update
    suspend fun updateLead(lead: LeadEntity)
    
    @Delete
    suspend fun deleteLead(lead: LeadEntity)
    
    @Query("DELETE FROM leads WHERE id = :id")
    suspend fun deleteLeadById(id: String)
    
    @Query("DELETE FROM leads")
    suspend fun deleteAllLeads()
    
    // Stats queries
    @Query("SELECT COUNT(*) FROM leads")
    suspend fun getTotalLeadsCount(): Int
    
    @Query("SELECT COUNT(*) FROM leads WHERE status = :status")
    suspend fun getLeadsCountByStatus(status: LeadStatus): Int
    
    @Query("SELECT * FROM leads WHERE nextFollowUpDate IS NOT NULL AND nextFollowUpDate > 0 AND isFollowUpCompleted = 0 ORDER BY nextFollowUpDate ASC")
    suspend fun getLeadsWithPendingFollowUps(): List<LeadEntity>
    
    @Query("SELECT COUNT(*) FROM leads")
    suspend fun getLeadsCount(): Int
}