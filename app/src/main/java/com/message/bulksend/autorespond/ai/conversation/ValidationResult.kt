package com.message.bulksend.autorespond.ai.conversation

/**
 * Base validation result class
 * Used by all template-specific validation engines
 */
data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val suggestion: String? = null,
    val alternativeOptions: List<String> = emptyList()
)
