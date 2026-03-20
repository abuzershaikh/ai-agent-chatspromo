package com.message.bulksend.voicenotereply

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * File Observer for Voice Notes
 * Fetches voice notes ONLY when notification is detected (on-demand)
 * Works even when app is closed (triggered by NotificationListener)
 */
object VoiceNoteFileObserver {
    
    private const val TAG = "VoiceNoteFileObserver"
    private const val MAX_WAIT_TIME = 10000L // Wait max 10 seconds for file to appear
    private const val CHECK_INTERVAL = 500L // Check every 500ms
    private const val MAX_SAVED_VOICE_NOTES = 10
    
    private val scope = CoroutineScope(Dispatchers.IO)
    
    // Track processed files to prevent duplicates
    private val processedFiles = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    
    // Track ongoing fetch operations to prevent duplicate fetches
    private val ongoingFetches = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    
    // Callback for real-time UI updates
    private var onVoiceNoteSavedCallback: ((String, File) -> Unit)? = null
    
    /**
     * Set callback for real-time UI updates
     */
    fun setOnVoiceNoteSavedCallback(callback: (String, File) -> Unit) {
        onVoiceNoteSavedCallback = callback
        Log.d(TAG, "✅ Real-time callback registered")
    }
    
    /**
     * Clear callback
     */
    fun clearCallback() {
        onVoiceNoteSavedCallback = null
        Log.d(TAG, "❌ Real-time callback cleared")
    }
    
    /**
     * Trigger immediate fetch when voice note notification is detected
     * This is called from WhatsAppNotificationListener
     */
    fun triggerImmediateFetch(context: Context, folderUri: Uri) {
        val fetchKey = "${folderUri}_${System.currentTimeMillis()}"
        
        // Check if fetch is already ongoing
        if (ongoingFetches.contains(fetchKey)) {
            Log.w(TAG, "⚠️ Fetch already in progress, skipping duplicate")
            return
        }
        
        Log.d(TAG, "🚀 Immediate fetch triggered!")
        Log.d(TAG, "📁 Folder: $folderUri")
        
        // Mark fetch as ongoing
        ongoingFetches.add(fetchKey)
        
        // Launch coroutine to fetch voice note
        scope.launch {
            try {
                fetchLatestVoiceNote(context, folderUri)
            } finally {
                // Remove from ongoing fetches after completion
                ongoingFetches.remove(fetchKey)
            }
        }
    }
    
    /**
     * Start monitoring (for backward compatibility - now just initializes)
     */
    fun startMonitoring(context: Context, folderUri: Uri) {
        Log.d(TAG, "✅ Voice Note Observer ready (on-demand mode)")
        Log.d(TAG, "📁 Folder: $folderUri")
        cleanupOldSavedVoiceNotes(context)
    }
    
    /**
     * Stop monitoring (cleanup)
     */
    fun stopMonitoring() {
        Log.d(TAG, "❌ Stopped voice note monitoring")
    }
    
    /**
     * Fetch latest voice note from folder
     * Waits up to MAX_WAIT_TIME for file to appear
     */
    private suspend fun fetchLatestVoiceNote(context: Context, folderUri: Uri) {
        try {
            val startTime = System.currentTimeMillis()
            var voiceNoteFound: DocumentFile? = null
            var attempts = 0
            
            Log.d(TAG, "🔍 Searching for new voice note...")
            
            // Keep checking until file appears or timeout
            while (voiceNoteFound == null && (System.currentTimeMillis() - startTime) < MAX_WAIT_TIME) {
                attempts++
                Log.d(TAG, "🔄 Attempt $attempts...")
                
                val folder = DocumentFile.fromTreeUri(context, folderUri)
                if (folder == null) {
                    Log.e(TAG, "❌ Cannot access folder")
                    return
                }
                
                // Get all subfolders and sort by name (date folders like 202608)
                val subfolders = folder.listFiles()
                    .filter { it.isDirectory }
                    .sortedByDescending { it.name } // Latest date first
                
                Log.d(TAG, "📁 Found ${subfolders.size} subfolders")
                
                // Check latest 3 date folders first (most recent voice notes)
                for (subfolder in subfolders.take(3)) {
                    Log.d(TAG, "� Checking folder: ${subfolder.name}")
                    
                    val files = subfolder.listFiles()
                        .filter { it.isFile && it.name?.endsWith(".opus", ignoreCase = true) == true }
                        .sortedByDescending { it.lastModified() } // Latest file first
                    
                    Log.d(TAG, "📂 Folder ${subfolder.name} has ${files.size} .opus files")
                    
                    if (files.isNotEmpty()) {
                        val latestFile = files.first()
                        val fileAge = System.currentTimeMillis() - latestFile.lastModified()
                        
                        Log.d(TAG, "📄 Latest file: ${latestFile.name}")
                        Log.d(TAG, "⏰ File age: ${fileAge}ms")
                        
                        // If file is very recent (within 15 seconds), it's likely our voice note
                        if (fileAge < 15000) {
                            voiceNoteFound = latestFile
                            Log.d(TAG, "✅ Found recent voice note: ${latestFile.name}")
                            break
                        }
                    }
                }
                
                // If not found, wait and try again
                if (voiceNoteFound == null) {
                    delay(CHECK_INTERVAL)
                }
            }
            
            if (voiceNoteFound != null) {
                Log.d(TAG, "🎤 Voice note found after $attempts attempts")
                processVoiceNote(context, voiceNoteFound)
            } else {
                Log.w(TAG, "⚠️ No recent voice note found after ${MAX_WAIT_TIME}ms")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fetching voice note: ${e.message}", e)
        }
    }
    
    /**
     * Process a voice note
     */
    private fun processVoiceNote(context: Context, voiceNote: DocumentFile) {
        try {
            val fileName = voiceNote.name ?: return
            val fileTime = voiceNote.lastModified()
            
            // Create unique file key to prevent duplicates
            val fileKey = "${fileName}_${fileTime}"
            
            // Check if already processed
            if (processedFiles.contains(fileKey)) {
                Log.w(TAG, "⚠️ File already processed, skipping: $fileName")
                return
            }
            
            Log.d(TAG, "📎 Processing voice note: $fileName")
            Log.d(TAG, "⏰ File time: $fileTime")
            
            // Mark as processed immediately
            processedFiles.add(fileKey)
            
            // Clean old entries (older than 5 minutes)
            cleanOldProcessedFiles()
            
            // Find matching notification within 30 seconds of file creation
            val matchingNotification = findMatchingNotification(fileTime)
            
            if (matchingNotification != null) {
                val (phoneNumber, senderName) = matchingNotification
                Log.d(TAG, "✅ Matched with phone: $phoneNumber ($senderName)")
                
                // Copy voice note to app storage
                val savedFile = copyVoiceNoteToApp(context, voiceNote, phoneNumber)
                
                if (savedFile != null) {
                    Log.d(TAG, "✅ Voice note saved: ${savedFile.absolutePath}")
                    
                    // Save info
                    VoiceNoteReplyManager.saveLastVoiceNote(context, phoneNumber, fileName)
                    
                    // Clear tracker for this phone
                    VoiceNoteNotificationTracker.clearRecentVoiceNote(phoneNumber)
                    
                    // Trigger AI reply or other action
                    onVoiceNoteSaved(context, phoneNumber, savedFile)
                } else {
                    Log.e(TAG, "❌ Failed to save voice note")
                }
            } else {
                Log.d(TAG, "⚠️ No matching notification found for file time: $fileTime")
                // Try to match with most recent notification as fallback
                val fallbackData = VoiceNoteNotificationTracker.getRecentVoiceNotePhone()
                if (fallbackData != null) {
                    val (phoneNumber, senderName) = fallbackData
                    Log.d(TAG, "🔄 Using fallback match: $phoneNumber ($senderName)")
                    
                    val savedFile = copyVoiceNoteToApp(context, voiceNote, phoneNumber)
                    if (savedFile != null) {
                        VoiceNoteReplyManager.saveLastVoiceNote(context, phoneNumber, fileName)
                        VoiceNoteNotificationTracker.clearRecentVoiceNote(phoneNumber)
                        onVoiceNoteSaved(context, phoneNumber, savedFile)
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error processing voice note: ${e.message}", e)
        }
    }
    
    /**
     * Clean old processed files (older than 5 minutes)
     */
    private fun cleanOldProcessedFiles() {
        if (processedFiles.size > 100) {
            processedFiles.clear()
            Log.d(TAG, "🗑️ Cleared processed files cache")
        }
    }
    
    /**
     * Find notification that matches file timestamp (within 30 seconds)
     */
    private fun findMatchingNotification(fileTime: Long): Pair<String, String>? {
        val allPending = VoiceNoteNotificationTracker.getAllPending()
        
        // Find notification closest to file time (within 30 seconds)
        var bestMatch: Pair<String, String>? = null
        var smallestDiff = Long.MAX_VALUE
        
        allPending.forEach { (phone, data) ->
            val (name, notifTime) = data
            val timeDiff = kotlin.math.abs(fileTime - notifTime)
            
            // Within 30 seconds and closest match
            if (timeDiff < 30000 && timeDiff < smallestDiff) {
                smallestDiff = timeDiff
                bestMatch = Pair(phone, name)
                Log.d(TAG, "🔍 Potential match: $phone, time diff: ${timeDiff}ms")
            }
        }
        
        return bestMatch
    }
    
    /**
     * Copy voice note to app storage
     */
    private fun copyVoiceNoteToApp(
        context: Context,
        voiceNote: DocumentFile,
        phoneNumber: String
    ): File? {
        try {
            // Create voice notes directory
            val voiceNotesDir = File(context.filesDir, "voice_notes")
            if (!voiceNotesDir.exists()) {
                voiceNotesDir.mkdirs()
            }
            
            // Create phone-specific directory
            val phoneDir = File(voiceNotesDir, phoneNumber.replace("+", ""))
            if (!phoneDir.exists()) {
                phoneDir.mkdirs()
            }
            
            // Generate filename with timestamp
            val timestamp = System.currentTimeMillis()
            val fileName = "voice_${timestamp}.opus"
            val destFile = File(phoneDir, fileName)
            
            // Copy file
            context.contentResolver.openInputStream(voiceNote.uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.d(TAG, "📁 Saved to: ${destFile.absolutePath}")
            Log.d(TAG, "📊 Size: ${destFile.length()} bytes")

            // Keep storage lightweight: retain only latest 10 voice notes globally.
            cleanupOldSavedVoiceNotes(context)
            
            return destFile
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error copying voice note: ${e.message}", e)
            return null
        }
    }

    /**
     * Auto-clean old saved voice notes.
     * Keeps only latest [MAX_SAVED_VOICE_NOTES] .opus files across all phone folders.
     */
    private fun cleanupOldSavedVoiceNotes(context: Context) {
        try {
            val voiceNotesDir = File(context.filesDir, "voice_notes")
            if (!voiceNotesDir.exists()) return

            val allVoiceFiles = voiceNotesDir.listFiles()
                ?.filter { it.isDirectory }
                ?.flatMap { phoneDir ->
                    phoneDir.listFiles()
                        ?.filter { it.isFile && it.extension.equals("opus", ignoreCase = true) }
                        ?: emptyList()
                }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()

            if (allVoiceFiles.size <= MAX_SAVED_VOICE_NOTES) return

            val filesToDelete = allVoiceFiles.drop(MAX_SAVED_VOICE_NOTES)
            var deletedCount = 0

            filesToDelete.forEach { opusFile ->
                val transcriptFile = File(opusFile.parentFile, "${opusFile.nameWithoutExtension}.txt")
                if (opusFile.delete()) {
                    deletedCount++
                    Log.d(TAG, "🗑️ Deleted old voice note: ${opusFile.absolutePath}")
                }
                if (transcriptFile.exists() && transcriptFile.delete()) {
                    Log.d(TAG, "🗑️ Deleted transcription: ${transcriptFile.absolutePath}")
                }
            }

            // Remove empty phone folders after cleanup.
            voiceNotesDir.listFiles()
                ?.filter { it.isDirectory && it.listFiles().isNullOrEmpty() }
                ?.forEach { emptyDir ->
                    if (emptyDir.delete()) {
                        Log.d(TAG, "🧹 Deleted empty phone folder: ${emptyDir.absolutePath}")
                    }
                }

            Log.d(
                TAG,
                "✅ Voice note cleanup complete: kept=$MAX_SAVED_VOICE_NOTES, removed=$deletedCount, totalBefore=${allVoiceFiles.size}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during voice note cleanup: ${e.message}", e)
        }
    }
    
    /**
     * Called when voice note is saved
     */
    private fun onVoiceNoteSaved(context: Context, phoneNumber: String, file: File) {
        Log.d(TAG, "🎉 Voice note saved for: $phoneNumber")
        Log.d(TAG, "📁 File: ${file.absolutePath}")
        
        // Queue transcription request (no language = auto-detect for Hinglish)
        scope.launch {
            VoiceTranscriptionService.queueTranscription(
                context = context,
                audioFile = file,
                phoneNumber = phoneNumber
                // language omitted = auto-detect (best for Hinglish)
            )
        }
        
        // Trigger real-time UI update callback
        onVoiceNoteSavedCallback?.invoke(phoneNumber, file)
    }
    
    /**
     * Save transcription text to file
     */
    fun saveTranscriptionText(audioFile: File, transcriptionText: String) {
        try {
            val transcriptionFile = File(audioFile.absolutePath.replace(".opus", ".txt"))
            transcriptionFile.writeText(transcriptionText)
            Log.d(TAG, "✅ Transcription saved: ${transcriptionFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving transcription: ${e.message}")
        }
    }
}

/**
 * Tracker for recent voice note notifications
 * Matches notifications with file system events
 */
object VoiceNoteNotificationTracker {
    
    private const val TAG = "VoiceNoteTracker"
    private const val TIMEOUT_MS = 30000L // 30 seconds
    
    // Track multiple pending notifications
    // Key: phone number, Value: Pair(sender name, timestamp)
    private val pendingNotifications = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()
    
    /**
     * Track a voice note notification
     * @param phoneNumber - Sender's phone number
     * @param senderName - Sender's display name
     */
    fun trackVoiceNoteNotification(phoneNumber: String, senderName: String) {
        val timestamp = System.currentTimeMillis()
        pendingNotifications[phoneNumber] = Pair(senderName, timestamp)
        Log.d(TAG, "🎤 Tracked voice note notification - Phone: $phoneNumber, Name: $senderName, Time: $timestamp")
    }
    
    /**
     * Get recent voice note phone number (if within timeout)
     * @return Pair of (phoneNumber, senderName) if found, null otherwise
     */
    fun getRecentVoiceNotePhone(): Pair<String, String>? {
        val now = System.currentTimeMillis()
        
        // Find the most recent notification within timeout
        var mostRecent: Pair<String, Pair<String, Long>>? = null
        
        pendingNotifications.forEach { (phone, data) ->
            val (name, time) = data
            if ((now - time) < TIMEOUT_MS) {
                if (mostRecent == null || time > mostRecent!!.second.second) {
                    mostRecent = Pair(phone, data)
                }
            }
        }
        
        return mostRecent?.let { (phone, data) ->
            Pair(phone, data.first)
        }
    }
    
    /**
     * Clear recent voice note for specific phone
     */
    fun clearRecentVoiceNote(phoneNumber: String) {
        pendingNotifications.remove(phoneNumber)
        Log.d(TAG, "🗑️ Cleared voice note for: $phoneNumber")
    }
    
    /**
     * Get all pending notifications (for timestamp matching)
     */
    fun getAllPending(): Map<String, Pair<String, Long>> {
        val now = System.currentTimeMillis()
        
        // Remove expired notifications
        val iterator = pendingNotifications.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val (_, timestamp) = entry.value
            if ((now - timestamp) >= TIMEOUT_MS) {
                iterator.remove()
                Log.d(TAG, "⏱️ Removed expired notification: ${entry.key}")
            }
        }
        
        return pendingNotifications.toMap()
    }
    
    /**
     * Clear all pending notifications
     */
    fun clearAll() {
        pendingNotifications.clear()
        Log.d(TAG, "🗑️ Cleared all pending notifications")
    }
}
