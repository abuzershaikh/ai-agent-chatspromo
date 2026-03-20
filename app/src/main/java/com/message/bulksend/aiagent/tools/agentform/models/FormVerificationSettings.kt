package com.message.bulksend.aiagent.tools.agentform.models

data class FormVerificationSettings(
    val requireGoogleAuth: Boolean = false,
    val requireContactVerification: Boolean = false,
    val requireLocationVerification: Boolean = false
)

