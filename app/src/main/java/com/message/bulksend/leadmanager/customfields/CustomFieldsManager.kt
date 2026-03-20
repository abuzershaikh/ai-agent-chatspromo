package com.message.bulksend.leadmanager.customfields

import android.content.Context
import com.message.bulksend.leadmanager.customfields.model.CustomField
import com.message.bulksend.leadmanager.customfields.model.CustomFieldValue
import com.message.bulksend.leadmanager.customfields.model.toEntity
import com.message.bulksend.leadmanager.customfields.model.toModel
import com.message.bulksend.leadmanager.database.LeadManagerDatabase
import com.message.bulksend.leadmanager.database.entities.CustomFieldValueEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Manager class for Custom Fields operations
 * Handles all custom field-related business logic
 * Requirements: 1.1, 1.3, 1.4, 1.5, 1.6, 8.1, 8.2, 8.3, 8.4
 */
class CustomFieldsManager(context: Context) {
    
    private val database = LeadManagerDatabase.getDatabase(context)
    private val customFieldDao = database.customFieldDao()
    
    // ==================== Field Definition Operations ====================
    
    /**
     * Get all active custom fields as Flow (reactive)
     * Returns Flow<List<CustomField>> for real-time updates
     * Requirement: 8.2 - New fields appear without app restart
     */
    fun getAllActiveFields(): Flow<List<CustomField>> {
        return customFieldDao.getAllActiveFields().map { entities ->
            entities.map { it.toModel() }
        }
    }
    
    /**
     * Get all active custom fields as suspend list
     * Returns List<CustomField> for one-time fetch
     */
    suspend fun getAllActiveFieldsList(): List<CustomField> {
        return customFieldDao.getAllActiveFieldsList().map { it.toModel() }
    }
    
    /**
     * Get all custom fields (including inactive) as Flow
     * Useful for management screen
     * Requirement: 1.1 - Display all existing custom fields
     */
    fun getAllFields(): Flow<List<CustomField>> {
        return customFieldDao.getAllFields().map { entities ->
            entities.map { it.toModel() }
        }
    }

    /**
     * Get a specific custom field by ID
     * Returns CustomField? (null if not found)
     */
    suspend fun getFieldById(fieldId: String): CustomField? {
        return customFieldDao.getFieldById(fieldId)?.toModel()
    }
    
    /**
     * Add a new custom field
     * Requirement: 1.3 - Create new custom field with name, type, required flag, default value, options
     */
    suspend fun addField(field: CustomField) {
        val fieldWithId = if (field.id.isEmpty()) {
            field.copy(
                id = UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis()
            )
        } else {
            field
        }
        customFieldDao.insertField(fieldWithId.toEntity())
    }
    
    /**
     * Update an existing custom field
     * Requirement: 1.4 - Edit dialog with pre-filled values
     */
    suspend fun updateField(field: CustomField) {
        customFieldDao.updateField(field.toEntity())
    }
    
    /**
     * Delete a custom field and all its associated values
     * Requirement: 1.5 - Delete field on confirm
     * Requirement: 8.3 - Remove from all screens and delete associated values
     */
    suspend fun deleteField(fieldId: String) {
        // First delete all values associated with this field
        customFieldDao.deleteValuesForField(fieldId)
        // Then delete the field definition
        customFieldDao.deleteField(fieldId)
    }
    
    /**
     * Reorder custom fields by updating their display order
     * Requirement: 1.6 - Allow reordering and save new display order
     */
    suspend fun reorderFields(fieldIds: List<String>) {
        fieldIds.forEachIndexed { index, fieldId ->
            customFieldDao.updateFieldOrder(fieldId, index)
        }
    }
    
    /**
     * Deactivate a custom field (soft delete)
     * Requirement: 8.4 - Hide from Add Lead but preserve existing values
     */
    suspend fun deactivateField(fieldId: String) {
        val field = customFieldDao.getFieldById(fieldId) ?: return
        customFieldDao.updateField(field.copy(isActive = false))
    }
    
    /**
     * Activate a previously deactivated custom field
     */
    suspend fun activateField(fieldId: String) {
        val field = customFieldDao.getFieldById(fieldId) ?: return
        customFieldDao.updateField(field.copy(isActive = true))
    }

    
    // ==================== Field Value Operations ====================
    
    /**
     * Get all custom field values for a lead as Flow (reactive)
     * Returns Flow<Map<String, String>> where key is fieldId and value is fieldValue
     * Requirement: 8.1 - Changes reflect immediately across screens
     */
    fun getValuesForLead(leadId: String): Flow<Map<String, String>> {
        return customFieldDao.getValuesForLead(leadId).map { entities ->
            entities.associate { it.fieldId to it.fieldValue }
        }
    }
    
    /**
     * Get all custom field values for a lead as Map
     * Returns Map<String, String> for one-time fetch
     */
    suspend fun getValuesForLeadMap(leadId: String): Map<String, String> {
        return customFieldDao.getValuesForLeadList(leadId)
            .associate { it.fieldId to it.fieldValue }
    }
    
    /**
     * Get all custom field values for a lead as list of CustomFieldValue
     */
    suspend fun getValuesForLeadList(leadId: String): List<CustomFieldValue> {
        return customFieldDao.getValuesForLeadList(leadId).map { it.toModel() }
    }
    
    /**
     * Save a single custom field value for a lead
     * Requirement: 8.1 - Update value in database immediately
     */
    suspend fun saveFieldValue(leadId: String, fieldId: String, value: String) {
        val entity = CustomFieldValueEntity(
            leadId = leadId,
            fieldId = fieldId,
            fieldValue = value,
            updatedAt = System.currentTimeMillis()
        )
        customFieldDao.insertValue(entity)
    }
    
    /**
     * Save multiple custom field values for a lead
     * Useful when saving all custom fields at once (e.g., when adding a new lead)
     */
    suspend fun saveFieldValues(leadId: String, values: Map<String, String>) {
        if (values.isEmpty()) return
        
        val now = System.currentTimeMillis()
        val entities = values.map { (fieldId, fieldValue) ->
            CustomFieldValueEntity(
                leadId = leadId,
                fieldId = fieldId,
                fieldValue = fieldValue,
                updatedAt = now
            )
        }
        customFieldDao.insertValues(entities)
    }
    
    /**
     * Delete all custom field values for a lead
     * Called when a lead is deleted (though CASCADE should handle this)
     */
    suspend fun deleteValuesForLead(leadId: String) {
        customFieldDao.deleteValuesForLead(leadId)
    }
    
    /**
     * Get custom field value for a specific field and lead
     */
    suspend fun getFieldValue(leadId: String, fieldId: String): String? {
        val values = getValuesForLeadMap(leadId)
        return values[fieldId]
    }
    
    /**
     * Check if a required field has a value
     * Returns true if field is not required OR if it has a non-empty value
     */
    suspend fun isFieldValueValid(leadId: String, field: CustomField): Boolean {
        if (!field.isRequired) return true
        val value = getFieldValue(leadId, field.id)
        return !value.isNullOrBlank()
    }
    
    /**
     * Validate all required custom fields for a lead
     * Returns list of field names that are missing required values
     */
    suspend fun validateRequiredFields(leadId: String, values: Map<String, String>): List<String> {
        val activeFields = getAllActiveFieldsList()
        val missingFields = mutableListOf<String>()
        
        for (field in activeFields) {
            if (field.isRequired) {
                val value = values[field.id]
                if (value.isNullOrBlank()) {
                    missingFields.add(field.fieldName)
                }
            }
        }
        
        return missingFields
    }
    
    /**
     * Get default values for all active custom fields
     * Useful when initializing a new lead form
     */
    suspend fun getDefaultValues(): Map<String, String> {
        return getAllActiveFieldsList()
            .filter { it.defaultValue.isNotEmpty() }
            .associate { it.id to it.defaultValue }
    }
    
    /**
     * Get the next display order for a new field
     */
    suspend fun getNextDisplayOrder(): Int {
        val fields = getAllActiveFieldsList()
        return if (fields.isEmpty()) 0 else fields.maxOf { it.displayOrder } + 1
    }
}
