package com.message.bulksend.autorespond.ai.conversation

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Manages conversation state for each user
 * Tracks what information has been collected and conversation stage
 */
class ConversationStateManager(private val context: Context) {
    
    private val prefs = context.getSharedPreferences("conversation_state", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        // Conversation stages
        const val STAGE_GREETING = "GREETING"
        const val STAGE_COLLECTING_INFO = "COLLECTING_INFO"
        const val STAGE_CONFIRMING = "CONFIRMING"
        const val STAGE_BOOKING = "BOOKING"
        const val STAGE_COMPLETED = "COMPLETED"
        
        // Field names
        const val FIELD_DOCTOR = "doctor"
        const val FIELD_DOCTOR_ID = "doctorId"
        const val FIELD_DATE = "date"
        const val FIELD_TIME = "time"
        const val FIELD_PATIENT_NAME = "patientName"
        
        // State timeout (30 minutes)
        private const val STATE_TIMEOUT_MS = 30 * 60 * 1000L
    }
    
    data class ConversationState(
        var stage: String = STAGE_GREETING,
        val collectedInfo: MutableMap<String, String> = mutableMapOf(),
        var pendingAction: String? = null,
        var lastUpdated: Long = System.currentTimeMillis(),
        var messageCount: Int = 0
    )
    
    /**
     * Get conversation state for a phone number
     * Returns new state if expired or doesn't exist
     */
    fun getState(phoneNumber: String): ConversationState {
        val json = prefs.getString("state_$phoneNumber", null)
        
        if (json == null) {
            android.util.Log.d("ConversationState", "📝 New state for $phoneNumber")
            return ConversationState()
        }
        
        val state = try {
            gson.fromJson(json, ConversationState::class.java)
        } catch (e: Exception) {
            android.util.Log.e("ConversationState", "Failed to parse state: ${e.message}")
            ConversationState()
        }
        
        // Check if state is expired
        val isExpired = (System.currentTimeMillis() - state.lastUpdated) > STATE_TIMEOUT_MS
        if (isExpired) {
            android.util.Log.d("ConversationState", "⏰ State expired for $phoneNumber, creating new")
            return ConversationState()
        }
        
        android.util.Log.d("ConversationState", "📋 Loaded state for $phoneNumber: Stage=${state.stage}, Collected=${state.collectedInfo.keys}")
        return state
    }
    
    /**
     * Save conversation state
     */
    fun setState(phoneNumber: String, state: ConversationState) {
        state.lastUpdated = System.currentTimeMillis()
        val json = gson.toJson(state)
        prefs.edit().putString("state_$phoneNumber", json).apply()
        android.util.Log.d("ConversationState", "💾 Saved state for $phoneNumber: Stage=${state.stage}")
    }
    
    /**
     * Set a collected field
     */
    fun setCollected(phoneNumber: String, field: String, value: String) {
        val state = getState(phoneNumber)
        state.collectedInfo[field] = value
        setState(phoneNumber, state)
        android.util.Log.d("ConversationState", "✅ Collected $field = $value for $phoneNumber")
    }
    
    /**
     * Check if a field is collected
     */
    fun isCollected(phoneNumber: String, field: String): Boolean {
        return getState(phoneNumber).collectedInfo.containsKey(field)
    }
    
    /**
     * Get collected field value
     */
    fun getCollected(phoneNumber: String, field: String): String? {
        return getState(phoneNumber).collectedInfo[field]
    }
    
    /**
     * Set conversation stage
     */
    fun setStage(phoneNumber: String, stage: String) {
        val state = getState(phoneNumber)
        state.stage = stage
        setState(phoneNumber, state)
        android.util.Log.d("ConversationState", "🔄 Stage changed to $stage for $phoneNumber")
    }
    
    /**
     * Get current stage
     */
    fun getStage(phoneNumber: String): String {
        return getState(phoneNumber).stage
    }
    
    /**
     * Clear conversation state (after booking complete)
     */
    fun clearState(phoneNumber: String) {
        prefs.edit().remove("state_$phoneNumber").apply()
        android.util.Log.d("ConversationState", "🗑️ Cleared state for $phoneNumber")
    }
    
    /**
     * Get missing fields from required list
     */
    fun getMissingFields(phoneNumber: String, requiredFields: List<String>): List<String> {
        val state = getState(phoneNumber)
        val missing = requiredFields.filter { !state.collectedInfo.containsKey(it) }
        android.util.Log.d("ConversationState", "❓ Missing fields for $phoneNumber: $missing")
        return missing
    }
    
    /**
     * Increment message count
     */
    fun incrementMessageCount(phoneNumber: String) {
        val state = getState(phoneNumber)
        state.messageCount++
        setState(phoneNumber, state)
    }
    
    /**
     * Get message count
     */
    fun getMessageCount(phoneNumber: String): Int {
        return getState(phoneNumber).messageCount
    }
    
    /**
     * Set pending action
     */
    fun setPendingAction(phoneNumber: String, action: String) {
        val state = getState(phoneNumber)
        state.pendingAction = action
        setState(phoneNumber, state)
        android.util.Log.d("ConversationState", "⏳ Pending action set: $action for $phoneNumber")
    }
    
    /**
     * Get pending action
     */
    fun getPendingAction(phoneNumber: String): String? {
        return getState(phoneNumber).pendingAction
    }
    
    /**
     * Clear pending action
     */
    fun clearPendingAction(phoneNumber: String) {
        val state = getState(phoneNumber)
        state.pendingAction = null
        setState(phoneNumber, state)
    }
    
    /**
     * Check if has pending action
     */
    fun hasPendingAction(phoneNumber: String): Boolean {
        return getState(phoneNumber).pendingAction != null
    }
    
    /**
     * Generate context string for AI
     */
    fun generateContextString(phoneNumber: String): String {
        val state = getState(phoneNumber)
        val sb = StringBuilder()
        
        sb.append("\n[CONVERSATION STATE TRACKING]\n")
        sb.append("Current Stage: ${state.stage}\n")
        sb.append("Message Count: ${state.messageCount}\n")
        
        if (state.collectedInfo.isNotEmpty()) {
            sb.append("\n✅ Collected Information:\n")
            state.collectedInfo.forEach { (key, value) ->
                sb.append("  • $key: $value\n")
            }
        }
        
        if (state.pendingAction != null) {
            sb.append("\n⏳ Pending Action: ${state.pendingAction}\n")
        }
        
        sb.append("\n⚠️ IMPORTANT RULES:\n")
        sb.append("• NEVER ask for information that is already collected above\n")
        sb.append("• ALWAYS check collected information before asking questions\n")
        sb.append("• If user changes information, update the collected value\n")
        sb.append("• Progress through stages: GREETING → COLLECTING_INFO → CONFIRMING → BOOKING → COMPLETED\n\n")
        
        return sb.toString()
    }
}
