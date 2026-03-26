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
                    queueStore.markCompleted(item.id)
                    return@forEach
                }

                queueStore.markRunning(item.id)
                val currentRound = item.attempts + 1

                val prompt = buildHeartbeatPrompt(item, currentRound)
                val aiReply =
                    runCatching {
                        aiService.generateReply(
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
                    queueStore.markCompleted(item.id)
                    return@forEach
                }

                val outgoingText = aiReply.trim()
                if (outgoingText.isBlank()) {
                    handleAttemptFailure(item, maxAttempts, "AI returned empty text")
                    return@forEach
                }

                val stateHash = buildStateHash(item)
                val decision = runtime.evaluateDispatch(
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
                    queueStore.markCompleted(item.id)
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
                    outboundText = outgoingText
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
        val previousOutbound = item.lastAgentMessage.trim()
        return """
            [AUTONOMOUS_HEARTBEAT]
            Primary Goal: ${item.goal}
            Goal Round: $currentRound
            Last user message: ${item.lastUserMessage}
            Previous autonomous outbound: ${if (previousOutbound.isBlank()) "N/A" else previousOutbound}

            Continue this chat in human style.
            Ask one relevant question if details are missing.
            If details are already known, guide to concrete next action.
            Do not repeat the exact previous outbound line.
            Keep reply concise, warm, and action-oriented.
        """.trimIndent()
    }

    private fun buildStateHash(item: AutonomousGoalQueueItem): String {
        return "${item.senderPhone}|${item.goal.trim().lowercase()}|${item.lastUserMessage.trim().lowercase()}"
            .replace(Regex("\\s+"), " ")
            .take(320)
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
    }
}





