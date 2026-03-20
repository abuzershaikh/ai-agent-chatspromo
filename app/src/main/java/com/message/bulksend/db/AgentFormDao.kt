package com.message.bulksend.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentFormDao {
    @Query("SELECT * FROM agent_forms ORDER BY createdAt DESC")
    fun getAllForms(): Flow<List<AgentFormEntity>>

    @Query("SELECT * FROM agent_forms")
    suspend fun getAllFormsOnce(): List<AgentFormEntity>

    @Query("SELECT * FROM agent_forms WHERE formId = :id")
    suspend fun getFormById(id: String): AgentFormEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertForm(form: AgentFormEntity)

    @Delete suspend fun deleteForm(form: AgentFormEntity)
}
