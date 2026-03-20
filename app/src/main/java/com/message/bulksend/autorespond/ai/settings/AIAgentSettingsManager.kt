package com.message.bulksend.autorespond.ai.settings

import android.content.Context
import android.content.SharedPreferences

class AIAgentSettingsManager(context: Context) {
    companion object {
        const val PROMPT_MODE_SIMPLE = "SIMPLE_PROMPT"
        const val PROMPT_MODE_STEP_FLOW = "STEP_FLOW"
        const val SHEET_WRITE_MODE_TABLE = "TABLE_SHEET"
        const val SHEET_WRITE_MODE_GOOGLE = "GOOGLE_SHEET"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ai_agent_settings", Context.MODE_PRIVATE)

    var isAgentEnabled: Boolean
        get() = prefs.getBoolean("is_agent_enabled", false)
        set(value) = prefs.edit().putBoolean("is_agent_enabled", value).apply()

    var agentName: String
        get() = prefs.getString("agent_name", "AI Assistant") ?: "AI Assistant"
        set(value) = prefs.edit().putString("agent_name", value).apply()

    var askCurrentUserName: Boolean
        get() = prefs.getBoolean("ask_user_name", true)
        set(value) = prefs.edit().putBoolean("ask_user_name", value).apply()

    var reAskNameIfNotGiven: Boolean
        get() = prefs.getBoolean("re_ask_name_if_not_given", false)
        set(value) = prefs.edit().putBoolean("re_ask_name_if_not_given", value).apply()

    var requireNameToContinue: Boolean
        get() = prefs.getBoolean("require_name_to_continue", false)
        set(value) = prefs.edit().putBoolean("require_name_to_continue", value).apply()

    var enableMemory: Boolean
        get() = prefs.getBoolean("enable_memory", true)
        set(value) = prefs.edit().putBoolean("enable_memory", value).apply()

    var enableDataSheetLookup: Boolean
        get() = prefs.getBoolean("enable_data_sheet_lookup", true)
        set(value) = prefs.edit().putBoolean("enable_data_sheet_lookup", value).apply()

    var enableProductLookup: Boolean
        get() = prefs.getBoolean("enable_product_lookup", true)
        set(value) = prefs.edit().putBoolean("enable_product_lookup", value).apply()

    var customSystemPrompt: String
        get() = prefs.getString("custom_system_prompt", "") ?: ""
        set(value) = prefs.edit().putString("custom_system_prompt", value).apply()

    var sheetInstruction: String
        get() = prefs.getString("sheet_instruction", "") ?: ""
        set(value) = prefs.edit().putString("sheet_instruction", value).apply()

    var memoryInstruction: String
        get() = prefs.getString("memory_instruction", "") ?: ""
        set(value) = prefs.edit().putString("memory_instruction", value).apply()

    var advancedInstruction: String
        get() = prefs.getString("advanced_instruction", "") ?: ""
        set(value) = prefs.edit().putString("advanced_instruction", value).apply()

    var productInstruction: String
        get() = prefs.getString("product_instruction", "") ?: ""
        set(value) = prefs.edit().putString("product_instruction", value).apply()

    var activeTemplate: String
        get() = prefs.getString("active_template", "NONE") ?: "NONE"
        set(value) = prefs.edit().putString("active_template", value).apply()

    var customTemplateName: String
        get() = prefs.getString("custom_template_name", "Custom AI Template") ?: "Custom AI Template"
        set(value) = prefs.edit().putString("custom_template_name", value).apply()

    var customTemplateGoal: String
        get() = prefs.getString("custom_template_goal", "") ?: ""
        set(value) = prefs.edit().putString("custom_template_goal", value).apply()

    var customTemplateTone: String
        get() = prefs.getString("custom_template_tone", "") ?: ""
        set(value) = prefs.edit().putString("custom_template_tone", value).apply()

    var customTemplateInstructions: String
        get() = prefs.getString("custom_template_instructions", "") ?: ""
        set(value) = prefs.edit().putString("custom_template_instructions", value).apply()

    var customTemplatePromptMode: String
        get() = prefs.getString("custom_template_prompt_mode", PROMPT_MODE_SIMPLE)
            ?: PROMPT_MODE_SIMPLE
        set(value) = prefs.edit().putString("custom_template_prompt_mode", value).apply()

    var customTemplateTaskModeEnabled: Boolean
        get() = prefs.getBoolean("custom_template_task_mode_enabled", false)
        set(value) = prefs.edit().putBoolean("custom_template_task_mode_enabled", value).apply()

    var customTemplateRepeatCounterEnabled: Boolean
        get() = prefs.getBoolean("custom_template_repeat_counter_enabled", false)
        set(value) = prefs.edit().putBoolean("custom_template_repeat_counter_enabled", value).apply()

    var customTemplateRepeatCounterLimit: Int
        get() = prefs.getInt("custom_template_repeat_counter_limit", 0)
        set(value) = prefs.edit()
            .putInt("custom_template_repeat_counter_limit", value.coerceAtLeast(0))
            .apply()

    var customTemplateRepeatCounterOwnerNotifyEnabled: Boolean
        get() = prefs.getBoolean("custom_template_repeat_counter_owner_notify_enabled", false)
        set(value) = prefs.edit()
            .putBoolean("custom_template_repeat_counter_owner_notify_enabled", value)
            .apply()

    var customTemplateRepeatCounterOwnerPhone: String
        get() = prefs.getString("custom_template_repeat_counter_owner_phone", "") ?: ""
        set(value) = prefs.edit()
            .putString("custom_template_repeat_counter_owner_phone", value.trim())
            .apply()

    var customTemplateEnablePaymentTool: Boolean
        get() = prefs.getBoolean("custom_template_enable_payment_tool", true)
        set(value) = prefs.edit().putBoolean("custom_template_enable_payment_tool", value).apply()

    var customTemplateEnableDocumentTool: Boolean
        get() = prefs.getBoolean("custom_template_enable_document_tool", true)
        set(value) = prefs.edit().putBoolean("custom_template_enable_document_tool", value).apply()

    var customTemplateEnableAgentFormTool: Boolean
        get() = prefs.getBoolean("custom_template_enable_agent_form_tool", true)
        set(value) = prefs.edit().putBoolean("custom_template_enable_agent_form_tool", value).apply()

    var customTemplateEnableSpeechTool: Boolean
        get() = prefs.getBoolean("custom_template_enable_speech_tool", false)
        set(value) = prefs.edit().putBoolean("custom_template_enable_speech_tool", value).apply()

    var customTemplateEnablePaymentVerificationTool: Boolean
        get() = prefs.getBoolean("custom_template_enable_payment_verification_tool", true)
        set(value) = prefs.edit()
            .putBoolean("custom_template_enable_payment_verification_tool", value)
            .apply()

    var customTemplateEnableAutonomousCatalogueSend: Boolean
        get() = prefs.getBoolean("custom_template_enable_autonomous_catalogue_send", false)
        set(value) = prefs.edit()
            .putBoolean("custom_template_enable_autonomous_catalogue_send", value)
            .apply()

    var customTemplateEnableSheetReadTool: Boolean
        get() = prefs.getBoolean("custom_template_enable_sheet_read_tool", true)
        set(value) = prefs.edit().putBoolean("custom_template_enable_sheet_read_tool", value).apply()

    var customTemplateEnableSheetWriteTool: Boolean
        get() = prefs.getBoolean("custom_template_enable_sheet_write_tool", true)
        set(value) = prefs.edit().putBoolean("custom_template_enable_sheet_write_tool", value).apply()

    var customTemplateSheetFolderName: String
        get() = prefs.getString("custom_template_sheet_folder_name", "") ?: ""
        set(value) = prefs.edit().putString("custom_template_sheet_folder_name", value).apply()

    var customTemplateReadSheetName: String
        get() = prefs.getString("custom_template_read_sheet_name", "Agent Read Sheet")
            ?: "Agent Read Sheet"
        set(value) = prefs.edit().putString("custom_template_read_sheet_name", value).apply()

    var customTemplateWriteSheetName: String
        get() = prefs.getString("custom_template_write_sheet_name", "Agent Write Sheet")
            ?: "Agent Write Sheet"
        set(value) = prefs.edit().putString("custom_template_write_sheet_name", value).apply()

    var customTemplateSalesSheetName: String
        get() = prefs.getString("custom_template_sales_sheet_name", "Product Sales Sheet")
            ?: "Product Sales Sheet"
        set(value) = prefs.edit().putString("custom_template_sales_sheet_name", value).apply()

    var customTemplateReferenceSheetName: String
        get() = prefs.getString("custom_template_reference_sheet_name", "Agent Read Sheet")
            ?: "Agent Read Sheet"
        set(value) = prefs.edit().putString("custom_template_reference_sheet_name", value).apply()

    var customTemplateWriteSheetColumns: String
        get() = prefs.getString("custom_template_write_sheet_columns", "") ?: ""
        set(value) = prefs.edit().putString("custom_template_write_sheet_columns", value).apply()

    var customTemplateGoogleSheetId: String
        get() = prefs.getString("custom_template_google_sheet_id", "") ?: ""
        set(value) = prefs.edit().putString("custom_template_google_sheet_id", value.trim()).apply()

    var customTemplateGoogleSheetName: String
        get() = prefs.getString("custom_template_google_sheet_name", "") ?: ""
        set(value) = prefs.edit().putString("custom_template_google_sheet_name", value.trim()).apply()

    var customTemplateGoogleWriteSheetName: String
        get() = prefs.getString("custom_template_google_write_sheet_name", "") ?: ""
        set(value) = prefs.edit()
            .putString("custom_template_google_write_sheet_name", value.trim())
            .apply()

    var customTemplateWriteStorageMode: String
        get() = prefs.getString("custom_template_write_storage_mode", SHEET_WRITE_MODE_TABLE)
            ?: SHEET_WRITE_MODE_TABLE
        set(value) = prefs.edit().putString(
            "custom_template_write_storage_mode",
            if (
                value.equals(SHEET_WRITE_MODE_TABLE, ignoreCase = true) ||
                value.equals(SHEET_WRITE_MODE_GOOGLE, ignoreCase = true)
            ) {
                value.uppercase()
            } else {
                SHEET_WRITE_MODE_TABLE
            }
        ).apply()

    var customTemplateWriteFieldSchema: String
        get() = prefs.getString("custom_template_write_field_schema", "") ?: ""
        set(value) = prefs.edit().putString("custom_template_write_field_schema", value).apply()

    var customTemplateBusinessKnowledgeJson: String
        get() = prefs.getString("custom_template_business_knowledge_json", "") ?: ""
        set(value) = prefs.edit().putString("custom_template_business_knowledge_json", value).apply()

    var clinicName: String
        get() = prefs.getString("clinic_name", "My Clinic") ?: "My Clinic"
        set(value) = prefs.edit().putString("clinic_name", value).apply()

    var clinicOpenTime: String
        get() = prefs.getString("clinic_open_time", "09:00") ?: "09:00"
        set(value) = prefs.edit().putString("clinic_open_time", value).apply()

    var clinicCloseTime: String
        get() = prefs.getString("clinic_close_time", "18:00") ?: "18:00"
        set(value) = prefs.edit().putString("clinic_close_time", value).apply()

    var clinicAddress: String
        get() = prefs.getString("clinic_address", "123 Main St, City") ?: "123 Main St, City"
        set(value) = prefs.edit().putString("clinic_address", value).apply()

    var weeklySchedule: String
        get() = prefs.getString(
            "clinic_weekly_schedule",
            """{"Mon":"OPEN","Tue":"OPEN","Wed":"OPEN","Thu":"OPEN","Fri":"OPEN","Sat":"HALF","Sun":"CLOSED"}"""
        ) ?: """{"Mon":"OPEN","Tue":"OPEN","Wed":"OPEN","Thu":"OPEN","Fri":"OPEN","Sat":"HALF","Sun":"CLOSED"}"""
        set(value) = prefs.edit().putString("clinic_weekly_schedule", value).apply()

    var halfDayCloseTime: String
        get() = prefs.getString("clinic_half_day_close", "13:00") ?: "13:00"
        set(value) = prefs.edit().putString("clinic_half_day_close", value).apply()

    var holidays: String
        get() = prefs.getString("clinic_holidays", "[]") ?: "[]"
        set(value) = prefs.edit().putString("clinic_holidays", value).apply()

    var confirmationTemplate: String
        get() = prefs.getString(
            "clinic_confirmation_template",
            "*Appointment Confirmed*\n\nName: {name}\nDoctor: {doctor}\nDate: {date}\nTime: {time}\nClinic Address: {address}\n\nPlease come 10 minutes early."
        ) ?: ""
        set(value) = prefs.edit().putString("clinic_confirmation_template", value).apply()

    var cancellationTemplate: String
        get() = prefs.getString(
            "clinic_cancellation_template",
            "*Appointment Cancelled*\n\nName: {name}\nDoctor: {doctor}\nWas scheduled: {date}\nTime: {time}\n\nIf you'd like to reschedule, please let us know."
        ) ?: ""
        set(value) = prefs.edit().putString("clinic_cancellation_template", value).apply()

    var afterHoursMessage: String
        get() = prefs.getString(
            "clinic_after_hours_message",
            "Thank you for contacting us.\n\nOur clinic is currently closed.\nHours: {open} - {close}\n\nWe'll respond when we reopen. For emergencies, please visit the nearest hospital."
        ) ?: ""
        set(value) = prefs.edit().putString("clinic_after_hours_message", value).apply()

    var clinicReminderEnabled: Boolean
        get() = prefs.getBoolean("clinic_reminder_enabled", false)
        set(value) = prefs.edit().putBoolean("clinic_reminder_enabled", value).apply()

    var clinicReminderTimeBefore: Int
        get() = prefs.getInt("clinic_reminder_time_before", 60)
        set(value) = prefs.edit().putInt("clinic_reminder_time_before", value).apply()

    var clinicReminderTemplate: String
        get() = prefs.getString(
            "clinic_reminder_template",
            "*Appointment Reminder*\n\nHello {name}, just a reminder for your appointment with {doctor} tomorrow at {time}.\n\nPlease arrive 10 minutes early."
        ) ?: ""
        set(value) = prefs.edit().putString("clinic_reminder_template", value).apply()

    var trainingData: String
        get() = prefs.getString("training_data", "") ?: ""
        set(value) = prefs.edit().putString("training_data", value).apply()

    var customTemplateEnableGoogleCalendarTool: Boolean
        get() = prefs.getBoolean("custom_template_enable_google_calendar_tool", false)
        set(value) = prefs.edit()
            .putBoolean("custom_template_enable_google_calendar_tool", value)
            .apply()

    var customTemplateEnableGoogleGmailTool: Boolean
        get() = prefs.getBoolean("custom_template_enable_google_gmail_tool", false)
        set(value) = prefs.edit()
            .putBoolean("custom_template_enable_google_gmail_tool", value)
            .apply()
}
