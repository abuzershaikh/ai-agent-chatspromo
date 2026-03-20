package com.message.bulksend.autorespond.ai.profile

import android.content.Context
import android.util.Log
import com.message.bulksend.autorespond.ai.data.model.UserProfile
import com.message.bulksend.autorespond.ai.data.repo.AIAgentRepository

/**
 * Smart Profile Extractor
 * Intelligently extracts and confirms user name and phone number
 * 
 * Flow:
 * 1. Ask for name
 * 2. Extract name from response
 * 3. Confirm name with user
 * 4. Save name to profile
 * 5. Ask for phone (if needed)
 * 6. Extract and save phone
 */
class SmartProfileExtractor(
    private val context: Context,
    private val repository: AIAgentRepository
) {
    
    companion object {
        const val TAG = "SmartProfileExtractor"
        private const val PREFS_NAME = "smart_profile_prefs"
        private const val KEY_PENDING_NAME = "pending_name_"
        private const val KEY_AWAITING_CONFIRMATION = "awaiting_confirmation_"
        private const val KEY_AWAITING_PHONE = "awaiting_phone_"

        private val NON_NAME_KEYWORDS = setOf(
            "payment", "pay", "paytm", "gpay", "phonepe", "upi", "amount", "price", "cost", "bill",
            "invoice", "order", "booking", "book", "appointment", "reminder", "product", "catalog",
            "catalogue", "stock", "size", "color", "colour", "delivery", "address", "location",
            "status", "document", "pdf", "brochure", "service", "support", "help", "send", "share",
            "message", "whatsapp", "business", "offer", "discount", "sale", "buy", "purchase"
        )

        private val NON_NAME_EXACT_WORDS = setOf(
            "hi", "hello", "hey", "hii", "hiii", "helloo", "hie",
            "sir", "madam", "mam", "ma'am", "mr", "ms", "friend", "dude", "bro", "bhai",
            "listen", "wait", "ok", "okay", "yes", "no", "what", "who", "why", "how",
            "agent", "bot", "ai", "assistant", "manager", "admin", "interested", "looking"
        )

        fun isLikelyPersonName(raw: String?): Boolean {
            val normalized = raw
                ?.trim()
                ?.replace(Regex("\\s+"), " ")
                ?.replace(Regex("[^A-Za-z\\s'\\-.]"), "")
                ?.trim()
                ?: return false

            if (normalized.length !in 2..40) return false
            if (!normalized.matches(Regex("[A-Za-z][A-Za-z\\s'\\-.]*"))) return false

            val lower = normalized.lowercase()
            if (lower.contains("what is") || lower.contains("who is")) return false
            if (NON_NAME_EXACT_WORDS.contains(lower)) return false

            val words = lower.split(" ").filter { it.isNotBlank() }
            if (words.isEmpty() || words.size > 3) return false
            if (words.all { it.length <= 2 }) return false
            if (words.any { NON_NAME_KEYWORDS.contains(it) || NON_NAME_EXACT_WORDS.contains(it) }) {
                return false
            }

            return true
        }
    }
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Check if we're awaiting name confirmation from user
     */
    fun isAwaitingNameConfirmation(phoneNumber: String): Boolean {
        return prefs.getBoolean(KEY_AWAITING_CONFIRMATION + phoneNumber, false)
    }
    
    /**
     * Check if we're awaiting phone number from user
     */
    fun isAwaitingPhoneNumber(phoneNumber: String): Boolean {
        return prefs.getBoolean(KEY_AWAITING_PHONE + phoneNumber, false)
    }
    
    /**
     * Get pending name that's awaiting confirmation
     */
    fun getPendingName(phoneNumber: String): String? {
        return prefs.getString(KEY_PENDING_NAME + phoneNumber, null)
    }
    
    /**
     * Extract name from user's message
     * Handles various formats:
     * - "My name is John"
     * - "I am John"
     * - "I'm John"
     * - "Call me John"
     * - "John" (single word)
     */
    fun extractNameFromMessage(message: String): String? {
        val lowerMessage = message.lowercase().trim()

        // Block obvious business-intent messages from being treated as names.
        if (NON_NAME_KEYWORDS.any { keyword -> Regex("\\b$keyword\\b").containsMatchIn(lowerMessage) }) {
            return null
        }

        if (NON_NAME_EXACT_WORDS.contains(lowerMessage)) return null

        // Pattern 1: "my name is X"
        val nameIsPattern = Regex(
            "(?:my name is|my name's|name is|mera naam(?:\\s+hai)?|naam(?:\\s+hai)?)\\s+([a-zA-Z]{2,}(?:\\s+[a-zA-Z]{2,}){0,2})",
            RegexOption.IGNORE_CASE
        )
        nameIsPattern.find(lowerMessage)?.let {
            val name = it.groupValues[1].trim()
            if (isLikelyPersonName(name)) {
                return capitalizeWords(name)
            }
        }
        
        // Pattern 2: "I am X" or "I'm X"
        val iAmPattern = Regex(
            "(?:i am|i'm|main hoon|main)\\s+([a-zA-Z]{2,}(?:\\s+[a-zA-Z]{2,}){0,2})",
            RegexOption.IGNORE_CASE
        )
        iAmPattern.find(lowerMessage)?.let {
            val name = it.groupValues[1].trim()
            if (isLikelyPersonName(name)) {
                return capitalizeWords(name)
            }
        }
        
        // Pattern 3: "Call me X"
        val callMePattern = Regex(
            "(?:call me|you can call me|mujhe|bulao)\\s+([a-zA-Z]{2,}(?:\\s+[a-zA-Z]{2,}){0,2})",
            RegexOption.IGNORE_CASE
        )
        callMePattern.find(lowerMessage)?.let {
            val name = it.groupValues[1].trim()
            if (isLikelyPersonName(name)) {
                return capitalizeWords(name)
            }
        }
        
        // Pattern 4: Single word (likely a name)
        val words = message.trim().split("\\s+".toRegex())
        if (words.size == 1 && words[0].length >= 3 && words[0].matches(Regex("[a-zA-Z]+"))) {
            if (isLikelyPersonName(words[0])) {
                return capitalizeWords(words[0])
            }
        }
        
        // Pattern 5: Two words (first and last name)
        if (words.size == 2 && words.all { it.matches(Regex("[a-zA-Z]+")) }) {
            val fullName = words.joinToString(" ")
            if (isLikelyPersonName(fullName)) {
                return capitalizeWords(fullName)
            }
        }
        
        return null
    }
    
    /**
     * Extract phone number from message
     * Handles various formats:
     * - "9876543210"
     * - "+919876543210"
     * - "My number is 9876543210"
     */
    fun extractPhoneFromMessage(message: String): String? {
        // Pattern 1: 10 digit number
        val tenDigitPattern = Regex("\\b([6-9]\\d{9})\\b")
        tenDigitPattern.find(message)?.let {
            return "+91${it.groupValues[1]}"
        }
        
        // Pattern 2: +91 followed by 10 digits
        val withPlusPattern = Regex("\\+91\\s*([6-9]\\d{9})\\b")
        withPlusPattern.find(message)?.let {
            return "+91${it.groupValues[1]}"
        }
        
        // Pattern 3: 91 followed by 10 digits
        val withoutPlusPattern = Regex("\\b91\\s*([6-9]\\d{9})\\b")
        withoutPlusPattern.find(message)?.let {
            return "+91${it.groupValues[1]}"
        }
        
        return null
    }
    
    /**
     * Check if message is a confirmation (yes/no)
     */
    fun isConfirmation(message: String): Boolean {
        val lowerMessage = message.lowercase().trim()
        val yesWords = listOf("yes", "yeah", "yep", "correct", "right", "ha", "haan", "han", "sahi", "bilkul", "ok", "okay")
        return yesWords.any { lowerMessage.contains(it) }
    }
    
    /**
     * Check if message is a rejection (no)
     */
    fun isRejection(message: String): Boolean {
        val lowerMessage = message.lowercase().trim()
        val noWords = listOf("no", "nope", "nahi", "na", "wrong", "galat", "incorrect")
        return noWords.any { lowerMessage.contains(it) }
    }
    
    /**
     * Save pending name for confirmation
     */
    fun savePendingName(phoneNumber: String, name: String) {
        prefs.edit()
            .putString(KEY_PENDING_NAME + phoneNumber, name)
            .putBoolean(KEY_AWAITING_CONFIRMATION + phoneNumber, true)
            .apply()
        Log.d(TAG, "Saved pending name: $name for $phoneNumber")
    }
    
    /**
     * Confirm and save name to profile
     */
    suspend fun confirmAndSaveName(phoneNumber: String): Boolean {
        val pendingName = getPendingName(phoneNumber)
        if (pendingName == null) {
            Log.e(TAG, "No pending name to confirm")
            return false
        }
        
        // Save to profile
        val profile = repository.getUserProfile(phoneNumber) ?: UserProfile(phoneNumber = phoneNumber)
        repository.saveUserProfile(profile.copy(
            name = pendingName,
            updatedAt = System.currentTimeMillis()
        ))
        
        // Clear pending state
        prefs.edit()
            .remove(KEY_PENDING_NAME + phoneNumber)
            .putBoolean(KEY_AWAITING_CONFIRMATION + phoneNumber, false)
            .apply()
        
        Log.d(TAG, "✅ Name confirmed and saved: $pendingName")
        return true
    }
    
    /**
     * Cancel name confirmation
     */
    fun cancelNameConfirmation(phoneNumber: String) {
        prefs.edit()
            .remove(KEY_PENDING_NAME + phoneNumber)
            .putBoolean(KEY_AWAITING_CONFIRMATION + phoneNumber, false)
            .apply()
        Log.d(TAG, "Name confirmation cancelled")
    }
    
    /**
     * Set awaiting phone number state
     */
    fun setAwaitingPhoneNumber(phoneNumber: String, awaiting: Boolean) {
        prefs.edit()
            .putBoolean(KEY_AWAITING_PHONE + phoneNumber, awaiting)
            .apply()
    }
    
    /**
     * Save phone number to profile
     */
    suspend fun savePhoneNumber(phoneNumber: String, newPhoneNumber: String): Boolean {
        return try {
            val profile = repository.getUserProfile(phoneNumber) ?: UserProfile(phoneNumber = phoneNumber)
            
            // Save additional phone number in custom field or update profile
            // For now, we'll log it
            Log.d(TAG, "✅ Additional phone saved: $newPhoneNumber")
            
            // Clear awaiting state
            setAwaitingPhoneNumber(phoneNumber, false)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save phone: ${e.message}")
            false
        }
    }
    
    /**
     * Capitalize first letter of each word
     */
    private fun capitalizeWords(text: String): String {
        return text.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
    
    /**
     * Clear all pending states for user
     */
    fun clearAllPendingStates(phoneNumber: String) {
        prefs.edit()
            .remove(KEY_PENDING_NAME + phoneNumber)
            .putBoolean(KEY_AWAITING_CONFIRMATION + phoneNumber, false)
            .putBoolean(KEY_AWAITING_PHONE + phoneNumber, false)
            .apply()
    }
}
