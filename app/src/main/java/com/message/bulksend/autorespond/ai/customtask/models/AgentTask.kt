package com.message.bulksend.autorespond.ai.customtask.models

import java.util.UUID

data class AgentTask(
    val taskId: String = UUID.randomUUID().toString(),
    val stepOrder: Int,
    val title: String,
    val goal: String = "",
    val instruction: String,
    val followUpQuestion: String = "",
    val allowedTools: List<String>? = emptyList(),
    val agentFormTemplateKey: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
