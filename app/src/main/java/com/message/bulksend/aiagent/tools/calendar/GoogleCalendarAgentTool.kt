package com.message.bulksend.aiagent.tools.calendar

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object GoogleCalendarAgentTool {
    const val WORKER_URL = "https://google-calendar-worker.aawuazer.workers.dev"

    private fun getUserId(): String = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    private fun baseError(message: String): JSONObject {
        return JSONObject().put("status", "error").put("message", message)
    }

    private fun validateRequestContext(): JSONObject? {
        if (WORKER_URL.isBlank() || WORKER_URL.contains("YOUR_ACCOUNT")) {
            return baseError("Worker URL not configured")
        }
        if (getUserId().isBlank()) {
            return baseError("User not logged in")
        }
        return null
    }

    private suspend fun postJson(endpoint: String, body: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            try {
                validateRequestContext()?.let { return@withContext it }

                val connection = URL("$WORKER_URL$endpoint").openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(body.toString())
                writer.flush()
                writer.close()

                val success = connection.responseCode in 200..299
                val stream = if (success) connection.inputStream else connection.errorStream
                val raw =
                    if (stream != null) {
                        val reader = BufferedReader(InputStreamReader(stream))
                        val text = reader.readText()
                        reader.close()
                        text
                    } else {
                        ""
                    }

                if (!success) {
                    return@withContext baseError(raw.ifBlank { "HTTP ${connection.responseCode}" })
                }

                val json = if (raw.isNotBlank()) JSONObject(raw) else JSONObject()
                json.put("status", "success")
                json
            } catch (e: Exception) {
                baseError(e.message ?: "Unknown error")
            }
        }

    private fun valueOf(params: Map<String, String>, vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key ->
            params.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value?.trim()
        }?.takeIf { it.isNotBlank() }
    }

    private fun boolValue(params: Map<String, String>, vararg keys: String): Boolean? {
        val raw = valueOf(params, *keys) ?: return null
        return when (raw.lowercase()) {
            "true", "1", "yes", "y", "on" -> true
            "false", "0", "no", "n", "off" -> false
            else -> null
        }
    }

    private fun hasValue(params: Map<String, String>, vararg keys: String): Boolean {
        return valueOf(params, *keys) != null
    }

    private fun parseReminderOverrides(raw: String?): JSONArray? {
        if (raw.isNullOrBlank()) return null

        val reminders = JSONArray()
        raw.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { part ->
            val pieces = part.split(":").map { it.trim() }
            if (pieces.size == 2) {
                val minutes = pieces[1].toIntOrNull() ?: return@forEach
                reminders.put(
                    JSONObject()
                        .put("method", pieces[0].ifBlank { "popup" })
                        .put("minutes", minutes)
                )
            } else {
                val minutes = part.toIntOrNull() ?: return@forEach
                reminders.put(JSONObject().put("method", "popup").put("minutes", minutes))
            }
        }

        return if (reminders.length() == 0) null else reminders
    }

    private fun parseEventReminders(raw: String?): JSONObject? {
        if (raw.isNullOrBlank()) return null
        if (raw.equals("default", ignoreCase = true)) {
            return JSONObject().put("useDefault", true)
        }

        val overrides = parseReminderOverrides(raw) ?: return null
        return JSONObject()
            .put("useDefault", false)
            .put("overrides", overrides)
    }

    private fun parseAttendees(raw: String?): JSONArray? {
        if (raw.isNullOrBlank()) return null
        val attendees = JSONArray()
        raw.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { email ->
            attendees.put(JSONObject().put("email", email))
        }
        return if (attendees.length() == 0) null else attendees
    }

    private fun parseRecurrence(raw: String?): JSONArray? {
        if (raw.isNullOrBlank()) return null
        val recurrence = JSONArray()
        raw.split("|").map { it.trim() }.filter { it.isNotBlank() }.forEach { rule ->
            recurrence.put(
                if (
                    rule.startsWith("RRULE:", ignoreCase = true) ||
                        rule.startsWith("EXDATE:", ignoreCase = true) ||
                        rule.startsWith("RDATE:", ignoreCase = true)
                ) {
                    rule.replace("&", ";")
                } else {
                    "RRULE:${rule.replace("&", ";")}"
                }
            )
        }
        return if (recurrence.length() == 0) null else recurrence
    }

    private fun parseNotificationSettings(raw: String?): JSONObject? {
        if (raw.isNullOrBlank()) return null
        val notifications = JSONArray()
        raw.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { part ->
            val pieces = part.split(":").map { it.trim() }
            if (pieces.size != 2 || pieces[0].isBlank() || pieces[1].isBlank()) return@forEach
            notifications.put(
                JSONObject()
                    .put("type", pieces[0])
                    .put("method", pieces[1])
            )
        }
        return if (notifications.length() == 0) null else JSONObject().put("notifications", notifications)
    }

    private fun buildConferenceData(params: Map<String, String>): JSONObject? {
        val shouldCreateMeet = boolValue(params, "createMeetLink", "googleMeet", "meet") == true
        val requestId = valueOf(params, "conferenceRequestId", "requestId")
        if (!shouldCreateMeet && requestId.isNullOrBlank()) return null

        val solutionType = valueOf(params, "conferenceType", "solutionType") ?: "hangoutsMeet"
        return JSONObject().put(
            "createRequest",
            JSONObject()
                .put("requestId", requestId ?: "meet-${System.currentTimeMillis()}")
                .put("conferenceSolutionKey", JSONObject().put("type", solutionType))
        )
    }

    private fun buildDatePayload(value: String, timeZone: String?, isAllDay: Boolean): JSONObject {
        return if (isAllDay) {
            JSONObject().put("date", value)
        } else {
            JSONObject().put("dateTime", value).apply {
                if (!timeZone.isNullOrBlank()) {
                    put("timeZone", timeZone)
                }
            }
        }
    }

    private fun buildEventDetails(params: Map<String, String>): JSONObject {
        val event = JSONObject()
        valueOf(params, "title", "summary")?.let { event.put("summary", it) }
        valueOf(params, "description", "notes")?.let { event.put("description", it) }
        valueOf(params, "location")?.let { event.put("location", it) }
        valueOf(params, "visibility")?.let { event.put("visibility", it) }
        valueOf(params, "status")?.let { event.put("status", it) }
        valueOf(params, "transparency")?.let { event.put("transparency", it) }
        valueOf(params, "colorId", "color")?.let { event.put("colorId", it) }
        boolValue(params, "guestsCanInviteOthers")?.let { event.put("guestsCanInviteOthers", it) }
        boolValue(params, "guestsCanModify")?.let { event.put("guestsCanModify", it) }
        boolValue(params, "guestsCanSeeOtherGuests")?.let { event.put("guestsCanSeeOtherGuests", it) }

        parseAttendees(valueOf(params, "attendees"))?.let { event.put("attendees", it) }
        parseRecurrence(valueOf(params, "recurrence", "rrule"))?.let { event.put("recurrence", it) }
        parseEventReminders(valueOf(params, "reminders"))?.let { event.put("reminders", it) }
        buildConferenceData(params)?.let { event.put("conferenceData", it) }

        val timeZone = valueOf(params, "timeZone", "timezone")
        val allDay = boolValue(params, "allDay", "allday") == true
        val hasStartDate = hasValue(params, "startDate")
        val hasEndDate = hasValue(params, "endDate")
        val start = valueOf(params, "startTime", "startDateTime", "start") ?: valueOf(params, "startDate")
        val end = valueOf(params, "endTime", "endDateTime", "end") ?: valueOf(params, "endDate")

        if (!start.isNullOrBlank()) {
            event.put("start", buildDatePayload(start, timeZone, allDay || hasStartDate))
        }
        if (!end.isNullOrBlank()) {
            event.put("end", buildDatePayload(end, timeZone, allDay || hasEndDate))
        }

        return event
    }

    private fun buildTaskDetails(params: Map<String, String>): JSONObject {
        val task = JSONObject()
        valueOf(params, "title", "summary")?.let { task.put("title", it) }
        valueOf(params, "notes", "description")?.let { task.put("notes", it) }
        valueOf(params, "due", "dueDate")?.let { task.put("due", it) }
        valueOf(params, "status")?.let { task.put("status", it) }
        valueOf(params, "completed", "completedAt")?.let { task.put("completed", it) }
        return task
    }

    private fun buildTaskListDetails(params: Map<String, String>): JSONObject {
        val taskList = JSONObject()
        valueOf(params, "title", "name")?.let { taskList.put("title", it) }
        return taskList
    }

    private fun buildCalendarDetails(params: Map<String, String>): JSONObject {
        val calendar = JSONObject()
        valueOf(params, "summaryOverride", "name", "title")?.let { calendar.put("summaryOverride", it) }
        valueOf(params, "colorId", "color")?.let { calendar.put("colorId", it) }
        valueOf(params, "backgroundColor")?.let { calendar.put("backgroundColor", it) }
        valueOf(params, "foregroundColor")?.let { calendar.put("foregroundColor", it) }
        boolValue(params, "hidden")?.let { calendar.put("hidden", it) }
        boolValue(params, "selected")?.let { calendar.put("selected", it) }
        parseReminderOverrides(valueOf(params, "defaultReminders", "calendarReminders", "reminders"))
            ?.let { calendar.put("defaultReminders", it) }
        parseNotificationSettings(valueOf(params, "notifications", "notificationSettings"))
            ?.let { calendar.put("notificationSettings", it) }
        return calendar
    }

    suspend fun listEvents(params: Map<String, String>): JSONObject {
        val body = JSONObject().apply {
            put("userId", getUserId())
            valueOf(params, "calendarId", "calendar")?.let { put("calendarId", it) }
            valueOf(params, "minTime", "timeMin")?.let { put("timeMin", it) }
            valueOf(params, "maxTime", "timeMax")?.let { put("timeMax", it) }
            valueOf(params, "q", "query", "search")?.let { put("q", it) }
            valueOf(params, "maxResults")?.toIntOrNull()?.let { put("maxResults", it) }
        }
        val result = postJson("/calendar/events/list", body)
        if (!result.has("events") && result.has("items")) {
            result.put("events", result.optJSONArray("items") ?: JSONArray())
        }
        return result
    }

    suspend fun createEvent(params: Map<String, String>): JSONObject {
        val eventDetails = buildEventDetails(params)
        val start = eventDetails.optJSONObject("start")
        val end = eventDetails.optJSONObject("end")
        if (start == null || end == null) {
            return baseError("Start and end are required")
        }
        val body = JSONObject().apply {
            put("userId", getUserId())
            valueOf(params, "calendarId", "calendar")?.let { put("calendarId", it) }
            valueOf(params, "sendUpdates")?.let { put("sendUpdates", it) }
            valueOf(params, "conferenceDataVersion")?.toIntOrNull()?.let { put("conferenceDataVersion", it) }
            put("eventDetails", eventDetails)
        }
        return postJson("/calendar/events/create", body)
    }

    suspend fun updateEvent(eventId: String, params: Map<String, String>): JSONObject {
        val eventDetails = buildEventDetails(params)
        if (eventDetails.length() == 0) {
            return baseError("No update fields provided")
        }
        val body = JSONObject().apply {
            put("userId", getUserId())
            put("eventId", eventId)
            valueOf(params, "calendarId", "calendar")?.let { put("calendarId", it) }
            valueOf(params, "sendUpdates")?.let { put("sendUpdates", it) }
            valueOf(params, "conferenceDataVersion")?.toIntOrNull()?.let { put("conferenceDataVersion", it) }
            put("eventDetails", eventDetails)
        }
        return postJson("/calendar/events/update", body)
    }

    suspend fun createMeetLink(eventId: String, params: Map<String, String>): JSONObject {
        val merged = params.toMutableMap()
        if (valueOf(merged, "createMeetLink", "googleMeet", "meet") == null) {
            merged["createMeetLink"] = "true"
        }
        if (valueOf(merged, "conferenceDataVersion") == null) {
            merged["conferenceDataVersion"] = "1"
        }
        return updateEvent(eventId, merged)
    }

    suspend fun deleteEvent(eventId: String, params: Map<String, String> = emptyMap()): JSONObject {
        val body = JSONObject().apply {
            put("userId", getUserId())
            put("eventId", eventId)
            valueOf(params, "calendarId", "calendar")?.let { put("calendarId", it) }
            valueOf(params, "sendUpdates")?.let { put("sendUpdates", it) }
        }
        return postJson("/calendar/events/delete", body)
    }

    suspend fun listTasks(params: Map<String, String>): JSONObject {
        val body = JSONObject().apply {
            put("userId", getUserId())
            valueOf(params, "tasklistId", "taskListId", "tasklist")?.let { put("tasklistId", it) }
            valueOf(params, "dueMin")?.let { put("dueMin", it) }
            valueOf(params, "dueMax")?.let { put("dueMax", it) }
            valueOf(params, "completedMin")?.let { put("completedMin", it) }
            valueOf(params, "completedMax")?.let { put("completedMax", it) }
            valueOf(params, "updatedMin")?.let { put("updatedMin", it) }
            valueOf(params, "maxResults")?.toIntOrNull()?.let { put("maxResults", it) }
            boolValue(params, "showCompleted")?.let { put("showCompleted", it) }
            boolValue(params, "showDeleted")?.let { put("showDeleted", it) }
            boolValue(params, "showHidden")?.let { put("showHidden", it) }
            boolValue(params, "showAssigned")?.let { put("showAssigned", it) }
        }
        val result = postJson("/calendar/tasks/list", body)
        if (!result.has("tasks") && result.has("items")) {
            result.put("tasks", result.optJSONArray("items") ?: JSONArray())
        }
        return result
    }

    suspend fun createTask(params: Map<String, String>): JSONObject {
        val taskDetails = buildTaskDetails(params)
        if (!taskDetails.has("title")) {
            return baseError("Task title is required")
        }
        val body = JSONObject().apply {
            put("userId", getUserId())
            valueOf(params, "tasklistId", "taskListId", "tasklist")?.let { put("tasklistId", it) }
            valueOf(params, "parent")?.let { put("parent", it) }
            valueOf(params, "previous")?.let { put("previous", it) }
            put("taskDetails", taskDetails)
        }
        return postJson("/calendar/tasks/create", body)
    }

    suspend fun updateTask(taskId: String, params: Map<String, String>): JSONObject {
        val taskDetails = buildTaskDetails(params)
        if (taskDetails.length() == 0) {
            return baseError("No task fields provided")
        }
        val body = JSONObject().apply {
            put("userId", getUserId())
            put("taskId", taskId)
            valueOf(params, "tasklistId", "taskListId", "tasklist")?.let { put("tasklistId", it) }
            put("taskDetails", taskDetails)
        }
        return postJson("/calendar/tasks/update", body)
    }

    suspend fun deleteTask(taskId: String, params: Map<String, String> = emptyMap()): JSONObject {
        val body = JSONObject().apply {
            put("userId", getUserId())
            put("taskId", taskId)
            valueOf(params, "tasklistId", "taskListId", "tasklist")?.let { put("tasklistId", it) }
        }
        return postJson("/calendar/tasks/delete", body)
    }

    suspend fun moveTask(taskId: String, params: Map<String, String>): JSONObject {
        if (
            valueOf(params, "destinationTasklist").isNullOrBlank() &&
                valueOf(params, "parent").isNullOrBlank() &&
                valueOf(params, "previous").isNullOrBlank()
        ) {
            return baseError("destinationTasklist, parent, or previous is required")
        }

        val body = JSONObject().apply {
            put("userId", getUserId())
            put("taskId", taskId)
            valueOf(params, "tasklistId", "taskListId", "tasklist")?.let { put("tasklistId", it) }
            valueOf(params, "destinationTasklist")?.let { put("destinationTasklist", it) }
            valueOf(params, "parent")?.let { put("parent", it) }
            valueOf(params, "previous")?.let { put("previous", it) }
        }
        return postJson("/calendar/tasks/move", body)
    }

    suspend fun clearCompletedTasks(params: Map<String, String>): JSONObject {
        val body = JSONObject().apply {
            put("userId", getUserId())
            valueOf(params, "tasklistId", "taskListId", "tasklist")?.let { put("tasklistId", it) }
        }
        return postJson("/calendar/tasks/clear", body)
    }

    suspend fun listTaskLists(params: Map<String, String> = emptyMap()): JSONObject {
        val body = JSONObject().apply {
            put("userId", getUserId())
            valueOf(params, "maxResults")?.toIntOrNull()?.let { put("maxResults", it) }
            valueOf(params, "pageToken")?.let { put("pageToken", it) }
        }
        val result = postJson("/calendar/tasklists/list", body)
        if (!result.has("tasklists") && result.has("items")) {
            result.put("tasklists", result.optJSONArray("items") ?: JSONArray())
        }
        return result
    }

    suspend fun getTaskList(taskListId: String): JSONObject {
        val body = JSONObject().apply {
            put("userId", getUserId())
            put("tasklistId", taskListId)
        }
        return postJson("/calendar/tasklists/get", body)
    }

    suspend fun createTaskList(params: Map<String, String>): JSONObject {
        val taskListDetails = buildTaskListDetails(params)
        if (!taskListDetails.has("title")) {
            return baseError("Task list title is required")
        }
        val body = JSONObject().apply {
            put("userId", getUserId())
            put("tasklistDetails", taskListDetails)
        }
        return postJson("/calendar/tasklists/create", body)
    }

    suspend fun updateTaskList(taskListId: String, params: Map<String, String>): JSONObject {
        val taskListDetails = buildTaskListDetails(params)
        if (taskListDetails.length() == 0) {
            return baseError("No task list fields provided")
        }
        val body = JSONObject().apply {
            put("userId", getUserId())
            put("tasklistId", taskListId)
            put("tasklistDetails", taskListDetails)
        }
        return postJson("/calendar/tasklists/update", body)
    }

    suspend fun deleteTaskList(taskListId: String): JSONObject {
        val body = JSONObject().apply {
            put("userId", getUserId())
            put("tasklistId", taskListId)
        }
        return postJson("/calendar/tasklists/delete", body)
    }

    suspend fun listCalendars(params: Map<String, String> = emptyMap()): JSONObject {
        val body = JSONObject().apply {
            put("userId", getUserId())
            valueOf(params, "minAccessRole")?.let { put("minAccessRole", it) }
            valueOf(params, "maxResults")?.toIntOrNull()?.let { put("maxResults", it) }
            valueOf(params, "pageToken")?.let { put("pageToken", it) }
            valueOf(params, "syncToken")?.let { put("syncToken", it) }
            boolValue(params, "showDeleted")?.let { put("showDeleted", it) }
            boolValue(params, "showHidden")?.let { put("showHidden", it) }
        }
        val result = postJson("/calendar/calendar-list/list", body)
        if (!result.has("calendars") && result.has("items")) {
            result.put("calendars", result.optJSONArray("items") ?: JSONArray())
        }
        return result
    }

    suspend fun getCalendar(calendarId: String): JSONObject {
        val body = JSONObject().apply {
            put("userId", getUserId())
            put("calendarId", calendarId)
        }
        return postJson("/calendar/calendar-list/get", body)
    }

    suspend fun updateCalendar(calendarId: String, params: Map<String, String>): JSONObject {
        val calendarDetails = buildCalendarDetails(params)
        if (calendarDetails.length() == 0) {
            return baseError("No calendar fields provided")
        }
        val body = JSONObject().apply {
            put("userId", getUserId())
            put("calendarId", calendarId)
            boolValue(params, "colorRgbFormat")?.let { put("colorRgbFormat", it) }
            put("calendarDetails", calendarDetails)
        }
        return postJson("/calendar/calendar-list/update", body)
    }
}
