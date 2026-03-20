package com.message.bulksend.data

// Data class for tracking status of each contact
data class ContactStatus(
    val number: String,
    var status: String, // status: "pending", "sent", "failed"
    val failureReason: String? = null
)
