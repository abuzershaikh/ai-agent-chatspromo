package com.message.bulksend.aiagent.tools.agentspeech

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Agent Speech Settings
 */
@Dao
interface AgentSpeechSettingsDao {
    @Query("SELECT * FROM agent_speech_settings WHERE id = 1")
    fun getSettings(): Flow<AgentSpeechSettings?>
    
    @Query("SELECT * FROM agent_speech_settings WHERE id = 1")
    suspend fun getSettingsOnce(): AgentSpeechSettings?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: AgentSpeechSettings)
    
    @Query("UPDATE agent_speech_settings SET isEnabled = :enabled, updatedAt = :timestamp WHERE id = 1")
    suspend fun updateEnabled(enabled: Boolean, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE agent_speech_settings SET language = :language, updatedAt = :timestamp WHERE id = 1")
    suspend fun updateLanguage(language: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE agent_speech_settings SET voiceName = :voiceName, updatedAt = :timestamp WHERE id = 1")
    suspend fun updateVoice(voiceName: String, timestamp: Long = System.currentTimeMillis())
}

/**
 * DAO for Speech Queue
 */
@Dao
interface SpeechQueueDao {
    @Query("SELECT * FROM speech_queue WHERE status = 'PENDING' ORDER BY createdAt ASC")
    fun getPendingQueue(): Flow<List<SpeechQueueItem>>
    
    @Query("SELECT * FROM speech_queue WHERE status = 'PENDING' ORDER BY createdAt ASC LIMIT 1")
    suspend fun getNextPendingItem(): SpeechQueueItem?
    
    @Insert
    suspend fun addToQueue(item: SpeechQueueItem): Long
    
    @Query("UPDATE speech_queue SET status = :status, processedAt = :timestamp WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE speech_queue SET status = :status, audioPath = :audioPath, processedAt = :timestamp WHERE id = :id")
    suspend fun updateCompleted(id: Long, status: String, audioPath: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE speech_queue SET status = :status, errorMessage = :error, retryCount = retryCount + 1, processedAt = :timestamp WHERE id = :id")
    suspend fun updateFailed(id: Long, status: String, error: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("DELETE FROM speech_queue WHERE status = 'COMPLETED' AND processedAt < :timestamp")
    suspend fun cleanupOldCompleted(timestamp: Long)
    
    @Query("SELECT COUNT(*) FROM speech_queue WHERE status = 'PENDING'")
    fun getPendingCount(): Flow<Int>
}

/**
 * DAO for Speech History
 */
@Dao
interface SpeechHistoryDao {
    @Query("SELECT * FROM speech_history ORDER BY createdAt DESC LIMIT 50")
    fun getRecentHistory(): Flow<List<SpeechHistory>>
    
    @Insert
    suspend fun addHistory(history: SpeechHistory)
    
    @Query("DELETE FROM speech_history WHERE createdAt < :timestamp")
    suspend fun cleanupOldHistory(timestamp: Long)
    
    @Query("SELECT COUNT(*) FROM speech_history")
    fun getTotalCount(): Flow<Int>
}
