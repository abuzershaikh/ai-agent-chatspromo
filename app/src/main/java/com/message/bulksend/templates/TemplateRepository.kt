package com.message.bulksend.templates

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * Yeh class SharedPreferences ka istemal karke user ke banaye hue templates ko save, load, update, aur delete karti hai.
 */
class TemplateRepository(private val context: Context) {

    private val gson = Gson()
    private val prefs = context.getSharedPreferences("user_templates_prefs", Context.MODE_PRIVATE)
    private val templatesKey = "my_templates"

    /**
     * SharedPreferences se sabhi templates ko load karta hai.
     */
    fun loadTemplates(): List<Template> {
        val jsonString = prefs.getString(templatesKey, null)
        return if (jsonString != null) {
            try {
                val type = object : TypeToken<List<Template>>() {}.type
                gson.fromJson(jsonString, type)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    /**
     * ID ke basis par ek single template load karta hai.
     */
    fun getTemplateById(id: String): Template? {
        return loadTemplates().find { it.id == id }
    }

    /**
     * Ek naye template ko list mein add karke save karta hai.
     */
    fun saveTemplate(name: String, message: String, mediaUri: String?) {
        val currentTemplates = loadTemplates().toMutableList()
        val newTemplate = Template(
            id = UUID.randomUUID().toString(),
            name = name,
            message = message,
            mediaUri = mediaUri,
            timestamp = System.currentTimeMillis()
        )
        currentTemplates.add(newTemplate)
        saveTemplatesList(currentTemplates)
    }

    /**
     * Ek maujooda template ko update karta hai.
     */
    fun updateTemplate(template: Template) {
        val currentTemplates = loadTemplates().toMutableList()
        val index = currentTemplates.indexOfFirst { it.id == template.id }
        if (index != -1) {
            currentTemplates[index] = template.copy(timestamp = System.currentTimeMillis())
            saveTemplatesList(currentTemplates)
        }
    }

    /**
     * Chune gaye IDs ke templates ko delete karta hai.
     */
    fun deleteTemplates(ids: Set<String>) {
        val currentTemplates = loadTemplates().toMutableList()
        currentTemplates.removeAll { it.id in ids }
        saveTemplatesList(currentTemplates)
    }

    /**
     * Templates ki poori list ko SharedPreferences mein save karta hai.
     */
    private fun saveTemplatesList(templates: List<Template>) {
        val jsonString = gson.toJson(templates)
        prefs.edit().putString(templatesKey, jsonString).apply()
    }
}

