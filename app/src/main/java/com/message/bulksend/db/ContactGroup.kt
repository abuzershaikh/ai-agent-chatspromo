package com.message.bulksend.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contact_groups")
data class ContactGroup(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val contacts: List<ContactEntity>,
    val timestamp: Long,
    // Track if group was created during premium plan
    val isPremiumGroup: Boolean = false
)