package com.message.bulksend.leadmanager.database.dao

import androidx.room.*
import com.message.bulksend.leadmanager.database.entities.CustomFieldDefinitionEntity
import com.message.bulksend.leadmanager.database.entities.CustomFieldValueEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Custom Fields operations
 * Requirements: 6.3, 6.4, 6.5
 */
@Dao
interface CustomFieldDao {
    
    // ==================== Field Definition Operations ====================
    
    /**
     * Get all active custom fields ordered by display order
     * Returns Flow for reactive updates
     */
    @Query("SELECT * FROM custom_field_definitions WHERE isActive = 1 ORDER BY displayOrder ASC")
    fun getAllActiveFields(): Flow<List<CustomFieldDefinitionEntity>>
    
    /**
     * Get all active custom fields as a suspend list
     */
    @Query("SELECT * FROM custom_field_definitions WHERE isActive = 1 ORDER BY displayOrder ASC")
    suspend fun getAllActiveFieldsList(): List<CustomFieldDefinitionEntity>
    
    /**
     * Get all custom fields (including inactive) ordered by display order
     */
    @Query("SELECT * FROM custom_field_definitions ORDER BY displayOrder ASC")
    fun getAllFields(): Flow<List<CustomFieldDefinitionEntity>>
    
    /**
     * Get a specific field by ID
     */
    @Query("SELECT * FROM custom_field_definitions WHERE id = :fieldId")
    suspend fun getFieldById(fieldId: String): CustomFieldDefinitionEntity?

    
    /**
     * Insert a new custom field definition
     * Uses REPLACE strategy to handle updates
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertField(field: CustomFieldDefinitionEntity)
    
    /**
     * Update an existing custom field definition
     */
    @Update
    suspend fun updateField(field: CustomFieldDefinitionEntity)
    
    /**
     * Delete a custom field definition by ID
     */
    @Query("DELETE FROM custom_field_definitions WHERE id = :fieldId")
    suspend fun deleteField(fieldId: String)
    
    /**
     * Update the display order of a field
     */
    @Query("UPDATE custom_field_definitions SET displayOrder = :order WHERE id = :fieldId")
    suspend fun updateFieldOrder(fieldId: String, order: Int)
    
    // ==================== Field Value Operations ====================
    
    /**
     * Get all custom field values for a specific lead
     * Returns Flow for reactive updates
     */
    @Query("SELECT * FROM custom_field_values WHERE leadId = :leadId")
    fun getValuesForLead(leadId: String): Flow<List<CustomFieldValueEntity>>
    
    /**
     * Get all custom field values for a specific lead as suspend list
     */
    @Query("SELECT * FROM custom_field_values WHERE leadId = :leadId")
    suspend fun getValuesForLeadList(leadId: String): List<CustomFieldValueEntity>
    
    /**
     * Insert or update a single custom field value
     * Uses REPLACE strategy for upsert behavior
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertValue(value: CustomFieldValueEntity)
    
    /**
     * Insert or update multiple custom field values
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertValues(values: List<CustomFieldValueEntity>)
    
    /**
     * Delete all values for a specific field (when field is deleted)
     */
    @Query("DELETE FROM custom_field_values WHERE fieldId = :fieldId")
    suspend fun deleteValuesForField(fieldId: String)
    
    /**
     * Delete all custom field values for a specific lead
     */
    @Query("DELETE FROM custom_field_values WHERE leadId = :leadId")
    suspend fun deleteValuesForLead(leadId: String)
    
    /**
     * Get all definitions as list for backup
     */
    @Query("SELECT * FROM custom_field_definitions ORDER BY displayOrder ASC")
    suspend fun getAllDefinitionsList(): List<CustomFieldDefinitionEntity>
    
    /**
     * Get all values as list for backup
     */
    @Query("SELECT * FROM custom_field_values")
    suspend fun getAllValuesList(): List<CustomFieldValueEntity>
    
    /**
     * Get definition by ID
     */
    @Query("SELECT * FROM custom_field_definitions WHERE id = :id")
    suspend fun getDefinitionById(id: String): CustomFieldDefinitionEntity?
    
    /**
     * Insert definition
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDefinition(definition: CustomFieldDefinitionEntity)
    
    /**
     * Update definition
     */
    @Update
    suspend fun updateDefinition(definition: CustomFieldDefinitionEntity)
    
    /**
     * Insert or update value
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateValue(value: CustomFieldValueEntity)
    
    /**
     * Get definitions count
     */
    @Query("SELECT COUNT(*) FROM custom_field_definitions")
    suspend fun getDefinitionsCount(): Int
}
