package com.message.bulksend.autorespond.ai.autonomous

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AutonomousUserStateStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun getState(senderPhone: String): AutonomousUserState? {
        if (senderPhone.isBlank()) return null
        return loadStates().firstOrNull { it.senderPhone == senderPhone }
    }

    @Synchronized
    fun upsertState(state: AutonomousUserState) {
        if (state.senderPhone.isBlank()) return
        val states = loadStates().toMutableList()
        val index = states.indexOfFirst { it.senderPhone == state.senderPhone }
        val normalized = state.copy(updatedAt = System.currentTimeMillis())
        if (index >= 0) {
            states[index] = normalized
        } else {
            states += normalized
        }
        pruneStates(states)
        saveStates(states)
    }

    @Synchronized
    fun updateState(senderPhone: String, transform: (AutonomousUserState?) -> AutonomousUserState) {
        val updated = transform(getState(senderPhone))
        upsertState(updated)
    }

    private fun pruneStates(states: MutableList<AutonomousUserState>) {
        val now = System.currentTimeMillis()
        states.removeAll { state ->
            state.updatedAt > 0L && (now - state.updatedAt) > STATE_RETENTION_MS
        }

        if (states.size <= MAX_STATES) return

        states.sortBy { it.updatedAt }
        while (states.size > MAX_STATES) {
            states.removeAt(0)
        }
    }

    @Synchronized
    private fun loadStates(): List<AutonomousUserState> {
        val raw = prefs.getString(KEY_STATES, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            val out = mutableListOf<AutonomousUserState>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                out +=
                    AutonomousUserState(
                        senderPhone = obj.optString("senderPhone"),
                        senderName = obj.optString("senderName"),
                        lastInboundAt = obj.optLong("lastInboundAt", 0L),
                        lastOutboundAt = obj.optLong("lastOutboundAt", 0L),
                        lastAutonomousAt = obj.optLong("lastAutonomousAt", 0L),
                        nudgesToday = obj.optInt("nudgesToday", 0),
                        nudgeDayKey = obj.optString("nudgeDayKey"),
                        pauseUntil = obj.optLong("pauseUntil", 0L),
                        lastStateHash = obj.optString("lastStateHash"),
                        lastMessageHash = obj.optString("lastMessageHash"),
                        lastMessageAt = obj.optLong("lastMessageAt", 0L),
                        updatedAt = obj.optLong("updatedAt", 0L)
                    )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    private fun saveStates(states: List<AutonomousUserState>) {
        val arr = JSONArray()
        states.forEach { state ->
            arr.put(
                JSONObject()
                    .put("senderPhone", state.senderPhone)
                    .put("senderName", state.senderName)
                    .put("lastInboundAt", state.lastInboundAt)
                    .put("lastOutboundAt", state.lastOutboundAt)
                    .put("lastAutonomousAt", state.lastAutonomousAt)
                    .put("nudgesToday", state.nudgesToday)
                    .put("nudgeDayKey", state.nudgeDayKey)
                    .put("pauseUntil", state.pauseUntil)
                    .put("lastStateHash", state.lastStateHash)
                    .put("lastMessageHash", state.lastMessageHash)
                    .put("lastMessageAt", state.lastMessageAt)
                    .put("updatedAt", state.updatedAt)
            )
        }
        prefs.edit().putString(KEY_STATES, arr.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "ai_agent_autonomous_state"
        private const val KEY_STATES = "states_json"
        private const val MAX_STATES = 2000
        private val STATE_RETENTION_MS = TimeUnit.DAYS.toMillis(30)
    }
}
