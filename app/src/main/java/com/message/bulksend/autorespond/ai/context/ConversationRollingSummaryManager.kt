package com.message.bulksend.autorespond.ai.context

import android.content.Context
import com.message.bulksend.autorespond.ai.data.model.UserProfile
import com.message.bulksend.autorespond.ai.needdiscovery.NeedDiscoveryState
import com.message.bulksend.autorespond.database.MessageEntity
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class ConversationRollingSummaryManager(
    context: Context,
    private val contextSwitchingManager: ContextSwitchingManager = ContextSwitchingManager()
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun buildContextSnippet(
        senderPhone: String,
        fullHistory: List<MessageEntity>,
        recentWindow: Int,
        userProfile: UserProfile?,
        needState: NeedDiscoveryState?,
        templateGoal: String
    ): String {
        if (senderPhone.isBlank()) return ""
        val orderedHistory = fullHistory.sortedBy { it.timestamp }
        val window = recentWindow.coerceAtLeast(5)
        if (orderedHistory.size <= window + 2) return ""

        val olderMessages = orderedHistory.dropLast(window)
        if (olderMessages.isEmpty()) return ""

        val olderUserMessages = olderMessages.mapNotNull(::extractUserText).filter { it.isNotBlank() }
        val olderAssistantMessages = olderMessages.mapNotNull(::extractAssistantText).filter { it.isNotBlank() }
        val topicCounts = linkedMapOf<String, Int>()
        olderUserMessages.forEach { text ->
            val topic = contextSwitchingManager.detectTopic(text).topic
            topicCounts[topic] = (topicCounts[topic] ?: 0) + 1
        }
        val dominantTopics = topicCounts.entries.sortedByDescending { it.value }.take(3).map { it.key }
        val knownFacts = buildKnownFacts(userProfile, needState)
        val unresolved = needState?.missingRequiredFieldIds.orEmpty().take(4)
        val lastOlderUser = olderUserMessages.lastOrNull().orEmpty()
        val lastOlderAssistant = olderAssistantMessages.lastOrNull().orEmpty()

        val summary = buildString {
            append("Older messages covered: ${olderMessages.size}. ")
            if (dominantTopics.isNotEmpty()) {
                append("Main older topics: ${dominantTopics.joinToString(", ")}. ")
            }
            if (knownFacts.isNotEmpty()) {
                append("Known facts from older chat: ${knownFacts.joinToString(", ")}. ")
            }
            if (unresolved.isNotEmpty()) {
                append("Still unresolved from longer chat: ${unresolved.joinToString(", ")}. ")
            }
            if (templateGoal.isNotBlank()) {
                append("Keep steering toward goal: ${compact(templateGoal, 140)}. ")
            }
            if (lastOlderUser.isNotBlank()) {
                append("Last older user direction: ${compact(lastOlderUser, 120)}. ")
            }
            if (lastOlderAssistant.isNotBlank()) {
                append("Last older agent move: ${compact(lastOlderAssistant, 120)}.")
            }
        }.replace(Regex("\\s+"), " ").trim()

        val state = RollingSummaryState(
            senderPhone = senderPhone,
            summary = summary,
            coveredMessageCount = olderMessages.size,
            dominantTopics = dominantTopics,
            lastCoveredTimestamp = olderMessages.lastOrNull()?.timestamp ?: 0L,
            updatedAt = System.currentTimeMillis()
        )
        upsertState(state)

        return buildString {
            append("[LONG CHAT SUMMARY MEMORY]\n")
            append(summary)
            append("\nUse this summary to avoid re-asking settled points from older chat.\n")
        }.trimEnd()
    }

    private fun buildKnownFacts(userProfile: UserProfile?, needState: NeedDiscoveryState?): List<String> {
        val facts = mutableListOf<String>()
        userProfile?.name?.takeIf { it.isNotBlank() }?.let { facts += "name=$it" }
        userProfile?.email?.takeIf { it.isNotBlank() }?.let { facts += "email=$it" }
        userProfile?.address?.takeIf { it.isNotBlank() }?.let { facts += "address=$it" }
        needState?.knownValues
            ?.filterValues { it.isNotBlank() }
            ?.entries
            ?.take(4)
            ?.forEach { entry -> facts += "${entry.key}=${compact(entry.value, 40)}" }
        return facts.distinct().take(5)
    }

    private fun extractUserText(message: MessageEntity): String? {
        return when {
            message.outgoingMessage.isBlank() -> message.incomingMessage.trim()
            message.status.equals("RECEIVED", ignoreCase = true) -> message.incomingMessage.trim()
            else -> null
        }
    }

    private fun extractAssistantText(message: MessageEntity): String? {
        return message.outgoingMessage.trim().ifBlank { null }
    }

    private fun compact(text: String, max: Int): String {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        return if (normalized.length <= max) normalized else normalized.take(max) + "..."
    }

    private fun loadStates(): MutableList<RollingSummaryState> {
        val raw = prefs.getString(KEY_STATES, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            val out = mutableListOf<RollingSummaryState>()
            for (index in 0 until arr.length()) {
                val obj = arr.optJSONObject(index) ?: continue
                out += RollingSummaryState(
                    senderPhone = obj.optString("senderPhone"),
                    summary = obj.optString("summary"),
                    coveredMessageCount = obj.optInt("coveredMessageCount", 0),
                    dominantTopics = parseStringArray(obj.optJSONArray("dominantTopics")),
                    lastCoveredTimestamp = obj.optLong("lastCoveredTimestamp", 0L),
                    updatedAt = obj.optLong("updatedAt", 0L)
                )
            }
            out
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun upsertState(state: RollingSummaryState) {
        val all = loadStates()
        val index = all.indexOfFirst { it.senderPhone == state.senderPhone }
        if (index >= 0) all[index] = state else all += state
        while (all.size > MAX_STATES) {
            all.removeAt(0)
        }
        val arr = JSONArray()
        all.forEach { item ->
            arr.put(
                JSONObject()
                    .put("senderPhone", item.senderPhone)
                    .put("summary", item.summary)
                    .put("coveredMessageCount", item.coveredMessageCount)
                    .put("dominantTopics", JSONArray(item.dominantTopics))
                    .put("lastCoveredTimestamp", item.lastCoveredTimestamp)
                    .put("updatedAt", item.updatedAt)
            )
        }
        prefs.edit().putString(KEY_STATES, arr.toString()).apply()
    }

    private fun parseStringArray(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val out = mutableListOf<String>()
        for (index in 0 until array.length()) {
            val value = array.optString(index).trim()
            if (value.isNotBlank()) out += value
        }
        return out
    }

    data class RollingSummaryState(
        val senderPhone: String,
        val summary: String,
        val coveredMessageCount: Int,
        val dominantTopics: List<String>,
        val lastCoveredTimestamp: Long,
        val updatedAt: Long
    )

    companion object {
        private const val PREFS_NAME = "conversation_rolling_summary"
        private const val KEY_STATES = "states_json"
        private const val MAX_STATES = 500
    }
}