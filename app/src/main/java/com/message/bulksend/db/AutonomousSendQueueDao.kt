package com.message.bulksend.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AutonomousSendQueueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<AutonomousSendQueueEntity>)

    @Query("SELECT * FROM autonomous_send_queue WHERE campaignId = :campaignId ORDER BY plannedTimeMillis ASC")
    suspend fun getAllForCampaign(campaignId: String): List<AutonomousSendQueueEntity>

    @Query(
        "SELECT * FROM autonomous_send_queue WHERE campaignId = :campaignId AND status = 'queued' ORDER BY plannedTimeMillis ASC"
    )
    suspend fun getQueuedForCampaign(campaignId: String): List<AutonomousSendQueueEntity>

    @Query(
        "SELECT * FROM autonomous_send_queue WHERE campaignId = :campaignId AND status = 'queued' ORDER BY plannedTimeMillis ASC LIMIT 1"
    )
    suspend fun getNextQueued(campaignId: String): AutonomousSendQueueEntity?

    @Query(
        "UPDATE autonomous_send_queue SET status = :status, retryCount = :retryCount, lastError = :lastError, sentTimeMillis = :sentTimeMillis WHERE id = :id"
    )
    suspend fun updateDeliveryStatus(
        id: Long,
        status: String,
        retryCount: Int,
        lastError: String?,
        sentTimeMillis: Long?
    )

    @Query(
        "UPDATE autonomous_send_queue SET status = 'queued', retryCount = :retryCount, plannedTimeMillis = :plannedTimeMillis, hourOfDay = :hourOfDay, lastError = :lastError WHERE id = :id"
    )
    suspend fun requeueWithNewPlan(
        id: Long,
        retryCount: Int,
        plannedTimeMillis: Long,
        hourOfDay: Int,
        lastError: String?
    )

    @Query("SELECT COUNT(*) FROM autonomous_send_queue WHERE campaignId = :campaignId AND status = :status")
    suspend fun countByStatus(campaignId: String, status: String): Int

    @Query("SELECT MIN(plannedTimeMillis) FROM autonomous_send_queue WHERE campaignId = :campaignId AND status = 'queued'")
    suspend fun getNextSendTime(campaignId: String): Long?

    @Query(
        "SELECT COUNT(*) FROM autonomous_send_queue WHERE campaignId = :campaignId AND status = 'sent' AND sentTimeMillis BETWEEN :dayStart AND :dayEnd"
    )
    suspend fun getSentTodayCount(campaignId: String, dayStart: Long, dayEnd: Long): Int

    @Query(
        "SELECT COUNT(*) FROM autonomous_send_queue WHERE campaignId = :campaignId AND status = 'queued' AND plannedTimeMillis BETWEEN :dayStart AND :dayEnd"
    )
    suspend fun getQueuedTodayCount(campaignId: String, dayStart: Long, dayEnd: Long): Int

    @Query("DELETE FROM autonomous_send_queue WHERE campaignId = :campaignId")
    suspend fun deleteForCampaign(campaignId: String)
}
