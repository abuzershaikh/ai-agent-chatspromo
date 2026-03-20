package com.message.bulksend.autorespond.ai.customtask.engine

import com.message.bulksend.autorespond.ai.customtask.manager.AgentTaskManager
import com.message.bulksend.autorespond.ai.customtask.models.AgentTask
import com.message.bulksend.autorespond.ai.customtask.models.AgentTaskSessionStatus

class AgentTaskEngine(
    private val taskManager: AgentTaskManager
) {

    data class StepRuntimeContext(
        val isTaskModeAvailable: Boolean,
        val currentTask: AgentTask?,
        val totalSteps: Int,
        val currentStep: Int,
        val sessionStatus: AgentTaskSessionStatus
    )

    fun buildStepRuntimeContext(phoneNumber: String): StepRuntimeContext {
        val activeTasks = taskManager.getActiveTasks()
        if (activeTasks.isEmpty()) {
            return StepRuntimeContext(
                isTaskModeAvailable = false,
                currentTask = null,
                totalSteps = 0,
                currentStep = 0,
                sessionStatus = AgentTaskSessionStatus.FAILED
            )
        }

        val session = taskManager.getOrCreateSession(phoneNumber)
        val currentTask =
            activeTasks.firstOrNull { it.stepOrder == session.currentStepOrder }
                ?: activeTasks.firstOrNull()

        return StepRuntimeContext(
            isTaskModeAvailable = currentTask != null,
            currentTask = currentTask,
            totalSteps = activeTasks.size,
            currentStep = currentTask?.stepOrder ?: 1,
            sessionStatus = session.status
        )
    }

    fun completeStep(phoneNumber: String, completedStepOrder: Int): AgentTaskManager.AdvanceStepResult {
        return taskManager.completeCurrentStepAndAdvance(phoneNumber, completedStepOrder)
    }
}

