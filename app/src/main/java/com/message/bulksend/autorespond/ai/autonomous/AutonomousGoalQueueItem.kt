package com.message.bulksend.autorespond.ai.autonomous

data class AutonomousGoalQueueItem(
    val id: String,
    val senderPhone: String,
    val senderName: String,
    val goal: String,
    val lastUserMessage: String,
    val status: String,
    val attempts: Int,
    val nextRunAt: Long,
    val dedupeKey: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastError: String = "",
    val lastAgentMessage: String = "",
    val continuationState: AutonomousContinuationState = AutonomousContinuationState()
) {
    companion object {
        const val STATUS_QUEUED = "queued"
        const val STATUS_RUNNING = "running"
        const val STATUS_WAITING = "waiting"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_FAILED = "failed"
    }
}

data class AutonomousContinuationState(
    val roundsCompleted: Int = 0,
    val summary: String = "",
    val recentInbound: List<String> = emptyList(),
    val recentOutbound: List<String> = emptyList(),
    val recentToolActions: List<String> = emptyList(),
    val lastDecision: String = "",
    val updatedAt: Long = 0L
)

data class AutonomousRuntimeStatus(
    val queueSize: Int,
    val lastHeartbeatAt: Long,
    val lastError: String
)
