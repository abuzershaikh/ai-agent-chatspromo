package com.message.bulksend.sync

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.message.bulksend.db.AppDatabase
import com.message.bulksend.db.Campaign
import com.message.bulksend.db.ContactGroup
import com.message.bulksend.db.ContactEntity
import kotlinx.coroutines.tasks.await

/**
 * SyncManager handles two-way sync between Room DB and Firestore
 * Based on BACKUP_LOGIC.md specifications
 */
class SyncManager(private val context: Context) {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val db = AppDatabase.getInstance(context)
    private val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val BATCH_SIZE = 450 // Keep margin under 500 limit
        private const val PREF_LAST_BACKUP = "last_backup_at"
        private const val PREF_LAST_RESTORE = "last_restore_at"
    }
    
    /**
     * Sanitize email for use as Firestore document ID
     */
    fun sanitizeEmail(email: String): String {
        return email.trim().lowercase()
            .replace(".", "_")
            .replace("@", "_at_")
    }
    
    /**
     * Get current user ID (sanitized email)
     */
    private fun getUserId(): String? {
        val email = auth.currentUser?.email ?: return null
        return sanitizeEmail(email)
    }
    
    /**
     * Incremental backup - only upload changed items
     */
    suspend fun backupPending(onProgress: (String) -> Unit = {}): Result<Unit> {
        return try {
            val userId = getUserId() ?: return Result.failure(Exception("User not logged in"))
            
            onProgress("Collecting pending changes...")
            
            // Get pending contact groups and campaigns (modified since last backup)
            val lastBackupAt = prefs.getLong(PREF_LAST_BACKUP, 0)
            val allGroups = db.contactGroupDao().getAllGroupsList()
            val pendingGroups = allGroups.filter { group -> group.timestamp > lastBackupAt }
            
            val allCampaigns = db.campaignDao().getAllCampaigns()
            val pendingCampaigns = allCampaigns.filter { campaign -> campaign.timestamp > lastBackupAt }
            
            if (pendingGroups.isEmpty() && pendingCampaigns.isEmpty()) {
                onProgress("No changes to backup")
                return Result.success(Unit)
            }
            
            // Upload contact groups
            if (pendingGroups.isNotEmpty()) {
                onProgress("Uploading ${pendingGroups.size} groups...")
                val batches = pendingGroups.chunked(BATCH_SIZE)
                for ((index, batch) in batches.withIndex()) {
                    onProgress("Groups batch ${index + 1}/${batches.size}...")
                    uploadGroupBatch(userId, batch)
                }
            }
            
            // Upload campaigns
            if (pendingCampaigns.isNotEmpty()) {
                onProgress("Uploading ${pendingCampaigns.size} campaigns...")
                val batches = pendingCampaigns.chunked(BATCH_SIZE)
                for ((index, batch) in batches.withIndex()) {
                    onProgress("Campaigns batch ${index + 1}/${batches.size}...")
                    uploadCampaignBatch(userId, batch)
                }
            }
            
            // Update meta
            val now = System.currentTimeMillis()
            firestore.collection("users")
                .document(userId)
                .collection("meta")
                .document("sync")
                .set(mapOf(
                    "lastBackupAt" to now,
                    "appVersion" to "6.6"
                ), SetOptions.merge())
                .await()
            
            prefs.edit().putLong(PREF_LAST_BACKUP, now).apply()
            
            onProgress("Backup complete!")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Upload a batch of contact groups
     */
    private suspend fun uploadGroupBatch(userId: String, groups: List<ContactGroup>) {
        val batch = firestore.batch()
        
        for (group in groups) {
            val docRef = firestore.collection("users")
                .document(userId)
                .collection("contact_groups")
                .document(group.id.toString())
            
            // Convert ContactEntity list to serializable format
            val contactsData = group.contacts.map { contact ->
                mapOf(
                    "name" to contact.name,
                    "number" to contact.number,
                    "isWhatsApp" to contact.isWhatsApp
                )
            }
            
            // Add unique fingerprint to detect true conflicts
            val fingerprint = "${group.name}_${group.timestamp}_${group.contacts.size}"
            
            val data = mapOf(
                "id" to group.id,
                "name" to group.name,
                "contacts" to contactsData,
                "timestamp" to group.timestamp,
                "lastModifiedAt" to System.currentTimeMillis(),
                "fingerprint" to fingerprint // Unique identifier for conflict detection
            )
            
            batch.set(docRef, data, SetOptions.merge())
        }
        
        batch.commit().await()
    }
    
    /**
     * Upload a batch of campaigns
     */
    private suspend fun uploadCampaignBatch(userId: String, campaigns: List<Campaign>) {
        val batch = firestore.batch()
        
        for (campaign in campaigns) {
            val docRef = firestore.collection("users")
                .document(userId)
                .collection("campaigns")
                .document(campaign.id)
            
            // Convert ContactStatus list to serializable format
            val contactStatusesData = campaign.contactStatuses.map { status ->
                mutableMapOf<String, Any>(
                    "number" to status.number,
                    "status" to status.status
                ).apply {
                    status.failureReason?.let { put("failureReason", it) }
                }
            }
            
            val data = mapOf(
                "id" to campaign.id,
                "groupId" to campaign.groupId,
                "campaignName" to campaign.campaignName,
                "message" to campaign.message,
                "timestamp" to campaign.timestamp,
                "totalContacts" to campaign.totalContacts,
                "contactStatuses" to contactStatusesData,
                "isStopped" to campaign.isStopped,
                "isRunning" to campaign.isRunning,
                "campaignType" to campaign.campaignType,
                "sheetFileName" to campaign.sheetFileName,
                "countryCode" to campaign.countryCode,
                "sheetDataJson" to campaign.sheetDataJson,
                "lastModifiedAt" to System.currentTimeMillis()
            )
            
            batch.set(docRef, data, SetOptions.merge())
        }
        
        batch.commit().await()
    }
    
    /**
     * Restore from cloud - merge with local data
     */
    suspend fun restoreIfExists(onProgress: (String) -> Unit = {}): Result<Unit> {
        return try {
            val userId = getUserId() ?: return Result.failure(Exception("User not logged in"))
            
            onProgress("Checking cloud backup...")
            
            // Check if meta exists
            val metaDoc = firestore.collection("users")
                .document(userId)
                .collection("meta")
                .document("sync")
                .get()
                .await()
            
            if (!metaDoc.exists()) {
                onProgress("No cloud backup found")
                return Result.success(Unit)
            }
            
            onProgress("Downloading contact groups...")
            
            // Fetch all contact groups from cloud
            val cloudGroups = firestore.collection("users")
                .document(userId)
                .collection("contact_groups")
                .get()
                .await()
            
            onProgress("Merging ${cloudGroups.size()} groups...")
            
            var inserted = 0
            var updated = 0
            var skipped = 0
            
            for (doc in cloudGroups.documents) {
                val cloudId = doc.getLong("id") ?: continue
                val cloudName = doc.getString("name") ?: continue
                val cloudContactsData = doc.get("contacts") as? List<Map<String, Any>> ?: emptyList()
                val cloudTimestamp = doc.getLong("timestamp") ?: 0L
                val cloudModified = doc.getLong("lastModifiedAt") ?: cloudTimestamp
                val cloudFingerprint = doc.getString("fingerprint") ?: ""
                
                // Convert cloud contacts to ContactEntity list
                val cloudContacts = cloudContactsData.map { contactMap ->
                    ContactEntity(
                        name = contactMap["name"] as? String ?: "",
                        number = contactMap["number"] as? String ?: "",
                        isWhatsApp = contactMap["isWhatsApp"] as? Boolean ?: false
                    )
                }
                
                // Check if exists locally by ID
                val localGroup = db.contactGroupDao().getGroupById(cloudId)
                
                // Create local fingerprint for comparison
                val localFingerprint = if (localGroup != null) {
                    "${localGroup.name}_${localGroup.timestamp}_${localGroup.contacts.size}"
                } else ""
                
                // Check if this is a real conflict (same ID but different fingerprint)
                val isRealConflict = localGroup != null && 
                    cloudFingerprint.isNotEmpty() &&
                    localFingerprint != cloudFingerprint
                
                when {
                    localGroup == null -> {
                        // No local group with this ID, safe to insert
                        db.contactGroupDao().insertGroup(ContactGroup(
                            id = cloudId,
                            name = cloudName,
                            contacts = cloudContacts,
                            timestamp = cloudTimestamp
                        ))
                        inserted++
                    }
                    isRealConflict -> {
                        // ID conflict: Different groups with same ID
                        // Cloud is from backup, local is new after reinstall
                        // Solution: Keep cloud data (it's the original backup)
                        if (cloudModified > localGroup.timestamp) {
                            // Cloud is older backup but we trust it more
                            db.contactGroupDao().insertGroup(ContactGroup(
                                id = cloudId,
                                name = cloudName,
                                contacts = cloudContacts,
                                timestamp = cloudTimestamp
                            ))
                            updated++
                        } else {
                            // Local is newer, but might be post-reinstall
                            // Still prefer cloud backup for safety
                            db.contactGroupDao().insertGroup(ContactGroup(
                                id = cloudId,
                                name = cloudName,
                                contacts = cloudContacts,
                                timestamp = cloudTimestamp
                            ))
                            updated++
                        }
                    }
                    cloudModified > localGroup.timestamp -> {
                        // Same group, cloud is newer
                        db.contactGroupDao().insertGroup(ContactGroup(
                            id = cloudId,
                            name = cloudName,
                            contacts = cloudContacts,
                            timestamp = cloudTimestamp
                        ))
                        updated++
                    }
                    else -> {
                        // Same group, local is newer or same, skip
                        skipped++
                    }
                }
            }
            
            // Fetch and merge campaigns
            onProgress("Downloading campaigns...")
            
            val cloudCampaigns = firestore.collection("users")
                .document(userId)
                .collection("campaigns")
                .get()
                .await()
            
            onProgress("Merging ${cloudCampaigns.size()} campaigns...")
            
            var campaignsInserted = 0
            var campaignsUpdated = 0
            var campaignsSkipped = 0
            
            for (doc in cloudCampaigns.documents) {
                val cloudId = doc.getString("id") ?: continue
                val cloudGroupId = doc.getString("groupId") ?: continue
                val cloudName = doc.getString("campaignName") ?: continue
                val cloudMessage = doc.getString("message") ?: ""
                val cloudTimestamp = doc.getLong("timestamp") ?: 0L
                val cloudModified = doc.getLong("lastModifiedAt") ?: cloudTimestamp
                val cloudTotalContacts = doc.getLong("totalContacts")?.toInt() ?: 0
                val cloudIsStopped = doc.getBoolean("isStopped") ?: false
                val cloudIsRunning = doc.getBoolean("isRunning") ?: false
                val cloudCampaignType = doc.getString("campaignType") ?: "BULKSEND"
                val cloudSheetFileName = doc.getString("sheetFileName")
                val cloudCountryCode = doc.getString("countryCode")
                val cloudSheetDataJson = doc.getString("sheetDataJson")
                
                val cloudStatusesData = doc.get("contactStatuses") as? List<Map<String, Any>> ?: emptyList()
                val cloudStatuses = cloudStatusesData.map { statusMap ->
                    com.message.bulksend.data.ContactStatus(
                        number = statusMap["number"] as? String ?: "",
                        status = statusMap["status"] as? String ?: "pending",
                        failureReason = statusMap["failureReason"] as? String
                    )
                }
                
                // Check if exists locally
                val localCampaign = db.campaignDao().getCampaignById(cloudId)
                
                when {
                    localCampaign == null -> {
                        // Insert new
                        db.campaignDao().upsertCampaign(Campaign(
                            id = cloudId,
                            groupId = cloudGroupId,
                            campaignName = cloudName,
                            message = cloudMessage,
                            timestamp = cloudTimestamp,
                            totalContacts = cloudTotalContacts,
                            contactStatuses = cloudStatuses,
                            isStopped = cloudIsStopped,
                            isRunning = cloudIsRunning,
                            campaignType = cloudCampaignType,
                            sheetFileName = cloudSheetFileName,
                            countryCode = cloudCountryCode,
                            sheetDataJson = cloudSheetDataJson
                        ))
                        campaignsInserted++
                    }
                    cloudModified > localCampaign.timestamp -> {
                        // Cloud is newer, replace local
                        db.campaignDao().upsertCampaign(Campaign(
                            id = cloudId,
                            groupId = cloudGroupId,
                            campaignName = cloudName,
                            message = cloudMessage,
                            timestamp = cloudTimestamp,
                            totalContacts = cloudTotalContacts,
                            contactStatuses = cloudStatuses,
                            isStopped = cloudIsStopped,
                            isRunning = cloudIsRunning,
                            campaignType = cloudCampaignType,
                            sheetFileName = cloudSheetFileName,
                            countryCode = cloudCountryCode,
                            sheetDataJson = cloudSheetDataJson
                        ))
                        campaignsUpdated++
                    }
                    else -> {
                        // Local is newer or same, skip
                        campaignsSkipped++
                    }
                }
            }
            
            // Update restore timestamp
            val now = System.currentTimeMillis()
            prefs.edit().putLong(PREF_LAST_RESTORE, now).apply()
            
            val message = buildString {
                append("Restore complete!\n")
                append("Groups: +$inserted ↻$updated ⊘$skipped\n")
                append("Campaigns: +$campaignsInserted ↻$campaignsUpdated ⊘$campaignsSkipped")
                if (updated > 0 || campaignsUpdated > 0) {
                    append("\n\nNote: Cloud backup data was restored (overwrites local)")
                }
            }
            onProgress(message)
            Result.success(Unit)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Full sync - backup then restore
     */
    suspend fun fullSync(onProgress: (String) -> Unit = {}): Result<Unit> {
        return try {
            onProgress("Starting full sync...")
            
            // First backup local changes
            val backupResult = backupPending { msg -> onProgress("Backup: $msg") }
            if (backupResult.isFailure) {
                return backupResult
            }
            
            // Then restore cloud changes
            val restoreResult = restoreIfExists { msg -> onProgress("Restore: $msg") }
            if (restoreResult.isFailure) {
                return restoreResult
            }
            
            onProgress("Full sync complete!")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get sync status
     */
    fun getSyncStatus(): SyncStatus {
        val lastBackup = prefs.getLong(PREF_LAST_BACKUP, 0)
        val lastRestore = prefs.getLong(PREF_LAST_RESTORE, 0)
        val isLoggedIn = auth.currentUser != null
        
        return SyncStatus(
            isLoggedIn = isLoggedIn,
            lastBackupAt = if (lastBackup > 0) lastBackup else null,
            lastRestoreAt = if (lastRestore > 0) lastRestore else null,
            userEmail = auth.currentUser?.email
        )
    }
}

data class SyncStatus(
    val isLoggedIn: Boolean,
    val lastBackupAt: Long?,
    val lastRestoreAt: Long?,
    val userEmail: String?
)
