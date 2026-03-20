package com.message.bulksend.utils

import java.text.SimpleDateFormat
import java.util.*

/**
 * Global DateTime Helper
 * Provides consistent date/time information across the app
 * Used by AI Agent to understand current time context
 */
object DateTimeHelper {
    
    /**
     * Get comprehensive current date/time information
     * Returns formatted string with all details AI needs
     */
    fun getCurrentDateTimeContext(): String {
        val calendar = Calendar.getInstance()
        val now = Date()
        
        // Date formats
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat24 = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeFormat12 = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
        val monthFormat = SimpleDateFormat("MMMM", Locale.getDefault())
        val fullDateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
        
        // Extract components
        val currentDate = dateFormat.format(now)
        val currentTime24 = timeFormat24.format(now)
        val currentTime12 = timeFormat12.format(now)
        val currentDay = dayFormat.format(now)
        val currentMonth = monthFormat.format(now)
        val currentYear = calendar.get(Calendar.YEAR)
        val currentDayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        
        // Time of day
        val timeOfDay = when (currentHour) {
            in 0..5 -> "Late Night"
            in 6..11 -> "Morning"
            in 12..16 -> "Afternoon"
            in 17..20 -> "Evening"
            else -> "Night"
        }
        
        // Build context
        val sb = StringBuilder()
        sb.append("\n[CURRENT DATE & TIME CONTEXT]\n")
        sb.append("📅 Full Date: ${fullDateFormat.format(now)}\n")
        sb.append("📆 Date (YYYY-MM-DD): $currentDate\n")
        sb.append("🗓️ Day: $currentDay\n")
        sb.append("📌 Day of Month: $currentDayOfMonth\n")
        sb.append("📅 Month: $currentMonth\n")
        sb.append("📅 Year: $currentYear\n")
        sb.append("⏰ Time (24h): $currentTime24\n")
        sb.append("🕐 Time (12h): $currentTime12\n")
        sb.append("🌅 Time of Day: $timeOfDay\n")
        sb.append("⏱️ Hour: $currentHour (24h format)\n")
        sb.append("⏱️ Minute: $currentMinute\n\n")
        
        // Tomorrow's date
        val tomorrow = Calendar.getInstance()
        tomorrow.add(Calendar.DAY_OF_MONTH, 1)
        val tomorrowDate = dateFormat.format(tomorrow.time)
        val tomorrowDay = dayFormat.format(tomorrow.time)
        
        sb.append("📅 Tomorrow: $tomorrowDate ($tomorrowDay)\n\n")
        
        // Important rules
        sb.append("⚠️ IMPORTANT TIME RULES:\n")
        sb.append("• Current time is $currentTime12 ($currentTime24 in 24h)\n")
        sb.append("• If user says \"today\" → Use date: $currentDate\n")
        sb.append("• If user says \"tomorrow\" → Use date: $tomorrowDate\n")
        sb.append("• If user says \"aaj\" → Use date: $currentDate\n")
        sb.append("• If user says \"kal\" → Use date: $tomorrowDate\n")
        sb.append("• If user says \"15 tarik\" → Use date: $currentYear-$currentMonth-15\n")
        sb.append("• New day starts at 12:00 AM (00:00 in 24h format)\n")
        sb.append("• Current hour is $currentHour (0-23 scale, where 0 = midnight, 12 = noon)\n")
        sb.append("• If current time is past appointment time today → Suggest tomorrow or future date\n")
        sb.append("• ❌ NEVER book appointments in the past!\n")
        sb.append("• ✅ ALWAYS check if appointment time is in the future\n\n")
        
        // Past/Future logic
        sb.append("⚠️ PAST vs FUTURE APPOINTMENTS:\n")
        sb.append("• Current time: $currentTime24 (Hour: $currentHour)\n")
        sb.append("• If user wants 10:00 AM (10:00) and current time is 11:30 AM (11:30):\n")
        sb.append("  → 10:00 is PAST today → Suggest tomorrow or ask \"Do you mean tomorrow?\"\n")
        sb.append("• If user wants 2:00 PM (14:00) and current time is 11:30 AM (11:30):\n")
        sb.append("  → 14:00 is FUTURE today → Book for today\n")
        sb.append("• Example: Current time is $currentTime24\n")
        if (currentHour < 12) {
            sb.append("  → Morning times before $currentTime24 are PAST\n")
            sb.append("  → Afternoon/Evening times are FUTURE\n")
        } else {
            sb.append("  → Morning times are PAST\n")
            sb.append("  → Times after $currentTime24 are FUTURE\n")
        }
        sb.append("\n")
        
        return sb.toString()
    }
    
    /**
     * Get current date in YYYY-MM-DD format
     */
    fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date())
    }
    
    /**
     * Get current time in HH:mm format (24h)
     */
    fun getCurrentTime24(): String {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        return timeFormat.format(Date())
    }
    
    /**
     * Get current time in 12h format
     */
    fun getCurrentTime12(): String {
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return timeFormat.format(Date())
    }
    
    /**
     * Get current day name (Monday, Tuesday, etc.)
     */
    fun getCurrentDay(): String {
        val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
        return dayFormat.format(Date())
    }
    
    /**
     * Get tomorrow's date in YYYY-MM-DD format
     */
    fun getTomorrowDate(): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(calendar.time)
    }
    
    /**
     * Check if given time is in the past today
     * @param time24 Time in HH:mm format
     * @return true if time is in the past
     */
    fun isTimePastToday(time24: String): Boolean {
        try {
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val currentMinute = Calendar.getInstance().get(Calendar.MINUTE)
            
            val parts = time24.split(":")
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()
            
            return if (hour < currentHour) {
                true
            } else if (hour == currentHour) {
                minute <= currentMinute
            } else {
                false
            }
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * Get current hour (0-23)
     */
    fun getCurrentHour(): Int {
        return Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    }
    
    /**
     * Get current minute (0-59)
     */
    fun getCurrentMinute(): Int {
        return Calendar.getInstance().get(Calendar.MINUTE)
    }
    
    /**
     * Check if it's currently morning (6 AM - 12 PM)
     */
    fun isMorning(): Boolean {
        val hour = getCurrentHour()
        return hour in 6..11
    }
    
    /**
     * Check if it's currently afternoon (12 PM - 5 PM)
     */
    fun isAfternoon(): Boolean {
        val hour = getCurrentHour()
        return hour in 12..16
    }
    
    /**
     * Check if it's currently evening (5 PM - 9 PM)
     */
    fun isEvening(): Boolean {
        val hour = getCurrentHour()
        return hour in 17..20
    }
    
    /**
     * Check if it's currently night (9 PM - 6 AM)
     */
    fun isNight(): Boolean {
        val hour = getCurrentHour()
        return hour >= 21 || hour < 6
    }
}
