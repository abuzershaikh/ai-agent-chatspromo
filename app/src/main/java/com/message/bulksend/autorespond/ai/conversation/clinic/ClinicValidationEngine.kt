package com.message.bulksend.autorespond.ai.conversation.clinic

import android.content.Context
import com.message.bulksend.autorespond.ai.conversation.ValidationResult
import com.message.bulksend.autorespond.ai.data.repo.ClinicRepository
import com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager
import com.message.bulksend.utils.DateTimeHelper
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Clinic-specific validation engine
 * Validates appointment information for clinic template
 */
class ClinicValidationEngine(private val context: Context) {
    
    private val settingsManager = AIAgentSettingsManager(context)
    private val clinicRepo = ClinicRepository(context)
    
    /**
     * Validate date for clinic
     */
    suspend fun validateDate(date: String): ValidationResult {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            dateFormat.isLenient = false
            val parsedDate = dateFormat.parse(date) ?: return ValidationResult(
                false,
                "Invalid date format. Please use YYYY-MM-DD format."
            )
            
            // Check if date is in the past
            val today = Calendar.getInstance()
            today.set(Calendar.HOUR_OF_DAY, 0)
            today.set(Calendar.MINUTE, 0)
            today.set(Calendar.SECOND, 0)
            today.set(Calendar.MILLISECOND, 0)
            
            if (parsedDate.before(today.time)) {
                return ValidationResult(
                    false,
                    "Cannot book appointments in the past.",
                    "Please choose today or a future date."
                )
            }
            
            // Check if it's a holiday
            val holidayResult = validateHoliday(date)
            if (!holidayResult.isValid) {
                return holidayResult
            }
            
            // Check weekly schedule
            val calendar = Calendar.getInstance()
            calendar.time = parsedDate
            val dayOfWeek = when (calendar.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "Mon"
                Calendar.TUESDAY -> "Tue"
                Calendar.WEDNESDAY -> "Wed"
                Calendar.THURSDAY -> "Thu"
                Calendar.FRIDAY -> "Fri"
                Calendar.SATURDAY -> "Sat"
                Calendar.SUNDAY -> "Sun"
                else -> ""
            }
            
            val scheduleResult = validateWeeklySchedule(dayOfWeek)
            if (!scheduleResult.isValid) {
                return scheduleResult
            }
            
            return ValidationResult(true)
            
        } catch (e: Exception) {
            return ValidationResult(
                false,
                "Invalid date format: ${e.message}",
                "Please provide date in YYYY-MM-DD format (e.g., 2026-02-15)"
            )
        }
    }
    
    /**
     * Validate time for clinic
     */
    fun validateTime(time: String, date: String? = null): ValidationResult {
        try {
            val parts = time.split(":")
            if (parts.size != 2) {
                return ValidationResult(
                    false,
                    "Invalid time format.",
                    "Please use HH:mm format (e.g., 10:00, 14:30)"
                )
            }
            
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()
            
            if (hour !in 0..23 || minute !in 0..59) {
                return ValidationResult(
                    false,
                    "Invalid time values.",
                    "Hour must be 0-23, minute must be 0-59"
                )
            }
            
            // Check if time is in clinic hours
            val clinicHoursResult = validateClinicHours(time)
            if (!clinicHoursResult.isValid) {
                return clinicHoursResult
            }
            
            // If date is today, check if time is in the future
            if (date != null && date == DateTimeHelper.getCurrentDate()) {
                if (DateTimeHelper.isTimePastToday(time)) {
                    val tomorrow = DateTimeHelper.getTomorrowDate()
                    return ValidationResult(
                        false,
                        "This time has already passed today.",
                        "Would you like to book for tomorrow ($tomorrow) at $time instead?"
                    )
                }
            }
            
            return ValidationResult(true)
            
        } catch (e: Exception) {
            return ValidationResult(
                false,
                "Invalid time format: ${e.message}",
                "Please provide time in HH:mm format (e.g., 10:00)"
            )
        }
    }
    
    /**
     * Validate clinic hours
     */
    private fun validateClinicHours(time: String): ValidationResult {
        try {
            val parts = time.split(":")
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()
            val timeMinutes = hour * 60 + minute
            
            val openParts = settingsManager.clinicOpenTime.split(":")
            val openHour = openParts[0].toInt()
            val openMinute = openParts[1].toInt()
            val openMinutes = openHour * 60 + openMinute
            
            val closeParts = settingsManager.clinicCloseTime.split(":")
            val closeHour = closeParts[0].toInt()
            val closeMinute = closeParts[1].toInt()
            val closeMinutes = closeHour * 60 + closeMinute
            
            if (timeMinutes < openMinutes || timeMinutes >= closeMinutes) {
                return ValidationResult(
                    false,
                    "Clinic is closed at this time.",
                    "Clinic hours: ${to12h(settingsManager.clinicOpenTime)} - ${to12h(settingsManager.clinicCloseTime)}"
                )
            }
            
            return ValidationResult(true)
            
        } catch (e: Exception) {
            return ValidationResult(true) // If parsing fails, allow it
        }
    }
    
    /**
     * Validate holiday
     */
    private fun validateHoliday(date: String): ValidationResult {
        try {
            val holidaysArr = JSONArray(settingsManager.holidays)
            for (i in 0 until holidaysArr.length()) {
                val holiday = holidaysArr.getJSONObject(i)
                val holidayDate = holiday.getString("date")
                val holidayName = holiday.getString("name")
                
                if (holidayDate == date) {
                    val nextWorkingDay = findNextWorkingDay(date)
                    return ValidationResult(
                        false,
                        "Clinic is closed on $date due to $holidayName.",
                        "Next available date: $nextWorkingDay"
                    )
                }
            }
            return ValidationResult(true)
        } catch (e: Exception) {
            return ValidationResult(true)
        }
    }
    
    /**
     * Validate weekly schedule
     */
    private fun validateWeeklySchedule(dayOfWeek: String): ValidationResult {
        try {
            val scheduleObj = JSONObject(settingsManager.weeklySchedule)
            val status = scheduleObj.optString(dayOfWeek, "OPEN")
            
            when (status) {
                "CLOSED" -> {
                    val nextDay = findNextOpenDay(dayOfWeek)
                    return ValidationResult(
                        false,
                        "Clinic is closed on ${dayOfWeek}days.",
                        "Next open day: $nextDay"
                    )
                }
                "HALF" -> {
                    return ValidationResult(
                        true,
                        null,
                        "Note: This is a half-day. Clinic closes at ${to12h(settingsManager.halfDayCloseTime)}"
                    )
                }
            }
            
            return ValidationResult(true)
        } catch (e: Exception) {
            return ValidationResult(true)
        }
    }
    
    /**
     * Validate doctor availability
     */
    suspend fun validateDoctorAvailability(
        doctorId: Int,
        date: String,
        time: String
    ): ValidationResult {
        try {
            val availableSlots = clinicRepo.getAvailableSlots(doctorId, date)
            
            if (!availableSlots.contains(time)) {
                val alternatives = findClosestSlots(time, availableSlots, 3)
                
                return ValidationResult(
                    false,
                    "This slot is not available.",
                    "Available nearby slots:",
                    alternatives.map { to12h(it) }
                )
            }
            
            return ValidationResult(true)
            
        } catch (e: Exception) {
            android.util.Log.e("ClinicValidation", "Error validating availability: ${e.message}")
            return ValidationResult(true)
        }
    }
    
    private fun findNextWorkingDay(fromDate: String): String {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val calendar = Calendar.getInstance()
            calendar.time = dateFormat.parse(fromDate)!!
            
            val scheduleObj = JSONObject(settingsManager.weeklySchedule)
            val holidaysArr = JSONArray(settingsManager.holidays)
            
            for (i in 1..14) {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
                val checkDate = dateFormat.format(calendar.time)
                
                var isHoliday = false
                for (j in 0 until holidaysArr.length()) {
                    if (holidaysArr.getJSONObject(j).getString("date") == checkDate) {
                        isHoliday = true
                        break
                    }
                }
                if (isHoliday) continue
                
                val dayOfWeek = when (calendar.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> "Mon"
                    Calendar.TUESDAY -> "Tue"
                    Calendar.WEDNESDAY -> "Wed"
                    Calendar.THURSDAY -> "Thu"
                    Calendar.FRIDAY -> "Fri"
                    Calendar.SATURDAY -> "Sat"
                    Calendar.SUNDAY -> "Sun"
                    else -> ""
                }
                
                val status = scheduleObj.optString(dayOfWeek, "OPEN")
                if (status != "CLOSED") {
                    return checkDate
                }
            }
            
            return fromDate
        } catch (e: Exception) {
            return fromDate
        }
    }
    
    private fun findNextOpenDay(fromDay: String): String {
        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val startIndex = days.indexOf(fromDay)
        
        try {
            val scheduleObj = JSONObject(settingsManager.weeklySchedule)
            
            for (i in 1..7) {
                val checkIndex = (startIndex + i) % 7
                val checkDay = days[checkIndex]
                val status = scheduleObj.optString(checkDay, "OPEN")
                
                if (status != "CLOSED") {
                    return "${checkDay}day"
                }
            }
        } catch (e: Exception) {
            // Fallback
        }
        
        return "Monday"
    }
    
    private fun findClosestSlots(requestedTime: String, availableSlots: List<String>, count: Int): List<String> {
        try {
            val parts = requestedTime.split(":")
            val requestedMinutes = parts[0].toInt() * 60 + parts[1].toInt()
            
            return availableSlots
                .map { slot ->
                    val slotParts = slot.split(":")
                    val slotMinutes = slotParts[0].toInt() * 60 + slotParts[1].toInt()
                    val diff = Math.abs(slotMinutes - requestedMinutes)
                    Pair(slot, diff)
                }
                .sortedBy { it.second }
                .take(count)
                .map { it.first }
        } catch (e: Exception) {
            return availableSlots.take(count)
        }
    }
    
    private fun to12h(time24: String): String {
        try {
            val parts = time24.split(":")
            val h = parts[0].toInt()
            val m = parts[1]
            val amPm = if (h < 12) "AM" else "PM"
            val h12 = if (h == 0) 12 else if (h > 12) h - 12 else h
            return "$h12:$m $amPm"
        } catch (e: Exception) {
            return time24
        }
    }
}
