package com.message.bulksend.leadmanager.database.dao

import androidx.room.*
import com.message.bulksend.leadmanager.database.entities.AutoAddSettingsEntity
import com.message.bulksend.leadmanager.database.entities.AutoAddKeywordRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AutoAddSettingsDao {
    
    // Settings
    @Query("SELECT * FROM auto_add_settings WHERE id = 'default' LIMIT 1")
    suspend fun getSettings(): AutoAddSettingsEntity?
    
    @Query("SELECT * FROM auto_add_settings WHERE id = 'default' LIMIT 1")
    fun getSettingsFlow(): Flow<AutoAddSettingsEntity?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: AutoAddSettingsEntity)
    
    @Update
    suspend fun updateSettings(settings: AutoAddSettingsEntity)
    
    // Keyword Rules
    @Query("SELECT * FROM auto_add_keyword_rules WHERE isEnabled = 1 ORDER BY createdAt DESC")
    fun getAllActiveRules(): Flow<List<AutoAddKeywordRuleEntity>>
    
    @Query("SELECT * FROM auto_add_keyword_rules WHERE isEnabled = 1 ORDER BY createdAt DESC")
    suspend fun getAllActiveRulesList(): List<AutoAddKeywordRuleEntity>
    
    @Query("SELECT * FROM auto_add_keyword_rules ORDER BY createdAt DESC")
    suspend fun getAllRulesList(): List<AutoAddKeywordRuleEntity>
    
    @Query("SELECT * FROM auto_add_keyword_rules WHERE id = :id")
    suspend fun getRuleById(id: String): AutoAddKeywordRuleEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: AutoAddKeywordRuleEntity)
    
    @Update
    suspend fun updateRule(rule: AutoAddKeywordRuleEntity)
    
    @Query("UPDATE auto_add_keyword_rules SET isEnabled = :enabled WHERE id = :id")
    suspend fun setRuleEnabled(id: String, enabled: Boolean)
    
    @Delete
    suspend fun deleteRule(rule: AutoAddKeywordRuleEntity)
    
    @Query("DELETE FROM auto_add_keyword_rules WHERE id = :id")
    suspend fun deleteRuleById(id: String)
    
    @Query("DELETE FROM auto_add_keyword_rules")
    suspend fun deleteAllRules()
    
    @Query("SELECT * FROM auto_add_settings WHERE id = 'default' LIMIT 1")
    suspend fun getSettingsDirect(): AutoAddSettingsEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSettings(settings: AutoAddSettingsEntity)
    
    @Query("SELECT * FROM auto_add_keyword_rules ORDER BY createdAt DESC")
    suspend fun getAllKeywordRulesList(): List<AutoAddKeywordRuleEntity>
    
    @Query("SELECT * FROM auto_add_keyword_rules WHERE id = :id")
    suspend fun getKeywordRuleById(id: String): AutoAddKeywordRuleEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKeywordRule(rule: AutoAddKeywordRuleEntity)
    
    @Update
    suspend fun updateKeywordRule(rule: AutoAddKeywordRuleEntity)
}
