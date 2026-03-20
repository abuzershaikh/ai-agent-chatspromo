package com.message.bulksend.leadmanager.model

data class Lead(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val email: String = "",
    val countryCode: String = "",
    val countryIso: String = "",
    val alternatePhone: String = "",
    val status: LeadStatus,
    val source: String,
    val lastMessage: String,
    val timestamp: Long,
    val category: String = "General",
    val notes: String = "",
    val priority: LeadPriority = LeadPriority.MEDIUM,
    val tags: List<String> = emptyList(),
    val product: String = "",
    val leadScore: Int = 50,
    val followUps: List<FollowUp> = emptyList(),
    val nextFollowUpDate: Long? = null,
    val isFollowUpCompleted: Boolean = false
)

enum class LeadStatus(val displayName: String, val color: Long) {
    NEW("New", 0xFF3B82F6),
    INTERESTED("Interested", 0xFF06B6D4),
    CONTACTED("Contacted", 0xFFF59E0B),
    QUALIFIED("Qualified", 0xFF10B981),
    CONVERTED("Converted", 0xFF8B5CF6),
    CUSTOMER("Customer", 0xFF22C55E),
    LOST("Lost", 0xFFEF4444)
}

enum class LeadPriority(val displayName: String, val color: Long) {
    HIGH("High", 0xFFEF4444),
    MEDIUM("Medium", 0xFFF59E0B),
    LOW("Low", 0xFF10B981)
}

data class LeadStats(
    val totalLeads: Int = 0,
    val newLeads: Int = 0,
    val interested: Int = 0,
    val contacted: Int = 0,
    val qualified: Int = 0,
    val converted: Int = 0,
    val conversionRate: Float = 0f
)

data class FollowUp(
    val id: String,
    val leadId: String,
    val title: String,
    val description: String = "",
    val scheduledDate: Long,
    val scheduledTime: String, // Format: "HH:mm"
    val type: FollowUpType,
    val isCompleted: Boolean = false,
    val completedDate: Long? = null,
    val notes: String = "",
    val reminderMinutes: Int = 15 // Minutes before to remind
)

enum class FollowUpType(val displayName: String, val icon: String, val color: Long) {
    CALL("Phone Call", "phone", 0xFF3B82F6),
    EMAIL("Email", "email", 0xFF10B981),
    MEETING("Meeting", "meeting", 0xFFF59E0B),
    WHATSAPP("WhatsApp", "message", 0xFF25D366),
    VISIT("Site Visit", "location", 0xFF8B5CF6),
    OTHER("Other", "event", 0xFF64748B)
}

// Import Progress tracking
data class ImportProgress(
    val total: Int,
    val imported: Int,
    val failed: Int,
    val isComplete: Boolean = false,
    val errorMessage: String? = null
) {
    val progress: Float get() = if (total > 0) imported.toFloat() / total else 0f
    val percentage: Int get() = (progress * 100).toInt()
}

// Sync State
data class SyncState(
    val lastSyncTime: Long = 0L,
    val isSyncing: Boolean = false,
    val totalLeads: Int = 0
)
