package com.message.bulksend.aiagent.tools.paymentverification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PaymentReviewNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val verificationId =
            intent.getStringExtra(OwnerPaymentReviewNotifier.EXTRA_VERIFICATION_ID).orEmpty()
        if (verificationId.isBlank()) return

        val action = intent.action.orEmpty()
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val manager = PaymentVerificationManager.getInstance(context.applicationContext)
                val notifier = OwnerPaymentReviewNotifier(context.applicationContext)

                when (action) {
                    OwnerPaymentReviewNotifier.ACTION_APPROVE -> {
                        manager.approvePayment(
                            verificationId,
                            notes = "Approved by owner from notification"
                        )
                        notifier.notifyDecisionResult(verificationId = verificationId, approved = true)
                    }

                    OwnerPaymentReviewNotifier.ACTION_REJECT -> {
                        manager.rejectPayment(
                            verificationId,
                            notes = "Rejected by owner from notification"
                        )
                        notifier.notifyDecisionResult(verificationId = verificationId, approved = false)
                    }
                }
            } catch (e: Exception) {
                Log.e("PaymentReviewReceiver", "Action failed: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

