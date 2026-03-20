package com.message.bulksend.autorespond.ai.conversation.clinic

import android.content.Context
import com.message.bulksend.autorespond.ai.conversation.ConversationStateManager
import com.message.bulksend.autorespond.ai.data.repo.ClinicRepository

/**
 * Clinic-specific question generator
 * Generates smart, contextual questions for clinic appointments
 */
class ClinicQuestionGenerator(private val context: Context) {
    
    private val clinicRepo = ClinicRepository(context)
    
    /**
     * Generate next question based on missing fields
     */
    suspend fun generateNextQuestion(
        missingFields: List<String>,
        collectedInfo: Map<String, String>
    ): String {
        if (missingFields.isEmpty()) {
            return "" // All info collected
        }
        
        val sb = StringBuilder()
        
        // Priority order: doctor → date/time → name
        when {
            missingFields.contains(ConversationStateManager.FIELD_DOCTOR) -> {
                // Ask for doctor
                val doctors = clinicRepo.getAllDoctorsList()
                
                if (doctors.size > 1) {
                    sb.append("We have the following doctors:\n")
                    doctors.forEachIndexed { index, doctor ->
                        sb.append("${index + 1}. Dr. ${doctor.name}")
                        if (doctor.specialty.isNotBlank()) {
                            sb.append(" (${doctor.specialty})")
                        }
                        sb.append("\n")
                    }
                    sb.append("\nWhich doctor would you like to see?")
                } else if (doctors.size == 1) {
                    sb.append("Would you like to book with Dr. ${doctors.first().name}?")
                } else {
                    sb.append("Which doctor would you like to see?")
                }
            }
            
            missingFields.contains(ConversationStateManager.FIELD_DATE) || 
            missingFields.contains(ConversationStateManager.FIELD_TIME) -> {
                // Ask for date and time together
                val doctorName = collectedInfo[ConversationStateManager.FIELD_DOCTOR]
                if (doctorName != null) {
                    sb.append("What date and time would you like for Dr. $doctorName?")
                } else {
                    sb.append("What date and time would you like?")
                }
            }
            
            missingFields.contains(ConversationStateManager.FIELD_PATIENT_NAME) -> {
                // Ask for patient name
                sb.append("May I have the patient's name please?")
            }
            
            else -> {
                sb.append("I need some more information to complete the booking.")
            }
        }
        
        return sb.toString()
    }
    
    /**
     * Generate confirmation question
     */
    fun generateConfirmationQuestion(collectedInfo: Map<String, String>): String {
        val sb = StringBuilder()
        
        sb.append("Just to confirm:\n")
        
        collectedInfo[ConversationStateManager.FIELD_PATIENT_NAME]?.let {
            sb.append("• Patient: $it\n")
        }
        
        collectedInfo[ConversationStateManager.FIELD_DOCTOR]?.let {
            sb.append("• Doctor: Dr. $it\n")
        }
        
        collectedInfo[ConversationStateManager.FIELD_DATE]?.let { date ->
            val formattedDate = formatDate(date)
            sb.append("• Date: $formattedDate\n")
        }
        
        collectedInfo[ConversationStateManager.FIELD_TIME]?.let { time ->
            val formattedTime = to12h(time)
            sb.append("• Time: $formattedTime\n")
        }
        
        sb.append("\nIs this correct? (Yes/No)")
        
        return sb.toString()
    }
    
    /**
     * Suggest options for a field
     */
    suspend fun suggestOptions(field: String): List<String> {
        return when (field) {
            ConversationStateManager.FIELD_DOCTOR -> {
                clinicRepo.getAllDoctorsList().map { "Dr. ${it.name}" }
            }
            ConversationStateManager.FIELD_DATE -> {
                listOf("Today", "Tomorrow", "Day after tomorrow")
            }
            ConversationStateManager.FIELD_TIME -> {
                listOf("Morning (9-12)", "Afternoon (12-5)", "Evening (5-8)")
            }
            else -> emptyList()
        }
    }
    
    /**
     * Format date to readable format
     */
    private fun formatDate(date: String): String {
        return try {
            val inFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val outFmt = java.text.SimpleDateFormat("dd MMM yyyy (EEEE)", java.util.Locale.getDefault())
            outFmt.format(inFmt.parse(date)!!)
        } catch (e: Exception) {
            date
        }
    }
    
    /**
     * Convert 24h to 12h format
     */
    private fun to12h(time24: String): String {
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
}
