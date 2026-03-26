package com.message.bulksend.autorespond.ai.autonomous

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class AutonomousGoalQueueStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun enqueueOrRefreshGoal(
        senderPhone: String,
        senderName: String,
        goal: String,
        lastUserMessage: String,
        dedupeKey: String,
        maxQueue: Int,
        maxQueuePerSender: Int,
        replaceExistingActiveForSender: Boolean = false,
        preserveDedupePrefixes: Set<String> = emptySet()
    ): AutonomousGoalQueueItem {
        val now = System.currentTimeMillis()
        val items = loadItems().toMutableList()

        if (replaceExistingActiveForSender && senderPhone.isNotBlank()) {
            items.removeAll { item ->
                isActiveStatus(item.status) &&
                    item.senderPhone == senderPhone &&
                    !item.dedupeKey.equals(dedupeKey, ignoreCase = true) &&
                    preserveDedupePrefixes.none { prefix ->
                        prefix.isNotBlank() && item.dedupeKey.startsWith(prefix, ignoreCase = true)
                    }
            }
        }

        val existingIndex =
            items.indexOfFirst {
                it.dedupeKey.equals(dedupeKey, ignoreCase = true) &&
                    it.status != AutonomousGoalQueueItem.STATUS_COMPLETED &&
                    it.status != AutonomousGoalQueueItem.STATUS_FAILED
            }

        val updated =
            if (existingIndex >= 0) {
                val current = items[existingIndex]
                current.copy(
                    senderName = senderName.ifBlank { current.senderName },
                    goal = goal,
                    lastUserMessage = lastUserMessage,
                    status = AutonomousGoalQueueItem.STATUS_WAITING,
                    attempts = 0,
                    nextRunAt = now,
                    updatedAt = now,
                    lastError = "",
                    lastAgentMessage = "",
                    continuationState =
                        updateContinuationState(
                            current = current.continuationState,
                            inbound = lastUserMessage,
                            outbound = "",
                            decision = "goal_refreshed",
                            updatedAt = now,
                            resetRounds = true
                        )
                )
            } else {
                AutonomousGoalQueueItem(
                    id = UUID.randomUUID().toString(),
                    senderPhone = senderPhone,
                    senderName = senderName,
                    goal = goal,
                    lastUserMessage = lastUserMessage,
                    status = AutonomousGoalQueueItem.STATUS_QUEUED,
                    attempts = 0,
                    nextRunAt = now,
                    dedupeKey = dedupeKey,
                    createdAt = now,
                    updatedAt = now,
                    continuationState = initialContinuationState(lastUserMessage, now)
                )
            }

        if (existingIndex >= 0) {
            items[existingIndex] = updated
        } else {
            items += updated
        }

        enforceQueueBounds(items, maxQueue, maxQueuePerSender, senderPhone)
        saveItems(items)
        return updated
    }

    @Synchronized
    fun cancelActiveGoalsForSender(
        senderPhone: String,
        preserveDedupePrefixes: Set<String> = emptySet()
    ): Int {
        val sender = senderPhone.trim()
        if (sender.isBlank()) return 0

        val items = loadItems().toMutableList()
        val before = items.size
        items.removeAll { item ->
            isActiveStatus(item.status) &&
                item.senderPhone == sender &&
                preserveDedupePrefixes.none { prefix ->
                    prefix.isNotBlank() && item.dedupeKey.startsWith(prefix, ignoreCase = true)
                }
        }

        val removed = before - items.size
        if (removed > 0) {
            saveItems(items)
        }
        return removed
    }

    @Synchronized
    fun getRunnableGoals(
        now: Long,
        maxGoals: Int,
        staleRunningMs: Long = TimeUnit.MINUTES.toMillis(5)
    ): List<AutonomousGoalQueueItem> {
        val cap = maxGoals.coerceAtLeast(1)
        val items = loadItems().toMutableList()
        val staleBefore = now - staleRunningMs.coerceAtLeast(TimeUnit.MINUTES.toMillis(1))
        var changed = false

        items.forEachIndexed { index, item ->
            if (
                item.status == AutonomousGoalQueueItem.STATUS_RUNNING &&
                    item.updatedAt <= staleBefore
            ) {
                items[index] =
                    item.copy(
                        status = AutonomousGoalQueueItem.STATUS_WAITING,
                        nextRunAt = now,
                        updatedAt = now,
                        lastError = item.lastError.ifBlank { "Recovered stale running goal" },
                        continuationState =
                            updateContinuationState(
                                current = item.continuationState,
                                decision = "stale_run_recovered",
                                updatedAt = now
                            )
                    )
                changed = true
            }
        }

        if (changed) {
            saveItems(items)
        }

        return items
            .filter {
                (it.status == AutonomousGoalQueueItem.STATUS_QUEUED ||
                    it.status == AutonomousGoalQueueItem.STATUS_WAITING) &&
                    it.nextRunAt <= now
            }
            .sortedBy { it.nextRunAt }
            .take(cap)
    }

    @Synchronized
    fun markRunning(id: String) {
        update(id) { item ->
            val now = System.currentTimeMillis()
            val currentRound = item.attempts + 1
            item.copy(
                status = AutonomousGoalQueueItem.STATUS_RUNNING,
                attempts = currentRound,
                updatedAt = now,
                lastError = "",
                continuationState =
                    updateContinuationState(
                        current = item.continuationState,
                        inbound = item.lastUserMessage,
                        decision = "running_round_$currentRound",
                        updatedAt = now,
                        incrementRound = true
                    )
            )
        }
    }

    @Synchronized
    fun markWaiting(id: String, nextRunAt: Long, error: String = "") {
        update(id) { item ->
            val now = System.currentTimeMillis()
            val normalizedError = error.trim()
            item.copy(
                status = AutonomousGoalQueueItem.STATUS_WAITING,
                nextRunAt = nextRunAt,
                updatedAt = now,
                lastError = normalizedError,
                continuationState =
                    updateContinuationState(
                        current = item.continuationState,
                        inbound = item.lastUserMessage,
                        outbound = item.lastAgentMessage,
                        decision =
                            if (normalizedError.isBlank()) {
                                "waiting"
                            } else {
                                "waiting_error:${normalizeSnippet(normalizedError)}"
                            },
                        updatedAt = now
                    )
            )
        }
        if (error.isNotBlank()) setLastError(error)
    }

    @Synchronized
    fun markWaitingAfterOutbound(
        id: String,
        nextRunAt: Long,
        outboundText: String,
        toolActions: List<String> = emptyList()
    ) {
        update(id) { item ->
            val now = System.currentTimeMillis()
            item.copy(
                status = AutonomousGoalQueueItem.STATUS_WAITING,
                nextRunAt = nextRunAt,
                updatedAt = now,
                lastError = "",
                lastAgentMessage = outboundText.trim(),
                continuationState =
                    updateContinuationState(
                        current = item.continuationState,
                        inbound = item.lastUserMessage,
                        outbound = outboundText,
                        toolActions = toolActions,
                        decision = "waiting_after_outbound",
                        updatedAt = now
                    )
            )
        }
    }

    @Synchronized
    fun markCompleted(id: String, reason: String = "Goal completed") {
        update(id) { item ->
            val now = System.currentTimeMillis()
            item.copy(
                status = AutonomousGoalQueueItem.STATUS_COMPLETED,
                nextRunAt = Long.MAX_VALUE,
                updatedAt = now,
                lastError = "",
                continuationState =
                    updateContinuationState(
                        current = item.continuationState,
                        inbound = item.lastUserMessage,
                        outbound = item.lastAgentMessage,
                        decision = "completed:${normalizeSnippet(reason)}",
                        updatedAt = now
                    )
            )
        }
    }

    @Synchronized
    fun markFailed(id: String, error: String) {
        update(id) { item ->
            val now = System.currentTimeMillis()
            val normalizedError = error.trim()
            item.copy(
                status = AutonomousGoalQueueItem.STATUS_FAILED,
                nextRunAt = Long.MAX_VALUE,
                updatedAt = now,
                lastError = normalizedError,
                continuationState =
                    updateContinuationState(
                        current = item.continuationState,
                        inbound = item.lastUserMessage,
                        outbound = item.lastAgentMessage,
                        decision = "failed:${normalizeSnippet(normalizedError)}",
                        updatedAt = now
                    )
            )
        }
        setLastError(error)
    }

    @Synchronized
    fun queueSize(): Int {
        return loadItems().count { isActiveStatus(it.status) }
    }

    @Synchronized
    fun recordHeartbeat(at: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_HEARTBEAT_AT, at).apply()
    }

    fun getLastHeartbeatAt(): Long = prefs.getLong(KEY_LAST_HEARTBEAT_AT, 0L)

    fun getLastError(): String = prefs.getString(KEY_LAST_ERROR, "") ?: ""

    private fun setLastError(error: String) {
        prefs.edit().putString(KEY_LAST_ERROR, error.trim()).apply()
    }

    private fun enforceQueueBounds(
        items: MutableList<AutonomousGoalQueueItem>,
        maxQueue: Int,
        maxQueuePerSender: Int,
        preferredSenderPhone: String
    ) {
        pruneOldTerminalItems(items)
        enforcePerSenderQueueBounds(items, maxQueuePerSender, preferredSenderPhone)

        val cap = maxQueue.coerceAtLeast(1)
        if (items.size <= cap) return

        val removable =
            items
                .sortedWith(
                    compareBy<AutonomousGoalQueueItem> {
                        when (it.status) {
                            AutonomousGoalQueueItem.STATUS_COMPLETED -> 0
                            AutonomousGoalQueueItem.STATUS_FAILED -> 1
                            else -> 2
                        }
                    }.thenBy { it.updatedAt }
                )
                .take((items.size - cap).coerceAtLeast(0))
                .map { it.id }
                .toSet()

        if (removable.isNotEmpty()) {
            items.removeAll { it.id in removable }
        }

        if (items.size > cap) {
            items.sortBy { it.updatedAt }
            while (items.size > cap) {
                items.removeAt(0)
            }
        }
    }

    private fun pruneOldTerminalItems(items: MutableList<AutonomousGoalQueueItem>) {
        val now = System.currentTimeMillis()
        items.removeAll { item ->
            (item.status == AutonomousGoalQueueItem.STATUS_COMPLETED ||
                item.status == AutonomousGoalQueueItem.STATUS_FAILED) &&
                (now - item.updatedAt) > TERMINAL_RETENTION_MS
        }
    }

    private fun enforcePerSenderQueueBounds(
        items: MutableList<AutonomousGoalQueueItem>,
        maxQueuePerSender: Int,
        preferredSenderPhone: String
    ) {
        val cap = maxQueuePerSender.coerceAtLeast(1)
        val sender = preferredSenderPhone.trim()
        if (sender.isBlank()) return

        while (items.count { it.senderPhone == sender } > cap) {
            val removable =
                items
                    .filter { it.senderPhone == sender }
                    .sortedWith(
                        compareBy<AutonomousGoalQueueItem> {
                            when (it.status) {
                                AutonomousGoalQueueItem.STATUS_COMPLETED -> 0
                                AutonomousGoalQueueItem.STATUS_FAILED -> 1
                                else -> 2
                            }
                        }.thenBy { it.updatedAt }
                    )
                    .firstOrNull()
                    ?: break
            items.removeAll { it.id == removable.id }
        }
    }

    private fun isActiveStatus(status: String): Boolean {
        return status == AutonomousGoalQueueItem.STATUS_QUEUED ||
            status == AutonomousGoalQueueItem.STATUS_WAITING ||
            status == AutonomousGoalQueueItem.STATUS_RUNNING
    }

    @Synchronized
    private fun update(id: String, transform: (AutonomousGoalQueueItem) -> AutonomousGoalQueueItem) {
        val items = loadItems().toMutableList()
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return
        items[index] = transform(items[index])
        saveItems(items)
    }

    @Synchronized
    private fun loadItems(): List<AutonomousGoalQueueItem> {
        val raw = prefs.getString(KEY_ITEMS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            val out = mutableListOf<AutonomousGoalQueueItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val updatedAt = obj.optLong("updatedAt", 0L)
                val lastUserMessage = obj.optString("lastUserMessage")
                val lastAgentMessage = obj.optString("lastAgentMessage")
                out +=
                    AutonomousGoalQueueItem(
                        id = obj.optString("id"),
                        senderPhone = obj.optString("senderPhone"),
                        senderName = obj.optString("senderName"),
                        goal = obj.optString("goal"),
                        lastUserMessage = lastUserMessage,
                        status = obj.optString("status", AutonomousGoalQueueItem.STATUS_QUEUED),
                        attempts = obj.optInt("attempts", 0),
                        nextRunAt = obj.optLong("nextRunAt", 0L),
                        dedupeKey = obj.optString("dedupeKey"),
                        createdAt = obj.optLong("createdAt", 0L),
                        updatedAt = updatedAt,
                        lastError = obj.optString("lastError"),
                        lastAgentMessage = lastAgentMessage,
                        continuationState =
                            parseContinuationState(
                                obj = obj.optJSONObject("continuationState"),
                                fallbackInbound = lastUserMessage,
                                fallbackOutbound = lastAgentMessage,
                                updatedAt = updatedAt
                            )
                    )
            }
            out
        } catch (error: Exception) {
            setLastError("Queue parse error: ${error.message}")
            emptyList()
        }
    }

    @Synchronized
    private fun saveItems(items: List<AutonomousGoalQueueItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("senderPhone", item.senderPhone)
                    .put("senderName", item.senderName)
                    .put("goal", item.goal)
                    .put("lastUserMessage", item.lastUserMessage)
                    .put("status", item.status)
                    .put("attempts", item.attempts)
                    .put("nextRunAt", item.nextRunAt)
                    .put("dedupeKey", item.dedupeKey)
                    .put("createdAt", item.createdAt)
                    .put("updatedAt", item.updatedAt)
                    .put("lastError", item.lastError)
                    .put("lastAgentMessage", item.lastAgentMessage)
                    .put("continuationState", buildContinuationJson(item.continuationState))
            )
        }
        prefs.edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    private fun parseContinuationState(
        obj: JSONObject?,
        fallbackInbound: String,
        fallbackOutbound: String,
        updatedAt: Long
    ): AutonomousContinuationState {
        if (obj == null) {
            return initialContinuationState(fallbackInbound, updatedAt).let { base ->
                if (fallbackOutbound.isBlank()) {
                    base
                } else {
                    updateContinuationState(
                        current = base,
                        outbound = fallbackOutbound,
                        decision = "legacy_loaded",
                        updatedAt = updatedAt
                    )
                }
            }
        }

        return AutonomousContinuationState(
            roundsCompleted = obj.optInt("roundsCompleted", 0),
            summary = obj.optString("summary"),
            recentInbound =
                arrayToStrings(obj.optJSONArray("recentInbound")).ifEmpty {
                    if (fallbackInbound.isBlank()) emptyList() else listOf(fallbackInbound.trim())
                },
            recentOutbound =
                arrayToStrings(obj.optJSONArray("recentOutbound")).ifEmpty {
                    if (fallbackOutbound.isBlank()) emptyList() else listOf(fallbackOutbound.trim())
                },
            recentToolActions = arrayToStrings(obj.optJSONArray("recentToolActions")),
            lastDecision = obj.optString("lastDecision"),
            updatedAt = obj.optLong("updatedAt", updatedAt)
        )
    }

    private fun buildContinuationJson(state: AutonomousContinuationState): JSONObject {
        return JSONObject()
            .put("roundsCompleted", state.roundsCompleted)
            .put("summary", state.summary)
            .put("recentInbound", JSONArray(state.recentInbound))
            .put("recentOutbound", JSONArray(state.recentOutbound))
            .put("recentToolActions", JSONArray(state.recentToolActions))
            .put("lastDecision", state.lastDecision)
            .put("updatedAt", state.updatedAt)
    }

    private fun initialContinuationState(lastUserMessage: String, at: Long): AutonomousContinuationState {
        return updateContinuationState(
            current = AutonomousContinuationState(),
            inbound = lastUserMessage,
            decision = "queued",
            updatedAt = at,
            resetRounds = true
        )
    }

    private fun updateContinuationState(
        current: AutonomousContinuationState,
        inbound: String? = null,
        outbound: String? = null,
        toolActions: List<String> = emptyList(),
        decision: String? = null,
        updatedAt: Long,
        incrementRound: Boolean = false,
        resetRounds: Boolean = false
    ): AutonomousContinuationState {
        val inboundMessages = appendRecent(current.recentInbound, inbound, MAX_RECENT_INBOUND)
        val outboundMessages = appendRecent(current.recentOutbound, outbound, MAX_RECENT_OUTBOUND)
        val actions = appendActionHints(current.recentToolActions, toolActions)

        val rounds =
            when {
                resetRounds -> 0
                incrementRound -> current.roundsCompleted + 1
                else -> current.roundsCompleted
            }
        val normalizedDecision = decision?.trim().orEmpty().ifBlank { current.lastDecision }

        return AutonomousContinuationState(
            roundsCompleted = rounds,
            summary = buildContinuationSummary(rounds, inboundMessages, outboundMessages, actions, normalizedDecision),
            recentInbound = inboundMessages,
            recentOutbound = outboundMessages,
            recentToolActions = actions,
            lastDecision = normalizedDecision,
            updatedAt = updatedAt
        )
    }

    private fun buildContinuationSummary(
        rounds: Int,
        inbound: List<String>,
        outbound: List<String>,
        actions: List<String>,
        decision: String
    ): String {
        val latestInbound = inbound.lastOrNull()?.let(::normalizeSnippet) ?: "N/A"
        val latestOutbound = outbound.lastOrNull()?.let(::normalizeSnippet) ?: "N/A"
        val actionSummary = if (actions.isEmpty()) "none" else actions.joinToString(", ") { normalizeSnippet(it) }
        val normalizedDecision = decision.trim().ifBlank { "in_progress" }
        return "rounds=$rounds; latest_user=$latestInbound; latest_agent=$latestOutbound; actions=$actionSummary; state=${normalizeSnippet(normalizedDecision)}"
            .take(600)
    }

    private fun appendRecent(existing: List<String>, candidate: String?, max: Int): List<String> {
        val normalized = candidate?.trim().orEmpty()
        if (normalized.isBlank()) return existing.takeLast(max)

        val list = existing.toMutableList()
        val last = list.lastOrNull()
        if (last == null || !last.equals(normalized, ignoreCase = true)) {
            list += normalized
        }
        return list.takeLast(max)
    }

    private fun appendActionHints(existing: List<String>, newActions: List<String>): List<String> {
        if (newActions.isEmpty()) return existing.takeLast(MAX_RECENT_ACTIONS)
        var updated = existing.toMutableList()
        newActions.forEach { action ->
            val normalized = normalizeSnippet(action)
            if (normalized.isBlank()) return@forEach
            if (updated.none { it.equals(normalized, ignoreCase = true) }) {
                updated += normalized
            }
        }
        if (updated.size > MAX_RECENT_ACTIONS) {
            updated = updated.takeLast(MAX_RECENT_ACTIONS).toMutableList()
        }
        return updated
    }

    private fun normalizeSnippet(text: String): String {
        return text.trim().replace(Regex("\\s+"), " ").take(160)
    }

    private fun arrayToStrings(array: JSONArray?): List<String> {
        if (array == null || array.length() == 0) return emptyList()
        val values = mutableListOf<String>()
        for (index in 0 until array.length()) {
            val value = array.optString(index).trim()
            if (value.isNotBlank()) {
                values += value
            }
        }
        return values
    }

    companion object {
        private const val PREFS_NAME = "ai_agent_autonomous_queue"
        private const val KEY_ITEMS = "items_json"
        private const val KEY_LAST_HEARTBEAT_AT = "last_heartbeat_at"
        private const val KEY_LAST_ERROR = "last_error"
        private const val MAX_RECENT_INBOUND = 4
        private const val MAX_RECENT_OUTBOUND = 4
        private const val MAX_RECENT_ACTIONS = 6
        private val TERMINAL_RETENTION_MS = TimeUnit.DAYS.toMillis(2)
    }
}
