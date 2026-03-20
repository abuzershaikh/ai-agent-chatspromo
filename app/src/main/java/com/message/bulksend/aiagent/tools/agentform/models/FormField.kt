package com.message.bulksend.aiagent.tools.agentform.models

import java.util.UUID

data class FormField(
    val id: String = UUID.randomUUID().toString(),
    var type: FieldType,
    var label: String,
    var required: Boolean = false,
    var options: MutableList<String> = mutableListOf(), // For SELECT type
    var hint: String = ""
)
