package com.message.bulksend.autorespond.ai.customtask.ui

import android.content.Context
import com.message.bulksend.aiagent.tools.agentform.AgentFormPredefinedTemplates
import com.message.bulksend.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.LinkedHashSet
import java.util.Locale

data class AgentFormTemplateOption(
    val key: String,
    val title: String,
    val isCustom: Boolean
)

/**
 * Provides Agent Form template options for task editor dropdowns.
 */
object AgentFormTemplatePickerRepository {

    private data class PredefinedTemplate(
        val key: String,
        val formId: String,
        val title: String
    )

    private val predefinedTemplates = listOf(
        PredefinedTemplate(
            key = "ADDRESS_LOCATION",
            formId = "preset-address-maps-fillup",
            title = "Address Fillup with Google Maps"
        ),
        PredefinedTemplate(
            key = "ADDRESS_BASIC",
            formId = "preset-address-verification",
            title = "Address Verification"
        ),
        PredefinedTemplate(
            key = "CONTACT_VERIFY",
            formId = "preset-contact-save-verification",
            title = "Contact Save Verification"
        ),
        PredefinedTemplate(
            key = "EMAIL_SIGNIN",
            formId = "preset-google-signin-email",
            title = "Google Sign-In Email"
        )
    )

    suspend fun loadOptions(context: Context): List<AgentFormTemplateOption> =
        withContext(Dispatchers.IO) {
            // Ensure default templates are available before loading list.
            runCatching { AgentFormPredefinedTemplates.seedIfNeeded(context.applicationContext) }

            val options = mutableListOf<AgentFormTemplateOption>()
            val usedFormIds = LinkedHashSet<String>()

            predefinedTemplates.forEach { template ->
                usedFormIds += template.formId
                options += AgentFormTemplateOption(
                    key = template.key,
                    title = template.title,
                    isCustom = false
                )
            }

            val forms = runCatching {
                AppDatabase.getInstance(context.applicationContext).agentFormDao().getAllFormsOnce()
            }.getOrDefault(emptyList())

            forms.forEach { form ->
                val formId = form.formId.trim()
                if (formId.isBlank() || usedFormIds.contains(formId)) return@forEach
                usedFormIds += formId

                options += AgentFormTemplateOption(
                    key = "FORM_${normalizeIdentifier(formId)}",
                    title = form.title.trim().ifBlank { "Custom Form" },
                    isCustom = true
                )
            }

            options
        }

    private fun normalizeIdentifier(raw: String): String {
        return raw.trim()
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .trim('_')
            .uppercase(Locale.ROOT)
            .take(44)
    }
}
