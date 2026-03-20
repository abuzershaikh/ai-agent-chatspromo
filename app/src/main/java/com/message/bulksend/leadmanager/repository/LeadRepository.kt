package com.message.bulksend.leadmanager.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.message.bulksend.leadmanager.database.LeadManagerDatabase
import com.message.bulksend.leadmanager.database.entities.FollowUpEntity
import com.message.bulksend.leadmanager.database.entities.LeadEntity
import com.message.bulksend.leadmanager.database.entities.ProductEntity
import com.message.bulksend.leadmanager.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.*

class LeadRepository(context: Context) {
    
    private val database = LeadManagerDatabase.getDatabase(context)
    private val leadDao = database.leadDao()
    private val followUpDao = database.followUpDao()
    private val productDao = database.productDao()
    private val chatMessageDao = database.chatMessageDao()
    private val autoAddSettingsDao = database.autoAddSettingsDao()
    private val gson = Gson()
    
    // Lead operations
    fun getAllLeads(): Flow<List<Lead>> {
        return leadDao.getAllLeads().map { entities ->
            entities.map { entity -> entity.toLead(getFollowUpsByLeadId(entity.id)) }
        }
    }
    
    suspend fun getAllLeadsList(): List<Lead> {
        val entities = leadDao.getAllLeadsList()
        return entities.map { entity -> 
            entity.toLead(followUpDao.getFollowUpsByLeadId(entity.id).map { it.toFollowUp() })
        }
    }
    
    suspend fun getLeadsByStatus(status: LeadStatus): List<Lead> {
        val entities = leadDao.getLeadsByStatus(status)
        return entities.map { entity -> 
            entity.toLead(followUpDao.getFollowUpsByLeadId(entity.id).map { it.toFollowUp() })
        }
    }
    
    suspend fun getLeadById(id: String): Lead? {
        val entity = leadDao.getLeadById(id) ?: return null
        val followUps = followUpDao.getFollowUpsByLeadId(id).map { it.toFollowUp() }
        return entity.toLead(followUps)
    }
    
    suspend fun insertLead(lead: Lead) {
        val entity = lead.toEntity()
        leadDao.insertLead(entity)
        
        // Insert follow-ups
        val followUpEntities = lead.followUps.map { it.toEntity() }
        if (followUpEntities.isNotEmpty()) {
            followUpDao.insertFollowUps(followUpEntities)
        }
    }
    
    suspend fun updateLead(lead: Lead) {
        val entity = lead.toEntity()
        leadDao.updateLead(entity)
        
        // Delete existing follow-ups and insert new ones
        followUpDao.deleteFollowUpsByLeadId(lead.id)
        val followUpEntities = lead.followUps.map { it.toEntity() }
        if (followUpEntities.isNotEmpty()) {
            followUpDao.insertFollowUps(followUpEntities)
        }
    }
    
    suspend fun deleteLead(id: String) {
        leadDao.deleteLeadById(id)
        // Follow-ups will be deleted automatically due to foreign key cascade
    }
    
    suspend fun deleteAllLeads() {
        leadDao.deleteAllLeads()
        // Follow-ups will be deleted automatically due to foreign key cascade
    }
    
    suspend fun searchLeads(query: String): List<Lead> {
        val entities = leadDao.searchLeads(query)
        return entities.map { entity -> 
            entity.toLead(followUpDao.getFollowUpsByLeadId(entity.id).map { it.toFollowUp() })
        }
    }
    
    // Follow-up operations
    suspend fun insertFollowUp(followUp: FollowUp) {
        followUpDao.insertFollowUp(followUp.toEntity())
        
        // Update lead's next follow-up date if this is the earliest pending follow-up
        updateLeadNextFollowUpDate(followUp.leadId)
    }
    
    suspend fun updateFollowUp(followUp: FollowUp) {
        followUpDao.updateFollowUp(followUp.toEntity())
        updateLeadNextFollowUpDate(followUp.leadId)
    }
    
    suspend fun deleteFollowUp(id: String) {
        val followUp = followUpDao.getFollowUpById(id)
        if (followUp != null) {
            followUpDao.deleteFollowUpById(id)
            updateLeadNextFollowUpDate(followUp.leadId)
        }
    }
    
    suspend fun getFollowUpsByLeadId(leadId: String): List<FollowUp> {
        return followUpDao.getFollowUpsByLeadId(leadId).map { it.toFollowUp() }
    }
    
    suspend fun getTodayFollowUps(): List<Pair<FollowUp, Lead>> {
        val followUps = followUpDao.getTodayFollowUps()
        return followUps.mapNotNull { followUpEntity ->
            val lead = leadDao.getLeadById(followUpEntity.leadId)
            if (lead != null) {
                followUpEntity.toFollowUp() to lead.toLead(emptyList())
            } else null
        }
    }
    
    suspend fun getUpcomingFollowUps(): List<Pair<FollowUp, Lead>> {
        val followUps = followUpDao.getUpcomingFollowUps()
        return followUps.mapNotNull { followUpEntity ->
            val lead = leadDao.getLeadById(followUpEntity.leadId)
            if (lead != null) {
                followUpEntity.toFollowUp() to lead.toLead(emptyList())
            } else null
        }
    }
    
    suspend fun getOverdueFollowUps(): List<Pair<FollowUp, Lead>> {
        val followUps = followUpDao.getOverdueFollowUps()
        return followUps.mapNotNull { followUpEntity ->
            val lead = leadDao.getLeadById(followUpEntity.leadId)
            if (lead != null) {
                followUpEntity.toFollowUp() to lead.toLead(emptyList())
            } else null
        }
    }
    
    // Product operations
    fun getAllProducts(): Flow<List<Product>> {
        return productDao.getAllProducts().map { entities ->
            entities.map { it.toProduct() }
        }
    }
    
    suspend fun getAllProductsList(): List<Product> {
        return productDao.getAllProductsList().map { it.toProduct() }
    }
    
    suspend fun insertProduct(product: Product) {
        productDao.insertProduct(product.toEntity())
    }
    
    suspend fun updateProduct(product: Product) {
        productDao.updateProduct(product.toEntity())
    }
    
    suspend fun deleteProduct(id: String) {
        productDao.deleteProductById(id)
    }
    
    // Stats
    suspend fun getLeadStats(): LeadStats {
        val total = leadDao.getTotalLeadsCount()
        val new = leadDao.getLeadsCountByStatus(LeadStatus.NEW)
        val interested = leadDao.getLeadsCountByStatus(LeadStatus.INTERESTED)
        val contacted = leadDao.getLeadsCountByStatus(LeadStatus.CONTACTED)
        val qualified = leadDao.getLeadsCountByStatus(LeadStatus.QUALIFIED)
        val converted = leadDao.getLeadsCountByStatus(LeadStatus.CONVERTED)
        val conversionRate = if (total > 0) (converted.toFloat() / total) * 100 else 0f
        
        return LeadStats(total, new, interested, contacted, qualified, converted, conversionRate)
    }
    
    // Helper methods
    private suspend fun updateLeadNextFollowUpDate(leadId: String) {
        val pendingFollowUps = followUpDao.getFollowUpsByLeadId(leadId).filter { !it.isCompleted }
        val nextFollowUpDate = pendingFollowUps.minByOrNull { it.scheduledDate }?.scheduledDate
        
        val lead = leadDao.getLeadById(leadId)
        if (lead != null) {
            val updatedLead = lead.copy(
                nextFollowUpDate = nextFollowUpDate,
                isFollowUpCompleted = pendingFollowUps.isEmpty()
            )
            leadDao.updateLead(updatedLead)
        }
    }
    
    // Chat Message operations
    fun getChatMessagesForLead(leadId: String): Flow<List<com.message.bulksend.leadmanager.database.entities.ChatMessageEntity>> {
        return chatMessageDao.getChatMessagesForLead(leadId)
    }
    
    suspend fun getChatMessagesForLeadList(leadId: String): List<com.message.bulksend.leadmanager.database.entities.ChatMessageEntity> {
        return chatMessageDao.getChatMessagesForLeadList(leadId)
    }
    
    suspend fun insertChatMessage(message: com.message.bulksend.leadmanager.database.entities.ChatMessageEntity) {
        chatMessageDao.insertMessage(message)
    }
    
    suspend fun getMessageCountForLead(leadId: String): Int {
        return chatMessageDao.getMessageCountForLead(leadId)
    }
    
    suspend fun markMessagesAsRead(leadId: String) {
        chatMessageDao.markAllAsRead(leadId)
    }
    
    suspend fun getUnreadMessageCount(leadId: String): Int {
        return chatMessageDao.getUnreadCount(leadId)
    }
    
    // Auto-Add Settings operations
    suspend fun getAutoAddSettings(): com.message.bulksend.leadmanager.database.entities.AutoAddSettingsEntity? {
        return autoAddSettingsDao.getSettings()
    }
    
    fun getAutoAddSettingsFlow(): Flow<com.message.bulksend.leadmanager.database.entities.AutoAddSettingsEntity?> {
        return autoAddSettingsDao.getSettingsFlow()
    }
    
    suspend fun saveAutoAddSettings(settings: com.message.bulksend.leadmanager.database.entities.AutoAddSettingsEntity) {
        autoAddSettingsDao.insertSettings(settings)
    }
    
    suspend fun getAllAutoAddKeywordRules(): List<com.message.bulksend.leadmanager.database.entities.AutoAddKeywordRuleEntity> {
        return autoAddSettingsDao.getAllActiveRulesList()
    }
    
    fun getAllAutoAddKeywordRulesFlow(): Flow<List<com.message.bulksend.leadmanager.database.entities.AutoAddKeywordRuleEntity>> {
        return autoAddSettingsDao.getAllActiveRules()
    }
    
    suspend fun insertAutoAddKeywordRule(rule: com.message.bulksend.leadmanager.database.entities.AutoAddKeywordRuleEntity) {
        autoAddSettingsDao.insertRule(rule)
    }
    
    suspend fun updateAutoAddKeywordRule(rule: com.message.bulksend.leadmanager.database.entities.AutoAddKeywordRuleEntity) {
        autoAddSettingsDao.updateRule(rule)
    }
    
    suspend fun deleteAutoAddKeywordRule(id: String) {
        autoAddSettingsDao.deleteRuleById(id)
    }
    
    // Check if lead exists by phone
    suspend fun getLeadByPhone(phone: String): Lead? {
        val normalizedPhone = phone.replace(Regex("[^0-9+]"), "")
        val entity = leadDao.getLeadByPhone(normalizedPhone)
        return entity?.toLead(emptyList())
    }
    
    // Bulk insert leads
    suspend fun insertLeads(leads: List<Lead>) {
        val entities = leads.map { it.toEntity() }
        leadDao.insertLeads(entities)
    }
    
    // Sample data generation - disabled, only real data
    suspend fun generateSampleData() {
        // No sample data - only real leads will be shown
    }
    
    // Timeline operations
    suspend fun getTimelineEntriesForLead(leadId: String): List<TimelineEntry> {
        // For now, generate sample timeline entries
        // In a real app, you'd have a TimelineDao and TimelineEntity
        return listOf(
            TimelineEntry(
                id = "1",
                leadId = leadId,
                eventType = com.message.bulksend.leadmanager.database.entities.TimelineEventType.LEAD_CREATED,
                title = "Lead Created",
                description = "New lead added to system",
                timestamp = System.currentTimeMillis() - 86400000,
                imageUri = null
            ),
            TimelineEntry(
                id = "2", 
                leadId = leadId,
                eventType = com.message.bulksend.leadmanager.database.entities.TimelineEventType.STATUS_CHANGED,
                title = "Status Changed",
                description = "Status changed from 'New' to 'Qualified'",
                timestamp = System.currentTimeMillis() - 43200000,
                imageUri = null
            ),
            TimelineEntry(
                id = "3",
                leadId = leadId,
                eventType = com.message.bulksend.leadmanager.database.entities.TimelineEventType.FOLLOWUP_SCHEDULED,
                title = "Follow-up Scheduled", 
                description = "Meeting scheduled for tomorrow",
                timestamp = System.currentTimeMillis(),
                imageUri = null
            )
        )
    }

    suspend fun insertTimelineEntry(entry: TimelineEntry) {
        // TODO: Implement when TimelineDao is created
    }
}

// Extension functions for entity conversion
private fun LeadEntity.toLead(followUps: List<FollowUp>): Lead {
    return Lead(
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
        tags = if (tags.isNotEmpty()) {
            try {
                val gson = Gson()
                val listType = object : TypeToken<List<String>>() {}.type
                gson.fromJson(tags, listType) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else emptyList(),
        product = product,
        leadScore = leadScore,
        followUps = followUps,
        nextFollowUpDate = nextFollowUpDate,
        isFollowUpCompleted = isFollowUpCompleted
    )
}

private fun Lead.toEntity(): LeadEntity {
    val gson = Gson()
    return LeadEntity(
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
        tags = gson.toJson(tags),
        product = product,
        leadScore = leadScore,
        nextFollowUpDate = nextFollowUpDate,
        isFollowUpCompleted = isFollowUpCompleted
    )
}

private fun FollowUpEntity.toFollowUp(): FollowUp {
    return FollowUp(
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
}

private fun FollowUp.toEntity(): FollowUpEntity {
    return FollowUpEntity(
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
}

private fun ProductEntity.toProduct(): Product {
    return Product(
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
        serviceType = serviceType ?: ServiceType.ONLINE,
        duration = duration,
        deliveryTime = deliveryTime
    )
}

private fun Product.toEntity(): ProductEntity {
    return ProductEntity(
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
}