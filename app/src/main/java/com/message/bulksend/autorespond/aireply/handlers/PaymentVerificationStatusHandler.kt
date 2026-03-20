package com.message.bulksend.autorespond.aireply.handlers

import android.content.Context
import android.util.Log
import com.message.bulksend.aiagent.tools.ecommerce.PaymentFlowModeManager
import com.message.bulksend.aiagent.tools.ecommerce.RazorPaymentManager
import com.message.bulksend.aiagent.tools.paymentverification.PaymentVerification
import com.message.bulksend.aiagent.tools.paymentverification.PaymentVerificationAIIntegration
import com.message.bulksend.aiagent.tools.paymentverification.PaymentVerificationManager
import com.message.bulksend.autorespond.ai.payment.PaymentSheetManager
import com.message.bulksend.autorespond.database.MessageEntity
import com.message.bulksend.autorespond.database.MessageRepository
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Smart payment verification decision handler.
 *
 * Decision order:
 * 1) Check real Razorpay API status for this phone (if configured)
 * 2) Check conversation/payment history to detect preferred payment source
 * 3) If mixed methods and Razorpay is not paid, prefer screenshot verification flow
 */
class PaymentVerificationStatusHandler(
    private val appContext: Context
) : MessageHandler {

    private val verificationManager = PaymentVerificationManager.getInstance(appContext)
    private val verificationIntegration = PaymentVerificationAIIntegration.getInstance(appContext)
    private val razorpayManager = RazorPaymentManager(appContext)
    private val flowModeManager = PaymentFlowModeManager(appContext)
    private val messageRepository = MessageRepository(appContext)
    private val paymentSheetManager = PaymentSheetManager(appContext)

    override fun getPriority(): Int = 0

    override suspend fun handle(
        context: Context,
        message: String,
        response: String,
        senderPhone: String,
        senderName: String
    ): HandlerResult {
        val lower = message.lowercase(Locale.ROOT).trim()
        if (!isPaymentStatusQuery(lower)) {
            return HandlerResult(success = false)
        }

        val queryHint = detectQueryHint(lower)
        val historySignal = loadHistorySignal(senderPhone)
        val razorpaySnapshot = checkRazorpaySnapshot(senderPhone, message)

        if (razorpaySnapshot.ambiguousCandidates.isNotEmpty() && queryHint != QueryHint.SCREENSHOT) {
            return HandlerResult(
                success = true,
                modifiedResponse = PaymentLinkResolver.buildAmbiguityPrompt(razorpaySnapshot.ambiguousCandidates),
                shouldStopChain = true
            )
        }

        if (isLatestPaid(razorpaySnapshot) && shouldPreferRazorpayPaid(queryHint, historySignal, razorpaySnapshot)) {
            logRazorpaySnapshotToSheet(senderPhone, senderName, razorpaySnapshot)
            return HandlerResult(
                success = true,
                modifiedResponse = buildRazorpayPaidResponse(razorpaySnapshot),
                shouldStopChain = true
            )
        }

        val route = chooseRoute(queryHint, historySignal, razorpaySnapshot)

        return when (route) {
            PaymentRoute.RAZORPAY -> {
                handleRazorpayRoute(
                    senderPhone = senderPhone,
                    senderName = senderName,
                    historySignal = historySignal,
                    razorpaySnapshot = razorpaySnapshot
                )
            }
            PaymentRoute.SCREENSHOT -> {
                handleScreenshotRoute(
                    senderPhone = senderPhone,
                    senderName = senderName,
                    historySignal = historySignal,
                    razorpaySnapshot = razorpaySnapshot
                )
            }
            PaymentRoute.UNKNOWN -> {
                // Fallback to existing RazorpayStatusCheckHandler for generic unknown cases.
                HandlerResult(success = false)
            }
        }
    }

    private suspend fun handleRazorpayRoute(
        senderPhone: String,
        senderName: String,
        historySignal: HistorySignal,
        razorpaySnapshot: RazorpaySnapshot
    ): HandlerResult {
        if (!razorpaySnapshot.configured) {
            return if (historySignal.hasScreenshotFlow) {
                val screenshotFallback = buildScreenshotUploadFallback(senderPhone, prefix = "Razorpay configured nahi hai.")
                HandlerResult(success = true, modifiedResponse = screenshotFallback, shouldStopChain = true)
            } else {
                HandlerResult(
                    success = true,
                    modifiedResponse = "Payment verification abhi available nahi hai kyunki Razorpay settings configured nahi hain.",
                    shouldStopChain = true
                )
            }
        }

        if (isLatestPaid(razorpaySnapshot)) {
            logRazorpaySnapshotToSheet(senderPhone, senderName, razorpaySnapshot)
            return HandlerResult(
                success = true,
                modifiedResponse = buildRazorpayPaidResponse(razorpaySnapshot),
                shouldStopChain = true
            )
        }

        if (razorpaySnapshot.latestLink != null) {
            logRazorpaySnapshotToSheet(senderPhone, senderName, razorpaySnapshot)
            val statusResponse = buildRazorpayLatestStatusResponse(
                senderPhone = senderPhone,
                historySignal = historySignal,
                razorpaySnapshot = razorpaySnapshot
            )
            return HandlerResult(
                success = true,
                modifiedResponse = statusResponse,
                shouldStopChain = true
            )
        }

        if (historySignal.hasScreenshotFlow) {
            val screenshotFallback = buildScreenshotUploadFallback(senderPhone, prefix = "Razorpay ka recent link record nahi mila.")
            return HandlerResult(success = true, modifiedResponse = screenshotFallback, shouldStopChain = true)
        }

        return HandlerResult(
            success = true,
            modifiedResponse =
                """
                Mujhe aapka koi recent payment link nahi mila.
                Kya main aapke liye naya payment link generate kar du?
                """.trimIndent(),
            shouldStopChain = true
        )
    }

    private suspend fun handleScreenshotRoute(
        senderPhone: String,
        senderName: String,
        historySignal: HistorySignal,
        razorpaySnapshot: RazorpaySnapshot
    ): HandlerResult {
        try {
            verificationManager.fetchFromFirestore()
        } catch (e: Exception) {
            Log.e("PaymentVerifyStatus", "Fetch before screenshot check failed: ${e.message}")
        }

        val latestVerification = verificationManager.getLatestVerificationForCustomer(senderPhone)
        if (latestVerification != null) {
            verificationManager.triggerOwnerReviewIfPending(
                verification = latestVerification,
                source = "customer_done"
            )
            logScreenshotVerificationToSheet(senderPhone, senderName, latestVerification)
            return HandlerResult(
                success = true,
                modifiedResponse = buildScreenshotStatusResponse(latestVerification, senderPhone),
                shouldStopChain = true
            )
        }

        val prefix = when {
            historySignal.hasBoth && razorpaySnapshot.paidLink == null ->
                "Razorpay API me current time ke around koi PAID entry nahi mili."
            historySignal.hasScreenshotFlow -> "Screenshot verification record abhi nahi mila."
            else -> ""
        }

        return HandlerResult(
            success = true,
            modifiedResponse = buildScreenshotUploadFallback(senderPhone, prefix = prefix),
            shouldStopChain = true
        )
    }

    private fun detectQueryHint(lower: String): QueryHint {
        val razorpayTerms = listOf("razorpay", "payment link", "link payment", "rzp")
        if (razorpayTerms.any { lower.contains(it) }) return QueryHint.RAZORPAY

        val screenshotTerms = listOf("qr", "upi", "bank", "screenshot", "screen shot", "upload", "verify link")
        if (screenshotTerms.any { lower.contains(it) }) return QueryHint.SCREENSHOT

        return QueryHint.NONE
    }

    private suspend fun loadHistorySignal(senderPhone: String): HistorySignal {
        return try {
            val messages = messageRepository.getRecentMessagesSync(senderPhone, 40)
            analyzeHistory(messages)
        } catch (e: Exception) {
            Log.e("PaymentVerifyStatus", "Failed to load message history: ${e.message}")
            HistorySignal()
        }
    }

    private fun analyzeHistory(messages: List<MessageEntity>): HistorySignal {
        var lastScreenshotAt = 0L
        var lastRazorpayAt = 0L

        for (msg in messages) {
            val outgoing = msg.outgoingMessage.lowercase(Locale.ROOT)
            if (outgoing.isBlank()) continue

            if (containsScreenshotFlowSignal(outgoing)) {
                lastScreenshotAt = max(lastScreenshotAt, msg.timestamp)
            }
            if (containsRazorpayFlowSignal(outgoing)) {
                lastRazorpayAt = max(lastRazorpayAt, msg.timestamp)
            }
        }

        return HistorySignal(
            lastScreenshotFlowAt = lastScreenshotAt,
            lastRazorpayFlowAt = lastRazorpayAt
        )
    }

    private suspend fun checkRazorpaySnapshot(
        senderPhone: String,
        userMessage: String
    ): RazorpaySnapshot {
        if (!flowModeManager.canUseRazorpay()) {
            return RazorpaySnapshot(configured = false)
        }
        if (!razorpayManager.isConfigured()) return RazorpaySnapshot(configured = false)

        return try {
            val resolution = PaymentLinkResolver.resolveForMessage(
                manager = razorpayManager,
                senderPhone = senderPhone,
                message = userMessage,
                limit = 8
            )

            if (resolution.isAmbiguous) {
                return RazorpaySnapshot(
                    configured = true,
                    latestLink = resolution.recentLinks.firstOrNull(),
                    latestStatus = resolution.recentLinks.firstOrNull()?.status?.lowercase(Locale.ROOT),
                    paidLink = null,
                    ambiguousCandidates = resolution.ambiguousCandidates
                )
            }

            val selectedLink = resolution.selectedLink
            if (selectedLink == null) {
                return RazorpaySnapshot(configured = true)
            }

            var latestStatus = selectedLink.status.lowercase(Locale.ROOT)
            val apiStatus = razorpayManager.verifyPaymentStatusFromApi(selectedLink.id)
            if (apiStatus !in setOf("unknown_no_creds", "api_error", "exception")) {
                latestStatus = apiStatus.lowercase(Locale.ROOT)
            }

            RazorpaySnapshot(
                configured = true,
                latestLink = selectedLink,
                latestStatus = latestStatus,
                paidLink = if (latestStatus == "paid") selectedLink else null,
                ambiguousCandidates = emptyList()
            )
        } catch (e: Exception) {
            Log.e("PaymentVerifyStatus", "Razorpay snapshot check failed: ${e.message}", e)
            RazorpaySnapshot(configured = true)
        }
    }

    private fun chooseRoute(
        queryHint: QueryHint,
        historySignal: HistorySignal,
        razorpaySnapshot: RazorpaySnapshot
    ): PaymentRoute {
        if (!flowModeManager.canUseRazorpay()) return PaymentRoute.SCREENSHOT
        if (queryHint == QueryHint.SCREENSHOT) return PaymentRoute.SCREENSHOT
        if (queryHint == QueryHint.RAZORPAY) return PaymentRoute.RAZORPAY

        if (historySignal.hasBoth) {
            // Mixed payment methods: always follow latest method sent in chat.
            // If latest was Razorpay, report Razorpay link status directly (including PENDING).
            if (historySignal.lastScreenshotFlowAt >= historySignal.lastRazorpayFlowAt) {
                return PaymentRoute.SCREENSHOT
            }
            return PaymentRoute.RAZORPAY
        }

        if (historySignal.hasScreenshotFlow && !historySignal.hasRazorpayFlow) return PaymentRoute.SCREENSHOT
        if (historySignal.hasRazorpayFlow && !historySignal.hasScreenshotFlow) return PaymentRoute.RAZORPAY

        return if (razorpaySnapshot.latestLink != null) PaymentRoute.RAZORPAY else PaymentRoute.UNKNOWN
    }

    private fun shouldPreferRazorpayPaid(
        queryHint: QueryHint,
        historySignal: HistorySignal,
        razorpaySnapshot: RazorpaySnapshot
    ): Boolean {
        if (queryHint == QueryHint.SCREENSHOT && historySignal.hasScreenshotFlow) return false
        if (historySignal.hasBoth) {
            if (historySignal.lastScreenshotFlowAt >= historySignal.lastRazorpayFlowAt) return false
            if (!isLatestPaid(razorpaySnapshot)) return false
        }
        return true
    }

    private fun buildRazorpayPaidResponse(snapshot: RazorpaySnapshot): String {
        val paidLink = snapshot.latestLink ?: snapshot.paidLink ?: return "Payment verify ho gaya."
        val amountText = formatAmount(paidLink.amount)
        val paidAt = formatDateTime(paidLink.createdAt)
        val customerLine = paidLink.customerName?.takeIf { it.isNotBlank() }?.let { "\nCustomer: $it" } ?: ""

        return """
            Haan, Razorpay se payment receive ho gaya hai.
            
            Status: PAID
            Amount: Rs $amountText
            Link ID: ${paidLink.id}
            For: ${paidLink.description}$customerLine
            ${if (paidAt.isNotBlank()) "Link Sent Time: $paidAt" else ""}
            """.trimIndent()
    }

    private suspend fun buildRazorpayLatestStatusResponse(
        senderPhone: String,
        historySignal: HistorySignal,
        razorpaySnapshot: RazorpaySnapshot
    ): String {
        val latest = razorpaySnapshot.latestLink ?: return "Razorpay ka recent status nahi mila."
        val status = (razorpaySnapshot.latestStatus ?: latest.status).lowercase(Locale.ROOT)
        val amountText = formatAmount(latest.amount)
        val sentAt = formatDateTime(latest.createdAt)
        val sentAtLine = if (sentAt.isNotBlank()) "\nLink Sent Time: $sentAt" else ""
        val customerLine = latest.customerName?.takeIf { it.isNotBlank() }?.let { "\nCustomer: $it" } ?: ""

        val base = when (status) {
            "created", "issued" ->
                """
                Razorpay status abhi PENDING hai.
                
                Status: PENDING
                Amount: Rs $amountText
                Link ID: ${latest.id}
                For: ${latest.description}$customerLine
                Link: ${latest.shortUrl}$sentAtLine
                """.trimIndent()
            "expired" ->
                """
                Razorpay payment link EXPIRED ho chuka hai.
                
                Status: EXPIRED
                Amount: Rs $amountText
                Link ID: ${latest.id}
                For: ${latest.description}$customerLine$sentAtLine
                """.trimIndent()
            "cancelled" ->
                """
                Razorpay payment status CANCELLED hai.
                
                Status: CANCELLED
                Amount: Rs $amountText
                Link ID: ${latest.id}
                For: ${latest.description}$customerLine$sentAtLine
                """.trimIndent()
            else ->
                """
                Razorpay payment status: ${status.uppercase(Locale.ROOT)}
                
                Status: ${status.uppercase(Locale.ROOT)}
                Amount: Rs $amountText
                Link ID: ${latest.id}
                For: ${latest.description}$customerLine$sentAtLine
                """.trimIndent()
        }

        if (!historySignal.hasScreenshotFlow || status == "paid") return base
        if (historySignal.lastScreenshotFlowAt <= historySignal.lastRazorpayFlowAt) return base

        val screenshotFallback = buildScreenshotUploadFallback(senderPhone, prefix = "")
        return "$base\n\nAgar aapne QR/UPI/Bank se payment kiya tha, to:\n$screenshotFallback"
    }

    private suspend fun buildScreenshotStatusResponse(
        verification: PaymentVerification,
        senderPhone: String
    ): String {
        val normalizedStatus = normalizeStatusForReply(verification)
        return when (normalizedStatus) {
            "APPROVED" -> {
                val amountLine =
                    if (verification.amount > 0) {
                        "Amount: Rs ${formatAmount(verification.amount)}\n"
                    } else {
                        ""
                    }
                val transactionLine =
                    if (verification.transactionId.isNotBlank()) {
                        "Transaction ID: ${verification.transactionId}\n"
                    } else {
                        ""
                    }

                """
                Haan, payment receive ho gaya hai.
                
                Status: APPROVED
                $amountLine$transactionLine
                """.trimIndent()
            }
            "MANUAL_REVIEW" ->
                """
                Payment verification abhi manual review mein hai.
                Team details check karke confirm karegi, please thoda wait karein.
                """.trimIndent()
            "PENDING" ->
                """
                Screenshot mil gaya hai.
                Owner verification pending hai. Confirm hone ke baad final update diya jayega.
                """.trimIndent()
            "REJECTED" -> {
                val link = generateReuploadLink(verification, senderPhone)
                val linkLine =
                    if (link.isNotBlank()) {
                        "\nSahi payment wala clear screenshot is link par dubara upload karein:\n$link"
                    } else {
                        ""
                    }

                """
                Uploaded screenshot details match nahi hui, isliye verification reject hua.
$linkLine
                """.trimIndent()
            }
            else -> "Screenshot mil gaya hai. Verification process chal raha hai, please thoda wait karein."
        }
    }

    private suspend fun buildScreenshotUploadFallback(
        senderPhone: String,
        prefix: String
    ): String {
        val link = verificationIntegration.generateVerificationLinkForPayment(senderPhone)
        val prefixLine = if (prefix.isNotBlank()) "$prefix\n\n" else ""

        return if (link != null) {
            """
            ${prefixLine}Jo payment aapne abhi kiya hai uska clear screenshot is link par upload karein:
            $link
            """.trimIndent()
        } else {
            "${prefixLine}Screenshot verification link currently off hai."
        }
    }

    private fun normalizeStatusForReply(verification: PaymentVerification): String {
        val status = verification.status.trim().uppercase(Locale.ROOT)
        if (status in setOf("APPROVED", "REJECTED", "MANUAL_REVIEW", "PENDING")) return status

        val recommendation = verification.recommendation.trim().uppercase(Locale.ROOT)
        val paymentSuccess = isPaymentSuccess(verification.paymentStatus)
        val hasCustomExpected = hasCustomExpectedFields(verification.customFieldsExpected)
        val detailsMatch =
            (verification.expectedName.isBlank() || verification.nameMatched) &&
                (verification.expectedUpiId.isBlank() || verification.upiMatched) &&
                (verification.expectedAmount <= 0.0 || verification.amountMatched) &&
                (!hasCustomExpected || verification.customFieldsMatched)

        return when {
            recommendation == "REJECTED" -> "REJECTED"
            paymentSuccess && detailsMatch -> "APPROVED"
            recommendation == "PAID" && paymentSuccess -> "APPROVED"
            recommendation == "MANUAL_REVIEW" -> "MANUAL_REVIEW"
            else -> "PENDING"
        }
    }

    private fun isPaymentStatusQuery(lower: String): Boolean {
        val directPhrases = listOf(
            "payment verify",
            "verify payment",
            "payment verification",
            "payment status",
            "check payment",
            "payment check",
            "payment done",
            "payment ho gaya",
            "payment received",
            "payment receive",
            "transaction status",
            "screenshot verify",
            "manual review",
            "approve payment",
            "reject payment",
            "qr par kiya",
            "qr se kiya",
            "upi se kiya",
            "bank se kiya",
            "payment kar diya",
            "payment kardiya",
            "maine payment kiya",
            "mene payment kiya",
            "ok payment done"
        )
        if (directPhrases.any { lower.contains(it) }) return true

        val paymentWords = listOf("payment", "paid", "transaction", "txn", "amount")
        val verifyWords = listOf(
            "verify",
            "verification",
            "status",
            "check",
            "confirm",
            "review",
            "received",
            "done",
            "hua",
            "hogaya"
        )

        if (paymentWords.any { lower.contains(it) } && verifyWords.any { lower.contains(it) }) {
            return true
        }

        val methodWords = listOf("qr", "upi", "bank")
        val completionWords = listOf("kiya", "kia", "done", "paid", "ho gaya", "hogaya", "kar diya", "kardiya")
        return methodWords.any { lower.contains(it) } && completionWords.any { lower.contains(it) }
    }

    private fun containsScreenshotFlowSignal(text: String): Boolean {
        return text.contains("payment-verify.html") ||
            text.contains("upload screenshot") ||
            text.contains("verification ke liye screenshot") ||
            text.contains("upi id:") ||
            text.contains("ifsc") ||
            text.contains("account number")
    }

    private fun containsRazorpayFlowSignal(text: String): Boolean {
        return text.contains("rzp.io") ||
            text.contains("razorpay") ||
            (text.contains("payment link") && !text.contains("payment-verify.html"))
    }

    private fun isPaymentSuccess(paymentStatus: String): Boolean {
        val value = paymentStatus.lowercase(Locale.ROOT)
        return value.contains("success") ||
            value.contains("successful") ||
            value.contains("completed") ||
            value.contains("paid")
    }

    private fun formatAmount(amount: Double): String {
        return if (amount % 1.0 == 0.0) amount.toInt().toString()
        else String.format(Locale.US, "%.2f", amount)
    }

    private fun isLatestPaid(snapshot: RazorpaySnapshot): Boolean {
        return snapshot.latestStatus?.lowercase(Locale.ROOT) == "paid"
    }

    private fun formatDateTime(createdAt: String): String {
        val millis = parseCreatedAtMillis(createdAt) ?: return createdAt
        return try {
            SimpleDateFormat("dd MMM yyyy hh:mm a", Locale.getDefault()).format(Date(millis))
        } catch (_: Exception) {
            createdAt
        }
    }

    private fun parseCreatedAtMillis(createdAt: String): Long? {
        if (createdAt.isBlank()) return null

        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX"
        )

        for (pattern in patterns) {
            try {
                val parser = SimpleDateFormat(pattern, Locale.US)
                parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val date = parser.parse(createdAt)
                if (date != null) return date.time
            } catch (_: Exception) {
            }
        }

        return null
    }

    private suspend fun generateReuploadLink(
        verification: PaymentVerification,
        senderPhone: String
    ): String {
        return try {
            val customFieldsExpected = parseCustomFieldJson(verification.customFieldsExpected)
            verificationIntegration.generateVerificationLinkForPayment(
                phoneNumber = senderPhone,
                orderId = verification.orderId,
                expectedName = verification.expectedName,
                expectedUpiId = verification.expectedUpiId,
                expectedAmount = verification.expectedAmount,
                customFieldValues = customFieldsExpected
            ) ?: ""
        } catch (e: Exception) {
            Log.e("PaymentVerifyStatus", "Failed to generate re-upload link: ${e.message}", e)
            ""
        }
    }

    private fun parseCustomFieldJson(customFieldJson: String): Map<String, String> {
        if (customFieldJson.isBlank()) return emptyMap()

        return try {
            val json = JSONObject(customFieldJson)
            val result = mutableMapOf<String, String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = json.optString(key, "")
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun hasCustomExpectedFields(customFieldJson: String): Boolean {
        return parseCustomFieldJson(customFieldJson).isNotEmpty()
    }

    private suspend fun logRazorpaySnapshotToSheet(
        senderPhone: String,
        senderName: String,
        snapshot: RazorpaySnapshot
    ) {
        val link = snapshot.latestLink ?: snapshot.paidLink ?: return
        val status = snapshot.latestStatus ?: link.status
        try {
            paymentSheetManager.logRazorpayStatus(
                phoneNumber = senderPhone,
                fallbackCustomerName = senderName,
                link = link,
                status = status
            )
        } catch (e: Exception) {
            Log.e("PaymentVerifyStatus", "Failed to sync Razorpay status to sheet: ${e.message}", e)
        }
    }

    private suspend fun logScreenshotVerificationToSheet(
        senderPhone: String,
        senderName: String,
        verification: PaymentVerification
    ) {
        try {
            paymentSheetManager.logScreenshotVerification(
                phoneNumber = senderPhone,
                fallbackCustomerName = senderName,
                verification = verification
            )
        } catch (e: Exception) {
            Log.e("PaymentVerifyStatus", "Failed to sync screenshot verification to sheet: ${e.message}", e)
        }
    }
}

private enum class QueryHint {
    RAZORPAY,
    SCREENSHOT,
    NONE
}

private enum class PaymentRoute {
    RAZORPAY,
    SCREENSHOT,
    UNKNOWN
}

private data class HistorySignal(
    val lastScreenshotFlowAt: Long = 0L,
    val lastRazorpayFlowAt: Long = 0L
) {
    val hasScreenshotFlow: Boolean get() = lastScreenshotFlowAt > 0
    val hasRazorpayFlow: Boolean get() = lastRazorpayFlowAt > 0
    val hasBoth: Boolean get() = hasScreenshotFlow && hasRazorpayFlow
}

private data class RazorpaySnapshot(
    val configured: Boolean = false,
    val latestLink: RazorPaymentManager.PaymentLinkInfo? = null,
    val latestStatus: String? = null,
    val paidLink: RazorPaymentManager.PaymentLinkInfo? = null,
    val ambiguousCandidates: List<RazorPaymentManager.PaymentLinkInfo> = emptyList()
)
