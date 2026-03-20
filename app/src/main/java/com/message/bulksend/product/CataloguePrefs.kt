package com.message.bulksend.product

import android.content.Context
import org.json.JSONArray

/**
 * Stores user preferences for the catalogue:
 * - itemLabel: What to call items (Product, Service, Course, etc.)
 * - categories: User-defined category list
 */
class CataloguePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("catalogue_prefs", Context.MODE_PRIVATE)
    
    var itemLabel: String
        get() = prefs.getString("item_label", "Product") ?: "Product"
        set(value) = prefs.edit().putString("item_label", value).apply()
    
    fun getCategories(): List<String> {
        val json = prefs.getString("categories", "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) { emptyList() }
    }
    
    fun addCategory(category: String) {
        val current = getCategories().toMutableList()
        if (category.isNotBlank() && category !in current) {
            current.add(category)
            saveCategories(current)
        }
    }
    
    fun removeCategory(category: String) {
        val current = getCategories().toMutableList()
        current.remove(category)
        saveCategories(current)
    }
    
    private fun saveCategories(list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString("categories", arr.toString()).apply()
    }
}
