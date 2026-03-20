package com.message.bulksend.autorespond.settings

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Data class for excluded contact
 */
data class ExcludedContact(
    val id: String = System.currentTimeMillis().toString(),
    val phoneNumber: String,
    val name: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

/**
 * Manages excluded numbers for auto-reply feature
 * Numbers in this list will NOT receive auto-replies
 */
class ExcludeNumberManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "exclude_number_settings",
        Context.MODE_PRIVATE
    )
    private val gson = Gson()
    
    companion object {
        private const val KEY_EXCLUDED_NUMBERS = "excluded_numbers"
        private const val KEY_EXCLUDE_ENABLED = "exclude_enabled"
        private const val KEY_EXCLUDE_SAVED_CONTACTS = "exclude_saved_contacts"
    }
    
    /**
     * Check if exclude feature is enabled
     */
    fun isExcludeEnabled(): Boolean {
        return prefs.getBoolean(KEY_EXCLUDE_ENABLED, true)
    }
    
    /**
     * Enable/disable exclude feature
     */
    fun setExcludeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EXCLUDE_ENABLED, enabled).apply()
    }
    
    /**
     * Check if saved contacts should be excluded
     */
    fun isExcludeSavedContactsEnabled(): Boolean {
        return prefs.getBoolean(KEY_EXCLUDE_SAVED_CONTACTS, false)
    }
    
    /**
     * Enable/disable exclude saved contacts
     */
    fun setExcludeSavedContactsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EXCLUDE_SAVED_CONTACTS, enabled).apply()
    }
    
    /**
     * Check if a number exists in device contacts
     */
    fun isNumberInContacts(phoneNumber: String): Boolean {
        val cleanNumber = cleanPhoneNumber(phoneNumber)
        if (cleanNumber.isEmpty()) return false
        
        try {
            val uri = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
            
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val numberIndex = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val contactNumber = cursor.getString(numberIndex) ?: continue
                    if (matchPhoneNumbers(contactNumber, cleanNumber)) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ExcludeNumberManager", "Error checking contacts: ${e.message}")
        }
        return false
    }
    
    /**
     * Check if sender name exists in device contacts
     */
    fun isSenderInContacts(senderName: String): Boolean {
        if (senderName.isEmpty() || senderName == "Unknown") return false
        
        try {
            val uri = android.provider.ContactsContract.Contacts.CONTENT_URI
            val projection = arrayOf(android.provider.ContactsContract.Contacts.DISPLAY_NAME)
            
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.ContactsContract.Contacts.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val contactName = cursor.getString(nameIndex) ?: continue
                    if (contactName.equals(senderName, ignoreCase = true)) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ExcludeNumberManager", "Error checking contacts: ${e.message}")
        }
        return false
    }
    
    /**
     * Get all excluded contacts
     */
    fun getExcludedContacts(): List<ExcludedContact> {
        val json = prefs.getString(KEY_EXCLUDED_NUMBERS, "[]")
        val type = object : TypeToken<List<ExcludedContact>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    
    /**
     * Save excluded contacts list
     */
    private fun saveExcludedContacts(contacts: List<ExcludedContact>) {
        val json = gson.toJson(contacts)
        prefs.edit().putString(KEY_EXCLUDED_NUMBERS, json).apply()
    }
    
    /**
     * Add a number to exclude list
     */
    fun addExcludedNumber(phoneNumber: String, name: String = ""): Boolean {
        val cleanNumber = cleanPhoneNumber(phoneNumber)
        if (cleanNumber.isEmpty()) return false
        
        val contacts = getExcludedContacts().toMutableList()
        
        // Check if already exists
        if (contacts.any { cleanPhoneNumber(it.phoneNumber) == cleanNumber }) {
            return false
        }
        
        contacts.add(ExcludedContact(
            phoneNumber = cleanNumber,
            name = name
        ))
        saveExcludedContacts(contacts)
        return true
    }
    
    /**
     * Remove a number from exclude list
     */
    fun removeExcludedNumber(id: String) {
        val contacts = getExcludedContacts().toMutableList()
        contacts.removeAll { it.id == id }
        saveExcludedContacts(contacts)
    }
    
    /**
     * Remove by phone number
     */
    fun removeByPhoneNumber(phoneNumber: String) {
        val cleanNumber = cleanPhoneNumber(phoneNumber)
        val contacts = getExcludedContacts().toMutableList()
        contacts.removeAll { cleanPhoneNumber(it.phoneNumber) == cleanNumber }
        saveExcludedContacts(contacts)
    }
    
    /**
     * Check if a number is excluded
     */
    fun isNumberExcluded(phoneNumber: String): Boolean {
        if (!isExcludeEnabled()) return false
        
        val cleanNumber = cleanPhoneNumber(phoneNumber)
        if (cleanNumber.isEmpty()) return false
        
        return getExcludedContacts().any { 
            matchPhoneNumbers(it.phoneNumber, cleanNumber)
        }
    }
    
    /**
     * Check if a sender name is excluded (for saved contacts)
     */
    fun isSenderExcluded(senderName: String): Boolean {
        if (!isExcludeEnabled()) return false
        if (senderName.isEmpty()) return false
        
        return getExcludedContacts().any { 
            it.name.isNotEmpty() && it.name.equals(senderName, ignoreCase = true)
        }
    }
    
    /**
     * Check if number or sender should be excluded
     */
    fun shouldExclude(phoneNumber: String, senderName: String): Boolean {
        // Check manual exclude list
        if (isExcludeEnabled()) {
            if (isNumberExcluded(phoneNumber) || isSenderExcluded(senderName)) {
                return true
            }
        }
        
        // Check saved contacts exclude
        if (isExcludeSavedContactsEnabled()) {
            if (isNumberInContacts(phoneNumber) || isSenderInContacts(senderName)) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Clear all excluded numbers
     */
    fun clearAll() {
        saveExcludedContacts(emptyList())
    }
    
    /**
     * Get count of excluded numbers
     */
    fun getExcludedCount(): Int {
        return getExcludedContacts().size
    }
    
    /**
     * Clean phone number - remove spaces, dashes, etc.
     */
    private fun cleanPhoneNumber(number: String): String {
        return number.replace(Regex("[^0-9+]"), "")
    }
    
    /**
     * Match phone numbers (handles different formats)
     */
    private fun matchPhoneNumbers(num1: String, num2: String): Boolean {
        val clean1 = cleanPhoneNumber(num1)
        val clean2 = cleanPhoneNumber(num2)
        
        if (clean1 == clean2) return true
        
        // Match last 10 digits (for different country code formats)
        val last10_1 = clean1.takeLast(10)
        val last10_2 = clean2.takeLast(10)
        
        return last10_1.length >= 10 && last10_1 == last10_2
    }
}
