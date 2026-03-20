package com.message.bulksend.leadmanager.customfields.model

import com.message.bulksend.leadmanager.database.entities.CustomFieldDefinitionEntity
import com.message.bulksend.leadmanager.database.entities.CustomFieldType
import com.message.bulksend.leadmanager.database.entities.CustomFieldValueEntity
import org.json.JSONArray

/**
 * Domain model for Custom Field definition
 * Requirements: 2.1-2.12 - Supports all 12 field types
 */
data class CustomField(
    val id: String,
    val fieldName: String,
    val fieldType: CustomFieldType,
    val isRequired: Boolean = false,
    val defaultValue: String = "",
    val options: List<String> = emptyList(),
    val displayOrder: Int = 0,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Domain model for Custom Field value associated with a lead
 */
data class CustomFieldValue(
    val leadId: String,
    val fieldId: String,
    val fieldValue: String,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Extension function to convert CustomFieldDefinitionEntity to CustomField model
 * Handles JSON parsing for dropdown options
 */
fun CustomFieldDefinitionEntity.toModel(): CustomField {
    return CustomField(
        id = id,
        fieldName = fieldName,
        fieldType = fieldType,
        isRequired = isRequired,
        defaultValue = defaultValue,
        options = parseOptionsFromJson(options),
        displayOrder = displayOrder,
        isActive = isActive,
        createdAt = createdAt
    )
}

/**
 * Extension function to convert CustomField model to CustomFieldDefinitionEntity
 * Handles JSON serialization for dropdown options
 */
fun CustomField.toEntity(): CustomFieldDefinitionEntity {
    return CustomFieldDefinitionEntity(
        id = id,
        fieldName = fieldName,
        fieldType = fieldType,
        isRequired = isRequired,
        defaultValue = defaultValue,
        options = optionsToJson(options),
        displayOrder = displayOrder,
        isActive = isActive,
        createdAt = createdAt
    )
}

/**
 * Extension function to convert CustomFieldValueEntity to CustomFieldValue model
 */
fun CustomFieldValueEntity.toModel(): CustomFieldValue {
    return CustomFieldValue(
        leadId = leadId,
        fieldId = fieldId,
        fieldValue = fieldValue,
        updatedAt = updatedAt
    )
}

/**
 * Extension function to convert CustomFieldValue model to CustomFieldValueEntity
 */
fun CustomFieldValue.toEntity(): CustomFieldValueEntity {
    return CustomFieldValueEntity(
        leadId = leadId,
        fieldId = fieldId,
        fieldValue = fieldValue,
        updatedAt = updatedAt
    )
}

/**
 * Helper function to parse JSON array string to List<String>
 * Returns empty list if parsing fails or string is empty
 */
private fun parseOptionsFromJson(optionsJson: String): List<String> {
    if (optionsJson.isEmpty()) return emptyList()
    return try {
        val jsonArray = JSONArray(optionsJson)
        (0 until jsonArray.length()).map { jsonArray.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * Helper function to convert List<String> to JSON array string
 * Returns empty string if list is empty
 */
private fun optionsToJson(options: List<String>): String {
    if (options.isEmpty()) return ""
    return JSONArray(options).toString()
}
