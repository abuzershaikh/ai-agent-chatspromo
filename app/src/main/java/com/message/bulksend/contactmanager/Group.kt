package com.message.bulksend.contactmanager

data class Group(
    val id: Long,
    val name: String,
    val contacts: List<Contact>,
    val timestamp: Long,
    val isPremiumGroup: Boolean = false  // Track if created during premium plan
)
