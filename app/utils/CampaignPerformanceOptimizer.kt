package com.message.bulksend.utils

import android.content.Context
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.measureTimeMillis

/**
 * Performance optimization utilities for campaign data operations
 * Implements lazy loading, efficient updates, and background processing
 * Requirements: 2.3, 3.2
 */
object CampaignPerformanceOptimizer {
    
    // Performance constants
    private const val LAZY_LOAD_BATCH_SIZE = 20
    private const val BACKGROUND_OPERATION_TIMEOUT = 30_000L // 30 seconds
    private const val CACHE_EXPIRY_TIME = 5 * 60 * 1000L // 5 minutes
    private const val MAX_CONCURRENT_OPERATIONS = 3
    
    // Cache for frequently accessed data
    private val campaignCache = ConcurrentHashMap<String, CachedCampaignData>()
    private val sortCache = ConcurrentHashMap<String, List<EnhancedCampaignProgress>>()
    
    // Background operation dispatcher with limited concurrency
    private val backgroundDispatcher = Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_OPERATIONS)
    
    /**
     * Cached campaign data with expiry time
     */
    private data class CachedCampaignData(
        val campaigns: List<EnhancedCampaignProgress>,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > CACHE_EXPIRY_TIME
    }
    
    /**
     * Lazy loading state for campaign lists
     */
    data class LazyLoadingState<T>(
        val items: List<T> = emptyList(),
        val isLoading: Boolean = false,
        val hasMore: Boolean = true,
        val error: String? = null,
        val loadedCount: Int = 0,
        val totalCount: Int = 0
    )
    
    /**
     * Performance metrics for monitoring
     */
    data class PerformanceMetrics(
        val operationName: String,
        val executionTimeMs: Long,
        val itemCount: Int,
        val cacheHit: Boolean = false,
        val backgroundExecution: Boolean = false
    )
    
    private val performanceMetrics = mutableListOf<PerformanceMetrics>()
    
    /**
     * Lazy load campaigns with batching and caching
     * Requirements: 2.3
     */
    suspend fun lazyLoadCampaigns(
        context: Context,
        batchSize: Int = LAZY_LOAD_BATCH_SIZE,
        sortOption: CampaignSortOption = CampaignSortOption.DATE_NEWEST_FIRST
    ): Flow<LazyLoadingState<EnhancedCampaignProgress>> = flow {
        val cacheKey = "campaigns_${sortOption.name}"
        
        // Check cache first
        val cachedData = campaignCache[cacheKey]
        if (cachedData != null && !cachedData.isExpired()) {
            val campaigns = cachedData.campaigns
            
            // Emit batched data
            var currentIndex = 0
            while (currentIndex < campaigns.size) {
                val batch = campaigns.drop(currentIndex).take(batchSize)
                currentIndex += batch.size
                
                emit(LazyLoadingState(
                    items = campaigns.take(currentIndex),
                    isLoading = false,
                    hasMore = currentIndex < campaigns.size,
                    loadedCount = currentIndex,
                    totalCount = campaigns.size
                ))
                
                // Small delay to prevent UI blocking
                delay(10)
            }
            
            recordMetrics("lazyLoadCampaigns", 0, campaigns.size, cacheHit = true)
            return@flow
        }
        
        // Load from storage in background
        emit(LazyLoadingState(isLoading = true))
        
        try {
            val executionTime = measureTimeMillis {
                when (val result = withContext(backgroundDispatcher) {
                    withTimeout(BACKGROUND_OPERATION_TIMEOUT) {
                        EnhancedCampaignProgressManager.getAllPausedCampaigns(context)
                    }
                }) {
                    is DataOperationResult.Success -> {
                        val sortedCampaigns = EnhancedCampaignProgressManager.sortCampaigns(result.data, sortOption)
                        
                        // Cache the result
                        campaignCache[cacheKey] = CachedCampaignData(sortedCampaigns)
                        
                        // Emit batched data
                        var currentIndex = 0
                        while (currentIndex < sortedCampaigns.size) {
                            val batch = sortedCampaigns.drop(currentIndex).take(batchSize)
                            currentIndex += batch.size
                            
                            emit(LazyLoadingState(
                                items = sortedCampaigns.take(currentIndex),
                                isLoading = currentIndex < sortedCampaigns.size,
                                hasMore = currentIndex < sortedCampaigns.size,
                                loadedCount = currentIndex,
                                totalCount = sortedCampaigns.size
                            ))
                            
                            // Small delay between batches
                            delay(50)
                        }
                    }
                    is DataOperationResult.PartialSuccess -> {
                        val sortedCampaigns = EnhancedCampaignProgressManager.sortCampaigns(result.data, sortOption)
                        
                        // Cache the result
                        campaignCache[cacheKey] = CachedCampaignData(sortedCampaigns)
                        
                        // Emit all data with warning
                        emit(LazyLoadingState(
                            items = sortedCampaigns,
                            isLoading = false,
                            hasMore = false,
                            error = "Loaded with warnings: ${result.warnings.joinToString(", ")}",
                            loadedCount = sortedCampaigns.size,
                            totalCount = sortedCampaigns.size
                        ))
                    }
                    is DataOperationResult.Error -> {
                        emit(LazyLoadingState(
                            isLoading = false,
                            hasMore = false,
                            error = result.message
                        ))
                    }
                }
            }
            
            recordMetrics("lazyLoadCampaigns", executionTime, campaignCache[cacheKey]?.campaigns?.size ?: 0, backgroundExecution = true)
            
        } catch (e: TimeoutCancellationException) {
            emit(LazyLoadingState(
                isLoading = false,
                hasMore = false,
                error = "Loading timed out. Please try again."
            ))
        } catch (e: Exception) {
            emit(LazyLoadingState(
                isLoading = false,
                hasMore = false,
                error = "Failed to load campaigns: ${e.message}"
            ))
        }
    }
    
    /**
     * Efficient campaign sorting with caching
     * Requirements: 2.3
     */
    suspend fun efficientSort(
        campaigns: List<EnhancedCampaignProgress>,
        sortOption: CampaignSortOption
    ): List<EnhancedCampaignProgress> = withContext(backgroundDispatcher) {
        val cacheKey = "${campaigns.hashCode()}_${sortOption.name}"
        
        // Check sort cache
        sortCache[cacheKey]?.let { cachedResult ->
            recordMetrics("efficientSort", 0, campaigns.size, cacheHit = true)
            return@withContext cachedResult
        }
        
        val executionTime = measureTimeMillis {
            val sortedCampaigns = when (sortOption) {
                CampaignSortOption.DATE_NEWEST_FIRST -> campaigns.sortedByDescending { it.createdAt }
                CampaignSortOption.DATE_OLDEST_FIRST -> campaigns.sortedBy { it.createdAt }
                CampaignSortOption.CAMPAIGN_TYPE -> campaigns.sortedBy { it.campaignType }
                CampaignSortOption.PROGRESS_HIGH_TO_LOW -> campaigns.sortedByDescending { 
                    if (it.totalContacts > 0) (it.sentCount.toDouble() / it.totalContacts.toDouble()) else 0.0 
                }
                CampaignSortOption.PROGRESS_LOW_TO_HIGH -> campaigns.sortedBy { 
                    if (it.totalContacts > 0) (it.sentCount.toDouble() / it.totalContacts.toDouble()) else 0.0 
                }
                CampaignSortOption.NAME_A_TO_Z -> campaigns.sortedBy { it.campaignName.lowercase() }
                CampaignSortOption.NAME_Z_TO_A -> campaigns.sortedByDescending { it.campaignName.lowercase() }
            }
            
            // Cache the result
            sortCache[cacheKey] = sortedCampaigns
            sortedCampaigns
        }
        
        recordMetrics("efficientSort", executionTime, campaigns.size, backgroundExecution = true)
        sortCache[cacheKey]!!
    }
    
    /**
     * Background data operations with timeout and error handling
     * Requirements: 2.3, 3.2
     */
    suspend fun <T> performBackgroundOperation(
        operationName: String,
        timeoutMs: Long = BACKGROUND_OPERATION_TIMEOUT,
        operation: suspend () -> T
    ): Result<T> = withContext(backgroundDispatcher) {
        try {
            val result = withTimeout(timeoutMs) {
                val executionTime = measureTimeMillis {
                    operation()
                }
                recordMetrics(operationName, executionTime, 1, backgroundExecution = true)
                operation()
            }
            Result.success(result)
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("Operation '$operationName' timed out after ${timeoutMs}ms"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Efficient list updates that minimize recomposition
     * Requirements: 2.3
     */
    fun <T> calculateListDiff(
        oldList: List<T>,
        newList: List<T>,
        keySelector: (T) -> Any
    ): ListDiffResult<T> {
        val oldKeys = oldList.associateBy(keySelector)
        val newKeys = newList.associateBy(keySelector)
        
        val added = newList.filter { keySelector(it) !in oldKeys }
        val removed = oldList.filter { keySelector(it) !in newKeys }
        val updated = newList.filter { item ->
            val key = keySelector(item)
            key in oldKeys && oldKeys[key] != item
        }
        val unchanged = newList.filter { item ->
            val key = keySelector(item)
            key in oldKeys && oldKeys[key] == item
        }
        
        return ListDiffResult(
            added = added,
            removed = removed,
            updated = updated,
            unchanged = unchanged,
            hasChanges = added.isNotEmpty() || removed.isNotEmpty() || updated.isNotEmpty()
        )
    }
    
    /**
     * Result of list difference calculation
     */
    data class ListDiffResult<T>(
        val added: List<T>,
        val removed: List<T>,
        val updated: List<T>,
        val unchanged: List<T>,
        val hasChanges: Boolean
    )
    
    /**
     * Clear expired cache entries
     */
    fun clearExpiredCache() {
        val currentTime = System.currentTimeMillis()
        campaignCache.entries.removeAll { (_, data) -> data.isExpired() }
        
        // Clear sort cache if it gets too large
        if (sortCache.size > 100) {
            sortCache.clear()
        }
    }
    
    /**
     * Clear all caches
     */
    fun clearAllCaches() {
        campaignCache.clear()
        sortCache.clear()
    }
    
    /**
     * Record performance metrics
     */
    private fun recordMetrics(
        operationName: String,
        executionTimeMs: Long,
        itemCount: Int,
        cacheHit: Boolean = false,
        backgroundExecution: Boolean = false
    ) {
        val metric = PerformanceMetrics(
            operationName = operationName,
            executionTimeMs = executionTimeMs,
            itemCount = itemCount,
            cacheHit = cacheHit,
            backgroundExecution = backgroundExecution
        )
        
        synchronized(performanceMetrics) {
            performanceMetrics.add(metric)
            
            // Keep only last 100 metrics
            if (performanceMetrics.size > 100) {
                performanceMetrics.removeAt(0)
            }
        }
    }
    
    /**
     * Get performance metrics for monitoring
     */
    fun getPerformanceMetrics(): List<PerformanceMetrics> {
        return synchronized(performanceMetrics) {
            performanceMetrics.toList()
        }
    }
    
    /**
     * Get performance summary
     */
    fun getPerformanceSummary(): Map<String, Any> {
        val metrics = getPerformanceMetrics()
        
        return mapOf(
            "total_operations" to metrics.size,
            "cache_hit_rate" to if (metrics.isNotEmpty()) {
                metrics.count { it.cacheHit }.toDouble() / metrics.size * 100
            } else 0.0,
            "average_execution_time" to if (metrics.isNotEmpty()) {
                metrics.map { it.executionTimeMs }.average()
            } else 0.0,
            "background_operations" to metrics.count { it.backgroundExecution },
            "cache_size" to campaignCache.size,
            "sort_cache_size" to sortCache.size
        )
    }
    
    /**
     * Optimized list updates for UI efficiency
     * Requirements: 2.3
     */
    fun <T> optimizeListUpdates(
        oldList: List<T>,
        newList: List<T>,
        keySelector: (T) -> String,
        onItemAdded: (T) -> Unit = {},
        onItemRemoved: (T) -> Unit = {},
        onItemUpdated: (T) -> Unit = {}
    ): OptimizedListUpdate<T> {
        val diff = calculateListDiff(oldList, newList, keySelector)
        
        // Apply optimizations
        diff.added.forEach(onItemAdded)
        diff.removed.forEach(onItemRemoved)
        diff.updated.forEach(onItemUpdated)
        
        return OptimizedListUpdate(
            newList = newList,
            diff = diff,
            shouldRecompose = diff.hasChanges,
            optimizationApplied = true
        )
    }
    
    /**
     * Result of optimized list update
     */
    data class OptimizedListUpdate<T>(
        val newList: List<T>,
        val diff: ListDiffResult<T>,
        val shouldRecompose: Boolean,
        val optimizationApplied: Boolean
    )
    
    /**
     * Lazy loading with virtual scrolling support
     * Requirements: 2.3
     */
    suspend fun <T> lazyLoadWithVirtualScrolling(
        totalItems: Int,
        visibleRange: IntRange,
        batchSize: Int = LAZY_LOAD_BATCH_SIZE,
        loader: suspend (offset: Int, limit: Int) -> List<T>
    ): Flow<VirtualScrollState<T>> = flow {
        val loadedItems = mutableMapOf<Int, T>()
        
        // Calculate which batches need to be loaded
        val startBatch = visibleRange.first / batchSize
        val endBatch = visibleRange.last / batchSize
        
        for (batchIndex in startBatch..endBatch) {
            val offset = batchIndex * batchSize
            val limit = minOf(batchSize, totalItems - offset)
            
            if (limit > 0) {
                try {
                    val batch = loader(offset, limit)
                    batch.forEachIndexed { index, item ->
                        loadedItems[offset + index] = item
                    }
                    
                    emit(VirtualScrollState(
                        totalItems = totalItems,
                        loadedItems = loadedItems.toMap(),
                        visibleRange = visibleRange,
                        isLoading = false
                    ))
                    
                } catch (e: Exception) {
                    emit(VirtualScrollState(
                        totalItems = totalItems,
                        loadedItems = loadedItems.toMap(),
                        visibleRange = visibleRange,
                        isLoading = false,
                        error = e.message
                    ))
                }
            }
        }
    }
    
    /**
     * Virtual scroll state
     */
    data class VirtualScrollState<T>(
        val totalItems: Int,
        val loadedItems: Map<Int, T>,
        val visibleRange: IntRange,
        val isLoading: Boolean = false,
        val error: String? = null
    )
    
    /**
     * Memory-efficient batch processing
     * Requirements: 2.3, 3.2
     */
    suspend fun <T, R> processBatchesMemoryEfficient(
        items: List<T>,
        batchSize: Int = LAZY_LOAD_BATCH_SIZE,
        processor: suspend (List<T>) -> List<R>
    ): Flow<BatchProcessingResult<R>> = flow {
        val totalBatches = (items.size + batchSize - 1) / batchSize
        val results = mutableListOf<R>()
        
        items.chunked(batchSize).forEachIndexed { batchIndex, batch ->
            try {
                val batchResults = withContext(backgroundDispatcher) {
                    processor(batch)
                }
                
                results.addAll(batchResults)
                
                emit(BatchProcessingResult(
                    processedItems = results.toList(),
                    currentBatch = batchIndex + 1,
                    totalBatches = totalBatches,
                    isComplete = batchIndex == totalBatches - 1,
                    progress = ((batchIndex + 1).toFloat() / totalBatches) * 100f
                ))
                
                // Small delay to prevent UI blocking
                delay(10)
                
            } catch (e: Exception) {
                emit(BatchProcessingResult(
                    processedItems = results.toList(),
                    currentBatch = batchIndex + 1,
                    totalBatches = totalBatches,
                    isComplete = false,
                    progress = (batchIndex.toFloat() / totalBatches) * 100f,
                    error = e.message
                ))
                break
            }
        }
    }
    
    /**
     * Batch processing result
     */
    data class BatchProcessingResult<T>(
        val processedItems: List<T>,
        val currentBatch: Int,
        val totalBatches: Int,
        val isComplete: Boolean,
        val progress: Float,
        val error: String? = null
    )
}