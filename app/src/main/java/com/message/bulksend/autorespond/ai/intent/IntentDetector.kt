package com.message.bulksend.autorespond.ai.intent

import android.util.Log

/**
 * Intent Detection System
 * Automatically detects user intent from messages
 */
class IntentDetector {

    companion object {
        // Intent Types
        const val INTENT_SALES = "SALES"
        const val INTENT_SUPPORT = "SUPPORT"
        const val INTENT_INQUIRY = "INQUIRY"
        const val INTENT_COMPLAINT = "COMPLAINT"
        const val INTENT_GREETING = "GREETING"
        const val INTENT_FAREWELL = "FAREWELL"
        const val INTENT_REMINDER = "REMINDER"
        const val INTENT_UNKNOWN = "UNKNOWN"

        // Intent Keywords
        private val SALES_KEYWORDS = listOf(
            "buy", "purchase", "order", "book", "booking", "reserve",
            "kharidna", "lena", "chahiye", "price", "cost", "rate",
            "kitna", "khareed", "खरीदना", "लेना"
        )

        private val SUPPORT_KEYWORDS = listOf(
            "help", "problem", "issue", "not working", "error", "fix",
            "madad", "samasya", "dikkat", "kharab", "theek", "repair",
            "मदद", "समस्या", "ठीक"
        )

        private val INQUIRY_KEYWORDS = listOf(
            "what", "how", "when", "where", "which", "tell me", "info",
            "kya", "kaise", "kab", "kahan", "batao", "information",
            "क्या", "कैसे", "कब", "कहाँ"
        )
        
        private val REMINDER_KEYWORDS = listOf(
            "remind", "reminder", "remember", "alert", "notify", "alarm",
            "yaad", "yad", "message me", "text me", "ping me", "msg me",
            "message kar", "msg kar", "bata dena", "call me",
            "याद", "रिमाइंडर"
        )

        private val COMPLAINT_KEYWORDS = listOf(
            "complaint", "bad", "terrible", "worst", "disappointed", "angry",
            "shikayat", "bura", "kharab", "ganda", "naraz", "gussa",
            "शिकायत", "बुरा", "खराब"
        )

        private val GREETING_KEYWORDS = listOf(
            "hi", "hello", "hey", "good morning", "good evening", "namaste",
            "hii", "hlo", "नमस्ते", "हेलो"
        )

        private val FAREWELL_KEYWORDS = listOf(
            "bye", "goodbye", "thanks", "thank you", "ok", "okay",
            "dhanyavad", "shukriya", "alvida", "धन्यवाद", "शुक्रिया"
        )
    }

    /**
     * Detect intent from user message
     */
    fun detectIntent(message: String): IntentResult {
        val lowerMessage = message.lowercase().trim()
        
        // Check each intent type
        val scores = mutableMapOf<String, Int>()
        
        scores[INTENT_SALES] = countKeywordMatches(lowerMessage, SALES_KEYWORDS)
        scores[INTENT_SUPPORT] = countKeywordMatches(lowerMessage, SUPPORT_KEYWORDS)
        scores[INTENT_INQUIRY] = countKeywordMatches(lowerMessage, INQUIRY_KEYWORDS)
        scores[INTENT_REMINDER] = countKeywordMatches(lowerMessage, REMINDER_KEYWORDS)
        scores[INTENT_COMPLAINT] = countKeywordMatches(lowerMessage, COMPLAINT_KEYWORDS)
        scores[INTENT_GREETING] = countKeywordMatches(lowerMessage, GREETING_KEYWORDS)
        scores[INTENT_FAREWELL] = countKeywordMatches(lowerMessage, FAREWELL_KEYWORDS)
        
        // Get highest scoring intent
        val maxScore = scores.maxByOrNull { it.value }
        
        val detectedIntent = if (maxScore != null && maxScore.value > 0) {
            maxScore.key
        } else {
            INTENT_UNKNOWN
        }
        
        val confidence = calculateConfidence(maxScore?.value ?: 0, lowerMessage.split(" ").size)
        
        Log.d("IntentDetector", "Message: $message | Intent: $detectedIntent | Confidence: $confidence")
        
        return IntentResult(
            intent = detectedIntent,
            confidence = confidence,
            matchedKeywords = getMatchedKeywords(lowerMessage, detectedIntent)
        )
    }

    /**
     * Count how many keywords match in the message
     */
    private fun countKeywordMatches(message: String, keywords: List<String>): Int {
        return keywords.count { keyword ->
            message.contains(keyword, ignoreCase = true)
        }
    }

    /**
     * Get list of matched keywords
     */
    private fun getMatchedKeywords(message: String, intent: String): List<String> {
        val keywords = when (intent) {
            INTENT_SALES -> SALES_KEYWORDS
            INTENT_SUPPORT -> SUPPORT_KEYWORDS
            INTENT_INQUIRY -> INQUIRY_KEYWORDS
            INTENT_REMINDER -> REMINDER_KEYWORDS
            INTENT_COMPLAINT -> COMPLAINT_KEYWORDS
            INTENT_GREETING -> GREETING_KEYWORDS
            INTENT_FAREWELL -> FAREWELL_KEYWORDS
            else -> emptyList()
        }
        
        return keywords.filter { keyword ->
            message.contains(keyword, ignoreCase = true)
        }
    }

    /**
     * Calculate confidence score (0.0 to 1.0)
     */
    private fun calculateConfidence(matchCount: Int, wordCount: Int): Double {
        if (wordCount == 0) return 0.0
        
        // Confidence based on match ratio
        val ratio = matchCount.toDouble() / wordCount.toDouble()
        
        return when {
            ratio >= 0.5 -> 0.95 // Very high confidence
            ratio >= 0.3 -> 0.80 // High confidence
            ratio >= 0.2 -> 0.65 // Medium confidence
            ratio >= 0.1 -> 0.50 // Low confidence
            else -> 0.30 // Very low confidence
        }
    }

    /**
     * Get priority level for intent
     */
    fun getIntentPriority(intent: String): String {
        return when (intent) {
            INTENT_COMPLAINT -> "URGENT"
            INTENT_SUPPORT -> "HIGH"
            INTENT_REMINDER -> "HIGH"
            INTENT_SALES -> "HIGH"
            INTENT_INQUIRY -> "NORMAL"
            INTENT_GREETING -> "LOW"
            INTENT_FAREWELL -> "LOW"
            else -> "NORMAL"
        }
    }

    /**
     * Get suggested response tone for intent
     */
    fun getSuggestedTone(intent: String): String {
        return when (intent) {
            INTENT_COMPLAINT -> "Apologetic and Helpful"
            INTENT_SUPPORT -> "Professional and Solution-focused"
            INTENT_REMINDER -> "Helpful and Reliable"
            INTENT_SALES -> "Enthusiastic and Informative"
            INTENT_INQUIRY -> "Friendly and Clear"
            INTENT_GREETING -> "Warm and Welcoming"
            INTENT_FAREWELL -> "Polite and Grateful"
            else -> "Neutral and Helpful"
        }
    }
}

/**
 * Intent Detection Result
 */
data class IntentResult(
    val intent: String,
    val confidence: Double,
    val matchedKeywords: List<String>
)
