package com.message.bulksend.autorespond.ai.context

import android.content.Context
import com.message.bulksend.autorespond.aireply.AIReplyResult
import com.message.bulksend.autorespond.aireply.ToolExecutionSignal
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class ToolOutcomeLearningManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recordIncomingEngagement(senderPhone: String) {
        if (senderPhone.isBlank()) return
        updateState(senderPhone) { current ->
            val successes = appendLesson(current.recentSuccesses, "User responded after previous follow-up")
            current.copy(
                recentSuccesses = successes,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    fun recordReplyResult(
        senderPhone: String,
        replyText: String,
        toolActions: List<String>,
        toolSignals: List<ToolExecutionSignal>
    ) {
        if (senderPhone.isBlank()) return
        val normalizedActions = toolActions.map { compact(it, 80) }.filter { it.isNotBlank() }.distinct()
        val replyHash = hash(replyText)

        updateState(senderPhone) { current ->
            var successes = current.recentSuccesses
            var failures = current.recentFailures
            var warnings = current.recentWarnings

            if (current.lastReplyHash.isNotBlank() && current.lastReplyHash == replyHash) {
                warnings = appendLesson(warnings, "Same reply phrasing was repeated recently")
            }

            if (normalizedActions.isNotEmpty() && normalizedActions == current.lastToolActions) {
                warnings = appendLesson(warnings, "Same tool action repeated: ${normalizedActions.joinToString(", ")}")
            }

            if (toolSignals.isEmpty() && normalizedActions.isNotEmpty()) {
                successes = appendMany(successes, normalizedActions.map { "Legacy action completed: $it" })
            }

            toolSignals.forEach { signal ->
                val label = buildSignalLabel(signal)
                when (signal.status.lowercase(Locale.ROOT)) {
                    "success" -> successes = appendLesson(successes, label)
                    "error" -> failures = appendLesson(failures, label)
                    "ignored" -> warnings = appendLesson(warnings, label)
                }
            }

            current.copy(
                recentSuccesses = successes,
                recentFailures = failures,
                recentWarnings = warnings,
                lastReplyHash = replyHash,
                lastToolActions = normalizedActions,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    fun buildContextSnippet(senderPhone: String): String {
        if (senderPhone.isBlank()) return ""
        val state = getState(senderPhone) ?: return ""
        if (state.recentSuccesses.isEmpty() && state.recentFailures.isEmpty() && state.recentWarnings.isEmpty()) {
            return ""
        }
        return buildString {
            append("[TOOL OUTCOME LEARNING]\n")
            if (state.recentSuccesses.isNotEmpty()) {
                append("Recent wins: ${state.recentSuccesses.joinToString(" | ")}\n")
            }
            if (state.recentFailures.isNotEmpty()) {
                append("Recent failures: ${state.recentFailures.joinToString(" | ")}\n")
            }
            if (state.recentWarnings.isNotEmpty()) {
                append("Avoid repeats: ${state.recentWarnings.joinToString(" | ")}\n")
            }
            append("Prefer actions that worked recently and avoid repeating the same failed or duplicate move.\n")
        }.trimEnd()
    }

    private fun buildSignalLabel(signal: ToolExecutionSignal): String {
        val action = compact(signal.actionName, 50)
        val detail = compact(signal.message, 70)
        return when (signal.status.lowercase(Locale.ROOT)) {
            "success" -> "Tool success: $action${if (detail.isNotBlank()) " ($detail)" else ""}"
            "error" -> "Tool failed: $action${if (detail.isNotBlank()) " ($detail)" else ""}"
            else -> "Tool note: $action${if (detail.isNotBlank()) " ($detail)" else ""}"
        }
    }

    private fun getState(senderPhone: String): ToolOutcomeLearningState? {
        return loadStates().firstOrNull { it.senderPhone == senderPhone }
    }

    private fun updateState(
        senderPhone: String,
        transform: (ToolOutcomeLearningState) -> ToolOutcomeLearningState
    ) {
        val all = loadStates()
        val current = all.firstOrNull { it.senderPhone == senderPhone } ?: ToolOutcomeLearningState(senderPhone = senderPhone)
        val updated = transform(current)
        val index = all.indexOfFirst { it.senderPhone == senderPhone }
        if (index >= 0) all[index] = updated else all += updated
        while (all.size > MAX_STATES) {
            all.removeAt(0)
        }
        saveStates(all)
    }

    private fun loadStates(): MutableList<ToolOutcomeLearningState> {
        val raw = prefs.getString(KEY_STATES, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            val out = mutableListOf<ToolOutcomeLearningState>()
            for (index in 0 until arr.length()) {
                val obj = arr.optJSONObject(index) ?: continue
                out += ToolOutcomeLearningState(
                    senderPhone = obj.optString("senderPhone"),
                    recentSuccesses = parseStringArray(obj.optJSONArray("recentSuccesses")),
                    recentFailures = parseStringArray(obj.optJSONArray("recentFailures")),
                    recentWarnings = parseStringArray(obj.optJSONArray("recentWarnings")),
                    lastReplyHash = obj.optString("lastReplyHash"),
                    lastToolActions = parseStringArray(obj.optJSONArray("lastToolActions")),
                    updatedAt = obj.optLong("updatedAt", 0L)
                )
            }
            out
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun saveStates(states: List<ToolOutcomeLearningState>) {
        val arr = JSONArray()
        states.forEach { state ->
            arr.put(
                JSONObject()
                    .put("senderPhone", state.senderPhone)
                    .put("recentSuccesses", JSONArray(state.recentSuccesses))
                    .put("recentFailures", JSONArray(state.recentFailures))
                    .put("recentWarnings", JSONArray(state.recentWarnings))
                    .put("lastReplyHash", state.lastReplyHash)
                    .put("lastToolActions", JSONArray(state.lastToolActions))
                    .put("updatedAt", state.updatedAt)
            )
        }
        prefs.edit().putString(KEY_STATES, arr.toString()).apply()
    }

    private fun appendMany(existing: List<String>, additions: List<String>): List<String> {
        var updated = existing
        additions.forEach { addition -> updated = appendLesson(updated, addition) }
        return updated
    }

    private fun appendLesson(existing: List<String>, lesson: String): List<String> {
        val normalized = compact(lesson, 120)
        if (normalized.isBlank()) return existing
        val updated = existing.toMutableList()
        updated.removeAll { it.equals(normalized, ignoreCase = true) }
        updated += normalized
        return updated.takeLast(MAX_LESSONS)
    }

    private fun compact(text: String, max: Int): String {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        return if (normalized.length <= max) normalized else normalized.take(max) + "..."
    }

    private fun hash(text: String): String {
        return compact(text.lowercase(Locale.ROOT), 220)
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

    data class ToolOutcomeLearningState(
        val senderPhone: String,
        val recentSuccesses: List<String> = emptyList(),
        val recentFailures: List<String> = emptyList(),
        val recentWarnings: List<String> = emptyList(),
        val lastReplyHash: String = "",
        val lastToolActions: List<String> = emptyList(),
        val updatedAt: Long = 0L
    )

    companion object {
        private const val PREFS_NAME = "tool_outcome_learning"
        private const val KEY_STATES = "states_json"
        private const val MAX_STATES = 500
        private const val MAX_LESSONS = 4
    }
}