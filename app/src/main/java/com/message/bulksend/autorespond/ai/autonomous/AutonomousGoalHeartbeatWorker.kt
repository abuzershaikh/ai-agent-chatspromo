package com.message.bulksend.autorespond.ai.autonomous

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.message.bulksend.aiagent.tools.globalsender.GlobalSenderAIIntegration
import com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager
import com.message.bulksend.autorespond.aireply.AIProvider
import com.message.bulksend.autorespond.aireply.AIReplyManager
import com.message.bulksend.autorespond.aireply.AIService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.min

class AutonomousGoalHeartbeatWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val settings = AIAgentSettingsManager(appContext)
    private val queueStore = AutonomousGoalQueueStore(appContext)
    private val runtime = AutonomousGoalRuntime(appContext)
    private val paymentStatusWatcher = PaymentStatusEventWatcher.getInstance(appContext)

    override suspend fun doWork(): Result {
        if (!runtime.isContinuousEnabled()) {
            return Result.success()
        }

        return runLock.withLock {
            queueStore.recordHeartbeat()
            runCatching { paymentStatusWatcher.pollOnceIfEligible() }
                .onFailure {
                    android.util.Log.e(
                        "AutonomousHeartbeat",
                        "Payment status poll failed: ${it.message}"
                    )
                }

            val maxGoalsPerRun = settings.customTemplateAutonomousMaxGoalsPerRun
            val staleRunningMs = java.util.concurrent.TimeUnit.MINUTES.toMillis(5)
            val candidates =
                queueStore.getRunnableGoals(
                    now = System.currentTimeMillis(),
                    maxGoals = maxGoalsPerRun,
                    staleRunningMs = staleRunningMs
                )
            if (candidates.isEmpty()) return@withLock Result.success()

            val replyManager = AIReplyManager(applicationContext)
            val selectedProvider = replyManager.getSelectedProvider()
            val provider =
                if (selectedProvider == AIProvider.CHATSPROMO) AIProvider.GEMINI else selectedProvider

            val aiService = AIService(applicationContext)
            val sender = GlobalSenderAIIntegration(applicationContext)
            val maxAttempts = settings.customTemplateAutonomousMaxRounds.coerceAtLeast(1)

            candidates.forEach { item ->
                if (runtime.isGoalCompletedForSender(item.senderPhone)) {
                    queueStore.markCompleted(item.id, reason = "Goal already completed before round")
                    return@forEach
                }

                queueStore.markRunning(item.id)
                val currentRound = item.attempts + 1

                val prompt = buildHeartbeatPrompt(item, currentRound)
                val aiReplyResult =
                    runCatching {
                        aiService.generateReplyResult(
                            provider = provider,
                            message = prompt,
                            senderName = item.senderName.ifBlank { "User" },
                            senderPhone = item.senderPhone,
                            fromAutonomousRuntime = true
                        )
                    }.getOrElse { error ->
                        handleAttemptFailure(item, maxAttempts, "AI error: ${error.message ?: "unknown"}")
                        return@forEach
                    }

                if (runtime.isGoalCompletedForSender(item.senderPhone)) {
                    queueStore.markCompleted(item.id, reason = "Goal completed during autonomous turn")
                    return@forEach
                }

                val outgoingText = aiReplyResult.text.trim()
                if (outgoingText.isBlank()) {
                    handleAttemptFailure(item, maxAttempts, "AI returned empty text")
                    return@forEach
                }

                val stateHash = buildStateHash(item)
                val decision =
                    runtime.evaluateDispatch(
                        senderPhone = item.senderPhone,
                        stateHash = stateHash,
                        outgoingText = outgoingText
                    )
                if (!decision.canSend) {
                    queueStore.markWaiting(
                        id = item.id,
                        nextRunAt = decision.retryAt,
                        error = decision.reason
                    )
                    scheduleRetryKickAt(decision.retryAt)
                    return@forEach
                }

                val sendResult =
                    runCatching {
                        sender.sendText(
                            phoneNumber = item.senderPhone,
                            message = outgoingText
                        )
                    }.getOrNull()

                if (sendResult == null || !sendResult.success) {
                    val error = sendResult?.message ?: "Accessibility send failed"
                    handleAttemptFailure(item, maxAttempts, error)
                    return@forEach
                }

                runtime.markAutonomousOutbound(
                    senderPhone = item.senderPhone,
                    senderName = item.senderName,
                    stateHash = stateHash,
                    outgoingText = outgoingText
                )

                val completion =
                    runtime.evaluateGoalCompletion(
                        senderPhone = item.senderPhone,
                        goal = item.goal,
                        latestUserMessage = item.lastUserMessage,
                        latestAgentReply = outgoingText
                    )

                if (completion.isCompleted) {
                    queueStore.markCompleted(item.id, reason = completion.reason)
                    return@forEach
                }

                if (currentRound >= maxAttempts) {
                    queueStore.markFailed(
                        item.id,
                        "Max autonomous rounds reached before goal completion"
                    )
                    return@forEach
                }

                val nextRunAt = runtime.nextRunAtAfterOutbound(item.senderPhone)
                queueStore.markWaitingAfterOutbound(
                    id = item.id,
                    nextRunAt = nextRunAt,
                    outboundText = outgoingText,
                    toolActions = if (aiReplyResult.toolActions.isNotEmpty()) aiReplyResult.toolActions else extractActionHints(outgoingText)
                )
                scheduleRetryKickAt(nextRunAt)
            }

            Result.success()
        }
    }

    private fun handleAttemptFailure(item: AutonomousGoalQueueItem, maxAttempts: Int, error: String) {
        if (item.attempts + 1 >= maxAttempts) {
            queueStore.markFailed(item.id, error)
        } else {
            val retryAt = nextRunAt(item.attempts)
            queueStore.markWaiting(
                id = item.id,
                nextRunAt = retryAt,
                error = error
            )
            scheduleRetryKickAt(retryAt)
        }
    }

    private fun buildHeartbeatPrompt(item: AutonomousGoalQueueItem, currentRound: Int): String {
        val continuation = item.continuationState
        val previousOutbound = item.lastAgentMessage.trim()
        val recentInbound = continuation.recentInbound.takeLast(3).joinToString(" | ").ifBlank { "N/A" }
        val recentOutbound = continuation.recentOutbound.takeLast(3).joinToString(" | ").ifBlank { "N/A" }
        val recentActions = continuation.recentToolActions.takeLast(4).joinToString(", ").ifBlank { "none" }
        val continuationSummary = continuation.summary.trim().ifBlank { "No continuation summary yet" }

        return """
            [AUTONOMOUS_HEARTBEAT]
            Primary Goal: ${item.goal}
            Goal Round: $currentRound
            Last user message: ${item.lastUserMessage}
            Previous autonomous outbound: ${if (previousOutbound.isBlank()) "N/A" else previousOutbound}

            [CONTINUATION STATE]
            Summary: $continuationSummary
            Last decision: ${continuation.lastDecision.ifBlank { "N/A" }}
            Recent inbound context: $recentInbound
            Recent autonomous replies: $recentOutbound
            Recent tool/action hints: $recentActions

            Continue this chat in human style.
            Ask one relevant question if details are missing.
            If details are already known, guide to concrete next action.
            Do not repeat the exact previous outbound line.
            Keep reply concise, warm, and action-oriented.
        """.trimIndent()
    }

    private fun buildStateHash(item: AutonomousGoalQueueItem): String {
        val continuation = item.continuationState.summary.trim().lowercase()
        return "${item.senderPhone}|${item.goal.trim().lowercase()}|${item.lastUserMessage.trim().lowercase()}|$continuation"
            .replace(Regex("\\s+"), " ")
            .take(500)
    }

    private fun extractActionHints(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val matches = TOOL_LIKE_PATTERN.findAll(text)
            .map { it.value.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .toList()
        return matches.take(6)
    }

    private fun nextRunAt(previousAttempts: Int): Long {
        val minutes = min(60, (previousAttempts + 1) * 5)
        return System.currentTimeMillis() + minutes * 60_000L
    }

    private fun scheduleRetryKickAt(runAtMillis: Long) {
        val delayMillis = (runAtMillis - System.currentTimeMillis()).coerceAtLeast(5_000L)
        val delaySeconds = (delayMillis / 1000L).coerceAtLeast(5L)
        runtime.scheduleImmediateKick(delaySeconds = delaySeconds)
    }

    companion object {
        private val runLock = Mutex()
        private val TOOL_LIKE_PATTERN =
            Regex("\\[[A-Z][A-Z0-9_\\-: ]{2,}\\]", RegexOption.IGNORE_CASE)
    }
}



