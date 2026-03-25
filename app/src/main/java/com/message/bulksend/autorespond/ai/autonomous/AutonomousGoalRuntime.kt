package com.message.bulksend.autorespond.ai.autonomous

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.message.bulksend.autorespond.ai.customtask.manager.AgentTaskManager
import com.message.bulksend.autorespond.ai.customtask.models.AgentTaskSessionStatus
import com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager
import java.util.Calendar

import java.util.Locale
import java.util.concurrent.TimeUnit

data class AutonomousDispatchDecision(
    val canSend: Boolean,
    val retryAt: Long,
    val reason: String
)

class AutonomousGoalRuntime(context: Context) {

    private val appContext = context.applicationContext
    private val settings = AIAgentSettingsManager(appContext)
    private val queueStore = AutonomousGoalQueueStore(appContext)
    private val userStateStore = AutonomousUserStateStore(appContext)
    private val taskManager = AgentTaskManager(appContext)

    fun enqueueFromIncomingMessage(
        senderPhone: String,
        senderName: String,
        lastUserMessage: String
    ) {
        if (!isContinuousEnabled()) return
        if (senderPhone.isBlank()) return

        val now = System.currentTimeMillis()
        val normalizedSenderName = senderName.ifBlank { "User" }
        val stopDetected = containsStopKeyword(lastUserMessage)
        val today = todayKey(now)

        userStateStore.updateState(senderPhone) { existing ->
            val current =
                existing ?: AutonomousUserState(
                    senderPhone = senderPhone,
                    senderName = normalizedSenderName,
                    nudgeDayKey = today
                )

            val resetNudges = if (current.nudgeDayKey == today) current.nudgesToday else 0
            current.copy(
                senderName = normalizedSenderName,
                lastInboundAt = now,
                nudgeDayKey = today,
                nudgesToday = resetNudges,
                pauseUntil = if (stopDetected) now + TimeUnit.HOURS.toMillis(24) else current.pauseUntil
            )
        }

        if (stopDetected) return
        if (isGoalCompletedForSender(senderPhone)) return

        val goal =
            settings.customTemplateGoal.trim().ifBlank {
                "Understand user intent and move conversation toward completion."
            }
        val dedupeKey = buildDedupeKey(senderPhone, goal, lastUserMessage)

        queueStore.enqueueOrRefreshGoal(
            senderPhone = senderPhone,
            senderName = normalizedSenderName,
            goal = goal,
            lastUserMessage = lastUserMessage,
            dedupeKey = dedupeKey,
            maxQueue = settings.customTemplateAutonomousMaxQueue
        )
        scheduleHeartbeat()
        scheduleImmediateKick()
    }

    fun enqueuePaymentStatusUpdate(
        senderPhone: String,
        senderName: String,
        status: String,
        amount: Double,
        description: String,
        linkId: String
    ) {
        if (!isContinuousEnabled()) return
        if (senderPhone.isBlank()) return
        if (isGoalCompletedForSender(senderPhone)) return

        val statusUpper = status.trim().uppercase(Locale.ROOT).ifBlank { "UNKNOWN" }
        val systemMessage =
            "Payment status update: status=$statusUpper; amount=$amount; description=$description; linkId=$linkId"

        val goal =
            "Confirm payment status update naturally and guide user for the next best action."
        val dedupeKey = buildDedupeKey(senderPhone, goal, systemMessage)

        queueStore.enqueueOrRefreshGoal(
            senderPhone = senderPhone,
            senderName = senderName.ifBlank { "User" },
            goal = goal,
            lastUserMessage = systemMessage,
            dedupeKey = dedupeKey,
            maxQueue = settings.customTemplateAutonomousMaxQueue
        )
        scheduleHeartbeat()
        scheduleImmediateKick(delaySeconds = 3)
    }

    fun evaluateDispatch(
        senderPhone: String,
        stateHash: String,
        outgoingText: String,
        now: Long = System.currentTimeMillis()
    ): AutonomousDispatchDecision {
        val state = userStateStore.getState(senderPhone)
            ?: return AutonomousDispatchDecision(
                canSend = false,
                retryAt = now + TimeUnit.MINUTES.toMillis(5),
                reason = "No user state available"
            )

        if (state.pauseUntil > now) {
            return AutonomousDispatchDecision(
                canSend = false,
                retryAt = state.pauseUntil,
                reason = "Paused by stop keyword"
            )
        }

        val activeWindow = TimeUnit.HOURS.toMillis(24)
        if (state.lastInboundAt <= 0L || now - state.lastInboundAt > activeWindow) {
            return AutonomousDispatchDecision(
                canSend = false,
                retryAt = now + TimeUnit.HOURS.toMillis(6),
                reason = "User not active in last 24h"
            )
        }

        val silenceGapMs = settings.customTemplateAutonomousSilenceGapMinutes.coerceAtLeast(1) * 60_000L
        val earliestBySilence = state.lastInboundAt + silenceGapMs
        if (now < earliestBySilence) {
            return AutonomousDispatchDecision(
                canSend = false,
                retryAt = earliestBySilence,
                reason = "Silence gap not reached"
            )
        }

        val today = todayKey(now)
        val nudgesToday = if (state.nudgeDayKey == today) state.nudgesToday else 0
        val maxNudges = settings.customTemplateAutonomousMaxNudgesPerDay.coerceAtLeast(1)
        if (nudgesToday >= maxNudges) {
            return AutonomousDispatchDecision(
                canSend = false,
                retryAt = startOfNextDayMillis(now),
                reason = "Daily autonomous nudge cap reached"
            )
        }

        val messageHash = hashText(outgoingText)
        val duplicateWindow = TimeUnit.MINUTES.toMillis(30)
        if (
            state.lastStateHash == stateHash &&
                state.lastMessageHash == messageHash &&
                now - state.lastMessageAt <= duplicateWindow
        ) {
            return AutonomousDispatchDecision(
                canSend = false,
                retryAt = state.lastMessageAt + duplicateWindow,
                reason = "Duplicate autonomous message in dedupe window"
            )
        }

        return AutonomousDispatchDecision(
            canSend = true,
            retryAt = now,
            reason = "allowed"
        )
    }

    fun markAutonomousOutbound(
        senderPhone: String,
        senderName: String,
        stateHash: String,
        outgoingText: String,
        at: Long = System.currentTimeMillis()
    ) {
        val today = todayKey(at)
        userStateStore.updateState(senderPhone) { existing ->
            val current =
                existing ?: AutonomousUserState(
                    senderPhone = senderPhone,
                    senderName = senderName.ifBlank { "User" },
                    nudgeDayKey = today
                )
            val baseNudges = if (current.nudgeDayKey == today) current.nudgesToday else 0
            current.copy(
                senderName = senderName.ifBlank { current.senderName },
                lastOutboundAt = at,
                lastAutonomousAt = at,
                nudgeDayKey = today,
                nudgesToday = baseNudges + 1,
                lastStateHash = stateHash,
                lastMessageHash = hashText(outgoingText),
                lastMessageAt = at
            )
        }
    }

    fun scheduleHeartbeat() {
        if (!isContinuousEnabled()) return

        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        val request =
            PeriodicWorkRequestBuilder<AutonomousGoalHeartbeatWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            HEARTBEAT_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun scheduleImmediateKick(delaySeconds: Long = 15L) {
        if (!isContinuousEnabled()) return

        val request =
            OneTimeWorkRequestBuilder<AutonomousGoalHeartbeatWorker>()
                .setInitialDelay(delaySeconds.coerceAtLeast(0L), TimeUnit.SECONDS)
                .build()

        WorkManager.getInstance(appContext).enqueue(request)
    }

    fun cancelHeartbeat() {

        WorkManager.getInstance(appContext).cancelUniqueWork(HEARTBEAT_WORK_NAME)

    }



    fun isGoalCompletedForSender(senderPhone: String): Boolean {
        if (senderPhone.isBlank()) return false
        if (!settings.activeTemplate.equals("CUSTOM", ignoreCase = true)) return false
        if (settings.customTemplatePromptMode != AIAgentSettingsManager.PROMPT_MODE_STEP_FLOW) return false
        if (!settings.customTemplateTaskModeEnabled) return false

        val session = taskManager.getSession(senderPhone) ?: return false
        return session.status == AgentTaskSessionStatus.COMPLETED
    }
    fun getRuntimeStatus(): AutonomousRuntimeStatus {
        return AutonomousRuntimeStatus(
            queueSize = queueStore.queueSize(),
            lastHeartbeatAt = queueStore.getLastHeartbeatAt(),
            lastError = queueStore.getLastError()
        )
    }

    fun isContinuousEnabled(): Boolean {
        return settings.isAgentEnabled &&
            settings.activeTemplate.equals("CUSTOM", ignoreCase = true) &&
            settings.customTemplateContinuousAutonomousEnabled
    }

    private fun containsStopKeyword(message: String): Boolean {
        if (message.isBlank()) return false
        val lower = message.lowercase(Locale.ROOT)
        val stopKeywords = listOf(
            "stop",
            "pause",
            "baad me",
            "mat bhejo",
            "don't message",
            "dont message",
            "reply mat"
        )
        return stopKeywords.any { lower.contains(it) }
    }

    private fun buildDedupeKey(phone: String, goal: String, message: String): String {
        val compactMessage = message.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").take(120)
        return "$phone|${goal.trim().lowercase(Locale.ROOT)}|$compactMessage"
    }

    private fun hashText(value: String): String {
        return value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").take(300)
    }

    private fun todayKey(now: Long): String {

        val calendar = Calendar.getInstance().apply { timeInMillis = now }

        val year = calendar.get(Calendar.YEAR)

        val month = calendar.get(Calendar.MONTH) + 1

        val day = calendar.get(Calendar.DAY_OF_MONTH)

        return String.format(Locale.US, "%04d-%02d-%02d", year, month, day)

    }

    private fun startOfNextDayMillis(now: Long): Long {

        val calendar = Calendar.getInstance().apply {

            timeInMillis = now

            set(Calendar.HOUR_OF_DAY, 0)

            set(Calendar.MINUTE, 0)

            set(Calendar.SECOND, 0)

            set(Calendar.MILLISECOND, 0)

            add(Calendar.DAY_OF_YEAR, 1)

        }

        return calendar.timeInMillis

    }

    companion object {
        const val HEARTBEAT_WORK_NAME = "custom_template_autonomous_heartbeat"
    }
}




