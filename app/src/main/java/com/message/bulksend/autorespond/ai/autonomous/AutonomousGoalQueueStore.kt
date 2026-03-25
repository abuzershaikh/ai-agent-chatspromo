package com.message.bulksend.autorespond.ai.autonomous

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class AutonomousGoalQueueStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun enqueueOrRefreshGoal(
        senderPhone: String,
        senderName: String,
        goal: String,
        lastUserMessage: String,
        dedupeKey: String,
        maxQueue: Int
    ): AutonomousGoalQueueItem {
        val now = System.currentTimeMillis()
        val items = loadItems().toMutableList()

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
                    nextRunAt = now,
                    updatedAt = now,
                    lastError = ""
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
                    updatedAt = now
                )
            }

        if (existingIndex >= 0) {
            items[existingIndex] = updated
        } else {
            items += updated
        }

        enforceQueueBounds(items, maxQueue)
        saveItems(items)
        return updated
    }

    @Synchronized
    fun getRunnableGoals(now: Long, maxGoals: Int): List<AutonomousGoalQueueItem> {
        val cap = maxGoals.coerceAtLeast(1)
        return loadItems()
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
            item.copy(
                status = AutonomousGoalQueueItem.STATUS_RUNNING,
                attempts = item.attempts + 1,
                updatedAt = System.currentTimeMillis(),
                lastError = ""
            )
        }
    }

    @Synchronized
    fun markWaiting(id: String, nextRunAt: Long, error: String = "") {
        update(id) { item ->
            item.copy(
                status = AutonomousGoalQueueItem.STATUS_WAITING,
                nextRunAt = nextRunAt,
                updatedAt = System.currentTimeMillis(),
                lastError = error.trim()
            )
        }
        if (error.isNotBlank()) setLastError(error)
    }

    @Synchronized
    fun markCompleted(id: String) {
        update(id) { item ->
            item.copy(
                status = AutonomousGoalQueueItem.STATUS_COMPLETED,
                nextRunAt = Long.MAX_VALUE,
                updatedAt = System.currentTimeMillis(),
                lastError = ""
            )
        }
    }

    @Synchronized
    fun markFailed(id: String, error: String) {
        update(id) { item ->
            item.copy(
                status = AutonomousGoalQueueItem.STATUS_FAILED,
                nextRunAt = Long.MAX_VALUE,
                updatedAt = System.currentTimeMillis(),
                lastError = error.trim()
            )
        }
        setLastError(error)
    }

    @Synchronized
    fun queueSize(): Int {
        return loadItems().count {
            it.status == AutonomousGoalQueueItem.STATUS_QUEUED ||
                it.status == AutonomousGoalQueueItem.STATUS_WAITING ||
                it.status == AutonomousGoalQueueItem.STATUS_RUNNING
        }
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

    private fun enforceQueueBounds(items: MutableList<AutonomousGoalQueueItem>, maxQueue: Int) {
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
                out +=
                    AutonomousGoalQueueItem(
                        id = obj.optString("id"),
                        senderPhone = obj.optString("senderPhone"),
                        senderName = obj.optString("senderName"),
                        goal = obj.optString("goal"),
                        lastUserMessage = obj.optString("lastUserMessage"),
                        status = obj.optString("status", AutonomousGoalQueueItem.STATUS_QUEUED),
                        attempts = obj.optInt("attempts", 0),
                        nextRunAt = obj.optLong("nextRunAt", 0L),
                        dedupeKey = obj.optString("dedupeKey"),
                        createdAt = obj.optLong("createdAt", 0L),
                        updatedAt = obj.optLong("updatedAt", 0L),
                        lastError = obj.optString("lastError")
                    )
            }
            out
        } catch (_: Exception) {
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
            )
        }
        prefs.edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "ai_agent_autonomous_queue"
        private const val KEY_ITEMS = "items_json"
        private const val KEY_LAST_HEARTBEAT_AT = "last_heartbeat_at"
        private const val KEY_LAST_ERROR = "last_error"
    }
}
