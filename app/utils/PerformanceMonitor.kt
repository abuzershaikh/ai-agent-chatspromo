package com.message.bulksend.utils

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

/**
 * Performance monitoring utility for tracking and optimizing campaign operations
 * Provides real-time performance metrics and optimization recommendations
 * Requirements: 2.3, 3.2
 */
object PerformanceMonitor {
    
    private const val TAG = "PerformanceMonitor"
    private const val MAX_METRICS_HISTORY = 1000
    
    // Performance thresholds (in milliseconds)
    private const val SLOW_OPERATION_THRESHOLD = 1000L
    private const val VERY_SLOW_OPERATION_THRESHOLD = 5000L
    private const val MEMORY_WARNING_THRESHOLD = 100 * 1024 * 1024L // 100MB
    
    // Metrics storage
    private val operationMetrics = ConcurrentHashMap<String, MutableList<OperationMetric>>()
    private val memorySnapshots = mutableListOf<MemorySnapshot>()
    private val performanceAlerts = mutableListOf<PerformanceAlert>()
    
    // Counters
    private val totalOperations = AtomicLong(0)
    private val slowOperations = AtomicLong(0)
    private val failedOperations = AtomicLong(0)
    
    /**
     * Operation performance metric
     */
    data class OperationMetric(
        val operationName: String,
        val executionTime: Long,
        val timestamp: Long = System.currentTimeMillis(),
        val success: Boolean = true,
        val memoryUsed: Long = 0,
        val itemCount: Int = 0,
        val cacheHit: Boolean = false,
        val threadName: String = Thread.currentThread().name
    )
    
    /**
     * Memory usage snapshot
     */
    data class MemorySnapshot(
        val timestamp: Long = System.currentTimeMillis(),
        val usedMemory: Long,
        val totalMemory: Long,
        val maxMemory: Long,
        val freeMemory: Long
    ) {
        val memoryUsagePercentage: Double
            get() = (usedMemory.toDouble() / maxMemory.toDouble()) * 100
    }
    
    /**
     * Performance alert
     */
    data class PerformanceAlert(
        val type: AlertType,
        val message: String,
        val timestamp: Long = System.currentTimeMillis(),
        val severity: Severity = Severity.WARNING,
        val operationName: String? = null,
        val metrics: Map<String, Any> = emptyMap()
    )
    
    enum class AlertType {
        SLOW_OPERATION,
        HIGH_MEMORY_USAGE,
        FREQUENT_CACHE_MISSES,
        OPERATION_FAILURE,
        PERFORMANCE_DEGRADATION
    }
    
    enum class Severity {
        INFO,
        WARNING,
        ERROR,
        CRITICAL
    }
    
    /**
     * Performance summary
     */
    data class PerformanceSummary(
        val totalOperations: Long,
        val averageExecutionTime: Double,
        val slowOperationsCount: Long,
        val failedOperationsCount: Long,
        val cacheHitRate: Double,
        val memoryUsagePercentage: Double,
        val activeAlerts: List<PerformanceAlert>,
        val recommendations: List<String>
    )
    
    /**
     * Start monitoring an operation
     */
    fun <T> monitorOperation(
        operationName: String,
        itemCount: Int = 0,
        operation: () -> T
    ): T {
        val startMemory = getCurrentMemoryUsage()
        var result: T
        var success = true
        var cacheHit = false
        
        val executionTime = measureTimeMillis {
            try {
                result = operation()
                
                // Check if this might be a cache hit (very fast execution)
                cacheHit = executionTime < 10 && itemCount > 0
                
            } catch (e: Exception) {
                success = false
                failedOperations.incrementAndGet()
                
                recordAlert(
                    AlertType.OPERATION_FAILURE,
                    "Operation '$operationName' failed: ${e.message}",
                    Severity.ERROR,
                    operationName
                )
                
                throw e
            }
        }
        
        val endMemory = getCurrentMemoryUsage()
        val memoryUsed = endMemory - startMemory
        
        // Record metric
        recordMetric(
            OperationMetric(
                operationName = operationName,
                executionTime = executionTime,
                success = success,
                memoryUsed = memoryUsed,
                itemCount = itemCount,
                cacheHit = cacheHit
            )
        )
        
        // Check for performance issues
        checkPerformanceThresholds(operationName, executionTime, memoryUsed)
        
        totalOperations.incrementAndGet()
        
        return result
    }
    
    /**
     * Monitor suspend operation
     */
    suspend fun <T> monitorSuspendOperation(
        operationName: String,
        itemCount: Int = 0,
        operation: suspend () -> T
    ): T {
        val startMemory = getCurrentMemoryUsage()
        var result: T
        var success = true
        var cacheHit = false
        
        val executionTime = measureTimeMillis {
            try {
                result = operation()
                cacheHit = executionTime < 10 && itemCount > 0
            } catch (e: Exception) {
                success = false
                failedOperations.incrementAndGet()
                
                recordAlert(
                    AlertType.OPERATION_FAILURE,
                    "Suspend operation '$operationName' failed: ${e.message}",
                    Severity.ERROR,
                    operationName
                )
                
                throw e
            }
        }
        
        val endMemory = getCurrentMemoryUsage()
        val memoryUsed = endMemory - startMemory
        
        recordMetric(
            OperationMetric(
                operationName = operationName,
                executionTime = executionTime,
                success = success,
                memoryUsed = memoryUsed,
                itemCount = itemCount,
                cacheHit = cacheHit
            )
        )
        
        checkPerformanceThresholds(operationName, executionTime, memoryUsed)
        totalOperations.incrementAndGet()
        
        return result
    }
    
    /**
     * Record performance metric
     */
    private fun recordMetric(metric: OperationMetric) {
        val metrics = operationMetrics.getOrPut(metric.operationName) { mutableListOf() }
        
        synchronized(metrics) {
            metrics.add(metric)
            
            // Keep only recent metrics
            if (metrics.size > MAX_METRICS_HISTORY) {
                metrics.removeAt(0)
            }
        }
        
        // Log slow operations
        if (metric.executionTime > SLOW_OPERATION_THRESHOLD) {
            Log.w(TAG, "Slow operation detected: ${metric.operationName} took ${metric.executionTime}ms")
        }
    }
    
    /**
     * Check performance thresholds and generate alerts
     */
    private fun checkPerformanceThresholds(operationName: String, executionTime: Long, memoryUsed: Long) {
        // Check execution time
        when {
            executionTime > VERY_SLOW_OPERATION_THRESHOLD -> {
                slowOperations.incrementAndGet()
                recordAlert(
                    AlertType.SLOW_OPERATION,
                    "Very slow operation: $operationName took ${executionTime}ms",
                    Severity.ERROR,
                    operationName,
                    mapOf("executionTime" to executionTime)
                )
            }
            executionTime > SLOW_OPERATION_THRESHOLD -> {
                slowOperations.incrementAndGet()
                recordAlert(
                    AlertType.SLOW_OPERATION,
                    "Slow operation: $operationName took ${executionTime}ms",
                    Severity.WARNING,
                    operationName,
                    mapOf("executionTime" to executionTime)
                )
            }
        }
        
        // Check memory usage
        if (memoryUsed > MEMORY_WARNING_THRESHOLD) {
            recordAlert(
                AlertType.HIGH_MEMORY_USAGE,
                "High memory usage: $operationName used ${memoryUsed / (1024 * 1024)}MB",
                Severity.WARNING,
                operationName,
                mapOf("memoryUsed" to memoryUsed)
            )
        }
    }
    
    /**
     * Record performance alert
     */
    private fun recordAlert(
        type: AlertType,
        message: String,
        severity: Severity,
        operationName: String? = null,
        metrics: Map<String, Any> = emptyMap()
    ) {
        val alert = PerformanceAlert(
            type = type,
            message = message,
            severity = severity,
            operationName = operationName,
            metrics = metrics
        )
        
        synchronized(performanceAlerts) {
            performanceAlerts.add(alert)
            
            // Keep only recent alerts
            if (performanceAlerts.size > MAX_METRICS_HISTORY) {
                performanceAlerts.removeAt(0)
            }
        }
        
        // Log critical alerts
        if (severity == Severity.CRITICAL || severity == Severity.ERROR) {
            Log.e(TAG, "Performance Alert [$severity]: $message")
        }
    }
    
    /**
     * Take memory snapshot
     */
    fun takeMemorySnapshot() {
        val runtime = Runtime.getRuntime()
        val snapshot = MemorySnapshot(
            usedMemory = runtime.totalMemory() - runtime.freeMemory(),
            totalMemory = runtime.totalMemory(),
            maxMemory = runtime.maxMemory(),
            freeMemory = runtime.freeMemory()
        )
        
        synchronized(memorySnapshots) {
            memorySnapshots.add(snapshot)
            
            // Keep only recent snapshots
            if (memorySnapshots.size > MAX_METRICS_HISTORY) {
                memorySnapshots.removeAt(0)
            }
        }
        
        // Check for memory warnings
        if (snapshot.memoryUsagePercentage > 80) {
            recordAlert(
                AlertType.HIGH_MEMORY_USAGE,
                "High memory usage: ${String.format("%.1f", snapshot.memoryUsagePercentage)}%",
                if (snapshot.memoryUsagePercentage > 90) Severity.ERROR else Severity.WARNING,
                metrics = mapOf("memoryUsagePercentage" to snapshot.memoryUsagePercentage)
            )
        }
    }
    
    /**
     * Get current memory usage
     */
    private fun getCurrentMemoryUsage(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }
    
    /**
     * Get performance summary
     */
    fun getPerformanceSummary(): PerformanceSummary {
        val allMetrics = operationMetrics.values.flatten()
        val recentAlerts = synchronized(performanceAlerts) {
            performanceAlerts.filter { System.currentTimeMillis() - it.timestamp < 3600000 } // Last hour
        }
        
        val averageExecutionTime = if (allMetrics.isNotEmpty()) {
            allMetrics.map { it.executionTime }.average()
        } else 0.0
        
        val cacheHitRate = if (allMetrics.isNotEmpty()) {
            allMetrics.count { it.cacheHit }.toDouble() / allMetrics.size * 100
        } else 0.0
        
        val currentMemoryUsage = synchronized(memorySnapshots) {
            memorySnapshots.lastOrNull()?.memoryUsagePercentage ?: 0.0
        }
        
        return PerformanceSummary(
            totalOperations = totalOperations.get(),
            averageExecutionTime = averageExecutionTime,
            slowOperationsCount = slowOperations.get(),
            failedOperationsCount = failedOperations.get(),
            cacheHitRate = cacheHitRate,
            memoryUsagePercentage = currentMemoryUsage,
            activeAlerts = recentAlerts,
            recommendations = generateRecommendations(allMetrics, recentAlerts)
        )
    }
    
    /**
     * Generate performance recommendations
     */
    private fun generateRecommendations(
        metrics: List<OperationMetric>,
        alerts: List<PerformanceAlert>
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        // Check cache hit rate
        if (metrics.isNotEmpty()) {
            val cacheHitRate = metrics.count { it.cacheHit }.toDouble() / metrics.size * 100
            if (cacheHitRate < 30) {
                recommendations.add("Consider implementing more aggressive caching to improve performance")
            }
        }
        
        // Check for frequent slow operations
        val slowOperationTypes = alerts
            .filter { it.type == AlertType.SLOW_OPERATION }
            .groupBy { it.operationName }
            .filter { it.value.size > 5 }
        
        if (slowOperationTypes.isNotEmpty()) {
            recommendations.add("Optimize frequently slow operations: ${slowOperationTypes.keys.joinToString(", ")}")
        }
        
        // Check memory usage
        val memoryAlerts = alerts.filter { it.type == AlertType.HIGH_MEMORY_USAGE }
        if (memoryAlerts.size > 3) {
            recommendations.add("Consider implementing memory optimization strategies or reducing batch sizes")
        }
        
        // Check for operation failures
        val failureRate = failedOperations.get().toDouble() / totalOperations.get() * 100
        if (failureRate > 5) {
            recommendations.add("High failure rate detected. Review error handling and data validation")
        }
        
        return recommendations
    }
    
    /**
     * Get metrics for specific operation
     */
    fun getOperationMetrics(operationName: String): List<OperationMetric> {
        return operationMetrics[operationName]?.toList() ?: emptyList()
    }
    
    /**
     * Get recent alerts
     */
    fun getRecentAlerts(maxAge: Long = 3600000): List<PerformanceAlert> {
        val cutoffTime = System.currentTimeMillis() - maxAge
        return synchronized(performanceAlerts) {
            performanceAlerts.filter { it.timestamp > cutoffTime }
        }
    }
    
    /**
     * Clear all metrics and alerts
     */
    fun clearMetrics() {
        operationMetrics.clear()
        synchronized(memorySnapshots) { memorySnapshots.clear() }
        synchronized(performanceAlerts) { performanceAlerts.clear() }
        totalOperations.set(0)
        slowOperations.set(0)
        failedOperations.set(0)
    }
    
    /**
     * Start automatic memory monitoring
     */
    fun startMemoryMonitoring(intervalMs: Long = 30000) {
        GlobalScope.launch {
            while (true) {
                takeMemorySnapshot()
                delay(intervalMs)
            }
        }
    }
    
    /**
     * Log performance summary
     */
    fun logPerformanceSummary() {
        val summary = getPerformanceSummary()
        
        Log.i(TAG, "=== Performance Summary ===")
        Log.i(TAG, "Total Operations: ${summary.totalOperations}")
        Log.i(TAG, "Average Execution Time: ${String.format("%.2f", summary.averageExecutionTime)}ms")
        Log.i(TAG, "Slow Operations: ${summary.slowOperationsCount}")
        Log.i(TAG, "Failed Operations: ${summary.failedOperationsCount}")
        Log.i(TAG, "Cache Hit Rate: ${String.format("%.1f", summary.cacheHitRate)}%")
        Log.i(TAG, "Memory Usage: ${String.format("%.1f", summary.memoryUsagePercentage)}%")
        Log.i(TAG, "Active Alerts: ${summary.activeAlerts.size}")
        
        if (summary.recommendations.isNotEmpty()) {
            Log.i(TAG, "Recommendations:")
            summary.recommendations.forEach { recommendation ->
                Log.i(TAG, "  - $recommendation")
            }
        }
        
        Log.i(TAG, "========================")
    }
}