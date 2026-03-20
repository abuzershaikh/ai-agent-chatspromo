package com.message.bulksend.aiagent.tools.paymentverification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.message.bulksend.R
import java.util.Locale

class OwnerPaymentReviewNotifier(private val context: Context) {

    private val appContext = context.applicationContext

    fun notifyPendingReview(verification: PaymentVerification, source: String) {
        createChannelIfNeeded()

        val openIntent =
            Intent(appContext, OwnerPaymentApprovalActivity::class.java).apply {
                putExtra(EXTRA_VERIFICATION_ID, verification.id)
                putExtra(EXTRA_SOURCE, source)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val openPendingIntent =
            PendingIntent.getActivity(
                appContext,
                verification.id.hashCode(),
                openIntent,
                pendingFlags()
            )

        val approveIntent =
            Intent(appContext, PaymentReviewNotificationReceiver::class.java).apply {
                action = ACTION_APPROVE
                putExtra(EXTRA_VERIFICATION_ID, verification.id)
                putExtra(EXTRA_SOURCE, source)
            }
        val approvePendingIntent =
            PendingIntent.getBroadcast(
                appContext,
                verification.id.hashCode() + 11,
                approveIntent,
                pendingFlags()
            )

        val rejectIntent =
            Intent(appContext, PaymentReviewNotificationReceiver::class.java).apply {
                action = ACTION_REJECT
                putExtra(EXTRA_VERIFICATION_ID, verification.id)
                putExtra(EXTRA_SOURCE, source)
            }
        val rejectPendingIntent =
            PendingIntent.getBroadcast(
                appContext,
                verification.id.hashCode() + 19,
                rejectIntent,
                pendingFlags()
            )

        val amountLine =
            if (verification.amount > 0) "Amount: Rs ${formatAmount(verification.amount)}"
            else "Amount: Not found"
        val orderLine =
            if (verification.orderId.isNotBlank()) "Order: ${verification.orderId}" else "Order: -"
        val title =
            "Payment Review Needed: ${verification.customerPhone.ifBlank { "Unknown User" }}"
        val body = "$amountLine\n$orderLine\nTap Approve/Reject to confirm payment."

        val notification =
            NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText("Owner confirmation required.")
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(openPendingIntent)
                .addAction(R.drawable.ic_notification, "Approve", approvePendingIntent)
                .addAction(R.drawable.ic_notification, "Reject", rejectPendingIntent)
                .build()

        notifyInternal(notificationIdForVerification(verification.id), notification)
    }

    fun notifyDecisionResult(verificationId: String, approved: Boolean) {
        createChannelIfNeeded()
        val status = if (approved) "APPROVED" else "REJECTED"
        val notification =
            NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Payment $status")
                .setContentText("Verification ID: ${verificationId.takeLast(8)}")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
        notifyInternal(notificationIdForVerification(verificationId) + 1, notification)
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Payment Owner Review",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Owner confirmation notifications for payment screenshot verification."
                enableVibration(true)
            }
        manager.createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    private fun notifyInternal(notificationId: Int, notification: android.app.Notification) {
        if (!canPostNotifications()) return
        NotificationManagerCompat.from(appContext).notify(notificationId, notification)
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun pendingFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
    }

    private fun notificationIdForVerification(id: String): Int {
        return kotlin.math.abs(id.hashCode()).coerceAtLeast(1000)
    }

    private fun formatAmount(amount: Double): String {
        return if (amount % 1.0 == 0.0) amount.toInt().toString()
        else String.format(Locale.US, "%.2f", amount)
    }

    companion object {
        const val CHANNEL_ID = "payment_owner_review_channel"
        const val ACTION_APPROVE = "com.message.bulksend.payment.ACTION_APPROVE"
        const val ACTION_REJECT = "com.message.bulksend.payment.ACTION_REJECT"
        const val EXTRA_VERIFICATION_ID = "extra_verification_id"
        const val EXTRA_SOURCE = "extra_source"
    }
}

