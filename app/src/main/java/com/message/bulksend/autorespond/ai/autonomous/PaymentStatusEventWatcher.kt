package com.message.bulksend.autorespond.ai.autonomous

import android.content.Context
import com.message.bulksend.aiagent.tools.ecommerce.RazorPaymentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

class PaymentStatusEventWatcher private constructor(
    private val appContext: Context
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val runtime = AutonomousGoalRuntime(appContext)
    private val manager = RazorPaymentManager(appContext)

    fun startIfEligible() {
        if (!runtime.isContinuousEnabled()) return

        val email = manager.getUserEmail()?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (email.isBlank()) return

        synchronized(lock) {
            if (started) return
            started = true
        }

        scope.launch {
            manager.getPaymentLinksFlow(email).collectLatest { links ->
                val tracked = loadTrackedStatuses().toMutableMap()
                links.forEach { link ->
                    val normalizedStatus = normalizeStatus(link.status)
                    val contact = link.customerContact.orEmpty().trim()
                    if (contact.isBlank()) return@forEach
                    if (normalizedStatus.isBlank()) return@forEach

                    val existingStatus = tracked[link.id]
                    if (existingStatus == normalizedStatus) return@forEach

                    tracked[link.id] = normalizedStatus
                    if (existingStatus.isNullOrBlank()) return@forEach

                    runtime.enqueuePaymentStatusUpdate(
                        senderPhone = contact,
                        senderName = link.customerName.orEmpty(),
                        status = normalizedStatus,
                        amount = link.amount,
                        description = link.description,
                        linkId = link.id
                    )
                }
                saveTrackedStatuses(tracked)
            }
        }
    }

    private fun normalizeStatus(raw: String): String {
        val lower = raw.trim().lowercase(Locale.ROOT)
        return when (lower) {
            "created", "issued" -> "pending"
            "paid", "failed", "expired", "cancelled", "pending" -> lower
            else -> lower
        }
    }

    private fun loadTrackedStatuses(): Map<String, String> {
        val raw = prefs.getString(KEY_LAST_STATUSES, "{}") ?: "{}"
        return try {
            val obj = JSONObject(raw)
            val out = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                out[key] = obj.optString(key)
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun saveTrackedStatuses(statuses: Map<String, String>) {
        val obj = JSONObject()
        statuses.forEach { (key, value) -> obj.put(key, value) }
        prefs.edit().putString(KEY_LAST_STATUSES, obj.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "ai_agent_payment_event_watcher"
        private const val KEY_LAST_STATUSES = "last_statuses"

        @Volatile
        private var started: Boolean = false
        private val lock = Any()

        fun getInstance(context: Context): PaymentStatusEventWatcher {
            return PaymentStatusEventWatcher(context.applicationContext)
        }
    }
}
