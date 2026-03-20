package com.message.bulksend.utils

import android.content.Context
import android.util.Patterns
import com.message.bulksend.data.ContactStatus
import java.util.regex.Pattern

/**
 * Enhanced data validation utilities with comprehensive error detection
 * Requirements: 7.3, 7.4
 */
object EnhancedDataValidator {
    
    // Enhanced validation constants
    const val MIN_CAMPAIGN_NAME_LENGTH = 1
    const val MAX_CAMPAIGN_NAME_LENGTH = 200
    const val MIN_MESSAGE_LENGTH = 0
    const val MAX_MESSAGE_LENGTH = 5000
    const val MIN_CONTACTS = 0
    const val MAX_CONTACTS = 10000
    const val MIN_TIMESTAMP = 946684800000L // Jan 1, 2000
    const val MAX_FUTURE_TIMESTAMP_OFFSET = 300000L // 5 minutes in future
    
    // Phone number validation patterns
    private val PHONE_PATTERNS = listOf(
        Pattern.compile("^\\+?[1-9]\\d{1,14}$"), // E.164 format
        Pattern.compile("^\\d{10,15}$"), // Simple numeric
        Pattern.compile("^\\+\\d{1,3}\\s?\\d{4,14}$"), // International with space
        Pattern.compile("^\\(\\d{3}\\)\\s?\\d{3}-\\d{4}$") // US format
    )
    
    // Campaign type validation
    val VALID_CAMPAIGN_TYPES = setOf(
        "BULKSEND", "BULKTEXT", "TEXTMEDIA", "SHEETSEND", 
        "FORMCAMPAIGN", "GOOGLEFORM", "WHATSAPP", "SMS"
    )
    
    // Contact status validation
    val VALID_CONTACT_STATUSES = setOf(
        "pending", "sent", "failed", "delivered", "read", "replied"
    )
    
    /**
     * Comprehensive campaign validation with detailed error reporting
     */
    fun validateCampaignComprehensive(
        campaign: EnhancedCampaignProgress,
        context: Context? = null
    ): DetailedValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()
        val suggestions = mutableListOf<ValidationSuggestion>()
        
        // Validate basic fields
        validateBasicFields(campaign, errors, warnings, suggestions)
        
        // Validate numerical consistency
        validateNumericalConsistency(campaign, errors, warnings)
        
        // Validate timestamps
        validateTimestamps(campaign, errors, warnings)
        
        // Validate contact statuses
        validateContactStatuses(campaign, errors, warnings, suggestions)
        
        // Validate business logic
        validateBusinessLogic(campaign, errors, warnings, suggestions)
        
        // Validate data integrity
        validateDataIntegrity(campaign, errors, warnings)
        
        // Performance validation
        validatePerformanceConstraints(campaign, warnings, suggestions)
        
        // Context-specific validation
        context?.let { ctx ->
            validateContextSpecific(campaign, ctx, warnings, suggestions)
        }
        
        return DetailedValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            suggestions = suggestions,
            severity = determineSeverity(errors, warnings)
        )
    }
    
    /**
     * Validate basic field requirements
     */
    private fun validateBasicFields(
        campaign: EnhancedCampaignProgress,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationWarning>,
        suggestions: MutableList<ValidationSuggestion>
    ) {
        // Unique ID validation
        if (campaign.uniqueId.isBlank()) {
            errors.add(ValidationError(
                field = "uniqueId",
                code = "REQUIRED",
                message = "Unique ID is required",
                severity = ValidationSeverity.CRITICAL
            ))
        } else if (!isValidUUID(campaign.uniqueId)) {
            errors.add(ValidationError(
                field = "uniqueId",
                code = "INVALID_FORMAT",
                message = "Unique ID must be a valid UUID format",
                severity = ValidationSeverity.HIGH
            ))
        }
        
        // Campaign ID validation
        if (campaign.campaignId.isBlank()) {
            errors.add(ValidationError(
                field = "campaignId",
                code = "REQUIRED",
                message = "Campaign ID is required",
                severity = ValidationSeverity.HIGH
            ))
        } else if (campaign.campaignId.length > 100) {
            errors.add(ValidationError(
                field = "campaignId",
                code = "TOO_LONG",
                message = "Campaign ID exceeds maximum length of 100 characters",
                severity = ValidationSeverity.MEDIUM
            ))
        }
        
        // Campaign name validation
        when {
            campaign.campaignName.isBlank() -> {
                errors.add(ValidationError(
                    field = "campaignName",
                    code = "REQUIRED",
                    message = "Campaign name is required",
                    severity = ValidationSeverity.HIGH
                ))
            }
            campaign.campaignName.length < MIN_CAMPAIGN_NAME_LENGTH -> {
                errors.add(ValidationError(
                    field = "campaignName",
                    code = "TOO_SHORT",
                    message = "Campaign name must be at least $MIN_CAMPAIGN_NAME_LENGTH character",
                    severity = ValidationSeverity.MEDIUM
                ))
            }
            campaign.campaignName.length > MAX_CAMPAIGN_NAME_LENGTH -> {
                errors.add(ValidationError(
                    field = "campaignName",
                    code = "TOO_LONG",
                    message = "Campaign name exceeds maximum length of $MAX_CAMPAIGN_NAME_LENGTH characters",
                    severity = ValidationSeverity.MEDIUM
                ))
            }
            campaign.campaignName.trim() != campaign.campaignName -> {
                warnings.add(ValidationWarning(
                    field = "campaignName",
                    message = "Campaign name has leading or trailing whitespace",
                    suggestion = "Consider trimming whitespace"
                ))
            }
        }
        
        // Campaign type validation
        if (campaign.campaignType.isBlank()) {
            errors.add(ValidationError(
                field = "campaignType",
                code = "REQUIRED",
                message = "Campaign type is required",
                severity = ValidationSeverity.HIGH
            ))
        } else if (campaign.campaignType !in VALID_CAMPAIGN_TYPES) {
            errors.add(ValidationError(
                field = "campaignType",
                code = "INVALID_VALUE",
                message = "Invalid campaign type: ${campaign.campaignType}. Valid types: ${VALID_CAMPAIGN_TYPES.joinToString(", ")}",
                severity = ValidationSeverity.HIGH
            ))
            suggestions.add(ValidationSuggestion(
                field = "campaignType",
                message = "Use one of the supported campaign types",
                suggestedValue = "BULKSEND"
            ))
        }
        
        // Message validation
        when {
            campaign.message.length > MAX_MESSAGE_LENGTH -> {
                errors.add(ValidationError(
                    field = "message",
                    code = "TOO_LONG",
                    message = "Message exceeds maximum length of $MAX_MESSAGE_LENGTH characters",
                    severity = ValidationSeverity.MEDIUM
                ))
            }
            campaign.message.isBlank() && campaign.campaignType in setOf("BULKTEXT", "TEXTMEDIA") -> {
                warnings.add(ValidationWarning(
                    field = "message",
                    message = "Message is empty for text-based campaign type",
                    suggestion = "Consider adding a message for better campaign effectiveness"
                ))
            }
            containsSuspiciousContent(campaign.message) -> {
                warnings.add(ValidationWarning(
                    field = "message",
                    message = "Message contains potentially suspicious content",
                    suggestion = "Review message content for compliance"
                ))
            }
        }
    }
    
    /**
     * Validate numerical consistency
     */
    private fun validateNumericalConsistency(
        campaign: EnhancedCampaignProgress,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationWarning>
    ) {
        // Total contacts validation
        when {
            campaign.totalContacts < MIN_CONTACTS -> {
                errors.add(ValidationError(
                    field = "totalContacts",
                    code = "INVALID_RANGE",
                    message = "Total contacts cannot be negative",
                    severity = ValidationSeverity.HIGH
                ))
            }
            campaign.totalContacts > MAX_CONTACTS -> {
                errors.add(ValidationError(
                    field = "totalContacts",
                    code = "EXCEEDS_LIMIT",
                    message = "Total contacts exceeds maximum limit of $MAX_CONTACTS",
                    severity = ValidationSeverity.HIGH
                ))
            }
            campaign.totalContacts == 0 -> {
                warnings.add(ValidationWarning(
                    field = "totalContacts",
                    message = "Campaign has no contacts",
                    suggestion = "Add contacts to make the campaign functional"
                ))
            }
        }
        
        // Individual count validations
        listOf(
            "sentCount" to campaign.sentCount,
            "failedCount" to campaign.failedCount,
            "remainingCount" to campaign.remainingCount,
            "currentIndex" to campaign.currentIndex
        ).forEach { (field, value) ->
            if (value < 0) {
                errors.add(ValidationError(
                    field = field,
                    code = "INVALID_RANGE",
                    message = "$field cannot be negative",
                    severity = ValidationSeverity.HIGH
                ))
            }
        }
        
        // Cross-field validations
        if (campaign.sentCount > campaign.totalContacts) {
            errors.add(ValidationError(
                field = "sentCount",
                code = "EXCEEDS_TOTAL",
                message = "Sent count (${campaign.sentCount}) cannot exceed total contacts (${campaign.totalContacts})",
                severity = ValidationSeverity.HIGH
            ))
        }
        
        if (campaign.failedCount > campaign.totalContacts) {
            errors.add(ValidationError(
                field = "failedCount",
                code = "EXCEEDS_TOTAL",
                message = "Failed count (${campaign.failedCount}) cannot exceed total contacts (${campaign.totalContacts})",
                severity = ValidationSeverity.HIGH
            ))
        }
        
        if (campaign.currentIndex > campaign.totalContacts) {
            errors.add(ValidationError(
                field = "currentIndex",
                code = "EXCEEDS_TOTAL",
                message = "Current index (${campaign.currentIndex}) cannot exceed total contacts (${campaign.totalContacts})",
                severity = ValidationSeverity.MEDIUM
            ))
        }
        
        // Count consistency validation
        val calculatedRemaining = campaign.totalContacts - campaign.sentCount - campaign.failedCount
        if (campaign.remainingCount != calculatedRemaining) {
            errors.add(ValidationError(
                field = "remainingCount",
                code = "INCONSISTENT",
                message = "Remaining count (${campaign.remainingCount}) doesn't match calculated value ($calculatedRemaining)",
                severity = ValidationSeverity.HIGH
            ))
        }
        
        // Progress validation
        val processedCount = campaign.sentCount + campaign.failedCount
        if (processedCount > campaign.totalContacts) {
            errors.add(ValidationError(
                field = "processedCount",
                code = "EXCEEDS_TOTAL",
                message = "Processed count ($processedCount) cannot exceed total contacts (${campaign.totalContacts})",
                severity = ValidationSeverity.HIGH
            ))
        }
    }
    
    /**
     * Validate timestamps
     */
    private fun validateTimestamps(
        campaign: EnhancedCampaignProgress,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationWarning>
    ) {
        val currentTime = System.currentTimeMillis()
        val maxFutureTime = currentTime + MAX_FUTURE_TIMESTAMP_OFFSET
        
        // Last updated validation
        when {
            campaign.lastUpdated < MIN_TIMESTAMP -> {
                errors.add(ValidationError(
                    field = "lastUpdated",
                    code = "INVALID_TIMESTAMP",
                    message = "Last updated timestamp is too old or invalid",
                    severity = ValidationSeverity.MEDIUM
                ))
            }
            campaign.lastUpdated > maxFutureTime -> {
                errors.add(ValidationError(
                    field = "lastUpdated",
                    code = "FUTURE_TIMESTAMP",
                    message = "Last updated timestamp is too far in the future",
                    severity = ValidationSeverity.MEDIUM
                ))
            }
        }
        
        // Created at validation
        when {
            campaign.createdAt < MIN_TIMESTAMP -> {
                errors.add(ValidationError(
                    field = "createdAt",
                    code = "INVALID_TIMESTAMP",
                    message = "Created at timestamp is too old or invalid",
                    severity = ValidationSeverity.MEDIUM
                ))
            }
            campaign.createdAt > maxFutureTime -> {
                errors.add(ValidationError(
                    field = "createdAt",
                    code = "FUTURE_TIMESTAMP",
                    message = "Created at timestamp is too far in the future",
                    severity = ValidationSeverity.MEDIUM
                ))
            }
        }
        
        // Paused at validation
        campaign.pausedAt?.let { pausedAt ->
            when {
                pausedAt < MIN_TIMESTAMP -> {
                    errors.add(ValidationError(
                        field = "pausedAt",
                        code = "INVALID_TIMESTAMP",
                        message = "Paused at timestamp is too old or invalid",
                        severity = ValidationSeverity.LOW
                    ))
                }
                pausedAt > maxFutureTime -> {
                    errors.add(ValidationError(
                        field = "pausedAt",
                        code = "FUTURE_TIMESTAMP",
                        message = "Paused at timestamp is too far in the future",
                        severity = ValidationSeverity.LOW
                    ))
                }
            }
        }
        
        // Timestamp consistency
        if (campaign.createdAt > campaign.lastUpdated) {
            errors.add(ValidationError(
                field = "timestamps",
                code = "INCONSISTENT",
                message = "Created at timestamp cannot be after last updated timestamp",
                severity = ValidationSeverity.MEDIUM
            ))
        }
        
        campaign.pausedAt?.let { pausedAt ->
            if (pausedAt < campaign.createdAt) {
                errors.add(ValidationError(
                    field = "pausedAt",
                    code = "INCONSISTENT",
                    message = "Paused at timestamp cannot be before created at timestamp",
                    severity = ValidationSeverity.MEDIUM
                ))
            }
        }
    }
    
    /**
     * Validate contact statuses
     */
    private fun validateContactStatuses(
        campaign: EnhancedCampaignProgress,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationWarning>,
        suggestions: MutableList<ValidationSuggestion>
    ) {
        if (campaign.contactStatuses.isEmpty()) {
            if (campaign.totalContacts > 0) {
                warnings.add(ValidationWarning(
                    field = "contactStatuses",
                    message = "No contact statuses provided for campaign with ${campaign.totalContacts} contacts",
                    suggestion = "Add contact status information for better tracking"
                ))
            }
            return
        }
        
        // Validate individual contact statuses
        campaign.contactStatuses.forEachIndexed { index, contactStatus ->
            // Phone number validation
            if (contactStatus.number.isBlank()) {
                errors.add(ValidationError(
                    field = "contactStatuses[$index].number",
                    code = "REQUIRED",
                    message = "Contact number at index $index is empty",
                    severity = ValidationSeverity.MEDIUM
                ))
            } else if (!isValidPhoneNumber(contactStatus.number)) {
                warnings.add(ValidationWarning(
                    field = "contactStatuses[$index].number",
                    message = "Contact number '${contactStatus.number}' may not be in a valid format",
                    suggestion = "Verify phone number format"
                ))
            }
            
            // Status validation
            if (contactStatus.status !in VALID_CONTACT_STATUSES) {
                errors.add(ValidationError(
                    field = "contactStatuses[$index].status",
                    code = "INVALID_VALUE",
                    message = "Invalid contact status '${contactStatus.status}' at index $index",
                    severity = ValidationSeverity.MEDIUM
                ))
            }
        }
        
        // Check for duplicate contact numbers
        val duplicateNumbers = campaign.contactStatuses
            .groupingBy { it.number }
            .eachCount()
            .filter { it.value > 1 }
        
        if (duplicateNumbers.isNotEmpty()) {
            errors.add(ValidationError(
                field = "contactStatuses",
                code = "DUPLICATES",
                message = "Duplicate contact numbers found: ${duplicateNumbers.keys.joinToString(", ")}",
                severity = ValidationSeverity.MEDIUM
            ))
        }
        
        // Validate status counts consistency
        val statusCounts = campaign.contactStatuses.groupingBy { it.status }.eachCount()
        val sentFromStatuses = statusCounts["sent"] ?: 0
        val failedFromStatuses = statusCounts["failed"] ?: 0
        
        if (sentFromStatuses != campaign.sentCount) {
            errors.add(ValidationError(
                field = "contactStatuses",
                code = "COUNT_MISMATCH",
                message = "Sent count mismatch: campaign shows ${campaign.sentCount}, contact statuses show $sentFromStatuses",
                severity = ValidationSeverity.HIGH
            ))
        }
        
        if (failedFromStatuses != campaign.failedCount) {
            errors.add(ValidationError(
                field = "contactStatuses",
                code = "COUNT_MISMATCH",
                message = "Failed count mismatch: campaign shows ${campaign.failedCount}, contact statuses show $failedFromStatuses",
                severity = ValidationSeverity.HIGH
            ))
        }
    }
    
    /**
     * Validate business logic
     */
    private fun validateBusinessLogic(
        campaign: EnhancedCampaignProgress,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationWarning>,
        suggestions: MutableList<ValidationSuggestion>
    ) {
        // State consistency validation
        if (campaign.isRunning && campaign.isStopped) {
            errors.add(ValidationError(
                field = "state",
                code = "INCONSISTENT",
                message = "Campaign cannot be both running and stopped",
                severity = ValidationSeverity.HIGH
            ))
        }
        
        // Pause state validation
        if (campaign.isStopped && campaign.pausedAt == null) {
            warnings.add(ValidationWarning(
                field = "pausedAt",
                message = "Campaign is marked as stopped but has no pause timestamp",
                suggestion = "Set pause timestamp for stopped campaigns"
            ))
        }
        
        if (!campaign.isStopped && campaign.pausedAt != null) {
            warnings.add(ValidationWarning(
                field = "pausedAt",
                message = "Campaign is not stopped but has a pause timestamp",
                suggestion = "Clear pause timestamp for active campaigns"
            ))
        }
        
        // Completion validation
        if (campaign.remainingCount == 0 && campaign.sentCount + campaign.failedCount != campaign.totalContacts) {
            errors.add(ValidationError(
                field = "completion",
                code = "INCONSISTENT",
                message = "Campaign appears complete but counts don't add up",
                severity = ValidationSeverity.MEDIUM
            ))
        }
        
        // Progress validation
        val progressPercentage = if (campaign.totalContacts > 0) {
            (campaign.sentCount + campaign.failedCount).toDouble() / campaign.totalContacts * 100
        } else 0.0
        
        if (progressPercentage > 100.0) {
            errors.add(ValidationError(
                field = "progress",
                code = "EXCEEDS_LIMIT",
                message = "Campaign progress exceeds 100%",
                severity = ValidationSeverity.HIGH
            ))
        }
        
        // Efficiency suggestions
        if (campaign.totalContacts > 0) {
            val failureRate = campaign.failedCount.toDouble() / campaign.totalContacts
            if (failureRate > 0.5) {
                warnings.add(ValidationWarning(
                    field = "efficiency",
                    message = "High failure rate (${(failureRate * 100).toInt()}%)",
                    suggestion = "Review contact data quality and campaign settings"
                ))
            }
        }
    }
    
    /**
     * Validate data integrity
     */
    private fun validateDataIntegrity(
        campaign: EnhancedCampaignProgress,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationWarning>
    ) {
        // Check for data corruption indicators
        if (campaign.uniqueId.contains("recovered") || campaign.campaignId.contains("recovered")) {
            warnings.add(ValidationWarning(
                field = "integrity",
                message = "Campaign appears to have been recovered from corrupted data",
                suggestion = "Verify campaign data accuracy"
            ))
        }
        
        // Check for suspicious patterns
        if (campaign.sentCount == 0 && campaign.failedCount == 0 && campaign.currentIndex > 0) {
            warnings.add(ValidationWarning(
                field = "integrity",
                message = "Current index is advanced but no contacts processed",
                suggestion = "Verify campaign state consistency"
            ))
        }
        
        // Error message validation
        campaign.errorMessage?.let { errorMsg ->
            if (errorMsg.contains("corrupt", ignoreCase = true)) {
                warnings.add(ValidationWarning(
                    field = "errorMessage",
                    message = "Campaign has corruption-related error message",
                    suggestion = "Consider data recovery or recreation"
                ))
            }
        }
    }
    
    /**
     * Validate performance constraints
     */
    private fun validatePerformanceConstraints(
        campaign: EnhancedCampaignProgress,
        warnings: MutableList<ValidationWarning>,
        suggestions: MutableList<ValidationSuggestion>
    ) {
        // Large dataset warnings
        if (campaign.totalContacts > 5000) {
            warnings.add(ValidationWarning(
                field = "performance",
                message = "Large campaign with ${campaign.totalContacts} contacts",
                suggestion = "Consider processing in smaller batches for better performance"
            ))
        }
        
        // Contact status size warning
        if (campaign.contactStatuses.size > 1000) {
            warnings.add(ValidationWarning(
                field = "performance",
                message = "Large contact status list may impact performance",
                suggestion = "Consider pagination or lazy loading for contact statuses"
            ))
        }
        
        // Message length warning
        if (campaign.message.length > 1000) {
            suggestions.add(ValidationSuggestion(
                field = "message",
                message = "Long message may impact delivery performance",
                suggestedValue = "Consider shortening message for better delivery rates"
            ))
        }
    }
    
    /**
     * Context-specific validation
     */
    private fun validateContextSpecific(
        campaign: EnhancedCampaignProgress,
        context: Context,
        warnings: MutableList<ValidationWarning>,
        suggestions: MutableList<ValidationSuggestion>
    ) {
        // Storage space validation
        try {
            val availableSpace = context.filesDir.freeSpace
            val estimatedSize = estimateCampaignStorageSize(campaign)
            
            if (estimatedSize > availableSpace) {
                warnings.add(ValidationWarning(
                    field = "storage",
                    message = "Campaign may exceed available storage space",
                    suggestion = "Free up storage space before proceeding"
                ))
            }
        } catch (e: Exception) {
            // Ignore storage check errors
        }
    }
    
    // Helper methods
    
    private fun isValidUUID(uuid: String): Boolean {
        return try {
            java.util.UUID.fromString(uuid)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }
    
    private fun isValidPhoneNumber(phoneNumber: String): Boolean {
        val cleanNumber = phoneNumber.replace(Regex("[\\s\\-\\(\\)]"), "")
        return PHONE_PATTERNS.any { it.matcher(cleanNumber).matches() }
    }
    
    private fun containsSuspiciousContent(message: String): Boolean {
        val suspiciousPatterns = listOf(
            "click here", "urgent", "limited time", "act now",
            "free money", "guaranteed", "risk-free"
        )
        return suspiciousPatterns.any { message.contains(it, ignoreCase = true) }
    }
    
    private fun determineSeverity(
        errors: List<ValidationError>,
        warnings: List<ValidationWarning>
    ): ValidationSeverity {
        return when {
            errors.any { it.severity == ValidationSeverity.CRITICAL } -> ValidationSeverity.CRITICAL
            errors.any { it.severity == ValidationSeverity.HIGH } -> ValidationSeverity.HIGH
            errors.any { it.severity == ValidationSeverity.MEDIUM } -> ValidationSeverity.MEDIUM
            errors.isNotEmpty() -> ValidationSeverity.LOW
            warnings.isNotEmpty() -> ValidationSeverity.LOW
            else -> ValidationSeverity.NONE
        }
    }
    
    private fun estimateCampaignStorageSize(campaign: EnhancedCampaignProgress): Long {
        // Rough estimation in bytes
        val baseSize = 1024L // Base campaign data
        val messageSize = campaign.message.length * 2L // UTF-16 encoding
        val contactStatusSize = campaign.contactStatuses.size * 100L // Estimated per contact
        
        return baseSize + messageSize + contactStatusSize
    }
}

/**
 * Detailed validation result with comprehensive error information
 */
data class DetailedValidationResult(
    val isValid: Boolean,
    val errors: List<ValidationError>,
    val warnings: List<ValidationWarning>,
    val suggestions: List<ValidationSuggestion>,
    val severity: ValidationSeverity
) {
    val hasErrors: Boolean = errors.isNotEmpty()
    val hasWarnings: Boolean = warnings.isNotEmpty()
    val hasSuggestions: Boolean = suggestions.isNotEmpty()
    val errorCount: Int = errors.size
    val warningCount: Int = warnings.size
    val suggestionCount: Int = suggestions.size
}

/**
 * Validation error with detailed information
 */
data class ValidationError(
    val field: String,
    val code: String,
    val message: String,
    val severity: ValidationSeverity
)

/**
 * Validation warning with suggestions
 */
data class ValidationWarning(
    val field: String,
    val message: String,
    val suggestion: String
)

/**
 * Validation suggestion for improvement
 */
data class ValidationSuggestion(
    val field: String,
    val message: String,
    val suggestedValue: String
)

/**
 * Validation severity levels
 */
enum class ValidationSeverity {
    NONE,     // No issues
    LOW,      // Minor issues
    MEDIUM,   // Moderate issues
    HIGH,     // Significant issues
    CRITICAL  // Critical issues that prevent operation
}