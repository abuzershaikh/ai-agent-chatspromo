package com.message.bulksend.leadmanager

import android.content.Context
import android.content.SharedPreferences
import com.message.bulksend.leadmanager.model.*
import com.message.bulksend.leadmanager.notifications.LeadFollowUpNotificationScheduler
import com.message.bulksend.leadmanager.repository.LeadRepository
import com.message.bulksend.utils.SubscriptionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class LeadManager(context: Context) {
    
    private val appContext = context.applicationContext
    private val repository = LeadRepository(appContext)
    private val followUpNotificationScheduler = LeadFollowUpNotificationScheduler(appContext)
    private val prefs: SharedPreferences = appContext.getSharedPreferences(
        "lead_manager_prefs",
        Context.MODE_PRIVATE
    )
    
    // Batch import configuration
    companion object {
        private const val FREE_LEAD_LIMIT = 5
        private const val KEY_FIELD_SETTINGS = "product_field_settings"
        private const val KEY_MIGRATION_DONE = "room_migration_done"
        private const val KEY_LAST_SYNC = "last_sync_time"
        const val BATCH_SIZE = 500 // Import 500 leads at a time
    }
    
    // Import progress state
    private val _importProgress = MutableStateFlow<ImportProgress?>(null)
    val importProgress: StateFlow<ImportProgress?> = _importProgress
    
    // Last sync time
    fun getLastSyncTime(): Long {
        return prefs.getLong(KEY_LAST_SYNC, 0L)
    }
    
    fun updateLastSyncTime() {
        prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
    }
    
    // Lead operations
    fun getAllLeads(): List<Lead> {
        return runBlocking {
            repository.getAllLeadsList()
        }
    }
    
    // Suspend version for async calls
    suspend fun getAllLeadsSuspend(): List<Lead> {
        return repository.getAllLeadsList()
    }
    
    fun getAllLeadsFlow(): Flow<List<Lead>> {
        return repository.getAllLeads()
    }
    
    fun getLeadsByStatus(status: LeadStatus): List<Lead> {
        return runBlocking {
            repository.getLeadsByStatus(status)
        }
    }
    
    fun canAddMoreLeadsForCurrentPlan(): Boolean {
        val isPremium = isPremiumPlanActive()
        if (isPremium) return true

        val currentCount = runBlocking { repository.getLeadStats().totalLeads }
        return currentCount < FREE_LEAD_LIMIT
    }

    fun addLead(lead: Lead): Boolean {
        return runBlocking(Dispatchers.IO) {
            if (!canAddMoreLeadsForCurrentPlan()) {
                return@runBlocking false
            }

            repository.insertLead(lead)
            followUpNotificationScheduler.syncLead(
                previousLead = null,
                updatedLead = lead,
                showConfirmation = true
            )
            true
        }
    }

    private fun isPremiumPlanActive(): Boolean {
        val subscriptionInfo = SubscriptionUtils.getLocalSubscriptionInfo(appContext)
        val type = subscriptionInfo["type"] as? String ?: "free"
        val isExpired = subscriptionInfo["isExpired"] as? Boolean ?: false
        return type == "premium" && !isExpired
    }
    
    fun updateLead(lead: Lead) {
        runBlocking(Dispatchers.IO) {
            val previousLead = repository.getLeadById(lead.id)
            repository.updateLead(lead)
            followUpNotificationScheduler.syncLead(
                previousLead = previousLead,
                updatedLead = lead,
                showConfirmation = true
            )
        }
    }
    
    fun deleteLead(id: String) {
        runBlocking(Dispatchers.IO) {
            val existingLead = repository.getLeadById(id)
            repository.deleteLead(id)
            existingLead?.let { followUpNotificationScheduler.cancelLead(it) }
        }
    }
    
    fun deleteAllLeads() {
        runBlocking(Dispatchers.IO) {
            val existingLeads = repository.getAllLeadsList()
            repository.deleteAllLeads()
            existingLeads.forEach { followUpNotificationScheduler.cancelLead(it) }
        }
    }
    
    // Suspend version for async delete
    suspend fun deleteAllLeadsSuspend() {
        val existingLeads = repository.getAllLeadsList()
        repository.deleteAllLeads()
        existingLeads.forEach { followUpNotificationScheduler.cancelLead(it) }
    }
    
    fun getStats(): LeadStats {
        return runBlocking {
            repository.getLeadStats()
        }
    }
    
    // Suspend version for async calls
    suspend fun getStatsSuspend(): LeadStats {
        return repository.getLeadStats()
    }
    
    // Follow-up operations
    fun addFollowUp(followUp: FollowUp) {
        runBlocking(Dispatchers.IO) {
            val previousLead = repository.getLeadById(followUp.leadId)
            repository.insertFollowUp(followUp)
            val updatedLead = repository.getLeadById(followUp.leadId)
            if (updatedLead != null) {
                followUpNotificationScheduler.syncLead(
                    previousLead = previousLead,
                    updatedLead = updatedLead,
                    showConfirmation = true
                )
            }
        }
    }
    
    fun updateFollowUp(followUp: FollowUp) {
        runBlocking(Dispatchers.IO) {
            val previousLead = repository.getLeadById(followUp.leadId)
            repository.updateFollowUp(followUp)
            val updatedLead = repository.getLeadById(followUp.leadId)
            if (updatedLead != null) {
                followUpNotificationScheduler.syncLead(
                    previousLead = previousLead,
                    updatedLead = updatedLead,
                    showConfirmation = true
                )
            }
        }
    }
    
    fun deleteFollowUp(id: String) {
        runBlocking(Dispatchers.IO) {
            val existingLeads = repository.getAllLeadsList()
            repository.deleteFollowUp(id)
            existingLeads.firstOrNull { lead -> lead.followUps.any { it.id == id } }?.let { previousLead ->
                val updatedLead = repository.getLeadById(previousLead.id)
                if (updatedLead != null) {
                    followUpNotificationScheduler.syncLead(
                        previousLead = previousLead,
                        updatedLead = updatedLead,
                        showConfirmation = false
                    )
                } else {
                    followUpNotificationScheduler.cancelFollowUp(id)
                }
            } ?: followUpNotificationScheduler.cancelFollowUp(id)
        }
    }
    
    fun getTodayFollowUps(): List<Pair<FollowUp, Lead>> {
        return runBlocking {
            repository.getTodayFollowUps()
        }
    }
    
    fun getUpcomingFollowUps(): List<Pair<FollowUp, Lead>> {
        return runBlocking {
            repository.getUpcomingFollowUps()
        }
    }
    
    fun getOverdueFollowUps(): List<Pair<FollowUp, Lead>> {
        return runBlocking {
            repository.getOverdueFollowUps()
        }
    }

    fun isFollowUpReminderEnabled(): Boolean {
        return followUpNotificationScheduler.isReminderEnabled()
    }

    fun setFollowUpReminderEnabled(enabled: Boolean) {
        followUpNotificationScheduler.setReminderEnabled(enabled)
        CoroutineScope(Dispatchers.IO).launch {
            if (enabled) {
                followUpNotificationScheduler.rescheduleAllFromDatabase()
            } else {
                followUpNotificationScheduler.cancelAllFromDatabase()
            }
        }
    }

    fun resyncFollowUpNotifications() {
        CoroutineScope(Dispatchers.IO).launch {
            followUpNotificationScheduler.rescheduleAllFromDatabase()
        }
    }
    
    // Tag Management (keeping in SharedPreferences for simplicity)
    fun getAllTags(): List<String> {
        return try {
            val json = prefs.getString("tags_list", "[]") ?: "[]"
            com.google.gson.Gson().fromJson(json, Array<String>::class.java)?.toList() ?: getDefaultTags()
        } catch (e: Exception) {
            getDefaultTags()
        }
    }
    
    fun addTag(tag: String) {
        val tags = getAllTags().toMutableList()
        if (!tags.contains(tag)) {
            tags.add(tag)
            saveTags(tags)
        }
    }
    
    fun deleteTag(tag: String) {
        val tags = getAllTags().toMutableList()
        tags.remove(tag)
        saveTags(tags)
    }
    
    private fun saveTags(tags: List<String>) {
        val json = com.google.gson.Gson().toJson(tags)
        prefs.edit().putString("tags_list", json).apply()
    }
    
    private fun getDefaultTags(): List<String> {
        return listOf("Hot Lead", "Premium", "Follow-up", "Ready to Buy", "VIP", "Bulk Order")
    }
    
    // Source Management (keeping in SharedPreferences for simplicity)
    fun getAllSources(): List<String> {
        return try {
            val json = prefs.getString("sources_list", "[]") ?: "[]"
            com.google.gson.Gson().fromJson(json, Array<String>::class.java)?.toList() ?: getDefaultSources()
        } catch (e: Exception) {
            getDefaultSources()
        }
    }
    
    fun addSource(source: String) {
        val sources = getAllSources().toMutableList()
        if (!sources.contains(source)) {
            sources.add(source)
            saveSources(sources)
        }
    }
    
    fun deleteSource(source: String) {
        val sources = getAllSources().toMutableList()
        sources.remove(source)
        saveSources(sources)
    }
    
    private fun saveSources(sources: List<String>) {
        val json = com.google.gson.Gson().toJson(sources)
        prefs.edit().putString("sources_list", json).apply()
    }
    
    private fun getDefaultSources(): List<String> {
        return listOf("WhatsApp", "Facebook", "Instagram", "Website", "Referral", "Cold Call")
    }
    
    // Product Management (V2 - Full Product Objects)
    fun getAllProductsV2(): List<Product> {
        return runBlocking {
            repository.getAllProductsList()
        }
    }
    
    fun getAllProductsV2Flow(): Flow<List<Product>> {
        return repository.getAllProducts()
    }
    
    fun addProductV2(product: Product) {
        runBlocking(Dispatchers.IO) {
            repository.insertProduct(product)
        }
    }
    
    fun updateProductV2(product: Product) {
        runBlocking(Dispatchers.IO) {
            repository.updateProduct(product)
        }
    }
    
    fun deleteProductV2(id: String) {
        runBlocking(Dispatchers.IO) {
            repository.deleteProduct(id)
        }
    }
    
    fun getProductByName(name: String): Product? {
        return getAllProductsV2().find { it.name == name }
    }
    
    // Product Field Settings Management
    fun getProductFieldSettings(): ProductFieldSettings {
        return try {
            val json = prefs.getString(KEY_FIELD_SETTINGS, null)
            if (json != null) {
                com.google.gson.Gson().fromJson(json, ProductFieldSettings::class.java) ?: ProductFieldSettings()
            } else {
                ProductFieldSettings() // Return default settings
            }
        } catch (e: Exception) {
            ProductFieldSettings()
        }
    }
    
    fun saveProductFieldSettings(settings: ProductFieldSettings) {
        val json = com.google.gson.Gson().toJson(settings)
        prefs.edit().putString(KEY_FIELD_SETTINGS, json).apply()
    }
    
    // Legacy Product Management (for backward compatibility)
    fun getAllProducts(): List<String> {
        return try {
            val products = getAllProductsV2()
            if (products.isNotEmpty()) {
                products.map { it.name }
            } else {
                getDefaultProducts()
            }
        } catch (e: Exception) {
            getDefaultProducts()
        }
    }
    
    fun addProduct(product: String) {
        // Convert to Product object and add to Room database
        val productObj = Product(
            id = java.util.UUID.randomUUID().toString(),
            name = product,
            type = ProductType.SERVICE,
            category = "General",
            description = "Added from legacy system"
        )
        addProductV2(productObj)
    }
    
    fun deleteProduct(product: String) {
        // Find product by name and delete
        runBlocking(Dispatchers.IO) {
            val products = repository.getAllProductsList()
            val productToDelete = products.find { it.name == product }
            if (productToDelete != null) {
                repository.deleteProduct(productToDelete.id)
            }
        }
    }
    
    private fun getDefaultProducts(): List<String> {
        return listOf("Product A", "Product B", "Service Package", "Premium Plan", "Basic Plan")
    }
    
    // Initialize default data
    fun generateSampleData() {
        runBlocking(Dispatchers.IO) {
            // Initialize default tags and sources
            if (getAllTags().isEmpty()) {
                saveTags(getDefaultTags())
            }
            if (getAllSources().isEmpty()) {
                saveSources(getDefaultSources())
            }
            
            // Generate sample data in Room database
            repository.generateSampleData()
        }
    }
    
    // ==================== Chat Message Operations ====================
    
    fun getChatMessagesForLead(leadId: String): List<com.message.bulksend.leadmanager.database.entities.ChatMessageEntity> {
        return runBlocking {
            repository.getChatMessagesForLeadList(leadId)
        }
    }
    
    fun addChatMessage(message: com.message.bulksend.leadmanager.database.entities.ChatMessageEntity) {
        runBlocking(Dispatchers.IO) {
            repository.insertChatMessage(message)
        }
    }
    
    fun getMessageCountForLead(leadId: String): Int {
        return runBlocking {
            repository.getMessageCountForLead(leadId)
        }
    }
    
    fun markMessagesAsRead(leadId: String) {
        runBlocking(Dispatchers.IO) {
            repository.markMessagesAsRead(leadId)
        }
    }
    
    // ==================== Auto-Add Settings Operations ====================
    
    fun getAutoAddSettings(): com.message.bulksend.leadmanager.database.entities.AutoAddSettingsEntity? {
        return runBlocking {
            repository.getAutoAddSettings()
        }
    }
    
    fun saveAutoAddSettings(settings: com.message.bulksend.leadmanager.database.entities.AutoAddSettingsEntity) {
        runBlocking(Dispatchers.IO) {
            repository.saveAutoAddSettings(settings)
        }
    }
    
    fun getAllAutoAddKeywordRules(): List<com.message.bulksend.leadmanager.database.entities.AutoAddKeywordRuleEntity> {
        return runBlocking {
            repository.getAllAutoAddKeywordRules()
        }
    }
    
    fun addAutoAddKeywordRule(rule: com.message.bulksend.leadmanager.database.entities.AutoAddKeywordRuleEntity) {
        runBlocking(Dispatchers.IO) {
            repository.insertAutoAddKeywordRule(rule)
        }
    }
    
    fun updateAutoAddKeywordRule(rule: com.message.bulksend.leadmanager.database.entities.AutoAddKeywordRuleEntity) {
        runBlocking(Dispatchers.IO) {
            repository.updateAutoAddKeywordRule(rule)
        }
    }
    
    fun deleteAutoAddKeywordRule(id: String) {
        runBlocking(Dispatchers.IO) {
            repository.deleteAutoAddKeywordRule(id)
        }
    }
    
    // ==================== Lead Import Operations ====================
    
    fun getLeadByPhone(phone: String): Lead? {
        return runBlocking {
            repository.getLeadByPhone(phone)
        }
    }
    
    fun addLeads(leads: List<Lead>): Int {
        return runBlocking(Dispatchers.IO) {
            if (!isPremiumPlanActive()) {
                val currentCount = repository.getLeadStats().totalLeads
                val availableSlots = (FREE_LEAD_LIMIT - currentCount).coerceAtLeast(0)
                if (availableSlots <= 0) return@runBlocking 0

                val leadsToAdd = leads.take(availableSlots)
                if (leadsToAdd.isNotEmpty()) {
                    repository.insertLeads(leadsToAdd)
                }
                return@runBlocking leadsToAdd.size
            }

            repository.insertLeads(leads)
            return@runBlocking leads.size
        }
    }
    
    /**
     * Add lead from AutoRespond message
     * Checks if lead already exists, if not creates new one
     * Returns the lead (existing or new)
     */
    fun addLeadFromAutoRespond(
        senderName: String,
        senderPhone: String,
        messageText: String,
        matchedKeyword: String? = null
    ): Lead? {
        // Check if lead already exists
        val existingLead = getLeadByPhone(senderPhone)
        if (existingLead != null) {
            // Update last message
            val updatedLead = existingLead.copy(
                lastMessage = messageText,
                timestamp = System.currentTimeMillis()
            )
            updateLead(updatedLead)
            return updatedLead
        }
        
        // Get auto-add settings
        val settings = getAutoAddSettings()
        
        // Create new lead
        val newLead = Lead(
            id = java.util.UUID.randomUUID().toString(),
            name = senderName.ifBlank { "Unknown" },
            phoneNumber = senderPhone,
            status = LeadStatus.valueOf(settings?.defaultStatus ?: "NEW"),
            source = settings?.defaultSource ?: "WhatsApp",
            lastMessage = messageText,
            timestamp = System.currentTimeMillis(),
            category = settings?.defaultCategory ?: "AutoRespond",
            notes = if (matchedKeyword != null) "Matched keyword: $matchedKeyword" else "",
            priority = LeadPriority.MEDIUM,
            tags = try {
                if (!settings?.defaultTags.isNullOrBlank()) {
                    com.google.gson.Gson().fromJson(settings?.defaultTags, Array<String>::class.java)?.toList() ?: emptyList()
                } else emptyList()
            } catch (e: Exception) { emptyList() }
        )
        
        val inserted = addLead(newLead)
        return if (inserted) newLead else null
    }
    
    /**
     * Check if message matches any auto-add keyword rule
     */
    fun checkAutoAddKeywordMatch(messageText: String): com.message.bulksend.leadmanager.database.entities.AutoAddKeywordRuleEntity? {
        val rules = getAllAutoAddKeywordRules()
        
        for (rule in rules) {
            val matches = when (rule.matchType) {
                com.message.bulksend.leadmanager.database.entities.KeywordMatchType.EXACT -> 
                    messageText.equals(rule.keyword, ignoreCase = true)
                com.message.bulksend.leadmanager.database.entities.KeywordMatchType.CONTAINS -> 
                    messageText.contains(rule.keyword, ignoreCase = true)
                com.message.bulksend.leadmanager.database.entities.KeywordMatchType.STARTS_WITH -> 
                    messageText.startsWith(rule.keyword, ignoreCase = true)
                com.message.bulksend.leadmanager.database.entities.KeywordMatchType.ENDS_WITH -> 
                    messageText.endsWith(rule.keyword, ignoreCase = true)
                com.message.bulksend.leadmanager.database.entities.KeywordMatchType.REGEX -> 
                    try { Regex(rule.keyword, RegexOption.IGNORE_CASE).containsMatchIn(messageText) } catch (e: Exception) { false }
            }
            
            if (matches) return rule
        }
        
        return null
    }
    
    // ==================== Timeline Operations ====================
    
    fun getTimelineEntries(leadId: String): List<TimelineEntry> {
        return runBlocking {
            repository.getTimelineEntriesForLead(leadId)
        }
    }
    
    fun addTimelineEntry(entry: TimelineEntry) {
        runBlocking(Dispatchers.IO) {
            repository.insertTimelineEntry(entry)
        }
    }
    
    // ==================== Batch Import Operations ====================
    
    /**
     * Import leads in batches for large datasets
     * Runs on background thread, updates progress via StateFlow
     */
    suspend fun importLeadsBatch(
        leads: List<Lead>,
        onProgress: ((ImportProgress) -> Unit)? = null
    ): ImportProgress {
        return withContext(Dispatchers.IO) {
            var imported = 0
            var failed = 0
            val total = leads.size
            
            try {
                // Process in batches
                leads.chunked(BATCH_SIZE).forEach { batch ->
                    try {
                        repository.insertLeads(batch)
                        imported += batch.size
                    } catch (e: Exception) {
                        failed += batch.size
                    }
                    
                    // Update progress
                    val progress = ImportProgress(total, imported, failed)
                    _importProgress.value = progress
                    onProgress?.invoke(progress)
                }
                
                // Update last sync time
                updateLastSyncTime()
                
                val finalProgress = ImportProgress(total, imported, failed, isComplete = true)
                _importProgress.value = finalProgress
                finalProgress
            } catch (e: Exception) {
                val errorProgress = ImportProgress(total, imported, failed, isComplete = true, errorMessage = e.message)
                _importProgress.value = errorProgress
                errorProgress
            }
        }
    }
    
    /**
     * Clear import progress
     */
    fun clearImportProgress() {
        _importProgress.value = null
    }
    
    // ==================== Optimized Data Loading ====================
    
    /**
     * Get leads count without loading all data
     */
    suspend fun getLeadsCountSuspend(): Int {
        return withContext(Dispatchers.IO) {
            repository.getLeadStats().totalLeads
        }
    }
    
    /**
     * Get paginated leads for large datasets
     */
    suspend fun getLeadsPaginated(page: Int, pageSize: Int = 50): List<Lead> {
        return withContext(Dispatchers.IO) {
            val allLeads = repository.getAllLeadsList()
            val startIndex = page * pageSize
            val endIndex = minOf(startIndex + pageSize, allLeads.size)
            if (startIndex < allLeads.size) {
                allLeads.subList(startIndex, endIndex)
            } else {
                emptyList()
            }
        }
    }
}
