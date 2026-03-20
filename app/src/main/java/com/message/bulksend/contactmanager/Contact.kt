package com.message.bulksend.contactmanager

data class Contact(
    val name: String,
    val number: String,
    val isWhatsApp: Boolean = false
)

