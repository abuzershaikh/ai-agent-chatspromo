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

    override suspend fun doWork(): Result {
        if (!runtime.isContinuousEnabled()) {
            return Result.success()
        }

        return runLock.withLock {
            queueStore.recordHeartbeat()

            val maxGoalsPerRun = settings.customTemplateAutonomousMaxGoalsPerRun
            val candidates = queueStore.getRunnableGoals(System.currentTimeMillis(), maxGoalsPerRun)
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

                val prompt = buildHeartbeatPrompt(item)
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
                queueStore.markCompleted(item.id)
            }

            Result.success()
        }
    }

    private fun handleAttemptFailure(item: AutonomousGoalQueueItem, maxAttempts: Int, error: String) {
        if (item.attempts + 1 >= maxAttempts) {
            queueStore.markFailed(item.id, error)
        } else {
            queueStore.markWaiting(
                id = item.id,
                nextRunAt = nextRunAt(item.attempts),
                error = error
            )
        }
    }

    private fun buildHeartbeatPrompt(item: AutonomousGoalQueueItem): String {
        return """
            [AUTONOMOUS_HEARTBEAT]
            Primary Goal: ${item.goal}
            Last user message: ${item.lastUserMessage}

            Continue this chat in human style.
            Ask one relevant question if details are missing.
            If user already gave required details, confirm next action naturally.
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

    companion object {
        private val runLock = Mutex()
    }
}
