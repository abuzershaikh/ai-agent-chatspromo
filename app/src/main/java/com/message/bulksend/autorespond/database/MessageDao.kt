package com.message.bulksend.autorespond.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for message tracking
 */
@Dao
interface MessageDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long
    
    @Update
    suspend fun updateMessage(message: MessageEntity)
    
    @Query("SELECT * FROM message_logs ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<MessageEntity>>
    
    @Query("SELECT * FROM message_logs WHERE phoneNumber = :phoneNumber ORDER BY timestamp DESC")
    fun getMessagesByPhone(phoneNumber: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM message_logs WHERE phoneNumber = :phoneNumber ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessagesByPhoneSync(phoneNumber: String, limit: Int): List<MessageEntity>
    
    @Query("SELECT * FROM message_logs WHERE notificationKey = :notificationKey LIMIT 1")
    suspend fun getMessageByNotificationKey(notificationKey: String): MessageEntity?
    
    @Query("SELECT * FROM message_logs WHERE phoneNumber = :phoneNumber AND incomingMessage = :incomingMessage ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessageByPhoneAndText(phoneNumber: String, incomingMessage: String): MessageEntity?
    
    @Query("SELECT * FROM message_logs WHERE phoneNumber = :phoneNumber AND incomingMessage = :incomingMessage AND status = 'SENT' AND timestamp > :afterTime ORDER BY timestamp DESC LIMIT 1")
    suspend fun getRecentSentMessageByPhoneAndText(phoneNumber: String, incomingMessage: String, afterTime: Long): MessageEntity?
    
    @Query("UPDATE message_logs SET status = :status, outgoingMessage = :outgoingMessage WHERE id = :id")
    suspend fun updateMessageStatus(id: Int, status: String, outgoingMessage: String)
    
    @Query("SELECT COUNT(*) FROM message_logs")
    suspend fun getMessageCount(): Int
    
    @Query("DELETE FROM message_logs WHERE timestamp < :timestamp")
    suspend fun deleteOldMessages(timestamp: Long)
    
    @Query("DELETE FROM message_logs")
    suspend fun deleteAllMessages()
    
    // ==================== Instagram Statistics Queries ====================
    
    @Query("SELECT COUNT(*) FROM message_logs WHERE phoneNumber LIKE 'instagram_%'")
    suspend fun getInstagramMessageCount(): Int
    
    @Query("SELECT COUNT(*) FROM message_logs WHERE phoneNumber LIKE 'instagram_%' AND status = :status")
    suspend fun getInstagramRepliesByStatus(status: String): Int
    
    @Query("SELECT * FROM message_logs WHERE phoneNumber LIKE 'instagram_%' ORDER BY timestamp DESC")
    fun getInstagramMessages(): Flow<List<MessageEntity>>
}
