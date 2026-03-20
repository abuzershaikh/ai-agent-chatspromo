package com.message.bulksend.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "agent_forms")
data class AgentFormEntity(
        @PrimaryKey val formId: String = UUID.randomUUID().toString(),
        val title: String,
        val description: String = "",
        val fieldsJson: String, // Storing fields list as JSON string
        val createdAt: Long = System.currentTimeMillis()
)
