package com.message.bulksend.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.message.bulksend.info.SimCountryDetector

data class Country(
    val name: String,
    val flag: String,
    val code: String,
    val dial_code: String
)

object CountryCodeManager {
    private const val PREFS_NAME = "country_code_prefs"
    private const val KEY_SELECTED_COUNTRY = "selected_country"
    private const val KEY_DIAL_CODE = "dial_code"
    private const val KEY_COUNTRY_NAME = "country_name"
    private const val KEY_FLAG = "flag"
    private const val KEY_USER_SELECTED = "user_selected"
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun loadCountries(context: Context): List<Country> {
        return try {
            val json = context.assets.open("country_dial_info.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<Country>>() {}.type
            Gson().fromJson(json, type)
        } catch (e: Exception) {
            android.util.Log.e("CountryCodeManager", "Error loading countries", e)
            emptyList()
        }
    }
    
    fun saveSelectedCountry(context: Context, country: Country, userSelected: Boolean = true) {
        getPrefs(context).edit().apply {
            putString(KEY_DIAL_CODE, country.dial_code)
            putString(KEY_COUNTRY_NAME, country.name)
            putString(KEY_FLAG, country.flag)
            putBoolean(KEY_USER_SELECTED, userSelected)
            apply()
        }
    }
    
    /**
     * Check if user has manually selected a country
     */
    fun isUserSelectedCountry(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_USER_SELECTED, false)
    }
    
    /**
     * Auto-detect country from SIM card MCC and match with country_dial_info.json
     * Returns Country if found, null otherwise
     */
    fun autoDetectCountryFromSim(context: Context): Country? {
        return try {
            val simDetector = SimCountryDetector(context)
            val simCountryInfo = simDetector.getCurrentSimCountry()
            
            if (simCountryInfo != null) {
                // Load countries from JSON and find matching by ISO code
                val countries = loadCountries(context)
                countries.find { it.code.equals(simCountryInfo.iso, ignoreCase = true) }
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Get country - first check if user selected, then try auto-detect from SIM
     * If auto-detected, save it to prefs (but mark as not user-selected)
     * Returns null if nothing found - user will need to select manually
     */
    fun getOrAutoDetectCountry(context: Context): Country? {
        // First check if user has already selected a country
        val savedCountry = getSelectedCountry(context)
        if (savedCountry != null && isUserSelectedCountry(context)) {
            return savedCountry
        }
        
        // Try auto-detect from SIM
        val autoDetected = autoDetectCountryFromSim(context)
        if (autoDetected != null) {
            // Save auto-detected country (mark as not user-selected)
            saveSelectedCountry(context, autoDetected, userSelected = false)
            return autoDetected
        }
        
        // Return saved country if exists (could be previously auto-detected)
        return savedCountry
    }
    
    fun getSelectedCountry(context: Context): Country? {
        val prefs = getPrefs(context)
        val dialCode = prefs.getString(KEY_DIAL_CODE, null)
        val name = prefs.getString(KEY_COUNTRY_NAME, null)
        val flag = prefs.getString(KEY_FLAG, null)
        
        return if (dialCode != null && name != null && flag != null) {
            Country(name, flag, "", dialCode)
        } else {
            null
        }
    }
    
    fun getSelectedDialCode(context: Context): String {
        return getPrefs(context).getString(KEY_DIAL_CODE, "") ?: ""
    }
    
    fun clearSelectedCountry(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
