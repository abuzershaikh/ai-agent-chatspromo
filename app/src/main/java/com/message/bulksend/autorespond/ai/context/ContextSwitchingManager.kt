package com.message.bulksend.autorespond.ai.context

import android.util.Log
import java.util.Locale

/**
 * Lightweight semantic context helper for topic and comparison detection.
 */
class ContextSwitchingManager {

    fun detectTopic(message: String): TopicResult {
        val normalized = normalize(message)
        if (normalized.isBlank()) {
            return TopicResult(topic = "GENERAL", confidence = 0.30, matchedKeywords = emptyList())
        }

        val topicScores = linkedMapOf<String, MutableList<String>>()
        TOPIC_RULES.forEach { rule ->
            val matched = rule.keywords.filter { keyword -> normalized.contains(keyword) }
            if (matched.isNotEmpty()) {
                topicScores.getOrPut(rule.topic) { mutableListOf() }.addAll(matched)
            }
        }

        detectComparisonRequest(message)?.let { compared ->
            if (compared.size >= 2) {
                topicScores.getOrPut("COMPARISON") { mutableListOf() }.add("comparison")
            }
        }

        val bestMatch =
            topicScores.maxByOrNull { (_, matches) -> matches.size }
                ?: return fallbackTopic(normalized)

        val confidence = (0.45 + (bestMatch.value.size * 0.12)).coerceAtMost(0.95)
        val result = TopicResult(bestMatch.key, confidence, bestMatch.value.distinct())
        Log.d("ContextSwitching", "Message=$message | Topic=${result.topic} | Confidence=${result.confidence}")
        return result
    }

    fun detectTopicChange(currentTopic: String, previousTopic: String?): Boolean {
        val previous = previousTopic?.trim().orEmpty().uppercase(Locale.ROOT)
        val current = currentTopic.trim().uppercase(Locale.ROOT)
        if (previous.isBlank() || current.isBlank()) return false
        if (previous == current) return false
        if (previous == "GENERAL" || current == "GENERAL") return false
        return true
    }

    fun buildContextString(currentTopic: String, previousTopic: String?): String {
        val previous = previousTopic?.trim().orEmpty().ifBlank { "Unknown" }
        val current = currentTopic.trim().ifBlank { "General" }
        return buildString {
            append("Previous Topic: $previous\n")
            append("Current Topic: $current\n")
            append("Answer the user's current topic first, but preserve any unresolved commitment from the previous topic.\n")
        }
    }

    fun detectComparisonRequest(message: String): List<String>? {
        val normalized = message.replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) return null

        COMPARISON_PATTERNS.forEach { pattern ->
            val match = pattern.find(normalized) ?: return@forEach
            val groups = match.groupValues.drop(1)
            val items =
                groups
                    .map(::cleanComparisonItem)
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase(Locale.ROOT) }
            if (items.size >= 2) {
                return items.take(3)
            }
        }

        return null
    }

    private fun fallbackTopic(normalized: String): TopicResult {
        val questionLike = normalized.contains("?") || normalized.startsWith("what ") || normalized.startsWith("how ")
        val topic =
            when {
                GREETING_KEYWORDS.any { normalized.contains(it) } -> "GREETING"
                questionLike -> "FOLLOW_UP"
                else -> "GENERAL"
            }
        return TopicResult(topic = topic, confidence = 0.40, matchedKeywords = emptyList())
    }

    private fun cleanComparisonItem(value: String): String {
        return value
            .substringBefore("?")
            .substringBefore(",")
            .replace(Regex("\\b(which|is|better|best|for me|please|tell me)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(60)
    }

    private fun normalize(text: String): String {
        return text.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()
    }

    private data class TopicRule(
        val topic: String,
        val keywords: List<String>
    )

    companion object {
        private val GREETING_KEYWORDS =
            listOf("hello", "hi", "hey", "good morning", "good evening", "namaste", "salam")

        private val TOPIC_RULES =
            listOf(
                TopicRule("PAYMENT", listOf("payment", "paid", "pay", "upi", "qr", "razorpay", "transaction", "receipt", "amount", "link")),
                TopicRule("PRICING", listOf("price", "pricing", "cost", "rate", "budget", "discount", "offer", "quote", "expensive", "cheap")),
                TopicRule("PRODUCT_DISCOVERY", listOf("product", "catalog", "catalogue", "model", "item", "details", "feature", "available", "stock", "variant", "color", "size")),
                TopicRule("DOCUMENTS", listOf("document", "pdf", "brochure", "quotation", "proposal", "invoice", "file", "catalogue", "catalog")),
                TopicRule("SCHEDULING", listOf("book", "booking", "appointment", "schedule", "meeting", "slot", "calendar", "tomorrow", "today", "time")),
                TopicRule("SUPPORT", listOf("issue", "problem", "error", "support", "help", "not working", "complaint", "stuck", "failed")),
                TopicRule("FOLLOW_UP", listOf("follow up", "followup", "any update", "update", "checking", "status", "reminder", "still interested")),
                TopicRule("OWNER_ASSIST", listOf("owner", "admin", "report", "summary", "command", "sheet status", "lead report"))
            )

        private val COMPARISON_PATTERNS =
            listOf(
                Regex("compare\\s+(.+?)\\s+(?:with|and|vs|versus)\\s+(.+)", RegexOption.IGNORE_CASE),
                Regex("difference\\s+between\\s+(.+?)\\s+and\\s+(.+)", RegexOption.IGNORE_CASE),
                Regex("(.+?)\\s+vs\\s+(.+)", RegexOption.IGNORE_CASE),
                Regex("(.+?)\\s+versus\\s+(.+)", RegexOption.IGNORE_CASE),
                Regex("which\\s+is\\s+better\\s+(.+?)\\s+or\\s+(.+)", RegexOption.IGNORE_CASE),
                Regex("(.+?)\\s+or\\s+(.+?)\\s*(?:\\?|$)", RegexOption.IGNORE_CASE)
            )
    }
}

/**
 * Topic Detection Result
 */
data class TopicResult(
    val topic: String,
    val confidence: Double,
    val matchedKeywords: List<String>
)
