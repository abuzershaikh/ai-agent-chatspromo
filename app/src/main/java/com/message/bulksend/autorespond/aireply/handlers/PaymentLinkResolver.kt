package com.message.bulksend.autorespond.aireply.handlers

import com.message.bulksend.aiagent.tools.ecommerce.RazorPaymentManager
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

internal data class PaymentLinkResolution(
    val selectedLink: RazorPaymentManager.PaymentLinkInfo?,
    val recentLinks: List<RazorPaymentManager.PaymentLinkInfo>,
    val ambiguousCandidates: List<RazorPaymentManager.PaymentLinkInfo> = emptyList()
) {
    val isAmbiguous: Boolean
        get() = ambiguousCandidates.isNotEmpty()
}

internal object PaymentLinkResolver {

    private val genericWords =
        setOf(
            "payment",
            "pay",
            "status",
            "check",
            "verify",
            "verification",
            "link",
            "mera",
            "mujhe",
            "kar",
            "karo",
            "please",
            "pls",
            "done",
            "received",
            "hua",
            "hogaya",
            "ho",
            "gaya",
            "hai",
            "ka",
            "ki",
            "ke"
        )

    private val activeStatuses = setOf("created", "issued")

    suspend fun resolveForMessage(
        manager: RazorPaymentManager,
        senderPhone: String,
        message: String,
        limit: Int = 8
    ): PaymentLinkResolution {
        val links = manager.getRecentPaymentLinksForUser(senderPhone, limit = limit)
        if (links.isEmpty()) {
            return PaymentLinkResolution(selectedLink = null, recentLinks = emptyList())
        }

        val lowerMessage = message.lowercase(Locale.ROOT)

        // 1) Explicit Link ID mention always wins.
        resolveByLinkId(links, lowerMessage)?.let { selected ->
            return PaymentLinkResolution(selectedLink = selected, recentLinks = links)
        }

        // 2) Explicit ordinal reference (second payment, pehla payment, etc.).
        val ordinalIndex = extractOrdinalIndex(lowerMessage)
        if (ordinalIndex != null && ordinalIndex in links.indices) {
            return PaymentLinkResolution(selectedLink = links[ordinalIndex], recentLinks = links)
        }

        val amountHints = extractAmountHints(message)
        val amountMatches =
            if (amountHints.isEmpty()) {
                emptyList()
            } else {
                links.filter { link -> amountHints.any { amount -> isAmountMatch(link.amount, amount) } }
            }

        if (amountMatches.size == 1) {
            return PaymentLinkResolution(selectedLink = amountMatches.first(), recentLinks = links)
        }

        // 3) Score by message-description overlap + amount/status hints.
        val scoredLinks = scoreLinks(links, lowerMessage, amountHints)
        val top = scoredLinks.firstOrNull()
        val second = scoredLinks.getOrNull(1)
        if (top != null) {
            val scoreGap = top.score - (second?.score ?: 0)
            if (top.score >= 26 && scoreGap >= 8) {
                return PaymentLinkResolution(selectedLink = top.link, recentLinks = links)
            }
        }

        // 4) Generic status question handling:
        //    - if exactly one active link exists, that is the current transaction.
        //    - if multiple active links, ask user to select.
        val activeLinks = links.filter { isActiveStatus(it.status) }
        if (isGenericStatusMessage(lowerMessage)) {
            if (activeLinks.size == 1) {
                return PaymentLinkResolution(selectedLink = activeLinks.first(), recentLinks = links)
            }
            if (activeLinks.size > 1) {
                return PaymentLinkResolution(
                    selectedLink = null,
                    recentLinks = links,
                    ambiguousCandidates = activeLinks.take(4)
                )
            }
        }

        // 5) If amount produced multiple matches, and still unclear, ask disambiguation.
        if (amountMatches.size > 1) {
            return PaymentLinkResolution(
                selectedLink = null,
                recentLinks = links,
                ambiguousCandidates = amountMatches.take(4)
            )
        }

        // 6) Safe fallback:
        //    - prefer latest active link (current pending payment), else latest link.
        if (activeLinks.isNotEmpty()) {
            return PaymentLinkResolution(selectedLink = activeLinks.first(), recentLinks = links)
        }
        return PaymentLinkResolution(selectedLink = links.first(), recentLinks = links)
    }

    fun buildAmbiguityPrompt(candidates: List<RazorPaymentManager.PaymentLinkInfo>): String {
        if (candidates.isEmpty()) {
            return "Aapke multiple payment records mile. Kripya amount ya product name bataye."
        }

        val lines =
            candidates.mapIndexed { index, link ->
                val amountText = formatAmount(link.amount)
                val status = normalizeStatus(link.status).uppercase(Locale.ROOT)
                val idShort = link.id.takeLast(8)
                val title = link.description.ifBlank { "Payment" }
                "${index + 1}. $title | Rs $amountText | $status | ID: $idShort"
            }

        return buildString {
            append("Aapke multiple payment links mile hain. Kis payment ka status check karun?\n\n")
            append(lines.joinToString("\n"))
            append("\n\nReply me product name, amount, ya Link ID bhej dijiye.")
        }
    }

    private data class ScoredLink(
        val link: RazorPaymentManager.PaymentLinkInfo,
        val score: Int
    )

    private fun scoreLinks(
        links: List<RazorPaymentManager.PaymentLinkInfo>,
        lowerMessage: String,
        amountHints: List<Double>
    ): List<ScoredLink> {
        val queryTokens = tokenize(lowerMessage)
        val wantsPending = containsAny(lowerMessage, listOf("pending", "abhi", "current", "latest"))
        val wantsPaid = containsAny(lowerMessage, listOf("paid", "done", "success", "received"))

        return links.mapIndexed { index, link ->
            val status = normalizeStatus(link.status)
            val descriptionTokens = tokenize(link.description)
            val overlap = queryTokens.intersect(descriptionTokens).size
            val amountMatched = amountHints.any { amount -> isAmountMatch(link.amount, amount) }

            var score = max(0, 12 - index * 2) // recency bias
            if (overlap > 0) score += overlap * 8
            if (amountMatched) score += 24
            if (wantsPending && isActiveStatus(status)) score += 8
            if (wantsPaid && status == "paid") score += 6
            if (queryTokens.isNotEmpty() && queryTokens.all { it in descriptionTokens }) score += 6

            ScoredLink(link = link, score = score)
        }.sortedByDescending { it.score }
    }

    private fun resolveByLinkId(
        links: List<RazorPaymentManager.PaymentLinkInfo>,
        lowerMessage: String
    ): RazorPaymentManager.PaymentLinkInfo? {
        val explicitId =
            Regex("(?i)\\bplink_[a-z0-9]+\\b")
                .find(lowerMessage)
                ?.value
                ?.lowercase(Locale.ROOT)

        if (explicitId != null) {
            return links.firstOrNull { it.id.equals(explicitId, ignoreCase = true) }
        }

        return links.firstOrNull { link ->
            val lowerId = link.id.lowercase(Locale.ROOT)
            lowerMessage.contains(lowerId)
        }
    }

    private fun extractOrdinalIndex(lowerMessage: String): Int? {
        val patterns =
            listOf(
                0 to listOf("latest", "recent", "new", "current", "abhi"),
                0 to listOf("first", "1st", "pehla", "pehle"),
                1 to listOf("second", "2nd", "dusra", "doosra", "2ra"),
                2 to listOf("third", "3rd", "teesra", "tisra")
            )

        for ((index, keys) in patterns) {
            if (keys.any { key -> lowerMessage.contains(key) }) {
                return index
            }
        }
        return null
    }

    private fun extractAmountHints(message: String): List<Double> {
        val contextual =
            Regex("(?i)(?:₹|rs\\.?|inr|amount)\\s*[:=\\-]?\\s*(\\d{1,7}(?:\\.\\d{1,2})?)")
                .findAll(message)
                .mapNotNull { it.groupValues.getOrNull(1)?.toDoubleOrNull() }
                .toList()

        if (contextual.isNotEmpty()) return contextual

        return Regex("(?<!\\d)(\\d{1,7}(?:\\.\\d{1,2})?)(?!\\d)")
            .findAll(message)
            .mapNotNull { it.groupValues.getOrNull(1)?.toDoubleOrNull() }
            .filter { it > 0 && it <= 10000000 }
            .toList()
    }

    private fun tokenize(text: String): Set<String> {
        return Regex("[a-z0-9]{2,}")
            .findAll(text.lowercase(Locale.ROOT))
            .map { it.value }
            .filter { it !in genericWords }
            .toSet()
    }

    private fun isGenericStatusMessage(lowerMessage: String): Boolean {
        if (!containsAny(lowerMessage, listOf("payment", "pay", "status", "verify", "check", "transaction"))) {
            return false
        }
        val meaningfulTokens = tokenize(lowerMessage)
        val hasAmount = extractAmountHints(lowerMessage).isNotEmpty()
        return meaningfulTokens.isEmpty() && !hasAmount
    }

    private fun containsAny(text: String, words: List<String>): Boolean {
        return words.any { text.contains(it) }
    }

    private fun isAmountMatch(linkAmount: Double, hintAmount: Double): Boolean {
        return abs(linkAmount - hintAmount) <= 0.5
    }

    internal fun isActiveStatus(status: String): Boolean {
        return normalizeStatus(status) in activeStatuses
    }

    private fun normalizeStatus(status: String): String {
        return status.trim().lowercase(Locale.ROOT)
    }

    private fun formatAmount(amount: Double): String {
        return if (amount % 1.0 == 0.0) amount.toInt().toString()
        else String.format(Locale.US, "%.2f", amount)
    }
}
