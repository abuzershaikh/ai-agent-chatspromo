package com.message.bulksend.templates

/**
 * Yeh data class ek user-created template ki saari jaankari rakhti hai.
 * Ismein se ab category aur tags hata diye gaye hain.
 */
data class Template(
    val id: String,
    val name: String,
    val message: String,
    val mediaUri: String? = null,
    val timestamp: Long = 0L,
    val isFavorite: Boolean = false,
    val useCount: Int = 0
)

