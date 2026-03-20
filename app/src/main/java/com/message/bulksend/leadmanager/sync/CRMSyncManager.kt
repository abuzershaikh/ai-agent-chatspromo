package com.message.bulksend.leadmanager.sync

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.message.bulksend.leadmanager.database.LeadManagerDatabase
import com.message.bulksend.leadmanager.database.entities.*
import com.message.bulksend.leadmanager.notes.NoteEntity
import com.message.bulksend.leadmanager.notes.NoteType
import com.message.bulksend.leadmanager.notes.NotePriority
import com.message.bulksend.leadmanager.payments.database.PaymentEntity
import com.message.bulksend.leadmanager.payments.database.InvoiceEntity
import com.message.bulksend.leadmanager.payments.database.PaymentType
import com.message.bulksend.leadmanager.payments.database.InvoiceStatus
import com.message.bulksend.leadmanager.model.LeadStatus
import com.message.bulksend.leadmanager.model.LeadPriority
import com.message.bulksend.leadmanager.model.FollowUpType
import com.message.bulksend.leadmanager.model.ProductType
import com.message.bulksend.leadmanager.model.ServiceType
import kotlinx.coroutines.tasks.await

/**
 * CRM Sync Manager for Chatspromo CRM
 * Handles backup and restore of all CRM data to/from Firestore
 */
class CRMSyncManager(private val context: Context) {
    
    companion object {
        private const val TAG = "CRMSyncManager"
        private const val BATCH_SIZE = 450
        private const val PREF_NAME = "crm_sync_prefs"
        private const val PREF_LAST_BACKUP = "last_crm_backup"
        private const val PREF_LAST_RESTORE = "last_crm_restore"
        private const val PREF_AUTO_SYNC = "auto_sync_enabled"
        
        @Volatile
        private var INSTANCE: CRMSyncManager? = null
        
        fun getInstance(context: Context): CRMSyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CRMSyncManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val db = LeadManagerDatabase.getDatabase(context)
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    
    // ==================== AUTO SYNC FUNCTIONS ====================
    
    /**
     * Check if auto sync is enabled
     */
    fun isAutoSyncEnabled(): Boolean {
        return prefs.getBoolean(PREF_AUTO_SYNC, false)
    }
    
    /**
     * Enable or disable auto sync
     */
    fun setAutoSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_AUTO_SYNC, enabled).apply()
    }
    
    /**
     * Upload a single lead to Firestore (for real-time sync)
     */
    suspend fun uploadLead(lead: LeadEntity): Result<Unit> {
        if (!isAutoSyncEnabled()) return Result.success(Unit)
        val crmCollection = getCRMCollection() ?: return Result.failure(Exception("Not logged in"))
        
        return try {
            val docRef = crmCollection.document("leads").collection("items").document(lead.id)
            val data = mapOf(
                "id" to lead.id,
                "name" to lead.name,
                "phoneNumber" to lead.phoneNumber,
                "email" to lead.email,
                "countryCode" to lead.countryCode,
                "countryIso" to lead.countryIso,
                "alternatePhone" to lead.alternatePhone,
                "status" to lead.status.name,
                "source" to lead.source,
                "lastMessage" to lead.lastMessage,
                "timestamp" to lead.timestamp,
                "category" to lead.category,
                "notes" to lead.notes,
                "priority" to lead.priority.name,
                "tags" to lead.tags,
                "product" to lead.product,
                "leadScore" to lead.leadScore,
                "nextFollowUpDate" to lead.nextFollowUpDate,
                "isFollowUpCompleted" to lead.isFollowUpCompleted,
                "lastModifiedAt" to System.currentTimeMillis()
            )
            docRef.set(data, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload lead", e)
            Result.failure(e)
        }
    }
    
    /**
     * Upload a single product to Firestore
     */
    suspend fun uploadProduct(product: ProductEntity): Result<Unit> {
        if (!isAutoSyncEnabled()) return Result.success(Unit)
        val crmCollection = getCRMCollection() ?: return Result.failure(Exception("Not logged in"))
        
        return try {
            val docRef = crmCollection.document("products").collection("items").document(product.id)
            val data = mapOf(
                "id" to product.id,
                "name" to product.name,
                "type" to product.type.name,
                "category" to product.category,
                "subcategory" to product.subcategory,
                "mrp" to product.mrp,
                "sellingPrice" to product.sellingPrice,
                "description" to product.description,
                "color" to product.color,
                "size" to product.size,
                "height" to product.height,
                "width" to product.width,
                "weight" to product.weight,
                "downloadLink" to product.downloadLink,
                "licenseType" to product.licenseType,
                "version" to product.version,
                "serviceType" to product.serviceType?.name,
                "duration" to product.duration,
                "deliveryTime" to product.deliveryTime,
                "lastModifiedAt" to System.currentTimeMillis()
            )
            docRef.set(data, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload product", e)
            Result.failure(e)
        }
    }
    
    /**
     * Upload a single follow-up to Firestore
     */
    suspend fun uploadFollowUp(followUp: FollowUpEntity): Result<Unit> {
        if (!isAutoSyncEnabled()) return Result.success(Unit)
        val crmCollection = getCRMCollection() ?: return Result.failure(Exception("Not logged in"))
        
        return try {
            val docRef = crmCollection.document("follow_ups").collection("items").document(followUp.id)
            val data = mapOf(
                "id" to followUp.id,
                "leadId" to followUp.leadId,
                "title" to followUp.title,
                "description" to followUp.description,
                "scheduledDate" to followUp.scheduledDate,
                "scheduledTime" to followUp.scheduledTime,
                "type" to followUp.type.name,
                "isCompleted" to followUp.isCompleted,
                "completedDate" to followUp.completedDate,
                "notes" to followUp.notes,
                "reminderMinutes" to followUp.reminderMinutes,
                "lastModifiedAt" to System.currentTimeMillis()
            )
            docRef.set(data, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload follow-up", e)
            Result.failure(e)
        }
    }
    
    /**
     * Upload a single note to Firestore
     */
    suspend fun uploadNote(note: NoteEntity): Result<Unit> {
        if (!isAutoSyncEnabled()) return Result.success(Unit)
        val crmCollection = getCRMCollection() ?: return Result.failure(Exception("Not logged in"))
        
        return try {
            val docRef = crmCollection.document("notes").collection("items").document(note.id)
            val data = mapOf(
                "id" to note.id,
                "leadId" to note.leadId,
                "title" to note.title,
                "content" to note.content,
                "noteType" to note.noteType.name,
                "priority" to note.priority.name,
                "isPinned" to note.isPinned,
                "createdAt" to note.createdAt,
                "updatedAt" to note.updatedAt,
                "createdBy" to note.createdBy,
                "attachments" to note.attachments,
                "tags" to note.tags,
                "isArchived" to note.isArchived,
                "parentNoteId" to note.parentNoteId,
                "lastModifiedAt" to System.currentTimeMillis()
            )
            docRef.set(data, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload note", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete a lead from Firestore
     */
    suspend fun deleteLead(leadId: String): Result<Unit> {
        if (!isAutoSyncEnabled()) return Result.success(Unit)
        val crmCollection = getCRMCollection() ?: return Result.failure(Exception("Not logged in"))
        
        return try {
            crmCollection.document("leads").collection("items").document(leadId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete lead from cloud", e)
            Result.failure(e)
        }
    }
    
    /**
     * Sanitize email for Firestore document ID
     */
    private fun sanitizeEmail(email: String): String {
        return email.trim().lowercase()
            .replace(".", "_")
            .replace("@", "_at_")
    }
    
    /**
     * Get current user ID
     */
    private fun getUserId(): String? {
        val email = auth.currentUser?.email ?: return null
        return sanitizeEmail(email)
    }
    
    /**
     * Get base collection reference for CRM data
     */
    private fun getCRMCollection() = getUserId()?.let { userId ->
        firestore.collection("users").document(userId).collection("chatspromo_crm")
    }
    
    // ==================== BACKUP FUNCTIONS ====================
    
    /**
     * Backup all CRM data to Firestore
     */
    suspend fun backupAll(onProgress: (String, Int) -> Unit): Result<BackupStats> {
        return try {
            val crmCollection = getCRMCollection() ?: return Result.failure(Exception("User not logged in"))
            val userId = getUserId()!!
            
            var totalItems = 0
            var backedUp = 0
            
            // 1. Backup Leads
            onProgress("Backing up leads...", 5)
            val leads = db.leadDao().getAllLeadsList()
            totalItems += leads.size
            backupLeads(crmCollection, leads)
            backedUp += leads.size
            onProgress("Leads backed up: ${leads.size}", 15)
            
            // 2. Backup Products
            onProgress("Backing up products...", 20)
            val products = db.productDao().getAllProductsList()
            totalItems += products.size
            backupProducts(crmCollection, products)
            backedUp += products.size
            onProgress("Products backed up: ${products.size}", 25)
            
            // 3. Backup Follow-ups
            onProgress("Backing up follow-ups...", 30)
            val followUps = db.followUpDao().getAllFollowUpsList()
            totalItems += followUps.size
            backupFollowUps(crmCollection, followUps)
            backedUp += followUps.size
            onProgress("Follow-ups backed up: ${followUps.size}", 35)
            
            // 4. Backup Custom Field Definitions
            onProgress("Backing up custom fields...", 40)
            val customFieldDefs = db.customFieldDao().getAllDefinitionsList()
            totalItems += customFieldDefs.size
            backupCustomFieldDefinitions(crmCollection, customFieldDefs)
            backedUp += customFieldDefs.size
            onProgress("Custom field definitions backed up: ${customFieldDefs.size}", 45)
            
            // 5. Backup Custom Field Values
            val customFieldValues = db.customFieldDao().getAllValuesList()
            totalItems += customFieldValues.size
            backupCustomFieldValues(crmCollection, customFieldValues)
            backedUp += customFieldValues.size
            onProgress("Custom field values backed up: ${customFieldValues.size}", 50)
            
            // 6. Backup Notes
            onProgress("Backing up notes...", 55)
            val notes = db.noteDao().getAllNotesList()
            totalItems += notes.size
            backupNotes(crmCollection, notes)
            backedUp += notes.size
            onProgress("Notes backed up: ${notes.size}", 60)
            
            // 7. Backup Timeline
            onProgress("Backing up timeline...", 65)
            val timeline = db.timelineDao().getAllTimelineList()
            totalItems += timeline.size
            backupTimeline(crmCollection, timeline)
            backedUp += timeline.size
            onProgress("Timeline backed up: ${timeline.size}", 70)
            
            // 8. Backup Chat Messages
            onProgress("Backing up chat messages...", 75)
            val chatMessages = db.chatMessageDao().getAllMessagesList()
            totalItems += chatMessages.size
            backupChatMessages(crmCollection, chatMessages)
            backedUp += chatMessages.size
            onProgress("Chat messages backed up: ${chatMessages.size}", 80)
            
            // 9. Backup Payments
            onProgress("Backing up payments...", 85)
            val payments = db.paymentDao().getAllPaymentsList()
            totalItems += payments.size
            backupPayments(crmCollection, payments)
            backedUp += payments.size
            onProgress("Payments backed up: ${payments.size}", 87)
            
            // 10. Backup Invoices
            val invoices = db.paymentDao().getAllInvoicesList()
            totalItems += invoices.size
            backupInvoices(crmCollection, invoices)
            backedUp += invoices.size
            onProgress("Invoices backed up: ${invoices.size}", 90)
            
            // 11. Backup Auto Add Settings
            onProgress("Backing up settings...", 92)
            val autoAddSettings = db.autoAddSettingsDao().getSettingsDirect()
            if (autoAddSettings != null) {
                backupAutoAddSettings(crmCollection, autoAddSettings)
                totalItems++
                backedUp++
            }
            
            // 12. Backup Keyword Rules
            val keywordRules = db.autoAddSettingsDao().getAllKeywordRulesList()
            totalItems += keywordRules.size
            backupKeywordRules(crmCollection, keywordRules)
            backedUp += keywordRules.size
            onProgress("Settings backed up", 95)
            
            // Update meta
            updateBackupMeta(userId, totalItems)
            
            // Save backup timestamp
            val now = System.currentTimeMillis()
            prefs.edit().putLong(PREF_LAST_BACKUP, now).apply()
            
            onProgress("Backup complete! Total items: $totalItems", 100)
            
            Result.success(BackupStats(totalItems, backedUp, 0))
            
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            Result.failure(e)
        }
    }
    
    private suspend fun backupLeads(crmCollection: CollectionReference, leads: List<LeadEntity>) {
        val batches = leads.chunked(BATCH_SIZE)
        for (batch in batches) {
            val writeBatch = firestore.batch()
            for (lead in batch) {
                val docRef = crmCollection.document("leads").collection("items").document(lead.id)
                val data = mapOf(
                    "id" to lead.id,
                    "name" to lead.name,
                    "phoneNumber" to lead.phoneNumber,
                    "email" to lead.email,
                    "countryCode" to lead.countryCode,
                    "countryIso" to lead.countryIso,
                    "alternatePhone" to lead.alternatePhone,
                    "status" to lead.status.name,
                    "source" to lead.source,
                    "lastMessage" to lead.lastMessage,
                    "timestamp" to lead.timestamp,
                    "category" to lead.category,
                    "notes" to lead.notes,
                    "priority" to lead.priority.name,
                    "tags" to lead.tags,
                    "product" to lead.product,
                    "leadScore" to lead.leadScore,
                    "nextFollowUpDate" to lead.nextFollowUpDate,
                    "isFollowUpCompleted" to lead.isFollowUpCompleted,
                    "lastModifiedAt" to System.currentTimeMillis()
                )
                writeBatch.set(docRef, data, SetOptions.merge())
            }
            writeBatch.commit().await()
        }
    }
    
    private suspend fun backupProducts(crmCollection: CollectionReference, products: List<ProductEntity>) {
        val batches = products.chunked(BATCH_SIZE)
        for (batch in batches) {
            val writeBatch = firestore.batch()
            for (product in batch) {
                val docRef = crmCollection.document("products").collection("items").document(product.id)
                val data = mapOf(
                    "id" to product.id,
                    "name" to product.name,
                    "type" to product.type.name,
                    "category" to product.category,
                    "subcategory" to product.subcategory,
                    "mrp" to product.mrp,
                    "sellingPrice" to product.sellingPrice,
                    "description" to product.description,
                    "color" to product.color,
                    "size" to product.size,
                    "height" to product.height,
                    "width" to product.width,
                    "weight" to product.weight,
                    "downloadLink" to product.downloadLink,
                    "licenseType" to product.licenseType,
                    "version" to product.version,
                    "serviceType" to product.serviceType?.name,
                    "duration" to product.duration,
                    "deliveryTime" to product.deliveryTime,
                    "lastModifiedAt" to System.currentTimeMillis()
                )
                writeBatch.set(docRef, data, SetOptions.merge())
            }
            writeBatch.commit().await()
        }
    }
    
    private suspend fun backupFollowUps(crmCollection: CollectionReference, followUps: List<FollowUpEntity>) {
        val batches = followUps.chunked(BATCH_SIZE)
        for (batch in batches) {
            val writeBatch = firestore.batch()
            for (followUp in batch) {
                val docRef = crmCollection.document("follow_ups").collection("items").document(followUp.id)
                val data = mapOf(
                    "id" to followUp.id,
                    "leadId" to followUp.leadId,
                    "title" to followUp.title,
                    "description" to followUp.description,
                    "scheduledDate" to followUp.scheduledDate,
                    "scheduledTime" to followUp.scheduledTime,
                    "type" to followUp.type.name,
                    "isCompleted" to followUp.isCompleted,
                    "completedDate" to followUp.completedDate,
                    "notes" to followUp.notes,
                    "reminderMinutes" to followUp.reminderMinutes,
                    "lastModifiedAt" to System.currentTimeMillis()
                )
                writeBatch.set(docRef, data, SetOptions.merge())
            }
            writeBatch.commit().await()
        }
    }

    
    private suspend fun backupCustomFieldDefinitions(crmCollection: CollectionReference, definitions: List<CustomFieldDefinitionEntity>) {
        val batches = definitions.chunked(BATCH_SIZE)
        for (batch in batches) {
            val writeBatch = firestore.batch()
            for (def in batch) {
                val docRef = crmCollection.document("custom_field_definitions").collection("items").document(def.id)
                val data = mapOf(
                    "id" to def.id,
                    "fieldName" to def.fieldName,
                    "fieldType" to def.fieldType.name,
                    "isRequired" to def.isRequired,
                    "defaultValue" to def.defaultValue,
                    "options" to def.options,
                    "displayOrder" to def.displayOrder,
                    "isActive" to def.isActive,
                    "createdAt" to def.createdAt,
                    "lastModifiedAt" to System.currentTimeMillis()
                )
                writeBatch.set(docRef, data, SetOptions.merge())
            }
            writeBatch.commit().await()
        }
    }
    
    private suspend fun backupCustomFieldValues(crmCollection: CollectionReference, values: List<CustomFieldValueEntity>) {
        val batches = values.chunked(BATCH_SIZE)
        for (batch in batches) {
            val writeBatch = firestore.batch()
            for (value in batch) {
                val docId = "${value.leadId}_${value.fieldId}"
                val docRef = crmCollection.document("custom_field_values").collection("items").document(docId)
                val data = mapOf(
                    "leadId" to value.leadId,
                    "fieldId" to value.fieldId,
                    "fieldValue" to value.fieldValue,
                    "updatedAt" to value.updatedAt,
                    "lastModifiedAt" to System.currentTimeMillis()
                )
                writeBatch.set(docRef, data, SetOptions.merge())
            }
            writeBatch.commit().await()
        }
    }
    
    private suspend fun backupNotes(crmCollection: CollectionReference, notes: List<NoteEntity>) {
        val batches = notes.chunked(BATCH_SIZE)
        for (batch in batches) {
            val writeBatch = firestore.batch()
            for (note in batch) {
                val docRef = crmCollection.document("notes").collection("items").document(note.id)
                val data = mapOf(
                    "id" to note.id,
                    "leadId" to note.leadId,
                    "title" to note.title,
                    "content" to note.content,
                    "noteType" to note.noteType.name,
                    "priority" to note.priority.name,
                    "isPinned" to note.isPinned,
                    "createdAt" to note.createdAt,
                    "updatedAt" to note.updatedAt,
                    "createdBy" to note.createdBy,
                    "attachments" to note.attachments,
                    "tags" to note.tags,
                    "isArchived" to note.isArchived,
                    "parentNoteId" to note.parentNoteId,
                    "lastModifiedAt" to System.currentTimeMillis()
                )
                writeBatch.set(docRef, data, SetOptions.merge())
            }
            writeBatch.commit().await()
        }
    }
    
    private suspend fun backupTimeline(crmCollection: CollectionReference, timeline: List<TimelineEntity>) {
        val batches = timeline.chunked(BATCH_SIZE)
        for (batch in batches) {
            val writeBatch = firestore.batch()
            for (entry in batch) {
                val docRef = crmCollection.document("timeline").collection("items").document(entry.id)
                val data = mapOf(
                    "id" to entry.id,
                    "leadId" to entry.leadId,
                    "eventType" to entry.eventType.name,
                    "title" to entry.title,
                    "description" to entry.description,
                    "timestamp" to entry.timestamp,
                    "imageUri" to entry.imageUri,
                    "metadata" to entry.metadata,
                    "iconType" to entry.iconType,
                    "lastModifiedAt" to System.currentTimeMillis()
                )
                writeBatch.set(docRef, data, SetOptions.merge())
            }
            writeBatch.commit().await()
        }
    }
    
    private suspend fun backupChatMessages(crmCollection: CollectionReference, messages: List<ChatMessageEntity>) {
        val batches = messages.chunked(BATCH_SIZE)
        for (batch in batches) {
            val writeBatch = firestore.batch()
            for (msg in batch) {
                val docRef = crmCollection.document("chat_messages").collection("items").document(msg.id)
                val data = mapOf(
                    "id" to msg.id,
                    "leadId" to msg.leadId,
                    "senderName" to msg.senderName,
                    "senderPhone" to msg.senderPhone,
                    "messageText" to msg.messageText,
                    "timestamp" to msg.timestamp,
                    "isIncoming" to msg.isIncoming,
                    "isAutoReply" to msg.isAutoReply,
                    "matchedKeyword" to msg.matchedKeyword,
                    "replyType" to msg.replyType,
                    "packageName" to msg.packageName,
                    "isRead" to msg.isRead,
                    "lastModifiedAt" to System.currentTimeMillis()
                )
                writeBatch.set(docRef, data, SetOptions.merge())
            }
            writeBatch.commit().await()
        }
    }
    
    private suspend fun backupPayments(crmCollection: CollectionReference, payments: List<PaymentEntity>) {
        val batches = payments.chunked(BATCH_SIZE)
        for (batch in batches) {
            val writeBatch = firestore.batch()
            for (payment in batch) {
                val docRef = crmCollection.document("payments").collection("items").document(payment.id)
                val data = mapOf(
                    "id" to payment.id,
                    "leadId" to payment.leadId,
                    "amount" to payment.amount,
                    "paymentType" to payment.paymentType.name,
                    "description" to payment.description,
                    "timestamp" to payment.timestamp,
                    "createdAt" to payment.createdAt,
                    "lastModifiedAt" to System.currentTimeMillis()
                )
                writeBatch.set(docRef, data, SetOptions.merge())
            }
            writeBatch.commit().await()
        }
    }
    
    private suspend fun backupInvoices(crmCollection: CollectionReference, invoices: List<InvoiceEntity>) {
        val batches = invoices.chunked(BATCH_SIZE)
        for (batch in batches) {
            val writeBatch = firestore.batch()
            for (invoice in batch) {
                val docRef = crmCollection.document("invoices").collection("items").document(invoice.id)
                val data = mapOf(
                    "id" to invoice.id,
                    "leadId" to invoice.leadId,
                    "invoiceNumber" to invoice.invoiceNumber,
                    "amount" to invoice.amount,
                    "tax" to invoice.tax,
                    "totalAmount" to invoice.totalAmount,
                    "status" to invoice.status.name,
                    "addressTo" to invoice.addressTo,
                    "addressFrom" to invoice.addressFrom,
                    "comments" to invoice.comments,
                    "items" to invoice.items,
                    "timestamp" to invoice.timestamp,
                    "dueDate" to invoice.dueDate,
                    "createdAt" to invoice.createdAt,
                    "lastModifiedAt" to System.currentTimeMillis()
                )
                writeBatch.set(docRef, data, SetOptions.merge())
            }
            writeBatch.commit().await()
        }
    }
    
    private suspend fun backupAutoAddSettings(crmCollection: CollectionReference, settings: AutoAddSettingsEntity) {
        val docRef = crmCollection.document("settings").collection("items").document("auto_add")
        val data = mapOf(
            "id" to settings.id,
            "isAutoAddEnabled" to settings.isAutoAddEnabled,
            "autoAddAllMessages" to settings.autoAddAllMessages,
            "keywordBasedAdd" to settings.keywordBasedAdd,
            "keywords" to settings.keywords,
            "defaultStatus" to settings.defaultStatus,
            "defaultSource" to settings.defaultSource,
            "defaultCategory" to settings.defaultCategory,
            "defaultTags" to settings.defaultTags,
            "excludeExistingContacts" to settings.excludeExistingContacts,
            "notifyOnNewLead" to settings.notifyOnNewLead,
            "createdAt" to settings.createdAt,
            "updatedAt" to settings.updatedAt,
            "lastModifiedAt" to System.currentTimeMillis()
        )
        docRef.set(data, SetOptions.merge()).await()
    }
    
    private suspend fun backupKeywordRules(crmCollection: CollectionReference, rules: List<AutoAddKeywordRuleEntity>) {
        val batches = rules.chunked(BATCH_SIZE)
        for (batch in batches) {
            val writeBatch = firestore.batch()
            for (rule in batch) {
                val docRef = crmCollection.document("keyword_rules").collection("items").document(rule.id)
                val data = mapOf(
                    "id" to rule.id,
                    "keyword" to rule.keyword,
                    "matchType" to rule.matchType.name,
                    "assignStatus" to rule.assignStatus,
                    "assignCategory" to rule.assignCategory,
                    "assignTags" to rule.assignTags,
                    "assignPriority" to rule.assignPriority,
                    "isEnabled" to rule.isEnabled,
                    "createdAt" to rule.createdAt,
                    "lastModifiedAt" to System.currentTimeMillis()
                )
                writeBatch.set(docRef, data, SetOptions.merge())
            }
            writeBatch.commit().await()
        }
    }
    
    private suspend fun updateBackupMeta(userId: String, totalItems: Int) {
        val docRef = firestore.collection("users").document(userId)
            .collection("chatspromo_crm").document("meta")
            .collection("items").document("sync_info")
        val data = mapOf(
            "lastBackupAt" to System.currentTimeMillis(),
            "totalItems" to totalItems,
            "appVersion" to "1.0",
            "deviceId" to android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
        )
        docRef.set(data, SetOptions.merge()).await()
    }

    
    // ==================== RESTORE FUNCTIONS ====================
    
    /**
     * Restore all CRM data from Firestore
     */
    suspend fun restoreAll(onProgress: (String, Int) -> Unit): Result<RestoreStats> {
        return try {
            val crmCollection = getCRMCollection() ?: return Result.failure(Exception("User not logged in"))
            
            var inserted = 0
            var updated = 0
            var skipped = 0
            
            // Check if backup exists
            onProgress("Checking cloud backup...", 5)
            val metaDoc = crmCollection.document("meta").collection("items").document("sync_info").get().await()
            if (!metaDoc.exists()) {
                return Result.failure(Exception("No backup found in cloud"))
            }
            
            // 1. Restore Leads first (parent entity)
            onProgress("Restoring leads...", 10)
            val leadsResult = restoreLeads(crmCollection)
            inserted += leadsResult.inserted
            updated += leadsResult.updated
            skipped += leadsResult.skipped
            onProgress("Leads restored: +${leadsResult.inserted} ↻${leadsResult.updated}", 20)
            
            // 2. Restore Products
            onProgress("Restoring products...", 25)
            val productsResult = restoreProducts(crmCollection)
            inserted += productsResult.inserted
            updated += productsResult.updated
            skipped += productsResult.skipped
            onProgress("Products restored: +${productsResult.inserted} ↻${productsResult.updated}", 30)
            
            // 3. Restore Follow-ups
            onProgress("Restoring follow-ups...", 35)
            val followUpsResult = restoreFollowUps(crmCollection)
            inserted += followUpsResult.inserted
            updated += followUpsResult.updated
            skipped += followUpsResult.skipped
            onProgress("Follow-ups restored: +${followUpsResult.inserted} ↻${followUpsResult.updated}", 40)
            
            // 4. Restore Custom Field Definitions
            onProgress("Restoring custom fields...", 45)
            val customFieldDefsResult = restoreCustomFieldDefinitions(crmCollection)
            inserted += customFieldDefsResult.inserted
            updated += customFieldDefsResult.updated
            skipped += customFieldDefsResult.skipped
            onProgress("Custom field definitions restored", 50)
            
            // 5. Restore Custom Field Values
            val customFieldValuesResult = restoreCustomFieldValues(crmCollection)
            inserted += customFieldValuesResult.inserted
            updated += customFieldValuesResult.updated
            skipped += customFieldValuesResult.skipped
            onProgress("Custom field values restored", 55)
            
            // 6. Restore Notes
            onProgress("Restoring notes...", 60)
            val notesResult = restoreNotes(crmCollection)
            inserted += notesResult.inserted
            updated += notesResult.updated
            skipped += notesResult.skipped
            onProgress("Notes restored: +${notesResult.inserted} ↻${notesResult.updated}", 65)
            
            // 7. Restore Timeline
            onProgress("Restoring timeline...", 70)
            val timelineResult = restoreTimeline(crmCollection)
            inserted += timelineResult.inserted
            updated += timelineResult.updated
            skipped += timelineResult.skipped
            onProgress("Timeline restored", 75)
            
            // 8. Restore Chat Messages
            onProgress("Restoring chat messages...", 80)
            val chatResult = restoreChatMessages(crmCollection)
            inserted += chatResult.inserted
            updated += chatResult.updated
            skipped += chatResult.skipped
            onProgress("Chat messages restored", 85)
            
            // 9. Restore Payments
            onProgress("Restoring payments...", 87)
            val paymentsResult = restorePayments(crmCollection)
            inserted += paymentsResult.inserted
            updated += paymentsResult.updated
            skipped += paymentsResult.skipped
            
            // 10. Restore Invoices
            val invoicesResult = restoreInvoices(crmCollection)
            inserted += invoicesResult.inserted
            updated += invoicesResult.updated
            skipped += invoicesResult.skipped
            onProgress("Payments & invoices restored", 90)
            
            // 11. Restore Auto Add Settings
            onProgress("Restoring settings...", 92)
            restoreAutoAddSettings(crmCollection)
            
            // 12. Restore Keyword Rules
            val rulesResult = restoreKeywordRules(crmCollection)
            inserted += rulesResult.inserted
            updated += rulesResult.updated
            skipped += rulesResult.skipped
            onProgress("Settings restored", 95)
            
            // Save restore timestamp
            val now = System.currentTimeMillis()
            prefs.edit().putLong(PREF_LAST_RESTORE, now).apply()
            
            onProgress("Restore complete! +$inserted ↻$updated ⊘$skipped", 100)
            
            Result.success(RestoreStats(inserted, updated, skipped))
            
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed", e)
            Result.failure(e)
        }
    }
    
    private suspend fun restoreLeads(crmCollection: CollectionReference): RestoreResult {
        var inserted = 0
        var updated = 0
        var skipped = 0
        
        val cloudDocs = crmCollection.document("leads").collection("items").get().await()
        
        for (doc in cloudDocs.documents) {
            try {
                val id = doc.getString("id") ?: continue
                val name = doc.getString("name") ?: ""
                val phoneNumber = doc.getString("phoneNumber") ?: ""
                val email = doc.getString("email") ?: ""
                val countryCode = doc.getString("countryCode") ?: ""
                val countryIso = doc.getString("countryIso") ?: ""
                val alternatePhone = doc.getString("alternatePhone") ?: ""
                val statusStr = doc.getString("status") ?: "NEW"
                val source = doc.getString("source") ?: ""
                val lastMessage = doc.getString("lastMessage") ?: ""
                val timestamp = doc.getLong("timestamp") ?: 0L
                val category = doc.getString("category") ?: "General"
                val notes = doc.getString("notes") ?: ""
                val priorityStr = doc.getString("priority") ?: "MEDIUM"
                val tags = doc.getString("tags") ?: ""
                val product = doc.getString("product") ?: ""
                val leadScore = doc.getLong("leadScore")?.toInt() ?: 50
                val nextFollowUpDate = doc.getLong("nextFollowUpDate")
                val isFollowUpCompleted = doc.getBoolean("isFollowUpCompleted") ?: false
                val lastModifiedAt = doc.getLong("lastModifiedAt") ?: timestamp
                
                val status = try { LeadStatus.valueOf(statusStr) } catch (e: Exception) { LeadStatus.NEW }
                val priority = try { LeadPriority.valueOf(priorityStr) } catch (e: Exception) { LeadPriority.MEDIUM }
                
                val cloudLead = LeadEntity(
                    id = id,
                    name = name,
                    phoneNumber = phoneNumber,
                    email = email,
                    countryCode = countryCode,
                    countryIso = countryIso,
                    alternatePhone = alternatePhone,
                    status = status,
                    source = source,
                    lastMessage = lastMessage,
                    timestamp = timestamp,
                    category = category,
                    notes = notes,
                    priority = priority,
                    tags = tags,
                    product = product,
                    leadScore = leadScore,
                    nextFollowUpDate = nextFollowUpDate,
                    isFollowUpCompleted = isFollowUpCompleted
                )
                
                val localLead = db.leadDao().getLeadById(id)
                
                when {
                    localLead == null -> {
                        db.leadDao().insertLead(cloudLead)
                        inserted++
                    }
                    lastModifiedAt > localLead.timestamp -> {
                        db.leadDao().updateLead(cloudLead)
                        updated++
                    }
                    else -> skipped++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring lead: ${doc.id}", e)
            }
        }
        
        return RestoreResult(inserted, updated, skipped)
    }
    
    private suspend fun restoreProducts(crmCollection: CollectionReference): RestoreResult {
        var inserted = 0
        var updated = 0
        var skipped = 0
        
        val cloudDocs = crmCollection.document("products").collection("items").get().await()
        
        for (doc in cloudDocs.documents) {
            try {
                val id = doc.getString("id") ?: continue
                val name = doc.getString("name") ?: ""
                val typeStr = doc.getString("type") ?: "PHYSICAL"
                val category = doc.getString("category") ?: ""
                val subcategory = doc.getString("subcategory") ?: ""
                val mrp = doc.getString("mrp") ?: ""
                val sellingPrice = doc.getString("sellingPrice") ?: ""
                val description = doc.getString("description") ?: ""
                val color = doc.getString("color") ?: ""
                val size = doc.getString("size") ?: ""
                val height = doc.getString("height") ?: ""
                val width = doc.getString("width") ?: ""
                val weight = doc.getString("weight") ?: ""
                val downloadLink = doc.getString("downloadLink") ?: ""
                val licenseType = doc.getString("licenseType") ?: ""
                val version = doc.getString("version") ?: ""
                val serviceTypeStr = doc.getString("serviceType")
                val duration = doc.getString("duration") ?: ""
                val deliveryTime = doc.getString("deliveryTime") ?: ""
                val lastModifiedAt = doc.getLong("lastModifiedAt") ?: 0L
                
                val type = try { ProductType.valueOf(typeStr) } catch (e: Exception) { ProductType.PHYSICAL }
                val serviceType = serviceTypeStr?.let { 
                    try { ServiceType.valueOf(it) } catch (e: Exception) { null }
                }
                
                val cloudProduct = ProductEntity(
                    id = id,
                    name = name,
                    type = type,
                    category = category,
                    subcategory = subcategory,
                    mrp = mrp,
                    sellingPrice = sellingPrice,
                    description = description,
                    color = color,
                    size = size,
                    height = height,
                    width = width,
                    weight = weight,
                    downloadLink = downloadLink,
                    licenseType = licenseType,
                    version = version,
                    serviceType = serviceType,
                    duration = duration,
                    deliveryTime = deliveryTime
                )
                
                val localProduct = db.productDao().getProductById(id)
                
                when {
                    localProduct == null -> {
                        db.productDao().insertProduct(cloudProduct)
                        inserted++
                    }
                    else -> {
                        db.productDao().updateProduct(cloudProduct)
                        updated++
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring product: ${doc.id}", e)
            }
        }
        
        return RestoreResult(inserted, updated, skipped)
    }

    
    private suspend fun restoreFollowUps(crmCollection: CollectionReference): RestoreResult {
        var inserted = 0
        var updated = 0
        var skipped = 0
        
        val cloudDocs = crmCollection.document("follow_ups").collection("items").get().await()
        
        for (doc in cloudDocs.documents) {
            try {
                val id = doc.getString("id") ?: continue
                val leadId = doc.getString("leadId") ?: continue
                val title = doc.getString("title") ?: ""
                val description = doc.getString("description") ?: ""
                val scheduledDate = doc.getLong("scheduledDate") ?: 0L
                val scheduledTime = doc.getString("scheduledTime") ?: "09:00"
                val typeStr = doc.getString("type") ?: "CALL"
                val isCompleted = doc.getBoolean("isCompleted") ?: false
                val completedDate = doc.getLong("completedDate")
                val notes = doc.getString("notes") ?: ""
                val reminderMinutes = doc.getLong("reminderMinutes")?.toInt() ?: 15
                
                val type = try { FollowUpType.valueOf(typeStr) } catch (e: Exception) { FollowUpType.CALL }
                
                // Check if lead exists
                val leadExists = db.leadDao().getLeadById(leadId) != null
                if (!leadExists) {
                    skipped++
                    continue
                }
                
                val cloudFollowUp = FollowUpEntity(
                    id = id,
                    leadId = leadId,
                    title = title,
                    description = description,
                    scheduledDate = scheduledDate,
                    scheduledTime = scheduledTime,
                    type = type,
                    isCompleted = isCompleted,
                    completedDate = completedDate,
                    notes = notes,
                    reminderMinutes = reminderMinutes
                )
                
                val localFollowUp = db.followUpDao().getFollowUpById(id)
                
                when {
                    localFollowUp == null -> {
                        db.followUpDao().insertFollowUp(cloudFollowUp)
                        inserted++
                    }
                    else -> {
                        db.followUpDao().updateFollowUp(cloudFollowUp)
                        updated++
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring follow-up: ${doc.id}", e)
            }
        }
        
        return RestoreResult(inserted, updated, skipped)
    }
    
    private suspend fun restoreCustomFieldDefinitions(crmCollection: CollectionReference): RestoreResult {
        var inserted = 0
        var updated = 0
        var skipped = 0
        
        val cloudDocs = crmCollection.document("custom_field_definitions").collection("items").get().await()
        
        for (doc in cloudDocs.documents) {
            try {
                val id = doc.getString("id") ?: continue
                val fieldName = doc.getString("fieldName") ?: ""
                val fieldTypeStr = doc.getString("fieldType") ?: "TEXT"
                val isRequired = doc.getBoolean("isRequired") ?: false
                val defaultValue = doc.getString("defaultValue") ?: ""
                val options = doc.getString("options") ?: ""
                val displayOrder = doc.getLong("displayOrder")?.toInt() ?: 0
                val isActive = doc.getBoolean("isActive") ?: true
                val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                
                val fieldType = try { CustomFieldType.valueOf(fieldTypeStr) } catch (e: Exception) { CustomFieldType.TEXT }
                
                val cloudDef = CustomFieldDefinitionEntity(
                    id = id,
                    fieldName = fieldName,
                    fieldType = fieldType,
                    isRequired = isRequired,
                    defaultValue = defaultValue,
                    options = options,
                    displayOrder = displayOrder,
                    isActive = isActive,
                    createdAt = createdAt
                )
                
                val localDef = db.customFieldDao().getDefinitionById(id)
                
                when {
                    localDef == null -> {
                        db.customFieldDao().insertDefinition(cloudDef)
                        inserted++
                    }
                    else -> {
                        db.customFieldDao().updateDefinition(cloudDef)
                        updated++
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring custom field definition: ${doc.id}", e)
            }
        }
        
        return RestoreResult(inserted, updated, skipped)
    }
    
    private suspend fun restoreCustomFieldValues(crmCollection: CollectionReference): RestoreResult {
        var inserted = 0
        var updated = 0
        var skipped = 0
        
        val cloudDocs = crmCollection.document("custom_field_values").collection("items").get().await()
        
        for (doc in cloudDocs.documents) {
            try {
                val leadId = doc.getString("leadId") ?: continue
                val fieldId = doc.getString("fieldId") ?: continue
                val fieldValue = doc.getString("fieldValue") ?: ""
                val updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                
                // Check if lead exists
                val leadExists = db.leadDao().getLeadById(leadId) != null
                if (!leadExists) {
                    skipped++
                    continue
                }
                
                val cloudValue = CustomFieldValueEntity(
                    leadId = leadId,
                    fieldId = fieldId,
                    fieldValue = fieldValue,
                    updatedAt = updatedAt
                )
                
                db.customFieldDao().insertOrUpdateValue(cloudValue)
                inserted++
                
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring custom field value: ${doc.id}", e)
            }
        }
        
        return RestoreResult(inserted, updated, skipped)
    }
    
    private suspend fun restoreNotes(crmCollection: CollectionReference): RestoreResult {
        var inserted = 0
        var updated = 0
        var skipped = 0
        
        val cloudDocs = crmCollection.document("notes").collection("items").get().await()
        
        for (doc in cloudDocs.documents) {
            try {
                val id = doc.getString("id") ?: continue
                val leadId = doc.getString("leadId") ?: continue
                val title = doc.getString("title") ?: ""
                val content = doc.getString("content") ?: ""
                val noteTypeStr = doc.getString("noteType") ?: "GENERAL"
                val priorityStr = doc.getString("priority") ?: "NORMAL"
                val isPinned = doc.getBoolean("isPinned") ?: false
                val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                val updatedAt = doc.getLong("updatedAt") ?: createdAt
                val createdBy = doc.getString("createdBy") ?: "User"
                val attachments = doc.getString("attachments") ?: ""
                val tags = doc.getString("tags") ?: ""
                val isArchived = doc.getBoolean("isArchived") ?: false
                val parentNoteId = doc.getString("parentNoteId")
                
                val noteType = try { NoteType.valueOf(noteTypeStr) } catch (e: Exception) { NoteType.GENERAL }
                val priority = try { NotePriority.valueOf(priorityStr) } catch (e: Exception) { NotePriority.NORMAL }
                
                // Check if lead exists
                val leadExists = db.leadDao().getLeadById(leadId) != null
                if (!leadExists) {
                    skipped++
                    continue
                }
                
                val cloudNote = NoteEntity(
                    id = id,
                    leadId = leadId,
                    title = title,
                    content = content,
                    noteType = noteType,
                    priority = priority,
                    isPinned = isPinned,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    createdBy = createdBy,
                    attachments = attachments,
                    tags = tags,
                    isArchived = isArchived,
                    parentNoteId = parentNoteId
                )
                
                val localNote = db.noteDao().getNoteById(id)
                
                when {
                    localNote == null -> {
                        db.noteDao().insertNote(cloudNote)
                        inserted++
                    }
                    else -> {
                        db.noteDao().updateNote(cloudNote)
                        updated++
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring note: ${doc.id}", e)
            }
        }
        
        return RestoreResult(inserted, updated, skipped)
    }
    
    private suspend fun restoreTimeline(crmCollection: CollectionReference): RestoreResult {
        var inserted = 0
        var updated = 0
        var skipped = 0
        
        val cloudDocs = crmCollection.document("timeline").collection("items").get().await()
        
        for (doc in cloudDocs.documents) {
            try {
                val id = doc.getString("id") ?: continue
                val leadId = doc.getString("leadId") ?: continue
                val eventTypeStr = doc.getString("eventType") ?: "CUSTOM_EVENT"
                val title = doc.getString("title") ?: ""
                val description = doc.getString("description") ?: ""
                val timestamp = doc.getLong("timestamp") ?: 0L
                val imageUri = doc.getString("imageUri")
                val metadata = doc.getString("metadata") ?: ""
                val iconType = doc.getString("iconType") ?: "default"
                
                val eventType = try { TimelineEventType.valueOf(eventTypeStr) } catch (e: Exception) { TimelineEventType.CUSTOM_EVENT }
                
                // Check if lead exists
                val leadExists = db.leadDao().getLeadById(leadId) != null
                if (!leadExists) {
                    skipped++
                    continue
                }
                
                val cloudEntry = TimelineEntity(
                    id = id,
                    leadId = leadId,
                    eventType = eventType,
                    title = title,
                    description = description,
                    timestamp = timestamp,
                    imageUri = imageUri,
                    metadata = metadata,
                    iconType = iconType
                )
                
                val localEntry = db.timelineDao().getTimelineById(id)
                
                when {
                    localEntry == null -> {
                        db.timelineDao().insertTimeline(cloudEntry)
                        inserted++
                    }
                    else -> skipped++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring timeline: ${doc.id}", e)
            }
        }
        
        return RestoreResult(inserted, updated, skipped)
    }

    
    private suspend fun restoreChatMessages(crmCollection: CollectionReference): RestoreResult {
        var inserted = 0
        var updated = 0
        var skipped = 0
        
        val cloudDocs = crmCollection.document("chat_messages").collection("items").get().await()
        
        for (doc in cloudDocs.documents) {
            try {
                val id = doc.getString("id") ?: continue
                val leadId = doc.getString("leadId") ?: continue
                val senderName = doc.getString("senderName") ?: ""
                val senderPhone = doc.getString("senderPhone") ?: ""
                val messageText = doc.getString("messageText") ?: ""
                val timestamp = doc.getLong("timestamp") ?: 0L
                val isIncoming = doc.getBoolean("isIncoming") ?: true
                val isAutoReply = doc.getBoolean("isAutoReply") ?: false
                val matchedKeyword = doc.getString("matchedKeyword")
                val replyType = doc.getString("replyType") ?: "manual"
                val packageName = doc.getString("packageName") ?: ""
                val isRead = doc.getBoolean("isRead") ?: false
                
                // Check if lead exists
                val leadExists = db.leadDao().getLeadById(leadId) != null
                if (!leadExists) {
                    skipped++
                    continue
                }
                
                val cloudMessage = ChatMessageEntity(
                    id = id,
                    leadId = leadId,
                    senderName = senderName,
                    senderPhone = senderPhone,
                    messageText = messageText,
                    timestamp = timestamp,
                    isIncoming = isIncoming,
                    isAutoReply = isAutoReply,
                    matchedKeyword = matchedKeyword,
                    replyType = replyType,
                    packageName = packageName,
                    isRead = isRead
                )
                
                val localMessage = db.chatMessageDao().getMessageById(id)
                
                when {
                    localMessage == null -> {
                        db.chatMessageDao().insertMessage(cloudMessage)
                        inserted++
                    }
                    else -> skipped++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring chat message: ${doc.id}", e)
            }
        }
        
        return RestoreResult(inserted, updated, skipped)
    }
    
    private suspend fun restorePayments(crmCollection: CollectionReference): RestoreResult {
        var inserted = 0
        var updated = 0
        var skipped = 0
        
        val cloudDocs = crmCollection.document("payments").collection("items").get().await()
        
        for (doc in cloudDocs.documents) {
            try {
                val id = doc.getString("id") ?: continue
                val leadId = doc.getString("leadId") ?: continue
                val amount = doc.getDouble("amount") ?: 0.0
                val paymentTypeStr = doc.getString("paymentType") ?: "RECEIVED"
                val description = doc.getString("description") ?: ""
                val timestamp = doc.getLong("timestamp") ?: 0L
                val createdAt = doc.getLong("createdAt") ?: timestamp
                
                val paymentType = try { PaymentType.valueOf(paymentTypeStr) } catch (e: Exception) { PaymentType.RECEIVED }
                
                // Check if lead exists
                val leadExists = db.leadDao().getLeadById(leadId) != null
                if (!leadExists) {
                    skipped++
                    continue
                }
                
                val cloudPayment = PaymentEntity(
                    id = id,
                    leadId = leadId,
                    amount = amount,
                    paymentType = paymentType,
                    description = description,
                    timestamp = timestamp,
                    createdAt = createdAt
                )
                
                val localPayment = db.paymentDao().getPaymentById(id)
                
                when {
                    localPayment == null -> {
                        db.paymentDao().insertPayment(cloudPayment)
                        inserted++
                    }
                    else -> skipped++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring payment: ${doc.id}", e)
            }
        }
        
        return RestoreResult(inserted, updated, skipped)
    }
    
    private suspend fun restoreInvoices(crmCollection: CollectionReference): RestoreResult {
        var inserted = 0
        var updated = 0
        var skipped = 0
        
        val cloudDocs = crmCollection.document("invoices").collection("items").get().await()
        
        for (doc in cloudDocs.documents) {
            try {
                val id = doc.getString("id") ?: continue
                val leadId = doc.getString("leadId") ?: continue
                val invoiceNumber = doc.getString("invoiceNumber") ?: ""
                val amount = doc.getDouble("amount") ?: 0.0
                val tax = doc.getDouble("tax") ?: 0.0
                val totalAmount = doc.getDouble("totalAmount") ?: 0.0
                val statusStr = doc.getString("status") ?: "DRAFT"
                val addressTo = doc.getString("addressTo") ?: ""
                val addressFrom = doc.getString("addressFrom") ?: ""
                val comments = doc.getString("comments") ?: ""
                val items = doc.getString("items") ?: ""
                val timestamp = doc.getLong("timestamp") ?: 0L
                val dueDate = doc.getLong("dueDate")
                val createdAt = doc.getLong("createdAt") ?: timestamp
                
                val status = try { InvoiceStatus.valueOf(statusStr) } catch (e: Exception) { InvoiceStatus.DRAFT }
                
                // Check if lead exists
                val leadExists = db.leadDao().getLeadById(leadId) != null
                if (!leadExists) {
                    skipped++
                    continue
                }
                
                val cloudInvoice = InvoiceEntity(
                    id = id,
                    leadId = leadId,
                    invoiceNumber = invoiceNumber,
                    amount = amount,
                    tax = tax,
                    totalAmount = totalAmount,
                    status = status,
                    addressTo = addressTo,
                    addressFrom = addressFrom,
                    comments = comments,
                    items = items,
                    timestamp = timestamp,
                    dueDate = dueDate,
                    createdAt = createdAt
                )
                
                val localInvoice = db.paymentDao().getInvoiceById(id)
                
                when {
                    localInvoice == null -> {
                        db.paymentDao().insertInvoice(cloudInvoice)
                        inserted++
                    }
                    else -> skipped++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring invoice: ${doc.id}", e)
            }
        }
        
        return RestoreResult(inserted, updated, skipped)
    }
    
    private suspend fun restoreAutoAddSettings(crmCollection: CollectionReference) {
        try {
            val doc = crmCollection.document("settings").collection("items").document("auto_add").get().await()
            if (!doc.exists()) return
            
            val id = doc.getString("id") ?: "default"
            val isAutoAddEnabled = doc.getBoolean("isAutoAddEnabled") ?: false
            val autoAddAllMessages = doc.getBoolean("autoAddAllMessages") ?: false
            val keywordBasedAdd = doc.getBoolean("keywordBasedAdd") ?: true
            val keywords = doc.getString("keywords") ?: ""
            val defaultStatus = doc.getString("defaultStatus") ?: "NEW"
            val defaultSource = doc.getString("defaultSource") ?: "WhatsApp"
            val defaultCategory = doc.getString("defaultCategory") ?: "AutoRespond"
            val defaultTags = doc.getString("defaultTags") ?: ""
            val excludeExistingContacts = doc.getBoolean("excludeExistingContacts") ?: true
            val notifyOnNewLead = doc.getBoolean("notifyOnNewLead") ?: true
            val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
            val updatedAt = doc.getLong("updatedAt") ?: createdAt
            
            val settings = AutoAddSettingsEntity(
                id = id,
                isAutoAddEnabled = isAutoAddEnabled,
                autoAddAllMessages = autoAddAllMessages,
                keywordBasedAdd = keywordBasedAdd,
                keywords = keywords,
                defaultStatus = defaultStatus,
                defaultSource = defaultSource,
                defaultCategory = defaultCategory,
                defaultTags = defaultTags,
                excludeExistingContacts = excludeExistingContacts,
                notifyOnNewLead = notifyOnNewLead,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
            
            db.autoAddSettingsDao().insertOrUpdateSettings(settings)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring auto add settings", e)
        }
    }
    
    private suspend fun restoreKeywordRules(crmCollection: CollectionReference): RestoreResult {
        var inserted = 0
        var updated = 0
        var skipped = 0
        
        val cloudDocs = crmCollection.document("keyword_rules").collection("items").get().await()
        
        for (doc in cloudDocs.documents) {
            try {
                val id = doc.getString("id") ?: continue
                val keyword = doc.getString("keyword") ?: ""
                val matchTypeStr = doc.getString("matchType") ?: "CONTAINS"
                val assignStatus = doc.getString("assignStatus") ?: "NEW"
                val assignCategory = doc.getString("assignCategory") ?: "General"
                val assignTags = doc.getString("assignTags") ?: ""
                val assignPriority = doc.getString("assignPriority") ?: "MEDIUM"
                val isEnabled = doc.getBoolean("isEnabled") ?: true
                val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                
                val matchType = try { KeywordMatchType.valueOf(matchTypeStr) } catch (e: Exception) { KeywordMatchType.CONTAINS }
                
                val cloudRule = AutoAddKeywordRuleEntity(
                    id = id,
                    keyword = keyword,
                    matchType = matchType,
                    assignStatus = assignStatus,
                    assignCategory = assignCategory,
                    assignTags = assignTags,
                    assignPriority = assignPriority,
                    isEnabled = isEnabled,
                    createdAt = createdAt
                )
                
                val localRule = db.autoAddSettingsDao().getKeywordRuleById(id)
                
                when {
                    localRule == null -> {
                        db.autoAddSettingsDao().insertKeywordRule(cloudRule)
                        inserted++
                    }
                    else -> {
                        db.autoAddSettingsDao().updateKeywordRule(cloudRule)
                        updated++
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring keyword rule: ${doc.id}", e)
            }
        }
        
        return RestoreResult(inserted, updated, skipped)
    }
    
    // ==================== STATUS FUNCTIONS ====================
    
    /**
     * Get current sync status
     */
    fun getSyncStatus(): CRMSyncStatus {
        val lastBackup = prefs.getLong(PREF_LAST_BACKUP, 0)
        val lastRestore = prefs.getLong(PREF_LAST_RESTORE, 0)
        val isLoggedIn = auth.currentUser != null
        
        return CRMSyncStatus(
            isLoggedIn = isLoggedIn,
            lastBackupAt = if (lastBackup > 0) lastBackup else null,
            lastRestoreAt = if (lastRestore > 0) lastRestore else null,
            userEmail = auth.currentUser?.email
        )
    }
    
    /**
     * Get local data counts
     */
    suspend fun getLocalDataCounts(): LocalDataCounts {
        return LocalDataCounts(
            leads = db.leadDao().getLeadsCount(),
            products = db.productDao().getProductsCount(),
            followUps = db.followUpDao().getFollowUpsCount(),
            notes = db.noteDao().getNotesCount(),
            customFields = db.customFieldDao().getDefinitionsCount(),
            payments = db.paymentDao().getPaymentsCount(),
            invoices = db.paymentDao().getInvoicesCount()
        )
    }
}

// Data classes for stats
data class BackupStats(
    val totalItems: Int,
    val backedUp: Int,
    val failed: Int
)

data class RestoreStats(
    val inserted: Int,
    val updated: Int,
    val skipped: Int
)

data class RestoreResult(
    val inserted: Int,
    val updated: Int,
    val skipped: Int
)

data class CRMSyncStatus(
    val isLoggedIn: Boolean,
    val lastBackupAt: Long?,
    val lastRestoreAt: Long?,
    val userEmail: String?
)

data class LocalDataCounts(
    val leads: Int,
    val products: Int,
    val followUps: Int,
    val notes: Int,
    val customFields: Int,
    val payments: Int,
    val invoices: Int
)
