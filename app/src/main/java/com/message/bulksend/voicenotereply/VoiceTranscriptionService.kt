package com.message.bulksend.voicenotereply

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Voice Transcription Service
 * Handles voice note transcription using Cloudflare Workers AI
 * Features:
 * - User-specific URLs based on Gmail ID
 * - Queue-based processing for multiple requests
 * - Real-time UI updates
 */
object VoiceTranscriptionService {
    
    private const val TAG = "VoiceTranscription"
    private const val WORKER_BASE_URL = "https://voice-transcription-worker.aawuazer.workers.dev"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    // Queue for processing multiple requests
    private val transcriptionQueue = LinkedBlockingQueue<TranscriptionRequest>()
    private val queueMutex = Mutex()
    private var isProcessing = false
    
    // Callback for real-time UI updates
    private var onTranscriptionCallback: ((TranscriptionResult) -> Unit)? = null
    
    /**
     * Set callback for real-time UI updates
     */
    fun setTranscriptionCallback(callback: (TranscriptionResult) -> Unit) {
        onTranscriptionCallback = callback
        Log.d(TAG, "✅ Transcription callback registered")
    }
    
    /**
     * Clear callback
     */
    fun clearCallback() {
        onTranscriptionCallback = null
        Log.d(TAG, "❌ Transcription callback cleared")
    }
    
    /**
     * Get user-specific worker URL based on Gmail ID
     */
    private fun getUserWorkerUrl(context: Context): String {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val userEmail = currentUser?.email
        
        return if (userEmail != null) {
            // Create unique identifier from email (remove special chars)
            val userId = userEmail.replace(Regex("[^a-zA-Z0-9]"), "").take(20)
            "$WORKER_BASE_URL?user=$userId"
        } else {
            WORKER_BASE_URL
        }
    }
    
    /**
     * Add transcription request to queue
     * Language is always auto-detected by Whisper AI
     */
    suspend fun queueTranscription(
        context: Context,
        audioFile: File,
        phoneNumber: String
    ) {
        val request = TranscriptionRequest(
            context = context,
            audioFile = audioFile,
            phoneNumber = phoneNumber,
            timestamp = System.currentTimeMillis()
        )
        
        transcriptionQueue.offer(request)
        Log.d(TAG, "📥 Added to queue: $phoneNumber (Queue size: ${transcriptionQueue.size})")
        
        // Start processing if not already running
        processQueue()
    }
    
    /**
     * Process transcription queue
     */
    private suspend fun processQueue() = withContext(Dispatchers.IO) {
        queueMutex.withLock {
            if (isProcessing) {
                Log.d(TAG, "⏳ Already processing queue")
                return@withContext
            }
            isProcessing = true
        }
        
        try {
            while (transcriptionQueue.isNotEmpty()) {
                val request = transcriptionQueue.poll()
                if (request != null) {
                    Log.d(TAG, "🔄 Processing: ${request.phoneNumber} (${transcriptionQueue.size} remaining)")
                    
                    val result = transcribeInternal(
                        request.context,
                        request.audioFile,
                        request.phoneNumber
                    )
                    
                    // Trigger callback for UI update
                    onTranscriptionCallback?.invoke(result)
                }
            }
        } finally {
            queueMutex.withLock {
                isProcessing = false
            }
            Log.d(TAG, "✅ Queue processing complete")
        }
    }
    
    /**
     * Transcribe voice note (direct call, bypasses queue)
     * Language is always auto-detected by Whisper AI
     */
    suspend fun transcribe(
        context: Context,
        audioFile: File,
        phoneNumber: String
    ): TranscriptionResult {
        return transcribeInternal(context, audioFile, phoneNumber)
    }
    
    /**
     * Internal transcription logic
     * Language is always auto-detected by Whisper AI
     */
    private suspend fun transcribeInternal(
        context: Context,
        audioFile: File,
        phoneNumber: String
    ): TranscriptionResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🎤 Transcribing voice note")
            Log.d(TAG, "📞 Phone: $phoneNumber")
            Log.d(TAG, "📁 File: ${audioFile.name}")
            Log.d(TAG, "📊 Size: ${audioFile.length()} bytes")
            Log.d(TAG, "🌍 Language: auto-detect (Hinglish supported)")
            
            // Check if file exists
            if (!audioFile.exists()) {
                Log.e(TAG, "❌ File not found: ${audioFile.absolutePath}")
                return@withContext TranscriptionResult.Error(
                    phoneNumber = phoneNumber,
                    message = "Audio file not found"
                )
            }
            
            // Read and encode audio
            val audioBytes = audioFile.readBytes()
            val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
            
            Log.d(TAG, "📊 Base64 size: ${base64Audio.length} characters")
            
            // Get user-specific URL
            val workerUrl = getUserWorkerUrl(context)
            Log.d(TAG, "🌐 Worker URL: $workerUrl")
            
            // Create JSON payload (no language = auto-detect)
            val json = JSONObject().apply {
                put("audio", base64Audio)
                // Language omitted = Whisper auto-detects (best for Hinglish)
                put("phoneNumber", phoneNumber)
            }
            
            // Make HTTP request
            val request = Request.Builder()
                .url(workerUrl)
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            Log.d(TAG, "📤 Sending request to Cloudflare Worker...")
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            
            Log.d(TAG, "📥 Response received: ${response.code}")
            
            if (response.isSuccessful) {
                val result = JSONObject(responseBody)
                
                if (result.getBoolean("success")) {
                    val text = result.getString("text")
                    val processingTime = result.getLong("processingTime")
                    
                    Log.d(TAG, "✅ Transcription successful!")
                    Log.d(TAG, "📝 Text: $text")
                    Log.d(TAG, "⏱️ Processing time: ${processingTime}ms")
                    
                    // Save transcription text to file
                    VoiceNoteFileObserver.saveTranscriptionText(audioFile, text)
                    
                    // CRITICAL: Trigger AI Agent processing with transcribed text
                    triggerAIAgentProcessing(context, phoneNumber, text)
                    
                    TranscriptionResult.Success(
                        phoneNumber = phoneNumber,
                        text = text,
                        processingTime = processingTime,
                        audioFile = audioFile
                    )
                } else {
                    val error = result.getString("error")
                    Log.e(TAG, "❌ Transcription failed: $error")
                    TranscriptionResult.Error(
                        phoneNumber = phoneNumber,
                        message = error
                    )
                }
            } else {
                Log.e(TAG, "❌ HTTP error: ${response.code}")
                TranscriptionResult.Error(
                    phoneNumber = phoneNumber,
                    message = "HTTP ${response.code}: ${response.message}"
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception: ${e.message}", e)
            TranscriptionResult.Error(
                phoneNumber = phoneNumber,
                message = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * Trigger AI Agent processing with transcribed text
     * This replaces the voice note notification text with actual transcription
     */
    private suspend fun triggerAIAgentProcessing(
        context: Context,
        phoneNumber: String,
        transcribedText: String
    ) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🤖 Triggering AI Agent with transcribed text")
            Log.d(TAG, "📞 Phone: $phoneNumber")
            Log.d(TAG, "📝 Text: $transcribedText")
            
            // Get message database
            val messageRepository = com.message.bulksend.autorespond.database.MessageRepository(context)
            
            // Find the most recent WAITING_TRANSCRIPTION message for this phone number
            val messages = messageRepository.getAllMessages().first()
            val waitingMessage = messages
                .filter { it.phoneNumber == phoneNumber && it.status == "WAITING_TRANSCRIPTION" }
                .maxByOrNull { it.timestamp }
            
            if (waitingMessage == null) {
                Log.w(TAG, "⚠️ No WAITING_TRANSCRIPTION message found for $phoneNumber")
                return@withContext
            }
            
            Log.d(TAG, "✓ Found waiting message ID: ${waitingMessage.id}")

            // NEW: Check for Owner Assist interception (strict owner-number only)
            val ownerAssistManager = com.message.bulksend.aiagent.tools.ownerassist.OwnerAssistManager(context)
            if (ownerAssistManager.isAuthorizedOwner(phoneNumber)) {
                Log.d(TAG, "🔄 Message is from Owner! Diverting to Owner Assist.")
                val responseMsg = ownerAssistManager.processOwnerInstruction(transcribedText)

                // Keep the WAITING_TRANSCRIPTION message logged but marked handled
                messageRepository.updateMessageWithReply(waitingMessage.id, responseMsg, "SENT")

                // Reply to the owner
                val intent = android.content.Intent("com.message.bulksend.SEND_VOICE_NOTE_REPLY").apply {
                    putExtra("phoneNumber", phoneNumber)
                    putExtra("senderName", waitingMessage.senderName)
                    putExtra("replyText", responseMsg)
                    putExtra("messageId", waitingMessage.id)
                }
                context.sendBroadcast(intent)
                return@withContext
            }
            // END Owner Assist interception

            // Check if AI Agent is enabled
            val aiAgentSettings = com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager(context)
            if (!aiAgentSettings.isAgentEnabled) {
                Log.d(TAG, "❌ AI Agent is disabled, skipping")
                messageRepository.updateMessageWithReply(waitingMessage.id, "", "AI_DISABLED")
                return@withContext
            }
            
            // Check if auto-respond is enabled
            val autoRespondManager = com.message.bulksend.autorespond.AutoRespondManager(context)
            if (!autoRespondManager.isAutoRespondEnabled()) {
                Log.d(TAG, "❌ Auto-respond is disabled, skipping")
                messageRepository.updateMessageWithReply(waitingMessage.id, "", "DISABLED")
                return@withContext
            }
            
            Log.d(TAG, "🚀 Generating AI reply for transcribed text...")
            
            // Generate AI reply with transcribed text using AIService
            val aiReplyManager = com.message.bulksend.autorespond.aireply.AIReplyManager(context)
            val provider = aiReplyManager.getSelectedProvider()
            val aiService = com.message.bulksend.autorespond.aireply.AIService(context)
            val aiReply = aiService.generateReply(
                provider = provider,
                message = transcribedText,
                senderName = waitingMessage.senderName,
                senderPhone = phoneNumber
            )
            
            Log.d(TAG, "✅ AI reply generated: ${aiReply.take(100)}...")
            
            // Update message with AI reply and mark as PENDING_SEND
            // The reply will be sent when user next opens WhatsApp or via notification action
            messageRepository.updateMessageWithReply(waitingMessage.id, aiReply, "PENDING_SEND")
            
            // Broadcast intent to trigger reply sending
            val intent = android.content.Intent("com.message.bulksend.SEND_VOICE_NOTE_REPLY").apply {
                putExtra("phoneNumber", phoneNumber)
                putExtra("senderName", waitingMessage.senderName)
                putExtra("replyText", aiReply)
                putExtra("messageId", waitingMessage.id)
            }
            context.sendBroadcast(intent)
            
            Log.d(TAG, "✅ AI Agent processing complete for voice note - reply queued")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ AI Agent processing failed: ${e.message}", e)
        }
    }
    
    /**
     * Get queue size
     */
    fun getQueueSize(): Int = transcriptionQueue.size
    
    /**
     * Clear queue
     */
    fun clearQueue() {
        transcriptionQueue.clear()
        Log.d(TAG, "🗑️ Queue cleared")
    }
}

/**
 * Transcription request data class
 */
private data class TranscriptionRequest(
    val context: Context,
    val audioFile: File,
    val phoneNumber: String,
    val timestamp: Long
)

/**
 * Transcription result sealed class
 */
sealed class TranscriptionResult {
    abstract val phoneNumber: String
    
    data class Success(
        override val phoneNumber: String,
        val text: String,
        val processingTime: Long,
        val audioFile: File
    ) : TranscriptionResult()
    
    data class Error(
        override val phoneNumber: String,
        val message: String
    ) : TranscriptionResult()
}
