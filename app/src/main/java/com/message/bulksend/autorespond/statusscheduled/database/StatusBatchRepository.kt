package com.message.bulksend.autorespond.statusscheduled.database

import com.message.bulksend.autorespond.statusscheduled.models.BatchStatus
import com.message.bulksend.autorespond.statusscheduled.models.StatusBatch
import kotlinx.coroutines.flow.Flow

class StatusBatchRepository(private val dao: StatusBatchDao) {
    
    fun getAllBatches(): Flow<List<StatusBatch>> = dao.getAllBatches()
    
    suspend fun insertBatch(batch: StatusBatch): Long = dao.insertBatch(batch)
    
    suspend fun updateBatch(batch: StatusBatch) = dao.updateBatch(batch)
    
    suspend fun deleteBatch(batch: StatusBatch) = dao.deleteBatch(batch)
    
    suspend fun getBatchById(batchId: Long): StatusBatch? = dao.getBatchById(batchId)
    
    suspend fun getBatchesByStatus(status: BatchStatus): List<StatusBatch> = 
        dao.getBatchesByStatus(status)

    suspend fun getMaxBatchId(): Long = dao.getMaxBatchId()
    
    suspend fun getActiveBatchCount(): Int = dao.getActiveBatchCount()
    
    suspend fun deleteBatchById(batchId: Long) = dao.deleteBatchById(batchId)
    
    suspend fun updateBatchStatus(batchId: Long, status: BatchStatus) = 
        dao.updateBatchStatus(batchId, status)
    
    suspend fun canAddMoreBatches(): Boolean {
        return getActiveBatchCount() < 30
    }
}
