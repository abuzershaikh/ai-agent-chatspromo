package com.message.bulksend.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

object OverlayHelper {
    
    private const val TAG = "OverlayHelper"
    
    fun hasOverlayPermission(context: Context): Boolean {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
        Log.d(TAG, "Has overlay permission: $hasPermission")
        return hasPermission
    }
    
    fun requestOverlayPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "Requesting overlay permission")
        }
    }
    
    fun startOverlay(context: Context, sent: Int = 0, total: Int = 0) {
        Log.d(TAG, "Starting overlay - sent: $sent, total: $total")
        if (hasOverlayPermission(context)) {
            try {
                val intent = Intent(context, OverlayService::class.java).apply {
                    putExtra(OverlayService.EXTRA_SENT, sent)
                    putExtra(OverlayService.EXTRA_TOTAL, total)
                }
                context.startService(intent)
                Log.d(TAG, "Overlay service started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting overlay service", e)
            }
        } else {
            Log.w(TAG, "No overlay permission, requesting...")
            requestOverlayPermission(context)
        }
    }
    
    fun stopOverlay(context: Context) {
        Log.d(TAG, "Stopping overlay")
        try {
            val intent = Intent(context, OverlayService::class.java)
            context.stopService(intent)
            Log.d(TAG, "Overlay service stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping overlay service", e)
        }
    }
    
    fun updateOverlay(context: Context, sent: Int, total: Int) {
        Log.d(TAG, "Updating overlay - sent: $sent, total: $total")
        try {
            val intent = Intent(context, OverlayService::class.java).apply {
                putExtra(OverlayService.EXTRA_SENT, sent)
                putExtra(OverlayService.EXTRA_TOTAL, total)
            }
            context.startService(intent)
            Log.d(TAG, "Overlay updated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating overlay", e)
        }
    }
}
