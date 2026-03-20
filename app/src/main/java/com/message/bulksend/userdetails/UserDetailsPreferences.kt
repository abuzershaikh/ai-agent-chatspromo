package com.message.bulksend.userdetails

import android.content.Context
import android.content.SharedPreferences

class UserDetailsPreferences(context: Context) {
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREF_NAME = "user_details_prefs"
        
        // Keys for user details
        private const val KEY_USER_ID = "user_id"
        private const val KEY_FULL_NAME = "full_name"
        private const val KEY_EMAIL = "email"
        private const val KEY_PHONE_NUMBER = "phone_number"
        private const val KEY_BUSINESS_NAME = "business_name"
        private const val KEY_COUNTRY_CODE = "country_code"
        private const val KEY_COUNTRY_ISO = "country_iso"
        private const val KEY_COUNTRY = "country"
        private const val KEY_REFERRAL_CODE = "referral_code"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val KEY_IS_DETAILS_SAVED = "is_details_saved"
        private const val KEY_LAST_SEEN_DATE = "last_seen_date"
    }
    
    // Save user details
    fun saveUserDetails(
        userId: String,
        fullName: String,
        email: String,
        phoneNumber: String,
        businessName: String,
        countryCode: String,
        countryIso: String,
        country: String,
        referralCode: String? = null
    ) {
        with(sharedPreferences.edit()) {
            putString(KEY_USER_ID, userId)
            putString(KEY_FULL_NAME, fullName)
            putString(KEY_EMAIL, email)
            putString(KEY_PHONE_NUMBER, phoneNumber)
            putString(KEY_BUSINESS_NAME, businessName)
            putString(KEY_COUNTRY_CODE, countryCode)
            putString(KEY_COUNTRY_ISO, countryIso)
            putString(KEY_COUNTRY, country)
            putString(KEY_REFERRAL_CODE, referralCode)
            putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            putBoolean(KEY_IS_DETAILS_SAVED, true)
            apply()
        }
    }
    
    // Get user details
    fun getUserId(): String? = sharedPreferences.getString(KEY_USER_ID, null)
    
    fun getFullName(): String? = sharedPreferences.getString(KEY_FULL_NAME, null)
    
    fun getEmail(): String? = sharedPreferences.getString(KEY_EMAIL, null)
    
    fun getPhoneNumber(): String? = sharedPreferences.getString(KEY_PHONE_NUMBER, null)
    
    fun getBusinessName(): String? = sharedPreferences.getString(KEY_BUSINESS_NAME, null)
    
    fun getCountryCode(): String? = sharedPreferences.getString(KEY_COUNTRY_CODE, null)
    
    fun getCountryIso(): String? = sharedPreferences.getString(KEY_COUNTRY_ISO, null)
    
    fun getCountry(): String? = sharedPreferences.getString(KEY_COUNTRY, null)
    
    fun getReferralCode(): String? = sharedPreferences.getString(KEY_REFERRAL_CODE, null)
    
    fun getTimestamp(): Long = sharedPreferences.getLong(KEY_TIMESTAMP, 0L)
    
    fun isDetailsSaved(): Boolean = sharedPreferences.getBoolean(KEY_IS_DETAILS_SAVED, false)
    
    // Get all user details as a map
    fun getAllUserDetails(): Map<String, Any?> {
        return mapOf(
            "userId" to getUserId(),
            "fullName" to getFullName(),
            "email" to getEmail(),
            "phoneNumber" to getPhoneNumber(),
            "businessName" to getBusinessName(),
            "countryCode" to getCountryCode(),
            "countryIso" to getCountryIso(),
            "country" to getCountry(),
            "referralCode" to getReferralCode(),
            "timestamp" to getTimestamp(),
            "isDetailsSaved" to isDetailsSaved()
        )
    }
    
    // Clear all user details
    fun clearUserDetails() {
        with(sharedPreferences.edit()) {
            remove(KEY_USER_ID)
            remove(KEY_FULL_NAME)
            remove(KEY_EMAIL)
            remove(KEY_PHONE_NUMBER)
            remove(KEY_BUSINESS_NAME)
            remove(KEY_COUNTRY_CODE)
            remove(KEY_COUNTRY_ISO)
            remove(KEY_COUNTRY)
            remove(KEY_REFERRAL_CODE)
            remove(KEY_TIMESTAMP)
            remove(KEY_IS_DETAILS_SAVED)
            apply()
        }
    }
    
    // Update specific fields
    fun updateFullName(fullName: String) {
        sharedPreferences.edit().putString(KEY_FULL_NAME, fullName).apply()
    }
    
    fun updateBusinessName(businessName: String) {
        sharedPreferences.edit().putString(KEY_BUSINESS_NAME, businessName).apply()
    }
    
    fun updatePhoneNumber(phoneNumber: String) {
        sharedPreferences.edit().putString(KEY_PHONE_NUMBER, phoneNumber).apply()
    }
    
    fun updateReferralCode(referralCode: String?) {
        sharedPreferences.edit().putString(KEY_REFERRAL_CODE, referralCode).apply()
    }
    
    // Last seen tracking - only updates once per day
    fun getLastSeenDate(): String? = sharedPreferences.getString(KEY_LAST_SEEN_DATE, null)
    
    fun setLastSeenDate(date: String) {
        sharedPreferences.edit().putString(KEY_LAST_SEEN_DATE, date).apply()
    }
    
    /**
     * Check if last seen needs to be updated today
     * Returns true if last seen was not today (needs update)
     */
    fun shouldUpdateLastSeen(): Boolean {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
        val lastSeenDate = getLastSeenDate()
        return lastSeenDate != today
    }
}