package com.message.bulksend.leadmanager.database.dao

import androidx.room.*
import com.message.bulksend.leadmanager.database.entities.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    
    @Query("SELECT * FROM chat_messages WHERE leadId = :leadId ORDER BY timestamp DESC")
    fun getChatMessagesForLead(leadId: String): Flow<List<ChatMessageEntity>>
    
    @Query("SELECT * FROM chat_messages WHERE leadId = :leadId ORDER BY timestamp DESC")
    suspend fun getChatMessagesForLeadList(leadId: String): List<ChatMessageEntity>
    
    @Query("SELECT * FROM chat_messages WHERE senderPhone = :phone ORDER BY timestamp DESC")
    suspend fun getChatMessagesByPhone(phone: String): List<ChatMessageEntity>
    
    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int = 100): List<ChatMessageEntity>
    
    @Query("SELECT * FROM chat_messages WHERE isAutoReply = 1 ORDER BY timestamp DESC")
    suspend fun getAutoRepliedMessages(): List<ChatMessageEntity>
    
    @Query("SELECT COUNT(*) FROM chat_messages WHERE leadId = :leadId")
    suspend fun getMessageCountForLead(leadId: String): Int
    
    @Query("SELECT * FROM chat_messages WHERE messageText LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    suspend fun searchMessages(query: String): List<ChatMessageEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)
    
    @Update
    suspend fun updateMessage(message: ChatMessageEntity)
    
    @Delete
    suspend fun deleteMessage(message: ChatMessageEntity)
    
    @Query("DELETE FROM chat_messages WHERE leadId = :leadId")
    suspend fun deleteMessagesForLead(leadId: String)
    
    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllMessages()
    
    @Query("UPDATE chat_messages SET isRead = 1 WHERE leadId = :leadId")
    suspend fun markAllAsRead(leadId: String)
    
    @Query("SELECT COUNT(*) FROM chat_messages WHERE leadId = :leadId AND isRead = 0")
    suspend fun getUnreadCount(leadId: String): Int
    
    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC")
    suspend fun getAllMessagesList(): List<ChatMessageEntity>
    
    @Query("SELECT * FROM chat_messages WHERE id = :id")
    suspend fun getMessageById(id: String): ChatMessageEntity?
}
