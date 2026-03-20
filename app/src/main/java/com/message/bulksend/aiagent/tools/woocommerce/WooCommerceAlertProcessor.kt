package com.message.bulksend.aiagent.tools.woocommerce

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.ListenerRegistration
import com.message.bulksend.aiagent.tools.globalsender.GlobalSenderManager
import com.message.bulksend.aiagent.tools.reverseai.ReverseAIManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class WooCommerceAlertProcessResult(
    val totalFetched: Int = 0,
    val sentCount: Int = 0,
    val failedCount: Int = 0
)

/**
 * Pulls pending WooCommerce alerts from Firestore and sends them via WhatsApp automation.
 */
class WooCommerceAlertProcessor(context: Context) {

    private val appContext = context.applicationContext
    private val manager = WooCommerceManager(context)
    private val sender = GlobalSenderManager(context)
    private val reverseAIManager = ReverseAIManager(appContext)
    private val inFlightAlertIds = ConcurrentHashMap.newKeySet<String>()

    suspend fun processPendingAlerts(limit: Int = 5): WooCommerceAlertProcessResult =
        withContext(Dispatchers.IO) {
            val pendingAlerts = manager.fetchPendingAlerts(limit)
            if (pendingAlerts.isEmpty()) {
                return@withContext WooCommerceAlertProcessResult()
            }

            var sent = 0
            var failed = 0

            pendingAlerts.forEachIndexed { index, alert ->
                try {
                    if (processSingleAlert(alert)) sent++ else failed++
                } catch (e: Exception) {
                    Log.e(TAG, "Alert send failed for ${alert.alertId}: ${e.message}", e)
                    manager.markAlertAttemptFailed(
                        alertId = alert.alertId,
                        errorMessage = e.message ?: "Unknown send error"
                    )
                    failed++
                }

                if (index < pendingAlerts.lastIndex) {
                    delay(1200)
                }
            }

            WooCommerceAlertProcessResult(
                totalFetched = pendingAlerts.size,
                sentCount = sent,
                failedCount = failed
            )
        }

    /**
     * Real-time listener: request aate hi GlobalSender se owner message send karega.
     */
    fun startRealtimeListener(
        scope: CoroutineScope,
        onProcessed: ((WooCommerceAlert, Boolean) -> Unit)? = null
    ): ListenerRegistration? {
        if (manager.currentUserId.isBlank()) return null

        return manager.listenForPendingAlerts { alert ->
            if (!inFlightAlertIds.add(alert.alertId)) return@listenForPendingAlerts

            scope.launch(Dispatchers.IO) {
                var success = false
                try {
                    success = processSingleAlert(alert)
                } catch (e: Exception) {
                    Log.e(TAG, "Realtime alert process failed: ${e.message}", e)
                    manager.markAlertAttemptFailed(
                        alertId = alert.alertId,
                        errorMessage = e.message ?: "Unknown realtime send error"
                    )
                } finally {
                    inFlightAlertIds.remove(alert.alertId)
                    onProcessed?.invoke(alert, success)
                }
            }
        }
    }

    private suspend fun processSingleAlert(alert: WooCommerceAlert): Boolean {
        val targetPhone = reverseAIManager.ownerPhoneNumber.trim().ifBlank { alert.toPhone.trim() }
        if (targetPhone.isBlank()) {
            manager.markAlertAttemptFailed(
                alertId = alert.alertId,
                errorMessage = "Owner Assistant phone is not set."
            )
            return false
        }

        val sendResult = sender.sendTextViaAccessibility(
            phoneNumber = targetPhone,
            message = alert.message
        )

        return if (sendResult.success) {
            manager.markAlertSent(alert.alertId)
            true
        } else {
            manager.markAlertAttemptFailed(
                alertId = alert.alertId,
                errorMessage = "${sendResult.status}: ${sendResult.message}"
            )
            false
        }
    }

    private companion object {
        const val TAG = "WooCommerceAlertProc"
    }
}
