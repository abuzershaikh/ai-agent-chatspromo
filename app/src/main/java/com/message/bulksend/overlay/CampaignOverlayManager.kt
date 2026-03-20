package com.message.bulksend.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent

/**
 * Manager to handle overlay integration with campaigns
 * Usage in Activity:
 *
 * val overlayManager = CampaignOverlayManager(this)
 * lifecycle.addObserver(overlayManager)
 *
 * // Start campaign with overlay
 * overlayManager.startCampaignWithOverlay(totalContacts)
 *
 * // Update progress
 * overlayManager.updateProgress(sent, total)
 *
 * // Stop campaign
 * overlayManager.stopCampaign()
 */
class CampaignOverlayManager(private val context: Context) : LifecycleObserver {

    private var isOverlayEnabled = false
    private var isPaused = false
    private var isCampaignRunning = false
    private var onStartCallback: (() -> Unit)? = null
    private var onStopCallback: (() -> Unit)? = null

    private val overlayReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.getStringExtra(OverlayService.EXTRA_ACTION)
            Log.d(TAG, "Received overlay action: $action")

            when (action) {
                "start" -> {
                    isPaused = false
                    onStartCallback?.invoke()
                }
                "stop" -> {
                    isPaused = true
                    onStopCallback?.invoke()
                }
            }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onCreate() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                overlayReceiver,
                IntentFilter(OverlayService.ACTION_CONTROL),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            ContextCompat.registerReceiver(
                context,
                overlayReceiver,
                IntentFilter(OverlayService.ACTION_CONTROL),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        Log.d(TAG, "Overlay receiver registered")
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        try {
            // Deactivate accessibility service when activity destroyed
            com.message.bulksend.bulksend.WhatsAppAutoSendService.deactivateService()
            Log.d(TAG, "Accessibility service deactivated")

            context.unregisterReceiver(overlayReceiver)
            if (isOverlayEnabled) {
                OverlayHelper.stopOverlay(context)
            }
            Log.d(TAG, "Overlay receiver unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
    }

    /**
     * Start campaign with overlay
     */
    fun startCampaignWithOverlay(totalContacts: Int) {
        if (OverlayHelper.hasOverlayPermission(context)) {
            OverlayHelper.startOverlay(context, 0, totalContacts)
            isOverlayEnabled = true
            isCampaignRunning = true
            isPaused = false
            Log.d(TAG, "Campaign started with overlay: $totalContacts contacts")
        } else {
            Log.w(TAG, "No overlay permission")
        }
    }

    /**
     * Update campaign progress
     */
    fun updateProgress(sent: Int, total: Int) {
        if (isOverlayEnabled) {
            OverlayHelper.updateOverlay(context, sent, total)
            Log.d(TAG, "Progress updated: $sent/$total")
        }
    }

    /**
     * Stop campaign and overlay
     */
    fun stopCampaign() {
        // Deactivate accessibility service
        com.message.bulksend.bulksend.WhatsAppAutoSendService.deactivateService()
        Log.d(TAG, "Accessibility service deactivated")

        if (isOverlayEnabled) {
            OverlayHelper.stopOverlay(context)
            isOverlayEnabled = false
            isCampaignRunning = false
            isPaused = false
            Log.d(TAG, "Campaign stopped")
        }
    }

    /**
     * Check if campaign is currently running
     */
    fun isCampaignRunning(): Boolean = isCampaignRunning && !isPaused

    /**
     * Check if campaign is paused by user
     */
    fun isPaused(): Boolean = isPaused

    /**
     * Set callback for start button
     */
    fun setOnStartCallback(callback: () -> Unit) {
        onStartCallback = callback
    }

    /**
     * Set callback for stop button
     */
    fun setOnStopCallback(callback: () -> Unit) {
        onStopCallback = callback
    }

    companion object {
        private const val TAG = "CampaignOverlayManager"
    }
}
