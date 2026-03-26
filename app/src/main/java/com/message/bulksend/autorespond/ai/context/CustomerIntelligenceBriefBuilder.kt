package com.message.bulksend.autorespond.ai.context

import com.message.bulksend.autorespond.ai.data.model.UserProfile
import com.message.bulksend.autorespond.ai.needdiscovery.NeedDiscoverySchema
import com.message.bulksend.autorespond.ai.needdiscovery.NeedDiscoveryState
import com.message.bulksend.autorespond.ai.scoring.AIAgentLeadScorer
import com.message.bulksend.autorespond.database.MessageEntity
import java.util.Locale

class CustomerIntelligenceBriefBuilder(
    private val contextSwitchingManager: ContextSwitchingManager = ContextSwitchingManager(),
    private val leadScorer: AIAgentLeadScorer = AIAgentLeadScorer()
) {

    fun buildPromptBlock(
        senderPhone: String,
        incomingMessage: String,
        templateGoal: String,
        userProfile: UserProfile?,
        history: List<MessageEntity>,
        needSchema: NeedDiscoverySchema,
        needState: NeedDiscoveryState?,
        hasAskedForName: Boolean,
        userIgnoredNameRequest: Boolean
    ): String {
        val profile = userProfile ?: UserProfile(phoneNumber = senderPhone)
        val orderedHistory = history.sortedBy { it.timestamp }
        val userMessages = orderedHistory.mapNotNull(::extractUserText).filter { it.isNotBlank() }
        val assistantMessages = orderedHistory.mapNotNull(::extractAssistantText).filter { it.isNotBlank() }

        val combinedUserSignal =
            buildList {
                addAll(userMessages.takeLast(3))
                incomingMessage.trim().takeIf { it.isNotBlank() }?.let(::add)
            }.joinToString(" ")

        val topicResult = contextSwitchingManager.detectTopic(combinedUserSignal.ifBlank { incomingMessage })
        val comparisonItems = contextSwitchingManager.detectComparisonRequest(combinedUserSignal)
        val effectiveScore = maxOf(profile.leadScore, leadScorer.calculateLeadScore(profile, orderedHistory))
        val effectiveTier =
            profile.leadTier
                .trim()
                .ifBlank { leadScorer.getLeadTier(effectiveScore) }
                .uppercase(Locale.ROOT)
        val priority = leadScorer.getPriority(effectiveScore)
        val followUpHours = leadScorer.getRecommendedFollowUpTime(effectiveScore, profile.currentIntent)
        val missingFieldLabels = resolveMissingFieldLabels(needSchema, needState)
        val latestUserFocus = incomingMessage.trim().ifBlank { userMessages.lastOrNull().orEmpty() }

        val stage = determineStage(topicResult.topic, effectiveTier, profile.currentIntent, missingFieldLabels, orderedHistory)
        val urgency = determineUrgency(topicResult.topic, priority, combinedUserSignal)
        val momentum = determineMomentum(orderedHistory)
        val likelyObjective = determineLikelyObjective(topicResult.topic, profile.currentIntent, templateGoal)
        val signals = buildSignals(combinedUserSignal, missingFieldLabels, comparisonItems, needState, profile, orderedHistory)
        val caution = determineCaution(userIgnoredNameRequest, missingFieldLabels, assistantMessages, latestUserFocus)
        val nextBestAction =
            determineNextBestAction(
                topic = topicResult.topic,
                templateGoal = templateGoal,
                missingFieldLabels = missingFieldLabels,
                suggestedQuestion = needState?.suggestedQuestion.orEmpty(),
                comparisonItems = comparisonItems
            )

        return buildString {
            append("[SMART CONVERSATION BRIEF]\n")
            append("Current Topic: ${topicResult.topic}\n")
            append("Conversation Stage: $stage\n")
            append("Lead Priority: $priority | Tier: $effectiveTier | Score: $effectiveScore\n")
            append("Urgency: $urgency | Momentum: $momentum\n")
            append("Likely Objective: $likelyObjective\n")
            if (latestUserFocus.isNotBlank()) {
                append("Latest User Focus: ${compact(latestUserFocus, 180)}\n")
            }
            if (missingFieldLabels.isNotEmpty()) {
                append("Missing Critical Details: ${missingFieldLabels.joinToString(", ")}\n")
            } else {
                append("Missing Critical Details: none\n")
            }
            if (signals.isNotEmpty()) {
                append("Strategic Signals:\n")
                signals.forEach { signal -> append("- $signal\n") }
            }
            if (hasAskedForName && userIgnoredNameRequest) {
                append("Name Handling: User ignored earlier name request, do not repeat it now.\n")
            }
            append("Next Best Action: $nextBestAction\n")
            append("Caution: $caution\n")
            append("If user goes silent, best follow-up window is about ${followUpHours}h.\n")
        }.trimEnd()
    }

    private fun determineStage(
        topic: String,
        leadTier: String,
        currentIntent: String?,
        missingFieldLabels: List<String>,
        history: List<MessageEntity>
    ): String {
        val intent = currentIntent.orEmpty().lowercase(Locale.ROOT)
        return when {
            topic == "PAYMENT" -> "Closing / Payment"
            missingFieldLabels.isNotEmpty() -> "Discovery"
            topic == "PRICING" || topic == "COMPARISON" || topic == "PRODUCT_DISCOVERY" -> "Evaluation"
            intent.contains("buy") || intent.contains("purchase") || intent.contains("order") || intent.contains("book") -> "Closing"
            leadTier == "HOT" -> "Closing"
            history.size <= 2 -> "Opening"
            else -> "Active Follow-up"
        }
    }

    private fun determineUrgency(topic: String, priority: String, combinedUserSignal: String): String {
        return when {
            containsAny(
                combinedUserSignal,
                listOf("urgent", "asap", "jaldi", "today", "aaj", "now", "immediately", "right away")
            ) -> "High"
            topic == "PAYMENT" || topic == "SCHEDULING" -> "High"
            priority == "URGENT" || priority == "HIGH" -> "High"
            topic == "PRICING" || topic == "FOLLOW_UP" || priority == "MEDIUM" -> "Medium"
            else -> "Low"
        }
    }

    private fun determineMomentum(history: List<MessageEntity>): String {
        if (history.isEmpty()) return "Low"
        val now = System.currentTimeMillis()
        val userMessages = history.filter { extractUserText(it)?.isNotBlank() == true }
        val lastSixHours = now - 6 * 60 * 60 * 1000L
        val lastDay = now - 24 * 60 * 60 * 1000L
        val userCountSixHours = userMessages.count { it.timestamp >= lastSixHours }
        val userCountDay = userMessages.count { it.timestamp >= lastDay }
        return when {
            userCountSixHours >= 3 -> "High"
            userCountDay >= 2 -> "Medium"
            else -> "Low"
        }
    }

    private fun determineLikelyObjective(topic: String, currentIntent: String?, templateGoal: String): String {
        val intent = currentIntent?.trim().orEmpty()
        if (intent.isNotBlank()) return compact(intent, 120)
        return when (topic) {
            "PRODUCT_DISCOVERY" -> "Understand product fit and move toward selection"
            "PRICING" -> "Get price clarity before deciding"
            "COMPARISON" -> "Compare options and shortlist one"
            "PAYMENT" -> "Complete or verify payment confidently"
            "SCHEDULING" -> "Lock a slot or next meeting step"
            "DOCUMENTS" -> "Receive supporting material before deciding"
            "SUPPORT" -> "Resolve the issue before moving forward"
            else -> templateGoal.trim().ifBlank { "Move the conversation toward a clear next step" }
        }
    }

    private fun buildSignals(
        combinedUserSignal: String,
        missingFieldLabels: List<String>,
        comparisonItems: List<String>?,
        needState: NeedDiscoveryState?,
        profile: UserProfile,
        history: List<MessageEntity>
    ): List<String> {
        val signals = mutableListOf<String>()
        if (!profile.name.isNullOrBlank() || !profile.email.isNullOrBlank() || !profile.address.isNullOrBlank()) {
            signals += "Some customer identity details are already known"
        }
        if (comparisonItems != null && comparisonItems.size >= 2) {
            signals += "User is actively comparing options"
        }
        if (containsAny(combinedUserSignal, listOf("price", "budget", "discount", "offer", "cost", "rate"))) {
            signals += "User is price-sensitive right now"
        }
        if (containsAny(combinedUserSignal, listOf("photo", "catalog", "catalogue", "brochure", "document", "pdf", "details"))) {
            signals += "User wants richer proof material before deciding"
        }
        if (containsAny(combinedUserSignal, listOf("payment", "pay", "upi", "qr", "link", "transaction", "paid"))) {
            signals += "Conversation is close to transaction stage"
        }
        if (missingFieldLabels.isEmpty()) {
            signals += "Required discovery details are already complete"
        }
        if (needState?.suggestedQuestion?.isNotBlank() == true) {
            signals += "Need-discovery already knows the best next question"
        }
        if (history.count { extractUserText(it)?.isNotBlank() == true } >= 4) {
            signals += "User is engaged enough for a direct next-step question"
        }
        return signals.distinct().take(4)
    }

    private fun determineNextBestAction(
        topic: String,
        templateGoal: String,
        missingFieldLabels: List<String>,
        suggestedQuestion: String,
        comparisonItems: List<String>?
    ): String {
        return when {
            missingFieldLabels.isNotEmpty() && suggestedQuestion.isNotBlank() ->
                "Answer any direct question first, then ask only this: ${compact(suggestedQuestion, 140)}"
            comparisonItems != null && comparisonItems.size >= 2 ->
                "Compare ${comparisonItems.joinToString(" vs ")} clearly, then ask which one fits best."
            topic == "PRICING" ->
                "Give direct pricing clarity, mention the most relevant option, then ask if they want to proceed."
            topic == "PAYMENT" ->
                "Handle payment confidently: verify status, send the correct payment step, or confirm what happens next."
            topic == "SCHEDULING" ->
                "Offer one concrete scheduling path and ask for a decision."
            topic == "DOCUMENTS" ->
                "Share the most relevant document or proof first, then ask one short qualifying question."
            templateGoal.isNotBlank() ->
                "Move toward the primary goal naturally: ${compact(templateGoal, 140)}"
            else ->
                "Resolve the latest intent and finish with one short forward-moving question."
        }
    }

    private fun determineCaution(
        userIgnoredNameRequest: Boolean,
        missingFieldLabels: List<String>,
        assistantMessages: List<String>,
        latestUserFocus: String
    ): String {
        return when {
            userIgnoredNameRequest ->
                "Do not ask for the name again right now. Keep the conversation moving."
            hasRepeatedAssistantQuestion(assistantMessages) ->
                "Avoid repeating the previous question verbatim."
            missingFieldLabels.size > 1 ->
                "Ask only one missing detail at a time."
            containsAny(latestUserFocus, listOf("confused", "not understand", "samajh", "matlab", "how")) ->
                "Clarify simply before pushing to the next step."
            else ->
                "Answer side questions first, then steer back toward the main goal."
        }
    }

    private fun resolveMissingFieldLabels(
        needSchema: NeedDiscoverySchema,
        needState: NeedDiscoveryState?
    ): List<String> {
        if (needState == null) return emptyList()
        if (needState.missingRequiredFieldIds.isEmpty()) return emptyList()
        val fieldsById = needSchema.allFields().associateBy { it.id }
        return needState.missingRequiredFieldIds.map { id ->
            fieldsById[id]?.label?.trim().orEmpty().ifBlank { id }
        }
    }

    private fun hasRepeatedAssistantQuestion(assistantMessages: List<String>): Boolean {
        if (assistantMessages.size < 2) return false
        val latest = normalize(assistantMessages.last())
        val previous = normalize(assistantMessages[assistantMessages.lastIndex - 1])
        if (!latest.contains("?") && !previous.contains("?")) return false
        return latest == previous || latest.contains(previous) || previous.contains(latest)
    }

    private fun extractUserText(message: MessageEntity): String? {
        return when {
            message.outgoingMessage.isBlank() -> message.incomingMessage.trim()
            message.status.equals("RECEIVED", ignoreCase = true) -> message.incomingMessage.trim()
            else -> null
        }
    }

    private fun extractAssistantText(message: MessageEntity): String? {
        val text = message.outgoingMessage.trim()
        return text.ifBlank { null }
    }

    private fun containsAny(text: String, candidates: List<String>): Boolean {
        val normalized = normalize(text)
        return candidates.any { candidate ->
            val token = normalize(candidate)
            token.isNotBlank() && normalized.contains(token)
        }
    }

    private fun normalize(text: String): String {
        return text.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()
    }

    private fun compact(text: String, max: Int): String {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        return if (normalized.length <= max) normalized else normalized.take(max) + "..."
    }
}
