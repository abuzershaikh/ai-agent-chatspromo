package com.message.bulksend.autorespond.statusscheduled.database

import androidx.room.*
import com.message.bulksend.autorespond.statusscheduled.models.BatchStatus
import com.message.bulksend.autorespond.statusscheduled.models.StatusBatch
import kotlinx.coroutines.flow.Flow

@Dao
interface StatusBatchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(batch: StatusBatch): Long

    @Update suspend fun updateBatch(batch: StatusBatch)

    @Delete suspend fun deleteBatch(batch: StatusBatch)

    @Query("SELECT * FROM status_batches ORDER BY createdAt DESC")
    fun getAllBatches(): Flow<List<StatusBatch>>

    @Query("SELECT * FROM status_batches WHERE id = :batchId")
    suspend fun getBatchById(batchId: Long): StatusBatch?

    @Query("SELECT * FROM status_batches WHERE status = :status ORDER BY scheduledAt ASC")
    suspend fun getBatchesByStatus(status: BatchStatus): List<StatusBatch>

    @Query("SELECT IFNULL(MAX(id), 0) FROM status_batches")
    suspend fun getMaxBatchId(): Long

    @Query("SELECT COUNT(*) FROM status_batches WHERE status = 'DRAFT' OR status = 'SCHEDULED'")
    suspend fun getActiveBatchCount(): Int

    @Query("DELETE FROM status_batches WHERE id = :batchId")
    suspend fun deleteBatchById(batchId: Long)

    @Query("UPDATE status_batches SET status = :status WHERE id = :batchId")
    suspend fun updateBatchStatus(batchId: Long, status: BatchStatus)
}
