package com.message.bulksend.utils

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

/**
 * Background data processor for handling data-intensive operations
 * Implements queuing, batching, and parallel processing for optimal performance
 * Requirements: 2.3, 3.2
 */
object BackgroundDataProcessor {
    
    // Configuration constants
    private const val MAX_CONCURRENT_OPERATIONS = 4
    private const val BATCH_SIZE = 50
    private const val OPERATION_TIMEOUT = 30_000L
    private const val QUEUE_CAPACITY = 1000
    
    // Background processing dispatcher
    private val processingDispatcher = Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_OPERATIONS)
    
    // Operation queue and processing state
    private val operationQueue = ConcurrentLinkedQueue<BackgroundOperation<*>>()
    private val activeOperations = AtomicInteger(0)
    private val processedOperations = AtomicInteger(0)
    
    // Processing channels
    private val operationChannel = Channel<BackgroundOperation<*>>(QUEUE_CAPACITY)
    private val resultChannel = Channel<OperationResult<*>>(QUEUE_CAPACITY)
    
    // Processing job
    private var processingJob: Job? = null
    
    /**
     * Background operation definition
     */
    data class BackgroundOperation<T>(
        val id: String,
        val type: OperationType,
        val priority: Priority = Priority.NORMAL,
        val operation: suspend () -> T,
        val onSuccess: (T) -> Unit = {},
        val onError: (Exception) -> Unit = {},
        val timeout: Long = OPERATION_TIMEOUT
    )
    
    /**
     * Operation types for categorization and optimization
     */
    enum class OperationType {
        DATA_LOAD,
        DATA_SAVE,
        DATA_DELETE,
        DATA_MIGRATION,
        DATA_VALIDATION,
        CACHE_OPERATION,
        SORT_OPERATION
    }
    
    /**
     * Operation priority levels
     */
    enum class Priority(val value: Int) {
        LOW(1),
        NORMAL(2),
        HIGH(3),
        CRITICAL(4)
    }
    
    /**
     * Operation result wrapper
     */
    sealed class OperationResult<T> {
        data class Success<T>(val id: String, val data: T, val executionTime: Long) : OperationResult<T>()
        data class Error<T>(val id: String, val exception: Exception, val executionTime: Long) : OperationResult<T>()
        data class Timeout<T>(val id: String, val executionTime: Long) : OperationResult<T>()
    }
    
    /**
     * Processing statistics
     */
    data class ProcessingStats(
        val queueSize: Int,
        val activeOperations: Int,
        val processedOperations: Int,
        val averageExecutionTime: Double,
        val successRate: Double,
        val operationsByType: Map<OperationType, Int>
    )
    
    private val executionTimes = mutableListOf<Long>()
    private val operationCounts = mutableMapOf<OperationType, Int>()
    private val successCount = AtomicInteger(0)
    private val errorCount = AtomicInteger(0)
    
    /**
     * Initialize background processor
     */
    fun initialize() {
        if (processingJob?.isActive == true) return
        
        processingJob = CoroutineScope(processingDispatcher).launch {
            processOperations()
        }
    }
    
    /**
     * Shutdown background processor
     */
    fun shutdown() {
        processingJob?.cancel()
        operationChannel.close()
        resultChannel.close()
        operationQueue.clear()
    }
    
    /**
     * Submit operation for background processing
     */
    fun <T> submitOperation(operation: BackgroundOperation<T>): String {
        operationQueue.offer(operation)
        
        // Try to send to channel (non-blocking)
        operationChannel.trySend(operation as BackgroundOperation<*>)
        
        return operation.id
    }
    
    /**
     * Submit high-priority operation
     */
    fun <T> submitHighPriorityOperation(
        id: String,
        type: OperationType,
        operation: suspend () -> T,
        onSuccess: (T) -> Unit = {},
        onError: (Exception) -> Unit = {}
    ): String {
        return submitOperation(
            BackgroundOperation(
                id = id,
                type = type,
                priority = Priority.HIGH,
                operation = operation,
                onSuccess = onSuccess,
                onError = onError
            )
        )
    }
    
    /**
     * Submit batch operations
     */
    fun <T> submitBatchOperations(
        operations: List<BackgroundOperation<T>>,
        batchSize: Int = BATCH_SIZE
    ): List<String> {
        return operations.chunked(batchSize).flatMap { batch ->
            batch.map { operation ->
                submitOperation(operation)
            }
        }
    }
    
    /**
     * Process operations from queue
     */
    private suspend fun processOperations() {
        while (!operationChannel.isClosedForReceive) {
            try {
                // Get next operation from channel
                val operation = operationChannel.receive()
                
                // Process operation based on priority
                when (operation.priority) {
                    Priority.CRITICAL -> {
                        processOperationImmediately(operation)
                    }
                    Priority.HIGH -> {
                        launch { processOperationImmediately(operation) }
                    }
                    else -> {
                        launch { processOperationWithDelay(operation) }
                    }
                }
                
            } catch (e: Exception) {
                // Log error but continue processing
                android.util.Log.e("BackgroundDataProcessor", "Error in processing loop", e)
            }
        }
    }
    
    /**
     * Process operation immediately
     */
    private suspend fun processOperationImmediately(operation: BackgroundOperation<*>) {
        activeOperations.incrementAndGet()
        
        try {
            val executionTime = measureTimeMillis {
                val result = withTimeout(operation.timeout) {
                    operation.operation()
                }
                
                // Handle success
                (operation.onSuccess as (Any?) -> Unit)(result)
                
                resultChannel.trySend(
                    OperationResult.Success(
                        id = operation.id,
                        data = result,
                        executionTime = executionTime
                    )
                )
                
                successCount.incrementAndGet()
            }
            
            recordExecutionTime(operation.type, executionTime)
            
        } catch (e: TimeoutCancellationException) {
            operation.onError(Exception("Operation timed out"))
            resultChannel.trySend(
                OperationResult.Timeout<Any>(
                    id = operation.id,
                    executionTime = operation.timeout
                )
            )
            errorCount.incrementAndGet()
            
        } catch (e: Exception) {
            operation.onError(e)
            resultChannel.trySend(
                OperationResult.Error<Any>(
                    id = operation.id,
                    exception = e,
                    executionTime = 0
                )
            )
            errorCount.incrementAndGet()
            
        } finally {
            activeOperations.decrementAndGet()
            processedOperations.incrementAndGet()
        }
    }
    
    /**
     * Process operation with small delay for batching
     */
    private suspend fun processOperationWithDelay(operation: BackgroundOperation<*>) {
        delay(10) // Small delay for batching
        processOperationImmediately(operation)
    }
    
    /**
     * Record execution time for statistics
     */
    private fun recordExecutionTime(type: OperationType, executionTime: Long) {
        synchronized(executionTimes) {
            executionTimes.add(executionTime)
            if (executionTimes.size > 1000) {
                executionTimes.removeAt(0)
            }
        }
        
        synchronized(operationCounts) {
            operationCounts[type] = operationCounts.getOrDefault(type, 0) + 1
        }
    }
    
    /**
     * Get processing statistics
     */
    fun getProcessingStats(): ProcessingStats {
        val avgExecutionTime = synchronized(executionTimes) {
            if (executionTimes.isNotEmpty()) {
                executionTimes.average()
            } else 0.0
        }
        
        val totalProcessed = processedOperations.get()
        val successRate = if (totalProcessed > 0) {
            successCount.get().toDouble() / totalProcessed * 100
        } else 0.0
        
        return ProcessingStats(
            queueSize = operationQueue.size,
            activeOperations = activeOperations.get(),
            processedOperations = totalProcessed,
            averageExecutionTime = avgExecutionTime,
            successRate = successRate,
            operationsByType = synchronized(operationCounts) { operationCounts.toMap() }
        )
    }
    
    /**
     * Clear statistics
     */
    fun clearStats() {
        synchronized(executionTimes) { executionTimes.clear() }
        synchronized(operationCounts) { operationCounts.clear() }
        successCount.set(0)
        errorCount.set(0)
        processedOperations.set(0)
    }
    
    /**
     * Optimized campaign loading with background processing
     */
    suspend fun loadCampaignsOptimized(
        context: Context,
        sortOption: CampaignSortOption = CampaignSortOption.DATE_NEWEST_FIRST
    ): Flow<DataOperationResult<List<EnhancedCampaignProgress>>> = flow {
        
        // Submit background operation
        val operationId = "load_campaigns_${System.currentTimeMillis()}"
        
        val resultFlow = MutableSharedFlow<DataOperationResult<List<EnhancedCampaignProgress>>>()
        
        submitOperation(
            BackgroundOperation(
                id = operationId,
                type = OperationType.DATA_LOAD,
                priority = Priority.HIGH,
                operation = {
                    // Load campaigns in background
                    val campaigns = when (val result = EnhancedCampaignProgressManager.getAllPausedCampaigns(context)) {
                        is DataOperationResult.Success -> {
                            EnhancedCampaignProgressManager.sortCampaigns(result.data, sortOption)
                        }
                        is DataOperationResult.PartialSuccess -> {
                            EnhancedCampaignProgressManager.sortCampaigns(result.data, sortOption)
                        }
                        is DataOperationResult.Error -> {
                            throw Exception(result.message)
                        }
                    }
                    campaigns
                },
                onSuccess = { campaigns ->
                    resultFlow.tryEmit(DataOperationResult.Success(campaigns))
                },
                onError = { exception ->
                    resultFlow.tryEmit(DataOperationResult.Error(exception.message ?: "Unknown error"))
                }
            )
        )
        
        // Emit results from the flow
        resultFlow.collect { result ->
            emit(result)
        }
    }
    
    /**
     * Optimized campaign deletion with background processing
     */
    suspend fun deleteCampaignOptimized(
        context: Context,
        uniqueId: String
    ): Flow<DeleteResult> = flow {
        
        val operationId = "delete_campaign_$uniqueId"
        val resultFlow = MutableSharedFlow<DeleteResult>()
        
        submitOperation(
            BackgroundOperation(
                id = operationId,
                type = OperationType.DATA_DELETE,
                priority = Priority.HIGH,
                operation = {
                    EnhancedCampaignProgressManager.deleteProgress(context, uniqueId)
                },
                onSuccess = { result ->
                    resultFlow.tryEmit(result)
                },
                onError = { exception ->
                    resultFlow.tryEmit(DeleteResult.Error(exception.message ?: "Delete failed"))
                }
            )
        )
        
        resultFlow.collect { result ->
            emit(result)
        }
    }
    
    /**
     * Batch campaign operations
     */
    suspend fun batchCampaignOperations(
        context: Context,
        operations: List<Pair<String, suspend () -> Any>>
    ): Flow<Map<String, Any>> = flow {
        
        val results = mutableMapOf<String, Any>()
        val resultFlow = MutableSharedFlow<Map<String, Any>>()
        
        val backgroundOps = operations.map { (id, operation) ->
            BackgroundOperation(
                id = id,
                type = OperationType.DATA_LOAD,
                operation = operation,
                onSuccess = { result ->
                    synchronized(results) {
                        results[id] = result
                        if (results.size == operations.size) {
                            resultFlow.tryEmit(results.toMap())
                        }
                    }
                },
                onError = { exception ->
                    synchronized(results) {
                        results[id] = exception
                        if (results.size == operations.size) {
                            resultFlow.tryEmit(results.toMap())
                        }
                    }
                }
            )
        }
        
        submitBatchOperations(backgroundOps)
        
        resultFlow.collect { result ->
            emit(result)
        }
    }
    
    /**
     * Memory-efficient data streaming for large datasets
     * Requirements: 2.3, 3.2
     */
    suspend fun <T> streamLargeDataset(
        context: Context,
        dataLoader: suspend (offset: Int, limit: Int) -> List<T>,
        totalCount: Int,
        batchSize: Int = BATCH_SIZE
    ): Flow<StreamingResult<T>> = flow {
        var offset = 0
        val allItems = mutableListOf<T>()
        
        while (offset < totalCount) {
            val limit = minOf(batchSize, totalCount - offset)
            
            try {
                val operationId = "stream_data_${offset}_${limit}"
                
                val batch = withContext(processingDispatcher) {
                    withTimeout(OPERATION_TIMEOUT) {
                        dataLoader(offset, limit)
                    }
                }
                
                allItems.addAll(batch)
                offset += batch.size
                
                emit(StreamingResult(
                    items = allItems.toList(),
                    loadedCount = allItems.size,
                    totalCount = totalCount,
                    isComplete = offset >= totalCount,
                    progress = (offset.toFloat() / totalCount) * 100f
                ))
                
                // Small delay to prevent overwhelming the system
                delay(50)
                
            } catch (e: Exception) {
                emit(StreamingResult(
                    items = allItems.toList(),
                    loadedCount = allItems.size,
                    totalCount = totalCount,
                    isComplete = false,
                    progress = (offset.toFloat() / totalCount) * 100f,
                    error = e.message
                ))
                break
            }
        }
    }
    
    /**
     * Streaming result for large datasets
     */
    data class StreamingResult<T>(
        val items: List<T>,
        val loadedCount: Int,
        val totalCount: Int,
        val isComplete: Boolean,
        val progress: Float,
        val error: String? = null
    )
    
    /**
     * Optimized parallel processing for CPU-intensive operations
     * Requirements: 2.3, 3.2
     */
    suspend fun <T, R> processInParallel(
        items: List<T>,
        maxConcurrency: Int = MAX_CONCURRENT_OPERATIONS,
        processor: suspend (T) -> R
    ): Flow<ParallelProcessingResult<R>> = flow {
        val results = mutableListOf<R>()
        val semaphore = kotlinx.coroutines.sync.Semaphore(maxConcurrency)
        
        val jobs = items.mapIndexed { index, item ->
            async(processingDispatcher) {
                semaphore.withPermit {
                    try {
                        val result = processor(item)
                        synchronized(results) {
                            results.add(result)
                        }
                        
                        // Emit progress update
                        emit(ParallelProcessingResult(
                            results = results.toList(),
                            processedCount = results.size,
                            totalCount = items.size,
                            isComplete = results.size == items.size,
                            progress = (results.size.toFloat() / items.size) * 100f
                        ))
                        
                        result
                    } catch (e: Exception) {
                        emit(ParallelProcessingResult(
                            results = results.toList(),
                            processedCount = results.size,
                            totalCount = items.size,
                            isComplete = false,
                            progress = (results.size.toFloat() / items.size) * 100f,
                            error = "Processing failed for item $index: ${e.message}"
                        ))
                        throw e
                    }
                }
            }
        }
        
        try {
            jobs.awaitAll()
            
            emit(ParallelProcessingResult(
                results = results.toList(),
                processedCount = results.size,
                totalCount = items.size,
                isComplete = true,
                progress = 100f
            ))
            
        } catch (e: Exception) {
            emit(ParallelProcessingResult(
                results = results.toList(),
                processedCount = results.size,
                totalCount = items.size,
                isComplete = false,
                progress = (results.size.toFloat() / items.size) * 100f,
                error = "Parallel processing failed: ${e.message}"
            ))
        }
    }
    
    /**
     * Parallel processing result
     */
    data class ParallelProcessingResult<T>(
        val results: List<T>,
        val processedCount: Int,
        val totalCount: Int,
        val isComplete: Boolean,
        val progress: Float,
        val error: String? = null
    )
    
    /**
     * Memory pressure monitoring and optimization
     * Requirements: 2.3, 3.2
     */
    fun monitorMemoryPressure(): Flow<MemoryPressureState> = flow {
        while (true) {
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val usedMemory = totalMemory - freeMemory
            
            val memoryUsagePercentage = (usedMemory.toDouble() / maxMemory.toDouble()) * 100
            
            val pressureLevel = when {
                memoryUsagePercentage > 90 -> MemoryPressureLevel.CRITICAL
                memoryUsagePercentage > 75 -> MemoryPressureLevel.HIGH
                memoryUsagePercentage > 50 -> MemoryPressureLevel.MEDIUM
                else -> MemoryPressureLevel.LOW
            }
            
            emit(MemoryPressureState(
                usedMemory = usedMemory,
                maxMemory = maxMemory,
                usagePercentage = memoryUsagePercentage,
                pressureLevel = pressureLevel,
                shouldOptimize = pressureLevel >= MemoryPressureLevel.HIGH
            ))
            
            // Trigger optimization if memory pressure is high
            if (pressureLevel >= MemoryPressureLevel.HIGH) {
                optimizeMemoryUsage()
            }
            
            delay(5000) // Check every 5 seconds
        }
    }
    
    /**
     * Memory pressure levels
     */
    enum class MemoryPressureLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    /**
     * Memory pressure state
     */
    data class MemoryPressureState(
        val usedMemory: Long,
        val maxMemory: Long,
        val usagePercentage: Double,
        val pressureLevel: MemoryPressureLevel,
        val shouldOptimize: Boolean
    )
    
    /**
     * Optimize memory usage when under pressure
     */
    private fun optimizeMemoryUsage() {
        // Clear operation queue of low priority items
        val iterator = operationQueue.iterator()
        while (iterator.hasNext()) {
            val operation = iterator.next()
            if (operation.priority == Priority.LOW) {
                iterator.remove()
            }
        }
        
        // Force garbage collection
        System.gc()
        
        // Clear old execution times
        synchronized(executionTimes) {
            if (executionTimes.size > 100) {
                executionTimes.subList(0, executionTimes.size - 100).clear()
            }
        }
    }
}