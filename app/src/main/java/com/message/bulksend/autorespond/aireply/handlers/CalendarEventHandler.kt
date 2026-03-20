package com.message.bulksend.autorespond.aireply.handlers

import android.content.Context
import com.message.bulksend.aiagent.tools.calendar.GoogleCalendarAgentTool
import com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * Executes Google Calendar and Google Tasks commands emitted by AI in CUSTOM template mode.
 */
class CalendarEventHandler(
    private val appContext: Context
) : MessageHandler {

    private val settings = AIAgentSettingsManager(appContext)

    override fun getPriority(): Int = 61

    override suspend fun handle(
        context: Context,
        message: String,
        response: String,
        senderPhone: String,
        senderName: String
    ): HandlerResult {
        if (!settings.activeTemplate.equals("CUSTOM", ignoreCase = true)) {
            return HandlerResult(success = true)
        }
        if (!settings.customTemplateEnableGoogleCalendarTool) {
            return HandlerResult(success = true)
        }

        return try {
            var modifiedResponse = response
            val toolActions = mutableListOf<String>()

            applyPattern(
                input = modifiedResponse,
                regex = Regex("\\[CALENDAR_CREATE_EVENT:\\s*(.+?)\\]", RegexOption.IGNORE_CASE),
                actionName = "CALENDAR_CREATE_EVENT",
                toolActions = toolActions
            ) { parsed ->
                val result = GoogleCalendarAgentTool.createEvent(parsed)
                jsonResult(
                    result = result,
                    successText = "Calendar event created successfully",
                    failurePrefix = "Failed to create event"
                )
            }.also { modifiedResponse = it }

            applyPattern(
                input = modifiedResponse,
                regex = Regex("\\[CALENDAR_LIST_EVENTS(?:(.*))?\\]", RegexOption.IGNORE_CASE),
                actionName = "CALENDAR_LIST_EVENTS",
                toolActions = toolActions,
                optionalPayload = true
            ) { parsed ->
                val result = GoogleCalendarAgentTool.listEvents(parsed)
                if (isSuccess(result)) {
                    CommandResult(
                        status = "SUCCESS",
                        replacement = "Events retrieved:\n${formatEvents(result.optJSONArray("events") ?: JSONArray())}"
                    )
                } else {
                    jsonResult(result, failurePrefix = "Failed to list events")
                }
            }.also { modifiedResponse = it }

            applyPattern(
                input = modifiedResponse,
                regex = Regex("\\[CALENDAR_UPDATE_EVENT:\\s*(.+?)\\]", RegexOption.IGNORE_CASE),
                actionName = "CALENDAR_UPDATE_EVENT",
                toolActions = toolActions
            ) { parsed ->
                val id = parsed.valueOf("id", "eventId")
                if (id.isNullOrBlank()) {
                    CommandResult("FAILED_MISSING_ID", "Failed: Event ID is required")
                } else {
                    val result = GoogleCalendarAgentTool.updateEvent(id, parsed)
                    jsonResult(
                        result = result,
                        successText = "Calendar event updated successfully",
                        failurePrefix = "Failed to update event"
                    )
                }
            }.also { modifiedResponse = it }

            applyPattern(
                input = modifiedResponse,
                regex = Regex("\\[CALENDAR_CREATE_MEET_LINK:\\s*(.+?)\\]", RegexOption.IGNORE_CASE),
                actionName = "CALENDAR_CREATE_MEET_LINK",
                toolActions = toolActions
            ) { parsed ->
                val id = parsed.valueOf("id", "eventId")
                if (id.isNullOrBlank()) {
                    CommandResult("FAILED_MISSING_ID", "Failed: Event ID is required")
                } else {
                    val result = GoogleCalendarAgentTool.createMeetLink(id, parsed)
                    if (isSuccess(result)) {
                        val event = result.optJSONObject("event")
                        val meetLink = event?.optString("hangoutLink").orEmpty()
                        val htmlLink = result.optString("htmlLink")
                        val linkText = when {
                            meetLink.isNotBlank() -> meetLink
                            htmlLink.isNotBlank() -> htmlLink
                            else -> ""
                        }
                        CommandResult(
                            status = "SUCCESS",
                            replacement = if (linkText.isNotBlank()) {
                                "Google Meet link created successfully: $linkText"
                            } else {
                                "Google Meet link created successfully"
                            }
                        )
                    } else {
                        jsonResult(result, failurePrefix = "Failed to create Google Meet link")
                    }
                }
            }.also { modifiedResponse = it }

            applyPattern(
                input = modifiedResponse,
                regex = Regex("\\[CALENDAR_DELETE_EVENT:\\s*(.+?)\\]", RegexOption.IGNORE_CASE),
                actionName = "CALENDAR_DELETE_EVENT",
                toolActions = toolActions
            ) { parsed ->
                val id = parsed.valueOf("id", "eventId")
                if (id.isNullOrBlank()) {
                    CommandResult("FAILED_MISSING_ID", "Failed: Event ID is required")
                } else {
                    val result = GoogleCalendarAgentTool.deleteEvent(id, parsed)
                    jsonResult(
                        result = result,
                        successText = "Calendar event deleted successfully",
                        failurePrefix = "Failed to delete event"
                    )
                }
            }.also { modifiedResponse = it }

            applyPattern(
                input = modifiedResponse,
                regex = Regex("\\[CALENDAR_CREATE_TASK:\\s*(.+?)\\]", RegexOption.IGNORE_CASE),
                actionName = "CALENDAR_CREATE_TASK",
                toolActions = toolActions
            ) { parsed ->
                val result = GoogleCalendarAgentTool.createTask(parsed)
                jsonResult(
                    result = result,
                    successText = "Calendar task created successfully",
                    failurePrefix = "Failed to create task"
                )
            }.also { modifiedResponse = it }

            applyPattern(
                input = modifiedResponse,
                regex = Regex("\\[CALENDAR_LIST_TASKS(?:(.*))?\\]", RegexOption.IGNORE_CASE),
                actionName = "CALENDAR_LIST_TASKS",
                toolActions = toolActions,
                optionalPayload = true
            ) { parsed ->
                val result = GoogleCalendarAgentTool.listTasks(parsed)
                if (isSuccess(result)) {
                    CommandResult(
                        status = "SUCCESS",
                        replacement = "Tasks retrieved:\n${formatTasks(result.optJSONArray("tasks") ?: JSONArray())}"
                    )
                } else {
                    jsonResult(result, failurePrefix = "Failed to list tasks")
                }
            }.also { modifiedResponse = it }

            applyPattern(
                input = modifiedResponse,
                regex = Regex("\\[CALENDAR_UPDATE_TASK:\\s*(.+?)\\]", RegexOption.IGNORE_CASE),
                actionName = "CALENDAR_UPDATE_TASK",
                toolActions = toolActions
            ) { parsed ->
                val id = parsed.valueOf("id", "taskId")
                if (id.isNullOrBlank()) {
                    CommandResult("FAILED_MISSING_ID", "Failed: Task ID is required")
                } else {
                    val result = GoogleCalendarAgentTool.updateTask(id, parsed)
                    jsonResult(
                        result = result,
                        successText = "Calendar task updated successfully",
                        failurePrefix = "Failed to update task"
                    )
                }
            }.also { modifiedResponse = it }

            applyPattern(
                input = modifiedResponse,
                regex = Regex("\\[CALENDAR_MOVE_TASK:\\s*(.+?)\\]", RegexOption.IGNORE_CASE),
                actionName = "CALENDAR_MOVE_TASK",
                toolActions = toolActions
            ) { parsed ->
                val id = parsed.valueOf("id", "taskId")
                if (id.isNullOrBlank()) {
                    CommandResult("FAILED_MISSING_ID", "Failed: Task ID is required")
                } else {
                    val result = GoogleCalendarAgentTool.moveTask(id, parsed)
                    jsonResult(
                        result = result,
                        successText = "Calendar task moved successfully",
                        failurePrefix = "Failed to move task"
                    )
                }
            }.also { modifiedResponse = it }

            applyPattern(
                input = modifiedResponse,
                regex = Regex("\\[CALENDAR_CLEAR_COMPLETED_TASKS(?:(.*))?\\]", RegexOption.IGNORE_CASE),
                actionName = "CALENDAR_CLEAR_COMPLETED_TASKS",
                toolActions = toolActions,
                optionalPayload = true
            ) { parsed ->
                val result = GoogleCalendarAgentTool.clearCompletedTasks(parsed)
                jsonResult(
                    result = result,
                    successText = "Completed tasks cleared successfully",
                    failurePrefix = "Failed to clear completed tasks"
                )
            }.also { modifiedResponse = it }

            applyPattern(
                input = modifiedResponse,
                regex = Regex("\\[CALENDAR_DELETE_TASK:\\s*(.+?)\\]", RegexOption.IGNORE_CASE),
                actionName = "CALENDAR_DELETE_TASK",
                toolActions = toolActions
            ) { parsed ->
                val id = parsed.valueOf("id", "taskId")
                if (id.isNullOrBlank()) {
                    CommandResult("FAILED_MISSING_ID", "Failed: Task ID is required")
                } else {
                    val result = GoogleCalendarAgentTool.deleteTask(id, parsed)
                    jsonResult(
                        result = result,
                        successText = "Calendar task deleted successfully",
                        failurePrefix = "Failed to delete task"
                    )
                }
            }.also { modifiedResponse = it }

            applyPattern(
                input = modifiedResponse,
                regex = Regex("\\[CALENDAR_LIST_TASKLISTS(?:(.*))?\\]", RegexOption.IGNORE_CASE),
                actionName = "CALENDAR_LIST_TASKLISTS",
                toolActions = toolActions,
                optionalPayload = true
            ) { parsed ->
                val result = GoogleCalendarAgentTool.listTaskLists(parsed)
                if (isSuccess(result)) {
                    CommandResult(
                        status = "SUCCESS",
                        replacement = "Task lists retrieved:\n${formatTaskLists(result.optJSONArray("tasklists") ?: JSONArray())}"
                    )
                } else {
                    jsonResult(result, failurePrefix = "Failed to list task lists")
                }
            }.also { modifiedResponse = it }

            applyPattern(
                input = modifiedResponse,
                regex = Regex("\\[CALENDAR_GET_TASKLIST:\\s*(.+?)\\]", RegexOption.IGNORE_CASE),
                actionName = "CALENDAR_GET_TASKLIST",
                toolActions = toolActions
            ) { parsed ->
                val id = parsed.valueOf("id", "tasklistId", "taskListId")
                if (id.isNullOrBlank()) {
                    CommandResult("FAILED_MISSING_ID", "Failed: Task List ID is required")
                } else {
                    val result = GoogleCalendarAgentTool.getTaskList(id)
                    if (isSuccess(result)) {
                        CommandResult(
                            status = "SUCCESS",
                            replacement = "Task list:\n${formatTaskList(result.optJSONObject("tasklist") ?: JSONObject())}"
                        )
                    } else {
                        jsonResult(result, failurePrefix = "Failed to get task list")
                    }
                }
            }.also { modifiedResponse = it }

            applyPattern(
                input = modifiedResponse,
                regex = Regex("\\[CALENDAR_CREATE_TASKLIST:\\s*(.+?)\\]", RegexOption.IGNORE_CASE),
                actionName = "CALENDAR_CREATE_TASKLIST",
                toolActions = toolActions
            ) { parsed ->
                val result = GoogleCalendarAgentTool.createTaskList(parsed)
                jsonResult(
                    result = result,
                    successText = "Task list created successfully",
                    failurePrefix = "Failed to create task list"
                )
            }.also { modifiedResponse = it }

            applyPattern(
                input = modifiedResponse,
                regex = Regex("\\[CALENDAR_UPDATE_TASKLIST:\\s*(.+?)\\]", RegexOption.IGNORE_CASE),
                actionName = "CALENDAR_UPDATE_TASKLIST",
                toolActions = toolActions
            ) { parsed ->
                val id = parsed.valueOf("id", "tasklistId", "taskListId")
                if (id.isNullOrBlank()) {
                    CommandResult("FAILED_MISSING_ID", "Failed: Task List ID is required")
                } else {
                    val result = GoogleCalendarAgentTool.updateTaskList(id, parsed)
                    jsonResult(
                        result = result,
                        successText = "Task list updated successfully",
                        failurePrefix = "Failed to update task list"
                    )
                }
            }.also { modifiedResponse = it }

            applyPattern(
                input = modifiedResponse,
                regex = Regex("\\[CALENDAR_DELETE_TASKLIST:\\s*(.+?)\\]", RegexOption.IGNORE_CASE),
                actionName = "CALENDAR_DELETE_TASKLIST",
                toolActions = toolActions
            ) { parsed ->
                val id = parsed.valueOf("id", "tasklistId", "taskListId")
                if (id.isNullOrBlank()) {
                    CommandResult("FAILED_MISSING_ID", "Failed: Task List ID is required")
                } else {
                    val result = GoogleCalendarAgentTool.deleteTaskList(id)
                    jsonResult(
                        result = result,
                        successText = "Task list deleted successfully",
                        failurePrefix = "Failed to delete task list"
                    )
                }
            }.also { modifiedResponse = it }

            applyPattern(
                input = modifiedResponse,
                regex = Regex("\\[CALENDAR_LIST_CALENDARS(?:(.*))?\\]", RegexOption.IGNORE_CASE),
                actionName = "CALENDAR_LIST_CALENDARS",
                toolActions = toolActions,
                optionalPayload = true
            ) { parsed ->
                val result = GoogleCalendarAgentTool.listCalendars(parsed)
                if (isSuccess(result)) {
                    CommandResult(
                        status = "SUCCESS",
                        replacement = "Calendars retrieved:\n${formatCalendars(result.optJSONArray("calendars") ?: JSONArray())}"
                    )
                } else {
                    jsonResult(result, failurePrefix = "Failed to list calendars")
                }
            }.also { modifiedResponse = it }

            applyPattern(
                input = modifiedResponse,
                regex = Regex("\\[CALENDAR_GET_CALENDAR:\\s*(.+?)\\]", RegexOption.IGNORE_CASE),
                actionName = "CALENDAR_GET_CALENDAR",
                toolActions = toolActions
            ) { parsed ->
                val id = parsed.valueOf("calendarId", "id", "calendar")
                if (id.isNullOrBlank()) {
                    CommandResult("FAILED_MISSING_ID", "Failed: Calendar ID is required")
                } else {
                    val result = GoogleCalendarAgentTool.getCalendar(id)
                    if (isSuccess(result)) {
                        CommandResult(
                            status = "SUCCESS",
                            replacement = "Calendar:\n${formatCalendar(result.optJSONObject("calendar") ?: JSONObject())}"
                        )
                    } else {
                        jsonResult(result, failurePrefix = "Failed to get calendar")
                    }
                }
            }.also { modifiedResponse = it }

            applyPattern(
                input = modifiedResponse,
                regex = Regex("\\[CALENDAR_UPDATE_CALENDAR:\\s*(.+?)\\]", RegexOption.IGNORE_CASE),
                actionName = "CALENDAR_UPDATE_CALENDAR",
                toolActions = toolActions
            ) { parsed ->
                val id = parsed.valueOf("calendarId", "id", "calendar")
                if (id.isNullOrBlank()) {
                    CommandResult("FAILED_MISSING_ID", "Failed: Calendar ID is required")
                } else {
                    val result = GoogleCalendarAgentTool.updateCalendar(id, parsed)
                    jsonResult(
                        result = result,
                        successText = "Calendar settings updated successfully",
                        failurePrefix = "Failed to update calendar settings"
                    )
                }
            }.also { modifiedResponse = it }

            modifiedResponse = modifiedResponse.replace(Regex("\\n{3,}"), "\n\n").trim()

            HandlerResult(
                success = true,
                modifiedResponse = modifiedResponse,
                metadata = mapOf(
                    "tool_actions" to toolActions,
                    "tool_action_count" to toolActions.size
                )
            )
        } catch (e: Exception) {
            android.util.Log.e("CalendarEventHandler", "Failed to process calendar command: ${e.message}", e)
            HandlerResult(success = false)
        }
    }

    private suspend fun applyPattern(
        input: String,
        regex: Regex,
        actionName: String,
        toolActions: MutableList<String>,
        optionalPayload: Boolean = false,
        block: suspend (Map<String, String>) -> CommandResult
    ): String {
        var updated = input
        regex.findAll(updated).toList().forEach { match ->
            val rawPayload =
                if (optionalPayload) {
                    match.groupValues.getOrNull(1)?.trim()?.removePrefix(":")?.trim().orEmpty()
                } else {
                    match.groupValues.getOrNull(1).orEmpty()
                }
            val parsed = parsePayload(rawPayload)
            val result = block(parsed)
            toolActions += "$actionName:${result.status}"
            updated = updated.replace(match.value, result.replacement).trim()
        }
        return updated
    }

    private fun parsePayload(raw: String): Map<String, String> {
        val normalized = raw.replace("|", ";").replace("\n", ";")
        var parts = normalized.split(";").map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size <= 1 && raw.contains(",")) {
            parts = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        }

        val map = linkedMapOf<String, String>()
        parts.forEach { part ->
            val idx = part.indexOf('=')
            if (idx <= 0 || idx >= part.length - 1) return@forEach
            val key = part.substring(0, idx).trim()
            val value = part.substring(idx + 1).trim()
            if (key.isBlank() || value.isBlank()) return@forEach
            map[key] = value
        }
        return map
    }

    private fun Map<String, String>.valueOf(vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key ->
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value?.trim()
        }?.takeIf { it.isNotBlank() }
    }

    private fun isSuccess(result: JSONObject): Boolean {
        return result.optString("status") == "success"
    }

    private fun jsonResult(
        result: JSONObject,
        successText: String = "",
        failurePrefix: String
    ): CommandResult {
        return if (isSuccess(result)) {
            CommandResult("SUCCESS", successText.ifBlank { "Success" })
        } else {
            CommandResult("FAILED", "$failurePrefix: ${result.optString("message")}")
        }
    }

    private fun formatEvents(events: JSONArray): String {
        if (events.length() == 0) return "No events found."
        val builder = StringBuilder()
        for (i in 0 until events.length()) {
            val ev = events.optJSONObject(i) ?: continue
            val start = ev.optJSONObject("start")?.optString("dateTime").orEmpty()
                .ifBlank { ev.optJSONObject("start")?.optString("date").orEmpty() }
            val end = ev.optJSONObject("end")?.optString("dateTime").orEmpty()
                .ifBlank { ev.optJSONObject("end")?.optString("date").orEmpty() }
            val meet = ev.optString("hangoutLink")
            builder.append("- ID: ${ev.optString("id")} | Title: ${ev.optString("summary")} | Start: $start | End: $end")
            if (meet.isNotBlank()) builder.append(" | Meet: $meet")
            builder.append("\n")
        }
        return builder.toString()
    }

    private fun formatTasks(tasks: JSONArray): String {
        if (tasks.length() == 0) return "No tasks found."
        val builder = StringBuilder()
        for (i in 0 until tasks.length()) {
            val task = tasks.optJSONObject(i) ?: continue
            builder.append(
                "- ID: ${task.optString("id")} | Title: ${task.optString("title")} | Status: ${task.optString("status")} | Due: ${task.optString("due")}\n"
            )
        }
        return builder.toString()
    }

    private fun formatTaskLists(taskLists: JSONArray): String {
        if (taskLists.length() == 0) return "No task lists found."
        val builder = StringBuilder()
        for (i in 0 until taskLists.length()) {
            val taskList = taskLists.optJSONObject(i) ?: continue
            builder.append("- ID: ${taskList.optString("id")} | Title: ${taskList.optString("title")}\n")
        }
        return builder.toString()
    }

    private fun formatTaskList(taskList: JSONObject): String {
        if (taskList.length() == 0) return "No task list found."
        return "- ID: ${taskList.optString("id")} | Title: ${taskList.optString("title")}"
    }

    private fun formatCalendars(calendars: JSONArray): String {
        if (calendars.length() == 0) return "No calendars found."
        val builder = StringBuilder()
        for (i in 0 until calendars.length()) {
            val calendar = calendars.optJSONObject(i) ?: continue
            builder.append(
                "- ID: ${calendar.optString("id")} | Title: ${calendar.optString("summaryOverride").ifBlank { calendar.optString("summary") }} | Selected: ${calendar.optBoolean("selected")} | Hidden: ${calendar.optBoolean("hidden")}\n"
            )
        }
        return builder.toString()
    }

    private fun formatCalendar(calendar: JSONObject): String {
        if (calendar.length() == 0) return "No calendar found."
        val summary = calendar.optString("summaryOverride").ifBlank { calendar.optString("summary") }
        val reminders = calendar.optJSONArray("defaultReminders")
        val reminderText =
            if (reminders == null || reminders.length() == 0) {
                "none"
            } else {
                buildString {
                    for (i in 0 until reminders.length()) {
                        val reminder = reminders.optJSONObject(i) ?: continue
                        if (isNotBlank()) append(", ")
                        append("${reminder.optString("method")}:${reminder.optInt("minutes")}")
                    }
                }
            }
        return "- ID: ${calendar.optString("id")} | Title: $summary | Selected: ${calendar.optBoolean("selected")} | Hidden: ${calendar.optBoolean("hidden")} | DefaultReminders: $reminderText"
    }

    private data class CommandResult(
        val status: String,
        val replacement: String
    )
}
