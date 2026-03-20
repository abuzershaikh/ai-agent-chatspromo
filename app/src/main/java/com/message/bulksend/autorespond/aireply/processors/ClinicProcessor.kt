package com.message.bulksend.autorespond.aireply.processors

import android.content.Context
import com.message.bulksend.autorespond.ai.core.ClinicContextGenerator
import com.message.bulksend.autorespond.ai.data.repo.ClinicRepository
import com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager
import com.message.bulksend.reminders.GlobalReminderManager
import com.message.bulksend.reminders.ReminderScheduleGuard
import java.text.SimpleDateFormat
import java.util.*

/**
 * Processor for CLINIC template
 * Handles appointment booking, cancellation, and clinic-specific logic
 */
class ClinicProcessor(private val context: Context) : TemplateProcessor {
    
    private val contextGenerator = ClinicContextGenerator(context)
    private val clinicRepo = ClinicRepository(context)
    private val settingsManager = AIAgentSettingsManager(context)
    private val reminderScheduleGuard = ReminderScheduleGuard(context)
    
    override fun getTemplateType(): String = "CLINIC"
    
    override suspend fun generateContext(senderPhone: String): String {
        return contextGenerator.generatePrompt(senderPhone)
    }
    
    override suspend fun processResponse(
        response: String,
        message: String,
        senderPhone: String,
        senderName: String
    ): String {
        var cleanResponse = response
        
        // Process booking command
        cleanResponse = processBookingCommand(cleanResponse, senderPhone)
        
        // Process cancellation command
        cleanResponse = processCancellationCommand(cleanResponse, senderPhone)
        
        return cleanResponse
    }
    
    /**
     * Process [BOOK_APPOINTMENT: DoctorID | Date | Time | PatientName]
     */
    private suspend fun processBookingCommand(response: String, senderPhone: String): String {
        var cleanResponse = response
        
        try {
            val bookPattern = Regex("\\[BOOK_APPOINTMENT:\\s*(.*?)\\s*\\|\\s*(.*?)\\s*\\|\\s*(.*?)\\s*\\|\\s*(.*?)\\]")
            val bookMatch = bookPattern.find(response) ?: return cleanResponse
            
            val doctorId = bookMatch.groupValues[1].trim().toIntOrNull()
            val date = bookMatch.groupValues[2].trim()
            val rawTime = bookMatch.groupValues[3].trim()
            val time = rawTime.replace(Regex("\\(?24h\\)?", RegexOption.IGNORE_CASE), "").trim()
            val patientName = bookMatch.groupValues[4].trim()
            
            android.util.Log.d("ClinicProcessor", "🏥 BOOK: Dr=$doctorId Date=$date Time=$time Patient=$patientName")
            
            // Validate patient name is not a doctor name
            val doctors = clinicRepo.getAllDoctorsList()
            val matchingDoctor = doctors.find { doctor ->
                patientName.equals(doctor.name, ignoreCase = true) ||
                patientName.contains(doctor.name, ignoreCase = true) ||
                doctor.name.contains(patientName, ignoreCase = true)
            }
            
            if (matchingDoctor != null) {
                android.util.Log.w("ClinicProcessor", "⚠️ Patient name matches Dr. ${matchingDoctor.name}")
                cleanResponse = cleanResponse.replace(bookMatch.value, "").trim()
                
                val confirmationMsg = """
                    ⚠️ I noticed the patient name "$patientName" is the same as one of our doctors (Dr. ${matchingDoctor.name}).
                    
                    Just to confirm:
                    • Is the patient name "$patientName"? (Reply: Yes/Correct)
                    • Or is this a mistake? (Reply: No, my name is [YourName])
                    
                    Please confirm so I can proceed with the booking.
                """.trimIndent()
                
                return if (cleanResponse.isBlank()) confirmationMsg else "$cleanResponse\n\n$confirmationMsg"
            }
            
            if (doctorId != null && date.isNotBlank() && time.isNotBlank() && patientName.isNotBlank()) {
                // Save appointment
                val appointment = com.message.bulksend.autorespond.ai.data.model.AppointmentEntity(
                    doctorId = doctorId,
                    patientName = patientName,
                    patientPhone = senderPhone,
                    date = date,
                    time = time,
                    status = "CONFIRMED"
                )
                clinicRepo.addBooking(appointment)
                android.util.Log.d("ClinicProcessor", "✅ Booking saved")
                
                cleanResponse = cleanResponse.replace(bookMatch.value, "").trim()
                
                // Generate confirmation message
                val confirmationMsg = generateConfirmationMessage(doctorId, date, time, patientName)
                cleanResponse = if (cleanResponse.isBlank()) confirmationMsg else "$cleanResponse\n\n$confirmationMsg"
                
                // Schedule reminder if enabled
                scheduleReminder(doctorId, date, time, patientName, senderPhone)
            }
            
        } catch (e: Exception) {
            android.util.Log.e("ClinicProcessor", "❌ Booking error: ${e.message}", e)
        }
        
        return cleanResponse
    }

    
    /**
     * Process [CANCEL_APPOINTMENT: DoctorID | Date | Time]
     */
    private suspend fun processCancellationCommand(response: String, senderPhone: String): String {
        var cleanResponse = response
        
        try {
            val cancelPattern = Regex("\\[CANCEL_APPOINTMENT:\\s*(.*?)\\s*\\|\\s*(.*?)\\s*\\|\\s*(.*?)\\]")
            val cancelMatch = cancelPattern.find(cleanResponse) ?: return cleanResponse
            
            val doctorId = cancelMatch.groupValues[1].trim().toIntOrNull()
            val date = cancelMatch.groupValues[2].trim()
            val rawTime = cancelMatch.groupValues[3].trim()
            val time = rawTime.replace(Regex("\\(?24h\\)?", RegexOption.IGNORE_CASE), "").trim()
            
            android.util.Log.d("ClinicProcessor", "🏥 CANCEL: Dr=$doctorId Date=$date Time=$time")
            
            if (doctorId != null) {
                val doctors = clinicRepo.getAllDoctorsList()
                val doctor = doctors.find { it.id == doctorId }
                val doctorName = doctor?.name ?: "Doctor"
                
                clinicRepo.cancelBooking(doctorId, date, time)
                android.util.Log.d("ClinicProcessor", "✅ Cancellation processed")
                
                cleanResponse = cleanResponse.replace(cancelMatch.value, "").trim()
                
                // Generate cancellation message
                val cancellationMsg = generateCancellationMessage(doctorName, date, time)
                
                val templateAlreadyIncluded = cleanResponse.contains("❌") && cleanResponse.contains("Appointment Cancelled")
                cleanResponse = if (!templateAlreadyIncluded) {
                    if (cleanResponse.isBlank()) cancellationMsg else "$cleanResponse\n\n$cancellationMsg"
                } else {
                    cleanResponse
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("ClinicProcessor", "❌ Cancellation error: ${e.message}", e)
        }
        
        return cleanResponse
    }
    
    /**
     * Generate confirmation message for booking
     */
    private suspend fun generateConfirmationMessage(
        doctorId: Int,
        date: String,
        time: String,
        patientName: String
    ): String {
        val doctor = clinicRepo.getAllDoctorsList().find { it.id == doctorId }
        val doctorName = doctor?.name ?: "Doctor"
        val address = settingsManager.clinicAddress
        
        val time12 = convertTo12Hour(time)
        val dateFormatted = formatDate(date)
        
        return settingsManager.confirmationTemplate
            .replace("{name}", patientName)
            .replace("{doctor}", "Dr. $doctorName")
            .replace("{date}", dateFormatted)
            .replace("{time}", time12)
            .replace("{address}", address)
    }
    
    /**
     * Generate cancellation message
     */
    private suspend fun generateCancellationMessage(
        doctorName: String,
        date: String,
        time: String
    ): String {
        val time12 = convertTo12Hour(time)
        val dateFormatted = formatDate(date)
        
        return settingsManager.cancellationTemplate
            .replace("{doctor}", "Dr. $doctorName")
            .replace("{date}", dateFormatted)
            .replace("{time}", time12)
    }
    
    /**
     * Schedule reminder for appointment
     */
    private suspend fun scheduleReminder(
        doctorId: Int,
        date: String,
        time: String,
        patientName: String,
        senderPhone: String
    ) {
        if (!reminderScheduleGuard.canScheduleFromIncomingChat(senderPhone)) {
            android.util.Log.d(
                "ClinicProcessor",
                "Reminder scheduling blocked: incoming chat sender is not owner ($senderPhone)"
            )
            return
        }

        if (!settingsManager.clinicReminderEnabled) {
            android.util.Log.d("ClinicProcessor", "⚠️ Reminder disabled in settings")
            return
        }
        
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val apptDate = sdf.parse("$date $time") ?: return
            
            android.util.Log.d("ClinicProcessor", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            android.util.Log.d("ClinicProcessor", "📅 APPOINTMENT TIME: $date $time")
            android.util.Log.d("ClinicProcessor", "📅 Parsed appointment: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(apptDate)}")
            android.util.Log.d("ClinicProcessor", "⏰ Reminder minutes BEFORE: ${settingsManager.clinicReminderTimeBefore}")
            
            val calendar = Calendar.getInstance()
            calendar.time = apptDate
            
            android.util.Log.d("ClinicProcessor", "🕐 Before subtraction: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(calendar.time)}")
            
            // SUBTRACT minutes to get reminder time BEFORE appointment
            calendar.add(Calendar.MINUTE, -settingsManager.clinicReminderTimeBefore)
            
            val reminderTime = calendar.time
            
            android.util.Log.d("ClinicProcessor", "🕐 After subtraction: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(reminderTime)}")
            android.util.Log.d("ClinicProcessor", "🔔 REMINDER TIME: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(reminderTime)}")
            android.util.Log.d("ClinicProcessor", "📊 Current time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            
            if (reminderTime.after(Date())) {
                val rDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(reminderTime)
                val rTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(reminderTime)
                
                android.util.Log.d("ClinicProcessor", "✅ Reminder is in future, scheduling...")
                android.util.Log.d("ClinicProcessor", "📤 Saving to sheet: Date=$rDate Time=$rTime")
                
                val doctor = clinicRepo.getAllDoctorsList().find { it.id == doctorId }
                val doctorName = doctor?.name ?: "Doctor"
                
                val reminderMsg = settingsManager.clinicReminderTemplate
                    .replace("{name}", patientName)
                    .replace("{doctor}", "Dr. $doctorName")
                    .replace("{date}", formatDate(date))
                    .replace("{time}", convertTo12Hour(time))
                
                GlobalReminderManager(context).addReminder(
                    phone = senderPhone,
                    name = patientName,
                    date = rDate,
                    time = rTime,
                    prompt = reminderMsg,
                    templateType = "CLINIC_REMINDER"
                )
                android.util.Log.d("ClinicProcessor", "✅ Reminder scheduled successfully!")
                android.util.Log.d("ClinicProcessor", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            } else {
                android.util.Log.w("ClinicProcessor", "⚠️ Reminder time is in the past, not scheduling")
                android.util.Log.d("ClinicProcessor", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            }
        } catch (e: Exception) {
            android.util.Log.e("ClinicProcessor", "❌ Failed to schedule reminder: ${e.message}", e)
            android.util.Log.d("ClinicProcessor", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        }
    }
    
    /**
     * Convert 24-hour time to 12-hour format
     */
    private fun convertTo12Hour(time24: String): String {
        return try {
            val parts = time24.split(":")
            val h = parts[0].toInt()
            val m = parts[1]
            val amPm = if (h < 12) "AM" else "PM"
            val h12 = if (h == 0) 12 else if (h > 12) h - 12 else h
            "$h12:$m $amPm"
        } catch (e: Exception) {
            time24
        }
    }
    
    /**
     * Format date to readable format
     */
    private fun formatDate(date: String): String {
        return try {
            val inFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            outFmt.format(inFmt.parse(date)!!)
        } catch (e: Exception) {
            date
        }
    }
}
