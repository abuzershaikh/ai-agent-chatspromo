package com.message.bulksend.autorespond.ai.customtask.manager

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.message.bulksend.autorespond.ai.customtask.models.AgentTask
import com.message.bulksend.autorespond.ai.customtask.models.AgentTaskSession
import com.message.bulksend.autorespond.ai.customtask.models.AgentTaskSessionStatus
import com.message.bulksend.autorespond.ai.customtask.models.AgentTaskToolRegistry

class AgentTaskManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "agent_task_mode_prefs"
        private const val KEY_CUSTOM_TASKS = "custom_template_tasks"
        private const val KEY_TASK_SESSIONS = "custom_template_task_sessions"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getTasks(): List<AgentTask> {
        val json = prefs.getString(KEY_CUSTOM_TASKS, null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<AgentTask>>() {}.type
            gson.fromJson<List<AgentTask>>(json, type).orEmpty()
                .map { task ->
                    val normalizedAllowedTools =
                        AgentTaskToolRegistry.normalizeToolIds(task.allowedTools.orEmpty())
                    val normalizedAgentFormKey =
                        if (normalizedAllowedTools.contains(AgentTaskToolRegistry.SEND_AGENT_FORM)) {
                            task.agentFormTemplateKey.trim()
                        } else {
                            ""
                        }
                    task.copy(
                        allowedTools = normalizedAllowedTools,
                        agentFormTemplateKey = normalizedAgentFormKey
                    )
                }
                .sortedBy { it.stepOrder }
        }.getOrDefault(emptyList())
    }

    fun getActiveTasks(): List<AgentTask> {
        return getTasks().filter { it.isActive }.sortedBy { it.stepOrder }
    }

    fun saveTasks(tasks: List<AgentTask>) {
        val normalized = tasks.sortedBy { it.stepOrder }
            .mapIndexed { index, task ->
                task.copy(stepOrder = index + 1, updatedAt = System.currentTimeMillis())
            }
        prefs.edit().putString(KEY_CUSTOM_TASKS, gson.toJson(normalized)).apply()
    }

    fun addTask(
        title: String,
        instruction: String,
        goal: String = "",
        followUpQuestion: String = "",
        allowedTools: List<String> = emptyList(),
        agentFormTemplateKey: String = ""
    ): AgentTask {
        val tasks = getTasks().toMutableList()
        val normalizedAllowedTools = AgentTaskToolRegistry.normalizeToolIds(allowedTools)
        val normalizedAgentFormKey =
            if (normalizedAllowedTools.contains(AgentTaskToolRegistry.SEND_AGENT_FORM)) {
                agentFormTemplateKey.trim()
            } else {
                ""
            }
        val task = AgentTask(
            stepOrder = tasks.size + 1,
            title = title.trim(),
            goal = goal.trim(),
            instruction = instruction.trim(),
            followUpQuestion = followUpQuestion.trim(),
            allowedTools = normalizedAllowedTools,
            agentFormTemplateKey = normalizedAgentFormKey
        )
        tasks.add(task)
        saveTasks(tasks)
        return task
    }

    fun updateTask(updatedTask: AgentTask) {
        val tasks = getTasks().toMutableList()
        val index = tasks.indexOfFirst { it.taskId == updatedTask.taskId }
        if (index != -1) {
            val normalizedAllowedTools =
                AgentTaskToolRegistry.normalizeToolIds(updatedTask.allowedTools.orEmpty())
            val normalizedAgentFormKey =
                if (normalizedAllowedTools.contains(AgentTaskToolRegistry.SEND_AGENT_FORM)) {
                    updatedTask.agentFormTemplateKey.trim()
                } else {
                    ""
                }
            tasks[index] = updatedTask.copy(
                allowedTools = normalizedAllowedTools,
                agentFormTemplateKey = normalizedAgentFormKey,
                updatedAt = System.currentTimeMillis()
            )
            saveTasks(tasks)
        }
    }

    fun deleteTask(taskId: String) {
        val remaining = getTasks().filterNot { it.taskId == taskId }
        saveTasks(remaining)
    }

    fun duplicateTask(taskId: String): AgentTask? {
        val tasks = getTasks().toMutableList()
        val original = tasks.firstOrNull { it.taskId == taskId } ?: return null
        val duplicate = original.copy(
            taskId = java.util.UUID.randomUUID().toString(),
            stepOrder = tasks.size + 1,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        tasks.add(duplicate)
        saveTasks(tasks)
        return duplicate
    }

    fun clearTasks() {
        prefs.edit().remove(KEY_CUSTOM_TASKS).apply()
    }

    fun getSession(phoneNumber: String): AgentTaskSession? {
        return getAllSessions().firstOrNull { it.phoneNumber == phoneNumber }
    }

    fun getOrCreateSession(phoneNumber: String): AgentTaskSession {
        val session = getSession(phoneNumber)
        if (session != null) return session

        val created = AgentTaskSession(phoneNumber = phoneNumber)
        val sessions = getAllSessions().toMutableList()
        sessions.add(created)
        saveSessions(sessions)
        return created
    }

    fun saveSession(session: AgentTaskSession) {
        val sessions = getAllSessions().toMutableList()
        val index = sessions.indexOfFirst { it.phoneNumber == session.phoneNumber }
        val normalized = session.copy(updatedAt = System.currentTimeMillis())
        if (index == -1) {
            sessions.add(normalized)
        } else {
            sessions[index] = normalized
        }
        saveSessions(sessions)
    }

    fun resetSession(phoneNumber: String) {
        val sessions = getAllSessions().toMutableList()
        sessions.removeAll { it.phoneNumber == phoneNumber }
        saveSessions(sessions)
    }

    fun clearAllSessions() {
        prefs.edit().remove(KEY_TASK_SESSIONS).apply()
    }

    fun markStepPrompted(phoneNumber: String, stepOrder: Int) {
        val session = getOrCreateSession(phoneNumber)
        saveSession(
            session.copy(
                lastPromptedStepOrder = stepOrder
            )
        )
    }

    data class AdvanceStepResult(
        val movedToStep: Int?,
        val isWorkflowCompleted: Boolean
    )

    data class RepeatGuardResult(
        val repeatCount: Int,
        val limitReached: Boolean,
        val shouldAlertOwner: Boolean
    )

    fun completeCurrentStepAndAdvance(
        phoneNumber: String,
        completedStepOrder: Int
    ): AdvanceStepResult {
        val activeTasks = getActiveTasks()
        if (activeTasks.isEmpty()) {
            return AdvanceStepResult(movedToStep = null, isWorkflowCompleted = true)
        }

        val session = getOrCreateSession(phoneNumber)
        val current = session.currentStepOrder
        if (completedStepOrder != current) {
            return AdvanceStepResult(movedToStep = current, isWorkflowCompleted = false)
        }

        val nextStep = activeTasks.firstOrNull { it.stepOrder > completedStepOrder }?.stepOrder
        return if (nextStep == null) {
            saveSession(
                session.copy(
                    status = AgentTaskSessionStatus.COMPLETED,
                    lastPromptedStepOrder = null,
                    stepRepeatCount = 0,
                    lastOwnerAlertStepOrder = null
                )
            )
            AdvanceStepResult(movedToStep = null, isWorkflowCompleted = true)
        } else {
            saveSession(
                session.copy(
                    currentStepOrder = nextStep,
                    status = AgentTaskSessionStatus.ACTIVE,
                    lastPromptedStepOrder = null,
                    stepRepeatCount = 0,
                    lastOwnerAlertStepOrder = null
                )
            )
            AdvanceStepResult(movedToStep = nextStep, isWorkflowCompleted = false)
        }
    }

    fun restartWorkflowForPhone(phoneNumber: String) {
        val session = getOrCreateSession(phoneNumber)
        saveSession(
            session.copy(
                currentStepOrder = 1,
                status = AgentTaskSessionStatus.ACTIVE,
                lastPromptedStepOrder = null,
                stepRepeatCount = 0,
                lastOwnerAlertStepOrder = null
            )
        )
    }

    fun trackStepRepeatAndCheckThreshold(
        phoneNumber: String,
        stepOrder: Int,
        threshold: Int
    ): RepeatGuardResult {
        val normalizedThreshold = threshold.coerceAtLeast(0)
        val session = getOrCreateSession(phoneNumber)

        val repeatCount =
            if (session.lastPromptedStepOrder == stepOrder) {
                (session.stepRepeatCount + 1).coerceAtLeast(1)
            } else {
                1
            }
        val limitReached = normalizedThreshold > 0 && repeatCount >= normalizedThreshold
        val shouldAlertOwner = limitReached && session.lastOwnerAlertStepOrder != stepOrder

        saveSession(
            session.copy(
                lastPromptedStepOrder = stepOrder,
                stepRepeatCount = repeatCount,
                lastOwnerAlertStepOrder =
                    if (shouldAlertOwner) {
                        stepOrder
                    } else if (session.lastPromptedStepOrder != stepOrder) {
                        null
                    } else {
                        session.lastOwnerAlertStepOrder
                    }
            )
        )

        return RepeatGuardResult(
            repeatCount = repeatCount,
            limitReached = limitReached,
            shouldAlertOwner = shouldAlertOwner
        )
    }

    fun getCurrentTask(phoneNumber: String): AgentTask? {
        val session = getOrCreateSession(phoneNumber)
        val activeTasks = getActiveTasks()
        return activeTasks.firstOrNull { it.stepOrder == session.currentStepOrder }
            ?: activeTasks.firstOrNull()
    }

    private fun getAllSessions(): List<AgentTaskSession> {
        val json = prefs.getString(KEY_TASK_SESSIONS, null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<AgentTaskSession>>() {}.type
            gson.fromJson<List<AgentTaskSession>>(json, type).orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun saveSessions(sessions: List<AgentTaskSession>) {
        prefs.edit().putString(KEY_TASK_SESSIONS, gson.toJson(sessions)).apply()
    }
}
