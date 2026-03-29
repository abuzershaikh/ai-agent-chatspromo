package com.message.bulksend.autorespond.statusscheduled

import android.app.Service
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.message.bulksend.autorespond.database.MessageDatabase
import com.message.bulksend.autorespond.statusscheduled.database.StatusBatchRepository
import com.message.bulksend.autorespond.statusscheduled.models.BatchStatus
import com.message.bulksend.autorespond.statusscheduled.models.MediaItem
import com.message.bulksend.bulksend.CampaignState
import com.message.bulksend.bulksend.WhatsAppAutoSendService
import kotlinx.coroutines.*
import java.io.File

class StatusBatchPostingService : Service() {
    
    companion object {
        const val EXTRA_BATCH_ID = "batch_id"
        private const val TAG = "StatusBatchPostingService"
        private const val INTER_MEDIA_GAP_MS = 2_000L
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(
            TAG,
            "onStartCommand action=${intent?.action} extras=${intent?.extras?.keySet()?.joinToString()} flags=$flags startId=$startId"
        )
        val batchId = intent?.getLongExtra(EXTRA_BATCH_ID, -1L) ?: -1L
        
        if (batchId == -1L) {
            Log.e(TAG, "Invalid batch ID")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        
        serviceScope.launch {
            postBatch(batchId)
            stopSelf(startId)
        }
        
        return START_NOT_STICKY
    }
    
    private suspend fun postBatch(batchId: Long) {
        val database = MessageDatabase.getDatabase(applicationContext)
        val repository = StatusBatchRepository(database.statusBatchDao())
        val manager = StatusBatchManager(applicationContext, repository)
        
        val batch = repository.getBatchById(batchId)
        if (batch == null) {
            Log.e(TAG, "Batch not found: $batchId")
            return
        }
        
        Log.d(
            TAG,
            "Starting to post batch=$batchId status=${batch.status} type=${batch.scheduleType} mediaCount=${batch.mediaList.size}"
        )
        
        // Update status to posting
        repository.updateBatchStatus(batchId, BatchStatus.POSTING)
        
        try {
            // Post each media item with delay
            batch.mediaList.forEachIndexed { index, mediaItem ->
                Log.d(TAG, "Posting media ${index + 1}/${batch.mediaList.size}: ${mediaItem.name}")
                
                val success = postMediaToWhatsApp(mediaItem)
                
                if (!success) {
                    Log.e(TAG, "Failed to post media: ${mediaItem.name}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            applicationContext,
                            "Failed to post: ${mediaItem.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                
                // Wait before next media (except for last item)
                if (index < batch.mediaList.size - 1) {
                    Log.d(TAG, "Applying fixed ${INTER_MEDIA_GAP_MS}ms gap before next media in batch=$batchId")
                    delay(INTER_MEDIA_GAP_MS)
                    if (mediaItem.delayMinutes > 0) {
                        val delayMillis = mediaItem.delayMinutes * 60 * 1000L
                        Log.d(TAG, "Waiting extra ${mediaItem.delayMinutes} minutes before next media in batch=$batchId")
                        delay(delayMillis)
                    }
                }
            }
            
            // Update status to posted
            repository.updateBatchStatus(batchId, BatchStatus.POSTED)
            
            // Legacy repeat-daily support only.
            if (batch.repeatDaily) {
                Log.d(TAG, "Attempting reschedule for batch=$batchId after successful posting")
                manager.scheduleBatch(batchId)
                Log.d(TAG, "Batch rescheduled for next day")
            }
            
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    applicationContext,
                    "Batch posted successfully",
                    Toast.LENGTH_SHORT
                ).show()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error posting batch: ${e.message}", e)
            repository.updateBatchStatus(batchId, BatchStatus.FAILED)
            
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    applicationContext,
                    "Batch posting failed: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private suspend fun postMediaToWhatsApp(mediaItem: MediaItem): Boolean {
        return withContext(Dispatchers.Main) {
            try {
                Log.d(TAG, "postMediaToWhatsApp name=${mediaItem.name} path=${mediaItem.uri} delay=${mediaItem.delayMinutes}")
                val file = File(mediaItem.uri)
                if (!file.exists()) {
                    Log.e(TAG, "Media file not found: ${mediaItem.uri}")
                    return@withContext false
                }
                
                // Get FileProvider URI
                val shareUri = FileProvider.getUriForFile(
                    applicationContext,
                    "${applicationContext.packageName}.provider",
                    file
                )
                
                // Detect WhatsApp package
                val whatsappPackage = detectWhatsAppPackage()
                if (whatsappPackage == null) {
                    Log.e(TAG, "No WhatsApp package detected for media=${mediaItem.name}")
                    Toast.makeText(
                        applicationContext,
                        "WhatsApp not installed",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@withContext false
                }
                
                // Prepare mime type
                val mimeType = when (mediaItem.type) {
                    com.message.bulksend.autorespond.statusscheduled.models.MediaType.IMAGE -> {
                        when {
                            mediaItem.name.endsWith(".png", true) -> "image/png"
                            mediaItem.name.endsWith(".jpg", true) || mediaItem.name.endsWith(".jpeg", true) -> "image/jpeg"
                            mediaItem.name.endsWith(".webp", true) -> "image/webp"
                            else -> "image/*"
                        }
                    }
                    com.message.bulksend.autorespond.statusscheduled.models.MediaType.VIDEO -> {
                        when {
                            mediaItem.name.endsWith(".mp4", true) -> "video/mp4"
                            mediaItem.name.endsWith(".3gp", true) -> "video/3gpp"
                            else -> "video/*"
                        }
                    }
                }
                
                // Grant URI permission to WhatsApp
                grantUriPermissionToWhatsApp(shareUri, whatsappPackage)
                
                // Activate accessibility service
                StatusAutoScheduledState.activate(
                    imagePath = file.absolutePath,
                    imageUri = shareUri.toString(),
                    hyperlink = ""
                )
                CampaignState.isSendActionSuccessful = null
                CampaignState.isAutoSendEnabled = true
                WhatsAppAutoSendService.activateService()
                
                // Create share intent
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    setPackage(whatsappPackage)
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    clipData = ClipData.newRawUri("status_media", shareUri)
                }
                
                // Start WhatsApp
                applicationContext.startActivity(shareIntent)
                
                Log.d(TAG, "WhatsApp opened for media=${mediaItem.name} package=$whatsappPackage mime=$mimeType")
                
                // Wait for accessibility to complete
                delay(5000) // 5 seconds for accessibility to click
                
                // Check if successful
                val success = CampaignState.isSendActionSuccessful == true
                Log.d(
                    TAG,
                    "postMediaToWhatsApp result media=${mediaItem.name} success=$success state=${CampaignState.isSendActionSuccessful}"
                )
                
                // Reset state
                StatusAutoScheduledState.reset()
                CampaignState.isAutoSendEnabled = false
                WhatsAppAutoSendService.deactivateService()
                
                return@withContext success
                
            } catch (e: Exception) {
                Log.e(TAG, "Error posting media to WhatsApp: ${e.message}", e)
                StatusAutoScheduledState.reset()
                CampaignState.isAutoSendEnabled = false
                WhatsAppAutoSendService.deactivateService()
                return@withContext false
            }
        }
    }
    
    private fun detectWhatsAppPackage(): String? {
        val packages = listOf(
            "com.whatsapp",
            "com.whatsapp.w4b"
        )
        
        val chosen = packages.firstOrNull { pkg ->
            try {
                applicationContext.packageManager.getPackageInfo(pkg, 0)
                true
            } catch (e: Exception) {
                false
            }
        }
        Log.d(TAG, "detectWhatsAppPackage result=$chosen")
        return chosen
    }
    
    private fun grantUriPermissionToWhatsApp(uri: Uri, packageName: String) {
        try {
            applicationContext.grantUriPermission(
                packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to grant URI permission: ${e.message}")
        }
    }
    
    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
