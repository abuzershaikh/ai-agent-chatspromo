package com.message.bulksend.autorespond.keywordreply

/**
 * Data class for keyword reply
 */
data class KeywordReplyData(
    val id: String = System.currentTimeMillis().toString(),
    val incomingKeyword: String,
    val replyMessage: String,
    val replyOption: String = "", // "menu", "chatgpt", "gemini"
    val matchOption: String = "exact", // "exact" or "contains"
    val minWordMatch: Int = 1, // Minimum words to match in contains mode
    val sendEmail: Boolean = false,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
