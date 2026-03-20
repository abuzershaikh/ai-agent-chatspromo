package com.message.bulksend.autorespond.ai.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey
    val phoneNumber: String, // Acts as unique ID
    val name: String? = null,
    val email: String? = null,
    val address: String? = null,
    val leadScore: Int = 0,
    val leadTier: String = "COLD", // COLD, WARM, HOT
    val currentIntent: String? = null, // e.g., "interested_in_product_X"
    val customData: String = "{}", // JSON Map<String, String> for flexible fields
    val missingFields: String = "[]", // JSON List<String> of fields to ask
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
