package com.message.bulksend.aiagent.tools.ecommerce

import android.content.Context

/**
 * Global payment channel mode lock.
 *
 * Only one family stays active at a time:
 * - MANUAL_QR_UPI_BANK (QR / UPI / Custom fields)
 * - RAZORPAY
 */
class PaymentFlowModeManager(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getMode(): PaymentFlowMode {
        val raw = prefs.getString(KEY_MODE, PaymentFlowMode.MANUAL_QR_UPI_BANK.name)
        return runCatching { PaymentFlowMode.valueOf(raw ?: PaymentFlowMode.MANUAL_QR_UPI_BANK.name) }
            .getOrDefault(PaymentFlowMode.MANUAL_QR_UPI_BANK)
    }

    fun setMode(mode: PaymentFlowMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }

    fun canUseManualMethods(): Boolean = getMode() == PaymentFlowMode.MANUAL_QR_UPI_BANK

    fun canUseRazorpay(): Boolean = getMode() == PaymentFlowMode.RAZORPAY

    companion object {
        private const val PREF_NAME = "payment_flow_mode_settings"
        private const val KEY_MODE = "active_payment_mode"
    }
}

enum class PaymentFlowMode {
    MANUAL_QR_UPI_BANK,
    RAZORPAY
}

