package com.message.bulksend.autorespond.settings

import android.content.Context
import android.content.SharedPreferences
import kotlin.random.Random

/**
 * Delay type options for auto-reply
 */
enum class ReplyDelayType {
    NO_DELAY,           // Instant reply (0 seconds)
    DELAY_5_SEC,        // Fixed 5 seconds delay
    DELAY_10_SEC,       // Fixed 10 seconds delay
    DELAY_15_SEC,       // Fixed 15 seconds delay
    RANDOM_5_TO_15      // Random delay between 5-15 seconds
}

/**
 * Manages reply delay settings for auto-reply feature
 */
class ReplyDelayManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "reply_delay_settings",
        Context.MODE_PRIVATE
    )
    
    companion object {
        private const val KEY_REPLY_DELAY_TYPE = "reply_delay_type"
        private const val KEY_CUSTOM_MIN_DELAY = "custom_min_delay"
        private const val KEY_CUSTOM_MAX_DELAY = "custom_max_delay"
    }
    
    /**
     * Get current delay type
     */
    fun getDelayType(): ReplyDelayType {
        val delayString = prefs.getString(KEY_REPLY_DELAY_TYPE, ReplyDelayType.NO_DELAY.name)
        return try {
            ReplyDelayType.valueOf(delayString ?: ReplyDelayType.NO_DELAY.name)
        } catch (e: Exception) {
            ReplyDelayType.NO_DELAY
        }
    }
    
    /**
     * Set delay type
     */
    fun setDelayType(delayType: ReplyDelayType) {
        prefs.edit().putString(KEY_REPLY_DELAY_TYPE, delayType.name).apply()
    }

    
    /**
     * Get delay in milliseconds based on current delay type
     */
    fun getDelayMillis(): Long {
        return when (getDelayType()) {
            ReplyDelayType.NO_DELAY -> 0L
            ReplyDelayType.DELAY_5_SEC -> 5000L
            ReplyDelayType.DELAY_10_SEC -> 10000L
            ReplyDelayType.DELAY_15_SEC -> 15000L
            ReplyDelayType.RANDOM_5_TO_15 -> Random.nextLong(5000L, 15001L)
        }
    }
    
    /**
     * Get delay in seconds based on current delay type
     */
    fun getDelaySeconds(): Int {
        return when (getDelayType()) {
            ReplyDelayType.NO_DELAY -> 0
            ReplyDelayType.DELAY_5_SEC -> 5
            ReplyDelayType.DELAY_10_SEC -> 10
            ReplyDelayType.DELAY_15_SEC -> 15
            ReplyDelayType.RANDOM_5_TO_15 -> Random.nextInt(5, 16)
        }
    }
    
    /**
     * Check if delay is enabled
     */
    fun isDelayEnabled(): Boolean {
        return getDelayType() != ReplyDelayType.NO_DELAY
    }
    
    /**
     * Get display text for current delay type (Hindi)
     */
    fun getDelayDisplayText(): String {
        return when (getDelayType()) {
            ReplyDelayType.NO_DELAY -> "कोई देरी नहीं (तुरंत)"
            ReplyDelayType.DELAY_5_SEC -> "5 सेकंड"
            ReplyDelayType.DELAY_10_SEC -> "10 सेकंड"
            ReplyDelayType.DELAY_15_SEC -> "15 सेकंड"
            ReplyDelayType.RANDOM_5_TO_15 -> "5-15 सेकंड (रैंडम)"
        }
    }
    
    /**
     * Get display text for current delay type (English)
     */
    fun getDelayDisplayTextEnglish(): String {
        return when (getDelayType()) {
            ReplyDelayType.NO_DELAY -> "No Delay (Instant)"
            ReplyDelayType.DELAY_5_SEC -> "5 Seconds"
            ReplyDelayType.DELAY_10_SEC -> "10 Seconds"
            ReplyDelayType.DELAY_15_SEC -> "15 Seconds"
            ReplyDelayType.RANDOM_5_TO_15 -> "5-15 Seconds (Random)"
        }
    }
    
    /**
     * Get all delay options for UI dropdown
     */
    fun getAllDelayOptions(): List<Pair<ReplyDelayType, String>> {
        return listOf(
            ReplyDelayType.NO_DELAY to "No Delay (Instant)",
            ReplyDelayType.DELAY_5_SEC to "5 Seconds",
            ReplyDelayType.DELAY_10_SEC to "10 Seconds",
            ReplyDelayType.DELAY_15_SEC to "15 Seconds",
            ReplyDelayType.RANDOM_5_TO_15 to "5-15 Seconds (Random)"
        )
    }
}
