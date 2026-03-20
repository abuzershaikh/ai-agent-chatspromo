package com.message.bulksend.leadmanager.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Enum representing all supported custom field types
 * Requirements: 2.1-2.12
 */
enum class CustomFieldType {
    TEXT,           // Single line text input
    NUMBER,         // Numeric input
    DATE,           // Date picker
    TIME,           // Time picker
    DATETIME,       // Date + Time picker
    DROPDOWN,       // Select from predefined options
    CHECKBOX,       // Boolean switch
    PHONE,          // Phone number with phone keyboard
    EMAIL,          // Email with validation
    URL,            // URL input
    TEXTAREA,       // Multi-line text
    CURRENCY        // Currency with formatting
}

/**
 * Entity for storing custom field definitions
 * Requirements: 6.1
 */
@Entity(tableName = "custom_field_definitions")
data class CustomFieldDefinitionEntity(
    @PrimaryKey
    val id: String,
    val fieldName: String,
    val fieldType: CustomFieldType,
    val isRequired: Boolean = false,
    val defaultValue: String = "",
    val options: String = "",      // JSON array for dropdown options
    val displayOrder: Int = 0,
    val isActive: Boolean = true,
    val createdAt: Long
)


/**
 * Entity for storing custom field values for each lead
 * Requirements: 6.2
 * 
 * Uses composite primary key (leadId, fieldId) and foreign key to leads table
 * with CASCADE delete to automatically remove values when lead is deleted
 */
@Entity(
    tableName = "custom_field_values",
    primaryKeys = ["leadId", "fieldId"],
    foreignKeys = [
        ForeignKey(
            entity = LeadEntity::class,
            parentColumns = ["id"],
            childColumns = ["leadId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["leadId"]),
        Index(value = ["fieldId"])
    ]
)
data class CustomFieldValueEntity(
    val leadId: String,
    val fieldId: String,
    val fieldValue: String,
    val updatedAt: Long
)
