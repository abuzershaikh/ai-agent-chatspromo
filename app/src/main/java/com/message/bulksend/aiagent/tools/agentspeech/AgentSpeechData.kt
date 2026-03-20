package com.message.bulksend.aiagent.tools.agentspeech

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Agent Speech Settings Data
 */
@Entity(tableName = "agent_speech_settings")
data class AgentSpeechSettings(
    @PrimaryKey
    val id: Int = 1,
    val isEnabled: Boolean = false,
    val language: String = "EN-IN", // EN-IN, en, hi (future)
    val voiceName: String = "Aoede", // Gemini TTS voice name
    val userEmail: String = "",
    val webhookUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Available Gemini TTS Voices for Hindi
 * Based on testing and recommendations for Indian languages
 */
enum class GeminiVoice(val voiceName: String, val gender: String, val description: String) {
    // Female Voices - Best for Hindi
    AOEDE("Aoede", "Female", "गर्म, पेशेवर - Hindi के लिए बेहतरीन"),
    KORE("Kore", "Female", "मजबूत, स्पष्ट - व्यावसायिक उपयोग"),
    LEDA("Leda", "Female", "युवा, ताज़ा - दोस्ताना लहजा"),
    DESPINA("Despina", "Female", "सुंदर, शांत - सुखद आवाज"),
    VINDEMIATRIX("Vindemiatrix", "Female", "कोमल, नरम - मधुर आवाज"),
    
    // Male Voices - Best for Hindi
    CHARON("Charon", "Male", "जानकारीपूर्ण, स्पष्ट - पेशेवर"),
    PUCK("Puck", "Male", "ऊर्जावान, युवा - उत्साही"),
    IAPETUS("Iapetus", "Male", "साफ, पेशेवर - विश्वसनीय"),
    ACHIRD("Achird", "Male", "दोस्ताना, सुलभ - आरामदायक"),
    ORUS("Orus", "Male", "मजबूत, आधिकारिक - गंभीर");
    
    companion object {
        fun getFemaleVoices() = values().filter { it.gender == "Female" }
        fun getMaleVoices() = values().filter { it.gender == "Male" }
        fun getAllVoices() = values().toList()
        fun getByName(name: String) = values().find { it.voiceName == name } ?: AOEDE
    }
}

/**
 * Speech Queue Item - Stores pending TTS requests
 */
@Entity(tableName = "speech_queue")
data class SpeechQueueItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val text: String,
    val language: String = "EN-IN",
    val phoneNumber: String,
    val userEmail: String,
    val status: String = "PENDING", // PENDING, PROCESSING, COMPLETED, FAILED
    val audioPath: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val processedAt: Long? = null,
    val retryCount: Int = 0
)

/**
 * Speech History - Tracks all generated speeches
 */
@Entity(tableName = "speech_history")
data class SpeechHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val text: String,
    val language: String,
    val phoneNumber: String,
    val audioPath: String,
    val duration: Int, // seconds
    val processingTime: Long, // milliseconds
    val createdAt: Long = System.currentTimeMillis()
)
