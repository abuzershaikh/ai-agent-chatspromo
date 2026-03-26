package com.message.bulksend.autorespond.aireply

data class ToolExecutionSignal(
    val actionName: String,
    val status: String,
    val message: String = "",
    val source: String = "unknown",
    val usedFallback: Boolean = false,
    val retryable: Boolean = false,
    val attempts: Int = 1
)

data class AIReplyResult(
    val text: String,
    val detectedIntent: String = "UNKNOWN",
    val toolActions: List<String> = emptyList(),
    val toolSignals: List<ToolExecutionSignal> = emptyList(),
    val usedNativeToolCalling: Boolean = false,
    val nativeFallbackUsed: Boolean = false,
    val shouldStopChain: Boolean = false
)