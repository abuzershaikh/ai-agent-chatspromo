package com.message.bulksend.utils

import com.message.bulksend.data.ContactStatus
import java.util.UUID

/**
 * Comprehensive validation utilities for Enhanced Campaign Progress
 * Requirements: 7.3 - Complete metadata storage and error handling
 */
object CampaignValidationUtils {
    
    // Validation constants
    private const val MAX_CAMPAIGN_NAME_LENGTH = 200
    private const val MAX_MESSAGE_LENGTH = 5000
    private const val MAX_CONTACTS = 10000
    private const val MIN_TIMESTAMP = 946684800000L // Jan 1, 2000
    private const val MAX_CAMPAIGN_ID_LENGTH = 100
    private const val MAX_ERROR_MESSAGE_LENGTH = 1000
    
    // Valid campaign types
    private val VALID_CAMPAIGN_TYPES = setOf(
        "BULKSEND", "BULKTEXT", "TEXTMEDIA", "SHEETSEND", 
        "FORMCAMPAIGN", "GOOGLEFORM"
    )
    
    // Valid contact status values
    private val VALID_CONTACT_STATUSES = setOf("sent", "failed", "pending")

    /**
     * Validate campaign progress data for integrity and consistency
     * Requirements: 7.3
     */
    fun validateCampaignProgress(progress: EnhancedCampaignProgress): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Validate unique ID
        if (progress.uniqueId.isBlank()) {
            errors.add("Unique ID cannot be empty")
        } else if (!isValidUUID(progress.uniqueId)) {
            errors.add("Unique ID must be a valid UUID format")
        }
        
        // Validate campaign ID
        if (progress.campaignId.isBlank()) {
            errors.add("Campaign ID cannot be empty")
        } else if (progress.campaignId.length > MAX_CAMPAIGN_ID_LENGTH) {
            errors.add("Campaign ID cannot exceed $MAX_CAMPAIGN_ID_LENGTH characters")
        }
        
        // Validate campaign name
        if (progress.campaignName.isBlank()) {
            errors.add("Campaign name cannot be empty")
        } else if (progress.campaignName.length > MAX_CAMPAIGN_NAME_LENGTH) {
            errors.add("Campaign name cannot exceed $MAX_CAMPAIGN_NAME_LENGTH characters")
        }
        
        // Validate campaign type
        if (progress.campaignType.isBlank()) {
            errors.add("Campaign type cannot be empty")
        } else if (progress.campaignType !in VALID_CAMPAIGN_TYPES) {
            errors.add("Campaign type '${progress.campaignType}' is not valid. Valid types: ${VALID_CAMPAIGN_TYPES.joinToString()}")
        }
        
        // Validate message
        if (progress.message.length > MAX_MESSAGE_LENGTH) {
            errors.add("Message cannot exceed $MAX_MESSAGE_LENGTH characters")
        }
        
        // Validate contact counts
        if (progress.totalContacts < 0) {
            errors.add("Total contacts cannot be negative")
        } else if (progress.totalContacts > MAX_CONTACTS) {
            errors.add("Total contacts cannot exceed $MAX_CONTACTS")
        }
        
        if (progress.sentCount < 0) {
            errors.add("Sent count cannot be negative")
        } else if (progress.sentCount > progress.totalContacts) {
            errors.add("Sent count cannot exceed total contacts")
        }
        
        if (progress.failedCount < 0) {
            errors.add("Failed count cannot be negative")
        } else if (progress.failedCount > progress.totalContacts) {
            errors.add("Failed count cannot exceed total contacts")
        }
        
        if (progress.remainingCount < 0) {
            errors.add("Remaining count cannot be negative")
        } else if (progress.remainingCount > progress.totalContacts) {
            errors.add("Remaining count cannot exceed total contacts")
        }
        
        // Validate count consistency
        val calculatedRemaining = progress.totalContacts - progress.sentCount - progress.failedCount
        if (progress.remainingCount != calculatedRemaining) {
            errors.add("Remaining count ($progress.remainingCount) does not match calculated value ($calculatedRemaining)")
        }
        
        if (progress.currentIndex < 0) {
            errors.add("Current index cannot be negative")
        } else if (progress.currentIndex > progress.totalContacts) {
            errors.add("Current index cannot exceed total contacts")
        }
        
        // Validate timestamps
        if (progress.lastUpdated < MIN_TIMESTAMP) {
            errors.add("Last updated timestamp is invalid")
        }
        
        if (progress.createdAt < MIN_TIMESTAMP) {
            errors.add("Created at timestamp is invalid")
        }
        
        if (progress.createdAt > progress.lastUpdated) {
            errors.add("Created at timestamp cannot be after last updated timestamp")
        }
        
        if (progress.pausedAt != null && progress.pausedAt < MIN_TIMESTAMP) {
            errors.add("Paused at timestamp is invalid")
        }
        
        // Validate state consistency
        if (progress.isRunning && progress.isStopped) {
            errors.add("Campaign cannot be both running and stopped")
        }
        
        if (progress.isStopped && progress.remainingCount > 0 && progress.pausedAt == null) {
            errors.add("Stopped campaign with remaining contacts should have pausedAt timestamp")
        }
        
        // Validate error message
        if (progress.errorMessage != null && progress.errorMessage.length > MAX_ERROR_MESSAGE_LENGTH) {
            errors.add("Error message cannot exceed $MAX_ERROR_MESSAGE_LENGTH characters")
        }
        
        // Validate contact statuses
        val contactStatusErrors = validateContactStatuses(progress.contactStatuses, progress.totalContacts)
        errors.addAll(contactStatusErrors)
        
        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }
    
    /**
     * Validate contact statuses list
     */
    private fun validateContactStatuses(contactStatuses: List<ContactStatus>, totalContacts: Int): List<String> {
        val errors = mutableListOf<String>()
        
        if (contactStatuses.size > totalContacts) {
            errors.add("Contact statuses count (${contactStatuses.size}) cannot exceed total contacts ($totalContacts)")
        }
        
        val phoneNumbers = mutableSetOf<String>()
        contactStatuses.forEachIndexed { index, contact ->
            // Validate phone number
            if (contact.phoneNumber.isBlank()) {
                errors.add("Contact status at index $index has empty phone number")
            } else if (contact.phoneNumber in phoneNumbers) {
                errors.add("Duplicate phone number found: ${contact.phoneNumber}")
            } else {
                phoneNumbers.add(contact.phoneNumber)
            }
            
            // Validate status
            if (contact.status !in VALID_CONTACT_STATUSES) {
                errors.add("Contact status '${contact.status}' at index $index is not valid. Valid statuses: ${VALID_CONTACT_STATUSES.joinToString()}")
            }
        }
        
        return errors
    }
    
    /**
     * Check if string is a valid UUID format
     */
    private fun isValidUUID(uuid: String): Boolean {
        return try {
            UUID.fromString(uuid)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }
    
    /**
     * Attempt to recover corrupted campaign data
     * Requirements: 7.3, 7.4
     */
    fun recoverCorruptedCampaign(progress: EnhancedCampaignProgress): EnhancedCampaignProgress? {
        return try {
            val recovered = progress.copy(
                uniqueId = if (progress.uniqueId.isBlank() || !isValidUUID(progress.uniqueId)) {
                    UUID.randomUUID().toString()
                } else {
                    progress.uniqueId
                },
                campaignId = if (progress.campaignId.isBlank()) {
                    "recovered_${System.currentTimeMillis()}"
                } else {
                    progress.campaignId.take(MAX_CAMPAIGN_ID_LENGTH)
                },
                campaignName = if (progress.campaignName.isBlank()) {
                    "Recovered Campaign"
                } else {
                    progress.campaignName.take(MAX_CAMPAIGN_NAME_LENGTH)
                },
                campaignType = if (progress.campaignType !in VALID_CAMPAIGN_TYPES) {
                    "BULKSEND"
                } else {
                    progress.campaignType
                },
                message = progress.message.take(MAX_MESSAGE_LENGTH),
                totalContacts = maxOf(0, minOf(progress.totalContacts, MAX_CONTACTS)),
                sentCount = maxOf(0, minOf(progress.sentCount, progress.totalContacts)),
                failedCount = maxOf(0, minOf(progress.failedCount, progress.totalContacts)),
                remainingCount = maxOf(0, progress.totalContacts - progress.sentCount - progress.failedCount),
                currentIndex = maxOf(0, minOf(progress.currentIndex, progress.totalContacts)),
                isRunning = if (progress.isRunning && progress.isStopped) false else progress.isRunning,
                isStopped = progress.isStopped,
                contactStatuses = progress.contactStatuses.take(progress.totalContacts).filter { 
                    it.phoneNumber.isNotBlank() && it.status in VALID_CONTACT_STATUSES 
                },
                errorMessage = progress.errorMessage?.take(MAX_ERROR_MESSAGE_LENGTH),
                lastUpdated = if (progress.lastUpdated < MIN_TIMESTAMP) {
                    System.currentTimeMillis()
                } else {
                    progress.lastUpdated
                },
                createdAt = if (progress.createdAt < MIN_TIMESTAMP) {
                    progress.lastUpdated
                } else {
                    minOf(progress.createdAt, progress.lastUpdated)
                },
                pausedAt = if (progress.pausedAt != null && progress.pausedAt < MIN_TIMESTAMP) {
                    null
                } else {
                    progress.pausedAt
                }
            )
            
            // Validate recovered campaign
            when (validateCampaignProgress(recovered)) {
                is ValidationResult.Valid -> recovered
                is ValidationResult.Invalid -> null // Could not recover
            }
        } catch (e: Exception) {
            null // Recovery failed
        }
    }
    
    /**
     * Sanitize campaign data for safe storage
     */
    fun sanitizeCampaignProgress(progress: EnhancedCampaignProgress): EnhancedCampaignProgress {
        return progress.copy(
            campaignName = progress.campaignName.trim().take(MAX_CAMPAIGN_NAME_LENGTH),
            campaignType = progress.campaignType.trim().uppercase(),
            message = progress.message.trim().take(MAX_MESSAGE_LENGTH),
            totalContacts = maxOf(0, minOf(progress.totalContacts, MAX_CONTACTS)),
            sentCount = maxOf(0, minOf(progress.sentCount, progress.totalContacts)),
            failedCount = maxOf(0, minOf(progress.failedCount, progress.totalContacts)),
            remainingCount = maxOf(0, progress.totalContacts - progress.sentCount - progress.failedCount),
            currentIndex = maxOf(0, minOf(progress.currentIndex, progress.totalContacts)),
            contactStatuses = progress.contactStatuses.take(progress.totalContacts).map { contact ->
                contact.copy(
                    phoneNumber = contact.phoneNumber.trim(),
                    status = if (contact.status in VALID_CONTACT_STATUSES) contact.status else "pending"
                )
            },
            errorMessage = progress.errorMessage?.trim()?.take(MAX_ERROR_MESSAGE_LENGTH)
        )
    }
    
    /**
     * Validate campaign data before save operation
     */
    fun validateForSave(progress: EnhancedCampaignProgress): ValidationResult {
        val baseValidation = validateCampaignProgress(progress)
        
        if (baseValidation is ValidationResult.Invalid) {
            return baseValidation
        }
        
        // Additional save-specific validations
        val errors = mutableListOf<String>()
        
        // Check for required fields that must be present for save
        if (progress.uniqueId.isBlank()) {
            errors.add("Unique ID is required for save operation")
        }
        
        if (progress.campaignId.isBlank()) {
            errors.add("Campaign ID is required for save operation")
        }
        
        if (progress.campaignName.isBlank()) {
            errors.add("Campaign name is required for save operation")
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }
    
    /**
     * Validate campaign data before update operation
     */
    fun validateForUpdate(progress: EnhancedCampaignProgress): ValidationResult {
        val saveValidation = validateForSave(progress)
        
        if (saveValidation is ValidationResult.Invalid) {
            return saveValidation
        }
        
        // Additional update-specific validations
        val errors = mutableListOf<String>()
        
        // Ensure lastUpdated is current for updates
        val now = System.currentTimeMillis()
        if (progress.lastUpdated > now + 60000) { // Allow 1 minute future tolerance
            errors.add("Last updated timestamp cannot be in the future")
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }
    
    /**
     * Get validation summary for debugging
     */
    fun getValidationSummary(progress: EnhancedCampaignProgress): String {
        val validation = validateCampaignProgress(progress)
        return when (validation) {
            is ValidationResult.Valid -> "Campaign data is valid"
            is ValidationResult.Invalid -> "Campaign data has ${validation.errors.size} validation errors: ${validation.errors.joinToString("; ")}"
        }
    }
    
    /**
     * Validate batch of campaigns efficiently
     */
    fun validateCampaignBatch(campaigns: List<EnhancedCampaignProgress>): BatchValidationResult {
        val validCampaigns = mutableListOf<EnhancedCampaignProgress>()
        val invalidCampaigns = mutableListOf<Pair<EnhancedCampaignProgress, List<String>>>()
        val warnings = mutableListOf<String>()
        
        campaigns.forEach { campaign ->
            when (val validation = validateCampaignProgress(campaign)) {
                is ValidationResult.Valid -> {
                    validCampaigns.add(campaign)
                }
                is ValidationResult.Invalid -> {
                    invalidCampaigns.add(campaign to validation.errors)
                    
                    // Check if campaign can be recovered
                    val recovered = recoverCorruptedCampaign(campaign)
                    if (recovered != null) {
                        warnings.add("Campaign '${campaign.campaignName}' has validation errors but can be recovered")
                    } else {
                        warnings.add("Campaign '${campaign.campaignName}' has validation errors and cannot be recovered")
                    }
                }
            }
        }
        
        return BatchValidationResult(
            totalCampaigns = campaigns.size,
            validCampaigns = validCampaigns,
            invalidCampaigns = invalidCampaigns,
            warnings = warnings
        )
    }
    
    /**
     * Validate campaign data for specific operation types
     */
    fun validateForOperation(progress: EnhancedCampaignProgress, operation: CampaignOperation): ValidationResult {
        val baseValidation = validateCampaignProgress(progress)
        
        if (baseValidation is ValidationResult.Invalid) {
            return baseValidation
        }
        
        val errors = mutableListOf<String>()
        
        when (operation) {
            CampaignOperation.SAVE -> {
                // Additional save validations
                if (progress.uniqueId.isBlank()) {
                    errors.add("Unique ID is required for save operation")
                }
            }
            
            CampaignOperation.UPDATE -> {
                // Additional update validations
                if (progress.lastUpdated > System.currentTimeMillis() + 60000) {
                    errors.add("Last updated timestamp cannot be in the future")
                }
            }
            
            CampaignOperation.DELETE -> {
                // Additional delete validations
                if (progress.isRunning) {
                    errors.add("Cannot delete a running campaign")
                }
            }
            
            CampaignOperation.RESUME -> {
                // Additional resume validations
                if (!progress.isStopped) {
                    errors.add("Cannot resume a campaign that is not stopped")
                }
                if (progress.remainingCount <= 0) {
                    errors.add("Cannot resume a campaign with no remaining contacts")
                }
            }
            
            CampaignOperation.MIGRATION -> {
                // Additional migration validations
                if (progress.createdAt > progress.lastUpdated) {
                    errors.add("Created timestamp cannot be after last updated timestamp")
                }
            }
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }
    
    /**
     * Validate system constraints
     */
    fun validateSystemConstraints(campaigns: List<EnhancedCampaignProgress>): SystemConstraintValidation {
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Check total campaign count
        if (campaigns.size > 1000) {
            warnings.add("Large number of campaigns (${campaigns.size}) may impact performance")
        }
        
        // Check total contacts across all campaigns
        val totalContacts = campaigns.sumOf { it.totalContacts }
        if (totalContacts > 100000) {
            warnings.add("Very large total contact count ($totalContacts) may impact performance")
        }
        
        // Check for duplicate unique IDs
        val uniqueIds = campaigns.map { it.uniqueId }
        val duplicateIds = uniqueIds.groupBy { it }.filter { it.value.size > 1 }.keys
        if (duplicateIds.isNotEmpty()) {
            issues.add("Duplicate unique IDs found: ${duplicateIds.joinToString(", ")}")
        }
        
        // Check for campaigns with very large contact lists
        val largeContactListCampaigns = campaigns.filter { it.contactStatuses.size > 5000 }
        if (largeContactListCampaigns.isNotEmpty()) {
            warnings.add("${largeContactListCampaigns.size} campaigns have very large contact lists (>5000)")
        }
        
        // Check for very old campaigns
        val oneYearAgo = System.currentTimeMillis() - (365L * 24 * 60 * 60 * 1000)
        val oldCampaigns = campaigns.filter { it.createdAt < oneYearAgo }
        if (oldCampaigns.isNotEmpty()) {
            warnings.add("${oldCampaigns.size} campaigns are over one year old")
        }
        
        return SystemConstraintValidation(
            isValid = issues.isEmpty(),
            issues = issues,
            warnings = warnings,
            totalCampaigns = campaigns.size,
            totalContacts = totalContacts
        )
    }
    
    /**
     * Perform deep validation with detailed analysis
     */
    fun performDeepValidation(progress: EnhancedCampaignProgress): DeepValidationResult {
        val basicValidation = validateCampaignProgress(progress)
        val issues = mutableListOf<ValidationIssue>()
        val suggestions = mutableListOf<String>()
        
        // Analyze campaign data patterns
        if (progress.sentCount == 0 && progress.failedCount == 0 && progress.isStopped) {
            suggestions.add("Campaign appears to be newly created and never started")
        }
        
        if (progress.failedCount > progress.sentCount && progress.failedCount > 0) {
            issues.add(ValidationIssue(
                severity = ValidationSeverity.WARNING,
                field = "failedCount",
                message = "High failure rate detected (${progress.failedCount} failed vs ${progress.sentCount} sent)"
            ))
            suggestions.add("Review campaign settings and contact data quality")
        }
        
        if (progress.contactStatuses.isNotEmpty()) {
            val statusCounts = progress.contactStatuses.groupBy { it.status }.mapValues { it.value.size }
            val actualSent = statusCounts["sent"] ?: 0
            val actualFailed = statusCounts["failed"] ?: 0
            val actualPending = statusCounts["pending"] ?: 0
            
            if (actualSent != progress.sentCount) {
                issues.add(ValidationIssue(
                    severity = ValidationSeverity.ERROR,
                    field = "sentCount",
                    message = "Sent count mismatch: reported ${progress.sentCount}, actual $actualSent"
                ))
            }
            
            if (actualFailed != progress.failedCount) {
                issues.add(ValidationIssue(
                    severity = ValidationSeverity.ERROR,
                    field = "failedCount",
                    message = "Failed count mismatch: reported ${progress.failedCount}, actual $actualFailed"
                ))
            }
            
            if (actualPending != progress.remainingCount) {
                issues.add(ValidationIssue(
                    severity = ValidationSeverity.ERROR,
                    field = "remainingCount",
                    message = "Remaining count mismatch: reported ${progress.remainingCount}, actual $actualPending"
                ))
            }
        }
        
        // Check for suspicious timestamps
        val now = System.currentTimeMillis()
        if (progress.lastUpdated > now + 300000) { // 5 minutes in future
            issues.add(ValidationIssue(
                severity = ValidationSeverity.WARNING,
                field = "lastUpdated",
                message = "Last updated timestamp is significantly in the future"
            ))
        }
        
        if (progress.createdAt > now) {
            issues.add(ValidationIssue(
                severity = ValidationSeverity.WARNING,
                field = "createdAt",
                message = "Created timestamp is in the future"
            ))
        }
        
        return DeepValidationResult(
            basicValidation = basicValidation,
            issues = issues,
            suggestions = suggestions,
            dataQualityScore = calculateDataQualityScore(progress, issues)
        )
    }
    
    /**
     * Calculate data quality score (0-100)
     */
    private fun calculateDataQualityScore(progress: EnhancedCampaignProgress, issues: List<ValidationIssue>): Int {
        var score = 100
        
        // Deduct points for validation issues
        issues.forEach { issue ->
            when (issue.severity) {
                ValidationSeverity.ERROR -> score -= 20
                ValidationSeverity.WARNING -> score -= 10
                ValidationSeverity.INFO -> score -= 5
            }
        }
        
        // Deduct points for missing optional data
        if (progress.errorMessage.isNullOrBlank() && progress.failedCount > 0) {
            score -= 5 // No error message despite failures
        }
        
        if (progress.contactStatuses.isEmpty() && progress.totalContacts > 0) {
            score -= 10 // No contact status tracking
        }
        
        if (progress.pausedAt == null && progress.isStopped) {
            score -= 5 // No pause timestamp
        }
        
        return maxOf(0, score)
    }
    
    /**
     * Campaign operation types for validation
     */
    enum class CampaignOperation {
        SAVE, UPDATE, DELETE, RESUME, MIGRATION
    }
    
    /**
     * Validation severity levels
     */
    enum class ValidationSeverity {
        INFO, WARNING, ERROR
    }
    
    /**
     * Validation issue data class
     */
    data class ValidationIssue(
        val severity: ValidationSeverity,
        val field: String,
        val message: String
    )
    
    /**
     * Batch validation result data class
     */
    data class BatchValidationResult(
        val totalCampaigns: Int,
        val validCampaigns: List<EnhancedCampaignProgress>,
        val invalidCampaigns: List<Pair<EnhancedCampaignProgress, List<String>>>,
        val warnings: List<String>
    ) {
        val validationRate: Float
            get() = if (totalCampaigns > 0) {
                validCampaigns.size.toFloat() / totalCampaigns.toFloat()
            } else 1.0f
    }
    
    /**
     * System constraint validation result data class
     */
    data class SystemConstraintValidation(
        val isValid: Boolean,
        val issues: List<String>,
        val warnings: List<String>,
        val totalCampaigns: Int,
        val totalContacts: Int
    )
    
    /**
     * Deep validation result data class
     */
    data class DeepValidationResult(
        val basicValidation: ValidationResult,
        val issues: List<ValidationIssue>,
        val suggestions: List<String>,
        val dataQualityScore: Int
    ) {
        val isValid: Boolean
            get() = basicValidation is ValidationResult.Valid && 
                    issues.none { it.severity == ValidationSeverity.ERROR }
    }
}