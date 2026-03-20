package com.message.bulksend.autorespond.ai.customtask.models

import java.util.UUID

enum class AgentTaskSessionStatus {
    ACTIVE,
    COMPLETED,
    PAUSED,
    FAILED
}

data class AgentTaskSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val phoneNumber: String,
    val currentStepOrder: Int = 1,
    val status: AgentTaskSessionStatus = AgentTaskSessionStatus.ACTIVE,
    val lastPromptedStepOrder: Int? = null,
    val stepRepeatCount: Int = 0,
    val lastOwnerAlertStepOrder: Int? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
