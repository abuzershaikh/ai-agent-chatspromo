package com.message.bulksend.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactGroupDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: ContactGroup)
    
    @androidx.room.Update
    suspend fun updateGroup(group: ContactGroup)

    @Query("SELECT * FROM contact_groups ORDER BY timestamp DESC")
    fun getAllGroups(): Flow<List<ContactGroup>>

    @Query("SELECT * FROM contact_groups ORDER BY timestamp DESC")
    suspend fun getAllGroupsList(): List<ContactGroup>

    @Query("SELECT * FROM contact_groups WHERE id = :groupId")
    suspend fun getGroupById(groupId: Long): ContactGroup?

    @Query("DELETE FROM contact_groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: Long)

    @Query("DELETE FROM contact_groups")
    suspend fun deleteAllGroups()
}

