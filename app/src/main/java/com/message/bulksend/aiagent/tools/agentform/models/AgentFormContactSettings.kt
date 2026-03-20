package com.message.bulksend.aiagent.tools.agentform.models

import java.util.UUID

data class AgentFormSavedContact(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String
)

data class AgentFormPostSubmitPdf(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val sizeBytes: Long = 0L
)

data class AgentFormContactSettings(
    val enabled: Boolean = false,
    val contacts: List<AgentFormSavedContact> = emptyList(),
    val postSubmitContentEnabled: Boolean = false,
    val postSubmitVideoUrl: String = "",
    val postSubmitPdfs: List<AgentFormPostSubmitPdf> = emptyList(),
    val autoStatusMonitorEnabled: Boolean = false,
    val autoReminderEnabled: Boolean = false,
    val autoVerifiedFollowupEnabled: Boolean = false,
    val reminderMessage: String = "",
    val verifiedMessage: String = ""
)
