package com.message.bulksend.db

// This is not an entity itself, but part of ContactGroup
// It will be stored as JSON in the database.
data class ContactEntity(
    val name: String,
    val number: String,
    val isWhatsApp: Boolean = false
)