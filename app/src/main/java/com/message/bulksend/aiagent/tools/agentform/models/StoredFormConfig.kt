package com.message.bulksend.aiagent.tools.agentform.models

data class StoredFormConfig(
    val fields: List<FormField> = emptyList(),
    val verification: FormVerificationSettings = FormVerificationSettings()
)

