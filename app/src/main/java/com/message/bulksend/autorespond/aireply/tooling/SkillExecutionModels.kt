package com.message.bulksend.autorespond.aireply.tooling

import org.json.JSONArray
import org.json.JSONObject

data class SkillExecutionPolicy(
    val timeoutMs: Long = 12_000L,
    val maxRetries: Int = 0
)

data class SkillExecutionResult(
    val status: String,
    val message: String,
    val payload: JSONObject = JSONObject(),
    val toolActions: List<String> = emptyList(),
    val shouldStopChain: Boolean = false,
    val retryable: Boolean = false,
    val usedFallback: Boolean = false,
    val attempts: Int = 1,
    val executionMillis: Long = 0L
) {
    fun toJsonString(): String {
        return JSONObject()
            .put("status", status)
            .put("message", message)
            .put("payload", payload)
            .put("tool_actions", JSONArray(toolActions))
            .put("should_stop_chain", shouldStopChain)
            .put("retryable", retryable)
            .put("used_fallback", usedFallback)
            .put("attempts", attempts)
            .put("execution_ms", executionMillis)
            .toString()
    }

    companion object {
        fun success(
            message: String,
            payload: JSONObject = JSONObject(),
            toolActions: List<String> = emptyList(),
            shouldStopChain: Boolean = false,
            usedFallback: Boolean = false,
            attempts: Int = 1,
            executionMillis: Long = 0L
        ): SkillExecutionResult {
            return SkillExecutionResult(
                status = "success",
                message = message,
                payload = payload,
                toolActions = toolActions,
                shouldStopChain = shouldStopChain,
                retryable = false,
                usedFallback = usedFallback,
                attempts = attempts,
                executionMillis = executionMillis
            )
        }

        fun ignored(
            message: String,
            payload: JSONObject = JSONObject(),
            attempts: Int = 1,
            executionMillis: Long = 0L
        ): SkillExecutionResult {
            return SkillExecutionResult(
                status = "ignored",
                message = message,
                payload = payload,
                retryable = false,
                attempts = attempts,
                executionMillis = executionMillis
            )
        }

        fun error(
            message: String,
            retryable: Boolean,
            payload: JSONObject = JSONObject(),
            attempts: Int = 1,
            executionMillis: Long = 0L,
            usedFallback: Boolean = false
        ): SkillExecutionResult {
            return SkillExecutionResult(
                status = "error",
                message = message,
                payload = payload,
                retryable = retryable,
                usedFallback = usedFallback,
                attempts = attempts,
                executionMillis = executionMillis
            )
        }
    }
}
