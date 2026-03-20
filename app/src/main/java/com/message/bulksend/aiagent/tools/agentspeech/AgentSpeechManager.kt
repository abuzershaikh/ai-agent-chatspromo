package com.message.bulksend.aiagent.tools.agentspeech

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Room Database for Agent Speech
 */
@Database(
    entities = [AgentSpeechSettings::class, SpeechQueueItem::class, SpeechHistory::class],
    version = 2,
    exportSchema = false
)
abstract class AgentSpeechDatabase : RoomDatabase() {
    abstract fun settingsDao(): AgentSpeechSettingsDao
    abstract fun queueDao(): SpeechQueueDao
    abstract fun historyDao(): SpeechHistoryDao
    
    companion object {
        @Volatile
        private var INSTANCE: AgentSpeechDatabase? = null
        
        fun getDatabase(context: Context): AgentSpeechDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AgentSpeechDatabase::class.java,
                    "agent_speech_database"
                )
                .fallbackToDestructiveMigration() // Allow schema changes
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * Manager for Agent Speech System
 */
class AgentSpeechManager(private val context: Context) {
    private val database = AgentSpeechDatabase.getDatabase(context)
    private val settingsDao = database.settingsDao()
    private val queueDao = database.queueDao()
    private val historyDao = database.historyDao()
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val workerUrl = "https://melotts-worker.aawuazer.workers.dev"
    
    private val processingMutex = Mutex()
    private var isProcessing = false
    
    companion object {
        private const val TAG = "AgentSpeechManager"
        private const val DEFAULT_PCM_SAMPLE_RATE = 24000
        private const val DEFAULT_PCM_CHANNELS = 1
        private const val DEFAULT_PCM_BITS_PER_SAMPLE = 16
        private const val TRAILING_SILENCE_MS = 1200
        private const val GEMINI_SEGMENT_MAX_RETRIES = 3
        private const val GEMINI_RETRY_BACKOFF_MS = 700L
        
        @Volatile
        private var INSTANCE: AgentSpeechManager? = null
        
        fun getInstance(context: Context): AgentSpeechManager {
            return INSTANCE ?: synchronized(this) {
                val instance = AgentSpeechManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    private data class GeneratedAudio(
        val bytes: ByteArray,
        val mimeType: String? = null,
        val source: String,
        val partCount: Int = 1,
        val finishReasons: List<String> = emptyList()
    )

    private data class PcmAudioSpec(
        val sampleRate: Int = DEFAULT_PCM_SAMPLE_RATE,
        val channels: Int = DEFAULT_PCM_CHANNELS,
        val bitsPerSample: Int = DEFAULT_PCM_BITS_PER_SAMPLE
    )

    private enum class AudioContainer {
        WAV,
        PCM,
        MP3,
        OGG,
        AAC,
        UNKNOWN
    }
    
    /**
     * Get current settings
     */
    fun getSettings(): Flow<AgentSpeechSettings?> = settingsDao.getSettings()
    
    /**
     * Check if speech is enabled
     */
    suspend fun isEnabled(): Boolean {
        val settings = settingsDao.getSettingsOnce()
        val enabled = settings?.isEnabled ?: false
        Log.d(TAG, "isEnabled() called: settings=$settings, enabled=$enabled")
        return enabled
    }
    
    /**
     * Initialize settings with user email
     */
    suspend fun initializeSettings() {
        val currentSettings = settingsDao.getSettingsOnce()
        if (currentSettings == null) {
            val userEmail = FirebaseAuth.getInstance().currentUser?.email ?: ""
            val webhookUrl = generateWebhookUrl(userEmail)
            
            val settings = AgentSpeechSettings(
                id = 1,
                isEnabled = false,
                language = "EN-IN",
                userEmail = userEmail,
                webhookUrl = webhookUrl
            )
            settingsDao.saveSettings(settings)
            Log.d(TAG, "Settings initialized: $settings")
        }
    }
    
    /**
     * Update speech enabled status
     */
    suspend fun setEnabled(enabled: Boolean) {
        initializeSettings()
        settingsDao.updateEnabled(enabled)
        Log.d(TAG, "Speech enabled: $enabled")
    }
    
    /**
     * Update language
     */
    suspend fun setLanguage(language: String) {
        settingsDao.updateLanguage(language)
        Log.d(TAG, "Language updated: $language")
    }
    
    /**
     * Update voice
     */
    suspend fun setVoice(voiceName: String) {
        settingsDao.updateVoice(voiceName)
        Log.d(TAG, "Voice updated: $voiceName")
    }
    
    /**
     * Generate webhook URL based on user email
     */
    private fun generateWebhookUrl(email: String): String {
        val sanitizedEmail = email.replace("@", "_at_").replace(".", "_dot_")
        return "$workerUrl?user=$sanitizedEmail"
    }
    
    /**
     * Add text to speech queue
     * Returns queue ID
     */
    suspend fun addToQueue(text: String, phoneNumber: String): Long {
        val settings = settingsDao.getSettingsOnce()
        if (settings == null || !settings.isEnabled) {
            Log.w(TAG, "Speech not enabled, skipping queue")
            return -1
        }
        
        // Auto-detect language based on text content
        val detectedLanguage = detectLanguage(text, settings.language)
        Log.d(TAG, "Text language detected: $detectedLanguage (Settings: ${settings.language})")
        
        val queueItem = SpeechQueueItem(
            text = text,
            language = detectedLanguage,
            phoneNumber = phoneNumber,
            userEmail = settings.userEmail,
            status = "PENDING"
        )
        
        val queueId = queueDao.addToQueue(queueItem)
        Log.d(TAG, "Added to queue: ID=$queueId, Language=$detectedLanguage, Text=${text.take(50)}...")
        
        // Start processing queue
        processQueue()
        
        return queueId
    }
    
    /**
     * Detect language from text
     * Returns appropriate language code for TTS
     */
    private fun detectLanguage(text: String, defaultLanguage: String): String {
        // Count Hindi (Devanagari) characters
        val hindiCharCount = text.count { char ->
            char in '\u0900'..'\u097F' // Devanagari Unicode range
        }
        
        // Count English characters
        val englishCharCount = text.count { char ->
            char in 'A'..'Z' || char in 'a'..'z'
        }
        
        Log.d(TAG, "Language detection: Hindi chars=$hindiCharCount, English chars=$englishCharCount")
        
        // If more than 30% Hindi characters, use Hindi
        val totalChars = text.length
        val hindiPercentage = if (totalChars > 0) (hindiCharCount.toFloat() / totalChars) * 100 else 0f
        
        return when {
            hindiPercentage > 30 -> {
                Log.d(TAG, "Detected Hindi text (${hindiPercentage.toInt()}% Hindi chars) - Will use Gemini Flash TTS")
                "hi" // Hindi - will trigger Gemini Flash TTS
            }
            englishCharCount > hindiCharCount -> {
                Log.d(TAG, "Detected English text - Will use MeloTTS")
                when (defaultLanguage) {
                    "EN-IN" -> "EN-IN" // English India
                    "en" -> "en" // English
                    else -> "EN-IN" // Default to English India
                }
            }
            else -> {
                Log.d(TAG, "Using default language: $defaultLanguage")
                defaultLanguage
            }
        }
    }
    
    /**
     * Check if language requires Gemini Flash TTS (for Hindi)
     */
    private fun shouldUseGeminiTTS(language: String): Boolean {
        return language == "hi" || language.startsWith("hi-")
    }
    
    /**
     * Process speech queue (FIFO)
     */
    suspend fun processQueue() = withContext(Dispatchers.IO) {
        processingMutex.withLock {
            if (isProcessing) {
                Log.d(TAG, "Already processing queue")
                return@withContext
            }
            isProcessing = true
        }
        
        try {
            while (true) {
                val nextItem = queueDao.getNextPendingItem()
                if (nextItem == null) {
                    Log.d(TAG, "Queue empty")
                    break
                }
                
                Log.d(TAG, "Processing queue item: ${nextItem.id}")
                processQueueItem(nextItem)
            }
        } finally {
            processingMutex.withLock {
                isProcessing = false
            }
        }
    }
    
    /**
     * Process single queue item
     */
    private suspend fun processQueueItem(item: SpeechQueueItem) {
        try {
            queueDao.updateStatus(item.id, "PROCESSING")
            
            Log.d(TAG, "Generating speech for: ${item.text.take(50)}...")
            Log.d(TAG, "Language: ${item.language}")
            
            // Choose TTS engine based on language
            val generatedAudio = if (shouldUseGeminiTTS(item.language)) {
                Log.d(TAG, "Using Gemini Flash TTS for Hindi")
                generateSpeechWithGemini(
                    text = item.text,
                    phoneNumber = item.phoneNumber
                )
            } else {
                Log.d(TAG, "Using MeloTTS for English")
                generateSpeech(
                    text = item.text,
                    language = item.language,
                    phoneNumber = item.phoneNumber,
                    userEmail = item.userEmail
                )
            }
            
            if (generatedAudio != null) {
                // Save audio to file
                val audioFile = saveAudioToFile(generatedAudio, item.id)
                
                // Update queue as completed
                queueDao.updateCompleted(item.id, "COMPLETED", audioFile.absolutePath)
                
                // Add to history
                val history = SpeechHistory(
                    text = item.text,
                    language = item.language,
                    phoneNumber = item.phoneNumber,
                    audioPath = audioFile.absolutePath,
                    duration = estimateDuration(item.text),
                    processingTime = System.currentTimeMillis() - item.createdAt
                )
                historyDao.addHistory(history)
                
                Log.d(TAG, "Speech generated successfully: ${audioFile.absolutePath}")
                
                // Send voice message via WhatsApp
                try {
                    Log.d(TAG, "🚀 Calling sendVoiceMessage...")
                    sendVoiceMessage(item.phoneNumber, item.text.take(20), audioFile.absolutePath, item.id)
                    Log.d(TAG, "✅ sendVoiceMessage completed")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error in sendVoiceMessage: ${e.message}", e)
                }
            } else {
                queueDao.updateFailed(item.id, "FAILED", "Failed to generate speech")
                Log.e(TAG, "Failed to generate speech for queue item: ${item.id}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing queue item: ${item.id}", e)
            queueDao.updateFailed(item.id, "FAILED", e.message ?: "Unknown error")
        }
    }
    
    /**
     * Generate speech using MeloTTS worker
     */
    private suspend fun generateSpeech(
        text: String,
        language: String,
        phoneNumber: String,
        userEmail: String
    ): GeneratedAudio? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("text", text)
                put("language", language)
                put("phoneNumber", phoneNumber)
                put("userEmail", userEmail)
            }
            
            val requestBody = json.toString()
                .toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url(workerUrl)
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (responseBody == null) {
                Log.e(TAG, "TTS Error: Empty response body")
                return@withContext null
            }
            
            val jsonResponse = JSONObject(responseBody)
            
            if (jsonResponse.getBoolean("success")) {
                val audioBase64 = jsonResponse.getString("audio")
                val audioBytes = android.util.Base64.decode(audioBase64, android.util.Base64.DEFAULT)
                val mimeType = extractMimeTypeFromResponse(jsonResponse)
                GeneratedAudio(
                    bytes = audioBytes,
                    mimeType = mimeType,
                    source = "melotts",
                    partCount = 1,
                    finishReasons = emptyList()
                )
            } else {
                Log.e(TAG, "TTS Error: ${jsonResponse.getString("error")}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception generating speech", e)
            null
        }
    }
    
    /**
     * Generate speech using Gemini Flash TTS (for Hindi)
     */
    private suspend fun generateSpeechWithGemini(
        text: String,
        phoneNumber: String
    ): GeneratedAudio? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🎙️ Generating Hindi speech with Gemini Flash TTS")
            
            // Get API key and model from AIConfigManager
            val configManager = com.message.bulksend.autorespond.aireply.AIConfigManager(context)
            val geminiConfig = try {
                configManager.getConfig(com.message.bulksend.autorespond.aireply.AIProvider.GEMINI)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get Gemini config: ${e.message}", e)
                return@withContext null
            }
            
            val apiKey = geminiConfig.apiKey
            val baseModel = geminiConfig.model
            
            if (apiKey.isEmpty()) {
                Log.e(TAG, "Gemini API key not configured in AI settings")
                return@withContext null
            }
            
            if (baseModel.isEmpty()) {
                Log.e(TAG, "Gemini model not configured in AI settings")
                return@withContext null
            }
            
            // Convert regular model to TTS model
            // gemini-2.5-flash -> gemini-2.5-flash-preview-tts
            // gemini-2.5-pro -> gemini-2.5-pro-preview-tts
            val ttsModel = when {
                baseModel.contains("flash") && !baseModel.endsWith("-preview-tts") -> {
                    "gemini-2.5-flash-preview-tts"
                }
                baseModel.contains("pro") && !baseModel.endsWith("-preview-tts") -> {
                    "gemini-2.5-pro-preview-tts"
                }
                baseModel.endsWith("-preview-tts") -> baseModel // Already a TTS model
                else -> "gemini-2.5-flash-preview-tts" // Default fallback
            }
            
            Log.d(TAG, "✅ Base model from config: $baseModel")
            Log.d(TAG, "✅ Using TTS model: $ttsModel")
            Log.d(TAG, "✅ Using Gemini API key from AI config")
            
            // Use the TTS model
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$ttsModel:generateContent?key=$apiKey"

            // Get voice from settings
            val settings = settingsDao.getSettingsOnce()
            val voiceName = settings?.voiceName ?: "Aoede"
            Log.d(TAG, "✅ Using voice: $voiceName")

            val segments = splitTextForGeminiTts(
                text = text,
                maxCharsPerSegment = 140,
                maxWordsPerSegment = 22
            )
            if (segments.isEmpty()) {
                Log.e(TAG, "Gemini TTS Error: text is empty after normalization")
                return@withContext null
            }

            Log.d(
                TAG,
                "✅ Gemini segmented synthesis: segments=${segments.size}, textLength=${text.length} chars"
            )

            val segmentAudios = mutableListOf<ByteArray>()
            val finishReasons = mutableListOf<String>()
            var resolvedMimeType: String? = null
            var totalEmbeddedParts = 0

            segments.forEachIndexed { index, segmentText ->
                Log.d(
                    TAG,
                    "🎙️ Synthesizing Gemini segment ${index + 1}/${segments.size} (${segmentText.length} chars)"
                )

                val segmentAudio = requestGeminiSegmentAudio(
                    url = url,
                    voiceName = voiceName,
                    segmentText = segmentText,
                    segmentIndex = index,
                    totalSegments = segments.size
                ) ?: return@withContext null

                if (resolvedMimeType == null && !segmentAudio.mimeType.isNullOrBlank()) {
                    resolvedMimeType = segmentAudio.mimeType
                } else if (
                    !segmentAudio.mimeType.isNullOrBlank() &&
                    !resolvedMimeType.isNullOrBlank() &&
                    !segmentAudio.mimeType.equals(resolvedMimeType, ignoreCase = true)
                ) {
                    Log.w(
                        TAG,
                        "Gemini segment mime mismatch: expected=$resolvedMimeType actual=${segmentAudio.mimeType}"
                    )
                }

                segmentAudios.add(segmentAudio.bytes)
                finishReasons.addAll(segmentAudio.finishReasons)
                totalEmbeddedParts += segmentAudio.partCount
            }

            if (segmentAudios.isEmpty()) {
                Log.e(TAG, "Gemini TTS Error: no audio produced from segments")
                return@withContext null
            }

            val mergedAudio = mergeGeminiAudioChunks(segmentAudios, resolvedMimeType)
            Log.d(
                TAG,
                "✅ Gemini TTS merged across segments: segments=${segmentAudios.size}, bytes=${mergedAudio.size}, mime=${resolvedMimeType ?: "unknown"}"
            )
            GeneratedAudio(
                bytes = mergedAudio,
                mimeType = resolvedMimeType,
                source = "gemini",
                partCount = totalEmbeddedParts,
                finishReasons = finishReasons.distinct()
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception generating speech with Gemini: ${e.message}", e)
            null
        }
    }

    private fun splitTextForGeminiTts(
        text: String,
        maxCharsPerSegment: Int = 220,
        maxWordsPerSegment: Int = 35
    ): List<String> {
        val normalized = text.replace("\\s+".toRegex(), " ").trim()
        if (normalized.isBlank()) return emptyList()

        val segmentBySentence = mutableListOf<String>()
        val sentenceChunks = normalized
            .split("(?<=[.!?।])\\s+".toRegex())
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val pendingSentences = if (sentenceChunks.isNotEmpty()) sentenceChunks else listOf(normalized)
        var current = StringBuilder()
        var currentWords = 0

        fun flushCurrent() {
            if (current.isNotEmpty()) {
                val value = current.toString().trim()
                if (value.isNotEmpty()) {
                    segmentBySentence.add(value)
                }
                current = StringBuilder()
                currentWords = 0
            }
        }

        pendingSentences.forEach { sentence ->
            val sentenceWords = countWords(sentence)
            val sentenceTooLarge = sentence.length > maxCharsPerSegment || sentenceWords > maxWordsPerSegment

            if (sentenceTooLarge) {
                flushCurrent()
                val words = sentence.split("\\s+".toRegex()).filter { it.isNotBlank() }
                var wordChunk = StringBuilder()
                var wordChunkCount = 0

                words.forEach { word ->
                    val fitsChars = wordChunk.isEmpty() || (wordChunk.length + 1 + word.length <= maxCharsPerSegment)
                    val fitsWords = wordChunkCount < maxWordsPerSegment

                    if (!fitsChars || !fitsWords) {
                        val value = wordChunk.toString().trim()
                        if (value.isNotEmpty()) {
                            segmentBySentence.add(value)
                        }
                        wordChunk = StringBuilder()
                        wordChunkCount = 0
                    }

                    if (wordChunk.isNotEmpty()) wordChunk.append(' ')
                    wordChunk.append(word)
                    wordChunkCount++
                }

                val lastValue = wordChunk.toString().trim()
                if (lastValue.isNotEmpty()) {
                    segmentBySentence.add(lastValue)
                }
            } else {
                val fitsChars = current.isEmpty() || (current.length + 1 + sentence.length <= maxCharsPerSegment)
                val fitsWords = currentWords + sentenceWords <= maxWordsPerSegment
                if (!fitsChars || !fitsWords) {
                    flushCurrent()
                }
                if (current.isNotEmpty()) current.append(' ')
                current.append(sentence)
                currentWords += sentenceWords
            }
        }

        flushCurrent()
        return segmentBySentence.filter { it.isNotBlank() }
    }

    private fun requestGeminiSegmentAudio(
        url: String,
        voiceName: String,
        segmentText: String,
        segmentIndex: Int,
        totalSegments: Int
    ): GeneratedAudio? {
        var lastFailureReason = "unknown"
        for (attempt in 1..GEMINI_SEGMENT_MAX_RETRIES) {
            val extracted = requestGeminiSegmentAudioOnce(
                url = url,
                voiceName = voiceName,
                segmentText = segmentText,
                segmentIndex = segmentIndex,
                totalSegments = totalSegments
            )

            if (extracted != null) {
                val abnormalFinishReason = hasAbnormalGeminiFinishReason(extracted.finishReasons)
                if (!abnormalFinishReason) {
                    return extracted
                }

                lastFailureReason =
                    "abnormal_finish_reason=${extracted.finishReasons.joinToString(",")}"
                Log.w(
                    TAG,
                    "⚠️ Gemini segment ${segmentIndex + 1}/$totalSegments incomplete on attempt $attempt: $lastFailureReason"
                )
            } else {
                lastFailureReason = "empty_or_failed_response"
            }

            if (attempt < GEMINI_SEGMENT_MAX_RETRIES) {
                val backoff = GEMINI_RETRY_BACKOFF_MS * attempt
                Log.w(
                    TAG,
                    "🔁 Retrying Gemini segment ${segmentIndex + 1}/$totalSegments in ${backoff}ms (attempt ${attempt + 1}/${GEMINI_SEGMENT_MAX_RETRIES})"
                )
                try {
                    Thread.sleep(backoff)
                } catch (_: InterruptedException) {
                }
            }
        }

        Log.e(
            TAG,
            "❌ Gemini segment ${segmentIndex + 1}/$totalSegments failed after $GEMINI_SEGMENT_MAX_RETRIES attempts ($lastFailureReason)"
        )
        return null
    }

    private fun requestGeminiSegmentAudioOnce(
        url: String,
        voiceName: String,
        segmentText: String,
        segmentIndex: Int,
        totalSegments: Int
    ): GeneratedAudio? {
        val prompt = """Read the following text exactly as written in natural conversational Hindi.
            |Do not skip, shorten, summarize, or omit any word.
            |Do not mention segment numbers.
            |
            |$segmentText""".trimMargin()

        val json = JSONObject().apply {
            put("contents", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseModalities", org.json.JSONArray().apply {
                    put("AUDIO")
                })
                put("maxOutputTokens", 4096)
                put("speechConfig", JSONObject().apply {
                    put("voiceConfig", JSONObject().apply {
                        put("prebuiltVoiceConfig", JSONObject().apply {
                            put("voiceName", voiceName)
                        })
                    })
                })
            })
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string()
            Log.e(
                TAG,
                "Gemini segment request failed (${segmentIndex + 1}/$totalSegments): code=${response.code}, body=${errorBody?.take(300)}"
            )
            return null
        }

        val responseBody = response.body?.string()
        if (responseBody.isNullOrBlank()) {
            Log.e(TAG, "Gemini segment response empty (${segmentIndex + 1}/$totalSegments)")
            return null
        }

        val jsonResponse = JSONObject(responseBody)
        val extracted = extractGeminiAudioFromResponse(jsonResponse)
        if (extracted == null) {
            Log.e(
                TAG,
                "Gemini segment has no audio (${segmentIndex + 1}/$totalSegments): ${responseBody.take(400)}"
            )
            return null
        }

        Log.d(
            TAG,
            "✅ Gemini segment audio extracted (${segmentIndex + 1}/$totalSegments): bytes=${extracted.bytes.size}, parts=${extracted.partCount}"
        )
        return extracted
    }

    private fun hasAbnormalGeminiFinishReason(finishReasons: List<String>): Boolean {
        if (finishReasons.isEmpty()) return false
        return finishReasons.any { reason ->
            val normalized = reason.trim().uppercase(Locale.ROOT)
            normalized.isNotEmpty() &&
                normalized != "STOP" &&
                normalized != "FINISH_REASON_UNSPECIFIED"
        }
    }

    private fun extractGeminiAudioFromResponse(jsonResponse: JSONObject): GeneratedAudio? {
        val candidates = jsonResponse.optJSONArray("candidates") ?: return null
        if (candidates.length() <= 0) return null

        val audioChunks = mutableListOf<ByteArray>()
        val finishReasons = mutableListOf<String>()
        var resolvedMimeType: String? = null
        var totalBase64Chars = 0

        for (candidateIndex in 0 until candidates.length()) {
            val candidate = candidates.optJSONObject(candidateIndex) ?: continue
            val finishReason = candidate.optString("finishReason")
            if (finishReason.isNotBlank()) {
                finishReasons.add(finishReason)
                Log.d(TAG, "Gemini candidate[$candidateIndex] finishReason=$finishReason")
            }
            val content = candidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts") ?: continue

            for (partIndex in 0 until parts.length()) {
                val part = parts.optJSONObject(partIndex) ?: continue
                val inlineData = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data")
                    ?: continue

                val audioBase64 = inlineData.optString("data")
                if (audioBase64.isBlank()) continue

                val chunkMimeType = inlineData.optString("mimeType")
                    .ifBlank { inlineData.optString("mime_type") }
                    .takeIf { it.isNotBlank() }

                if (resolvedMimeType == null && chunkMimeType != null) {
                    resolvedMimeType = chunkMimeType
                } else if (
                    chunkMimeType != null &&
                    resolvedMimeType != null &&
                    !chunkMimeType.equals(resolvedMimeType, ignoreCase = true)
                ) {
                    Log.w(TAG, "Gemini TTS mixed mime types: $resolvedMimeType and $chunkMimeType")
                }

                val decodedChunk = android.util.Base64.decode(audioBase64, android.util.Base64.DEFAULT)
                if (decodedChunk.isNotEmpty()) {
                    audioChunks.add(decodedChunk)
                    totalBase64Chars += audioBase64.length
                }
            }
        }

        if (audioChunks.isEmpty()) {
            return null
        }

        val mergedAudio = mergeGeminiAudioChunks(audioChunks, resolvedMimeType)
        Log.d(
            TAG,
            "✅ Gemini response audio merged: chunks=${audioChunks.size}, base64Chars=$totalBase64Chars, bytes=${mergedAudio.size}, mime=${resolvedMimeType ?: "unknown"}"
        )
        return GeneratedAudio(
            bytes = mergedAudio,
            mimeType = resolvedMimeType,
            source = "gemini",
            partCount = audioChunks.size,
            finishReasons = finishReasons.distinct()
        )
    }
    
    /**
     * Save audio data to file
     * Detects audio container and writes in a WhatsApp-friendly format.
     */
    private fun saveAudioToFile(generatedAudio: GeneratedAudio, queueId: Long): File {
        val audioDir = File(context.filesDir, "agent_speech")
        if (!audioDir.exists()) {
            audioDir.mkdirs()
        }

        val timestamp = System.currentTimeMillis()
        val mimeType = generatedAudio.mimeType.orEmpty()
        val container = detectAudioContainer(generatedAudio.bytes, mimeType, generatedAudio.source)

        try {
            when (container) {
                AudioContainer.WAV -> {
                    val audioFile = File(audioDir, "speech_${queueId}_${timestamp}.wav")
                    val paddedWav = appendTrailingSilenceToWav(
                        wavData = generatedAudio.bytes,
                        silenceMs = TRAILING_SILENCE_MS
                    ) ?: generatedAudio.bytes
                    audioFile.writeBytes(paddedWav)
                    Log.d(TAG, "✅ WAV audio saved: ${audioFile.name}, size: ${paddedWav.size} bytes")
                    return audioFile
                }
                AudioContainer.PCM -> {
                    val audioFile = File(audioDir, "speech_${queueId}_${timestamp}.wav")
                    val pcmSpec = parsePcmSpec(mimeType)
                    val paddedPcm = appendTrailingSilenceToPcm(
                        pcmData = generatedAudio.bytes,
                        spec = pcmSpec,
                        silenceMs = TRAILING_SILENCE_MS
                    )
                    val wavData = addWavHeader(
                        pcmData = paddedPcm,
                        sampleRate = pcmSpec.sampleRate,
                        channels = pcmSpec.channels,
                        bitsPerSample = pcmSpec.bitsPerSample
                    )
                    audioFile.writeBytes(wavData)
                    Log.d(
                        TAG,
                        "✅ PCM converted to WAV: ${audioFile.name}, size: ${wavData.size} bytes, spec=$pcmSpec"
                    )
                    return audioFile
                }
                AudioContainer.MP3 -> {
                    val audioFile = File(audioDir, "speech_${queueId}_${timestamp}.mp3")
                    audioFile.writeBytes(generatedAudio.bytes)
                    Log.d(TAG, "✅ MP3 audio saved: ${audioFile.name}, size: ${generatedAudio.bytes.size} bytes")
                    return audioFile
                }
                AudioContainer.OGG -> {
                    val audioFile = File(audioDir, "speech_${queueId}_${timestamp}.ogg")
                    audioFile.writeBytes(generatedAudio.bytes)
                    Log.d(TAG, "✅ OGG audio saved: ${audioFile.name}, size: ${generatedAudio.bytes.size} bytes")
                    return audioFile
                }
                AudioContainer.AAC -> {
                    val extension = if (mimeType.lowercase(Locale.ROOT).contains("mp4")) "m4a" else "aac"
                    val audioFile = File(audioDir, "speech_${queueId}_${timestamp}.$extension")
                    audioFile.writeBytes(generatedAudio.bytes)
                    Log.d(TAG, "✅ AAC audio saved: ${audioFile.name}, size: ${generatedAudio.bytes.size} bytes")
                    return audioFile
                }
                AudioContainer.UNKNOWN -> {
                    val audioFile = File(audioDir, "speech_${queueId}_${timestamp}.wav")
                    val paddedPcm = appendTrailingSilenceToPcm(
                        pcmData = generatedAudio.bytes,
                        spec = PcmAudioSpec(),
                        silenceMs = TRAILING_SILENCE_MS
                    )
                    val wavData = addWavHeader(
                        pcmData = paddedPcm,
                        sampleRate = DEFAULT_PCM_SAMPLE_RATE,
                        channels = DEFAULT_PCM_CHANNELS,
                        bitsPerSample = DEFAULT_PCM_BITS_PER_SAMPLE
                    )
                    audioFile.writeBytes(wavData)
                    Log.w(TAG, "⚠️ Unknown audio container, fallback PCM->WAV: ${audioFile.name}")
                    return audioFile
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save audio with format detection", e)
            val fallbackFile = File(audioDir, "speech_${queueId}_${timestamp}.wav")
            val paddedPcm = appendTrailingSilenceToPcm(
                pcmData = generatedAudio.bytes,
                spec = PcmAudioSpec(),
                silenceMs = TRAILING_SILENCE_MS
            )
            val wavData = addWavHeader(
                pcmData = paddedPcm,
                sampleRate = DEFAULT_PCM_SAMPLE_RATE,
                channels = DEFAULT_PCM_CHANNELS,
                bitsPerSample = DEFAULT_PCM_BITS_PER_SAMPLE
            )
            fallbackFile.writeBytes(wavData)
            return fallbackFile
        }
    }
    
    /**
     * Add WAV header to PCM audio data
     * Gemini returns PCM 16-bit 24kHz mono audio
     */
    private fun addWavHeader(pcmData: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val totalSize = 36 + dataSize
        
        val header = ByteArray(44)
        
        // RIFF header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        
        // File size - 8
        header[4] = (totalSize and 0xff).toByte()
        header[5] = ((totalSize shr 8) and 0xff).toByte()
        header[6] = ((totalSize shr 16) and 0xff).toByte()
        header[7] = ((totalSize shr 24) and 0xff).toByte()
        
        // WAVE header
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        
        // fmt subchunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        
        // Subchunk1Size (16 for PCM)
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        
        // AudioFormat (1 for PCM)
        header[20] = 1
        header[21] = 0
        
        // NumChannels
        header[22] = channels.toByte()
        header[23] = 0
        
        // SampleRate
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        
        // ByteRate
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        
        // BlockAlign
        header[32] = blockAlign.toByte()
        header[33] = 0
        
        // BitsPerSample
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        
        // data subchunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        
        // Subchunk2Size
        header[40] = (dataSize and 0xff).toByte()
        header[41] = ((dataSize shr 8) and 0xff).toByte()
        header[42] = ((dataSize shr 16) and 0xff).toByte()
        header[43] = ((dataSize shr 24) and 0xff).toByte()
        
        // Combine header and PCM data
        return header + pcmData
    }

    private fun mergeGeminiAudioChunks(audioChunks: List<ByteArray>, mimeType: String?): ByteArray {
        if (audioChunks.isEmpty()) return ByteArray(0)
        if (audioChunks.size == 1) return audioChunks[0]

        val normalizedMime = mimeType.orEmpty().lowercase(Locale.ROOT)
        val looksLikeWav = normalizedMime.contains("wav") || audioChunks.all { isWavData(it) }

        return if (looksLikeWav) {
            mergeWavChunks(audioChunks)
        } else {
            concatenateByteArrays(audioChunks)
        }
    }

    private fun mergeWavChunks(wavChunks: List<ByteArray>): ByteArray {
        if (wavChunks.isEmpty()) return ByteArray(0)
        if (wavChunks.size == 1) return wavChunks[0]

        val firstChunk = wavChunks.first()
        if (!isWavData(firstChunk) || firstChunk.size < 44) {
            return concatenateByteArrays(wavChunks)
        }

        val channels = readLittleEndianShort(firstChunk, 22)
        val sampleRate = readLittleEndianInt(firstChunk, 24)
        val bitsPerSample = readLittleEndianShort(firstChunk, 34)
        if (channels <= 0 || sampleRate <= 0 || bitsPerSample <= 0) {
            return concatenateByteArrays(wavChunks)
        }

        val pcmChunks = mutableListOf<ByteArray>()
        wavChunks.forEach { chunk ->
            if (isWavData(chunk) && chunk.size >= 44) {
                val dataSize = readLittleEndianInt(chunk, 40)
                val safeDataSize = dataSize.coerceIn(0, chunk.size - 44)
                pcmChunks.add(chunk.copyOfRange(44, 44 + safeDataSize))
            } else {
                pcmChunks.add(chunk)
            }
        }

        val mergedPcm = concatenateByteArrays(pcmChunks)
        return addWavHeader(
            pcmData = mergedPcm,
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample
        )
    }

    private fun concatenateByteArrays(chunks: List<ByteArray>): ByteArray {
        if (chunks.isEmpty()) return ByteArray(0)
        if (chunks.size == 1) return chunks[0]

        val totalSize = chunks.sumOf { it.size }
        val merged = ByteArray(totalSize)
        var offset = 0
        chunks.forEach { chunk ->
            System.arraycopy(chunk, 0, merged, offset, chunk.size)
            offset += chunk.size
        }
        return merged
    }

    private fun countWords(text: String): Int {
        return text.trim()
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .size
    }

    private fun extractMimeTypeFromResponse(jsonResponse: JSONObject): String? {
        val directMimeType = listOf("mimeType", "audioMimeType", "mime_type")
            .asSequence()
            .map { jsonResponse.optString(it).trim() }
            .firstOrNull { it.isNotEmpty() }
        if (directMimeType != null) {
            return directMimeType
        }

        val format = jsonResponse.optString("format").trim().lowercase(Locale.ROOT)
        return when (format) {
            "wav", "wave" -> "audio/wav"
            "mp3", "mpeg" -> "audio/mpeg"
            "ogg" -> "audio/ogg"
            "aac" -> "audio/aac"
            "pcm", "raw", "l16" -> "audio/L16;rate=$DEFAULT_PCM_SAMPLE_RATE"
            else -> null
        }
    }

    private fun detectAudioContainer(audioData: ByteArray, mimeType: String, source: String): AudioContainer {
        val normalizedMime = mimeType.trim().lowercase(Locale.ROOT)

        if (isWavData(audioData)) return AudioContainer.WAV
        if (isMp3Data(audioData)) return AudioContainer.MP3
        if (isOggData(audioData)) return AudioContainer.OGG

        if (normalizedMime.contains("wav")) return AudioContainer.WAV
        if (normalizedMime.contains("mpeg") || normalizedMime.contains("mp3")) return AudioContainer.MP3
        if (normalizedMime.contains("ogg")) return AudioContainer.OGG
        if (
            normalizedMime.contains("aac") ||
            normalizedMime.contains("mp4a") ||
            normalizedMime.contains("audio/mp4") ||
            normalizedMime.contains("m4a")
        ) return AudioContainer.AAC
        if (
            normalizedMime.contains("pcm") ||
            normalizedMime.contains("l16") ||
            normalizedMime.contains("l24") ||
            normalizedMime.contains("audio/raw")
        ) return AudioContainer.PCM

        // Gemini TTS currently returns raw PCM with MIME like audio/L16;rate=24000.
        if (source == "gemini") return AudioContainer.PCM

        return AudioContainer.UNKNOWN
    }

    private fun parsePcmSpec(mimeType: String): PcmAudioSpec {
        val normalizedMime = mimeType.trim().lowercase(Locale.ROOT)
        if (normalizedMime.isEmpty()) {
            return PcmAudioSpec()
        }

        val sampleRate = Regex("rate\\s*=\\s*(\\d+)", RegexOption.IGNORE_CASE)
            .find(normalizedMime)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: DEFAULT_PCM_SAMPLE_RATE

        val channels = Regex("channels\\s*=\\s*(\\d+)", RegexOption.IGNORE_CASE)
            .find(normalizedMime)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: DEFAULT_PCM_CHANNELS

        val bitsPerSample = Regex("l(\\d+)", RegexOption.IGNORE_CASE)
            .find(normalizedMime)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.takeIf { it in 8..32 }
            ?: DEFAULT_PCM_BITS_PER_SAMPLE

        return PcmAudioSpec(
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample
        )
    }

    private fun appendTrailingSilenceToPcm(
        pcmData: ByteArray,
        spec: PcmAudioSpec,
        silenceMs: Int
    ): ByteArray {
        val bytesPerFrame = (spec.channels * spec.bitsPerSample) / 8
        if (bytesPerFrame <= 0 || silenceMs <= 0) {
            return pcmData
        }

        val bytesPerSecond = spec.sampleRate * bytesPerFrame
        if (bytesPerSecond <= 0) {
            return pcmData
        }

        val rawSilenceBytes = (bytesPerSecond.toLong() * silenceMs / 1000L).toInt()
        val alignedSilenceBytes = ((rawSilenceBytes + bytesPerFrame - 1) / bytesPerFrame) * bytesPerFrame
        if (alignedSilenceBytes <= 0) {
            return pcmData
        }

        return pcmData + ByteArray(alignedSilenceBytes)
    }

    private fun appendTrailingSilenceToWav(wavData: ByteArray, silenceMs: Int): ByteArray? {
        if (!isWavData(wavData) || silenceMs <= 0 || wavData.size < 44) {
            return null
        }

        if (
            wavData[12].toInt().toChar() != 'f' ||
            wavData[13].toInt().toChar() != 'm' ||
            wavData[14].toInt().toChar() != 't' ||
            wavData[15].toInt().toChar() != ' ' ||
            wavData[36].toInt().toChar() != 'd' ||
            wavData[37].toInt().toChar() != 'a' ||
            wavData[38].toInt().toChar() != 't' ||
            wavData[39].toInt().toChar() != 'a'
        ) {
            // Skip padding for non-standard WAV layouts.
            return wavData
        }

        val channels = readLittleEndianShort(wavData, 22)
        val sampleRate = readLittleEndianInt(wavData, 24)
        val bitsPerSample = readLittleEndianShort(wavData, 34)
        val dataSize = readLittleEndianInt(wavData, 40)

        val bytesPerFrame = (channels * bitsPerSample) / 8
        if (channels <= 0 || sampleRate <= 0 || bitsPerSample <= 0 || bytesPerFrame <= 0 || dataSize < 0) {
            return wavData
        }

        val bytesPerSecond = sampleRate * bytesPerFrame
        val rawSilenceBytes = (bytesPerSecond.toLong() * silenceMs / 1000L).toInt()
        val alignedSilenceBytes = ((rawSilenceBytes + bytesPerFrame - 1) / bytesPerFrame) * bytesPerFrame
        if (alignedSilenceBytes <= 0) {
            return wavData
        }

        val paddedWav = wavData.copyOf(wavData.size + alignedSilenceBytes)
        val newDataSize = dataSize + alignedSilenceBytes
        val newRiffSize = 36 + newDataSize

        writeLittleEndianInt(paddedWav, 4, newRiffSize)
        writeLittleEndianInt(paddedWav, 40, newDataSize)

        return paddedWav
    }

    private fun isWavData(audioData: ByteArray): Boolean {
        return audioData.size >= 12 &&
            audioData[0].toInt().toChar() == 'R' &&
            audioData[1].toInt().toChar() == 'I' &&
            audioData[2].toInt().toChar() == 'F' &&
            audioData[3].toInt().toChar() == 'F' &&
            audioData[8].toInt().toChar() == 'W' &&
            audioData[9].toInt().toChar() == 'A' &&
            audioData[10].toInt().toChar() == 'V' &&
            audioData[11].toInt().toChar() == 'E'
    }

    private fun isMp3Data(audioData: ByteArray): Boolean {
        return (audioData.size >= 3 &&
            audioData[0].toInt().toChar() == 'I' &&
            audioData[1].toInt().toChar() == 'D' &&
            audioData[2].toInt().toChar() == '3') ||
            (audioData.size >= 2 &&
                (audioData[0].toInt() and 0xFF) == 0xFF &&
                (audioData[1].toInt() and 0xE0) == 0xE0)
    }

    private fun isOggData(audioData: ByteArray): Boolean {
        return audioData.size >= 4 &&
            audioData[0].toInt().toChar() == 'O' &&
            audioData[1].toInt().toChar() == 'g' &&
            audioData[2].toInt().toChar() == 'g' &&
            audioData[3].toInt().toChar() == 'S'
    }

    private fun readLittleEndianInt(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xff) or
            ((data[offset + 1].toInt() and 0xff) shl 8) or
            ((data[offset + 2].toInt() and 0xff) shl 16) or
            ((data[offset + 3].toInt() and 0xff) shl 24)
    }

    private fun readLittleEndianShort(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xff) or ((data[offset + 1].toInt() and 0xff) shl 8)
    }

    private fun writeLittleEndianInt(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value and 0xff).toByte()
        data[offset + 1] = ((value shr 8) and 0xff).toByte()
        data[offset + 2] = ((value shr 16) and 0xff).toByte()
        data[offset + 3] = ((value shr 24) and 0xff).toByte()
    }
    
    /**
     * Send voice message via WhatsApp
     */
    private fun sendVoiceMessage(phoneNumber: String, senderName: String, audioPath: String, queueId: Long) {
        try {
            Log.d(TAG, "🎤 Sending voice message to $phoneNumber")
            
            // Enable speech send service
            AgentSpeechSendService.enableSpeechSend()
            
            // Add to send queue
            val sendService = AgentSpeechSendService.getInstance()
            sendService.addSpeechSendTask(
                context = context,
                phoneNumber = phoneNumber,
                senderName = senderName,
                audioPath = audioPath,
                queueId = queueId
            )
            
            Log.d(TAG, "✅ Voice message send task added")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error adding voice send task: ${e.message}", e)
        }
    }
    
    /**
     * Estimate audio duration (rough estimate: ~150 words per minute)
     */
    private fun estimateDuration(text: String): Int {
        val words = text.trim().split("\\s+".toRegex()).size
        val minutes = words / 150.0
        return (minutes * 60).toInt()
    }
    
    /**
     * Get audio file path for queue item
     */
    suspend fun getAudioPath(queueId: Long): String? {
        val item = queueDao.getNextPendingItem() // This is a placeholder, need proper query
        return item?.audioPath
    }
    
    /**
     * Get pending queue count
     */
    fun getPendingCount(): Flow<Int> = queueDao.getPendingCount()
    
    /**
     * Get recent history
     */
    fun getRecentHistory(): Flow<List<SpeechHistory>> = historyDao.getRecentHistory()
    
    /**
     * Cleanup old data
     */
    suspend fun cleanup() {
        val oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
        queueDao.cleanupOldCompleted(oneWeekAgo)
        historyDao.cleanupOldHistory(oneWeekAgo)
        Log.d(TAG, "Cleanup completed")
    }
}
