package com.message.bulksend.leadmanager.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity to store auto-add settings for leads from AutoRespond
 */
@Entity(tableName = "auto_add_settings")
data class AutoAddSettingsEntity(
    @PrimaryKey
    val id: String = "default",
    val isAutoAddEnabled: Boolean = false,
    val autoAddAllMessages: Boolean = false, // Add all incoming messages as leads
    val keywordBasedAdd: Boolean = true, // Only add when keyword matches
    val keywords: String = "", // JSON array of keywords to match
    val defaultStatus: String = "NEW",
    val defaultSource: String = "WhatsApp",
    val defaultCategory: String = "AutoRespond",
    val defaultTags: String = "", // JSON array of default tags
    val excludeExistingContacts: Boolean = true, // Don't add if already exists
    val notifyOnNewLead: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Entity to store keyword rules for auto-adding leads
 */
@Entity(tableName = "auto_add_keyword_rules")
data class AutoAddKeywordRuleEntity(
    @PrimaryKey
    val id: String,
    val keyword: String,
    val matchType: KeywordMatchType = KeywordMatchType.CONTAINS,
    val assignStatus: String = "NEW",
    val assignCategory: String = "General",
    val assignTags: String = "", // JSON array
    val assignPriority: String = "MEDIUM",
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

enum class KeywordMatchType {
    EXACT,      // Exact match
    CONTAINS,   // Contains keyword
    STARTS_WITH,// Starts with keyword
    ENDS_WITH,  // Ends with keyword
    REGEX       // Regular expression
}
