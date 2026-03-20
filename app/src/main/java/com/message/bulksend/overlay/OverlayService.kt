package com.message.bulksend.overlay

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    
    private var sentCount = 0
    private var totalCount = 0
    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            createOverlay()
            Log.d("OverlayService", "Overlay created successfully")
        } catch (e: Exception) {
            Log.e("OverlayService", "Error creating overlay", e)
        }
    }

    private fun createOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            x = 0
            y = 0
        }

        // Create overlay programmatically to avoid layout issues
        overlayView = createOverlayView()
        
        setupViews()
        
        try {
            windowManager?.addView(overlayView, params)
            Log.d("OverlayService", "View added to window manager")
        } catch (e: Exception) {
            Log.e("OverlayService", "Error adding view to window", e)
        }
    }
    
    private fun createOverlayView(): View {
        val dpToPx = { dp: Int -> (dp * resources.displayMetrics.density).toInt() }
        
        val cardView = CardView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                dpToPx(320),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            radius = 48f
            cardElevation = 24f
            setCardBackgroundColor(Color.parseColor("#E6000000"))
            setPadding(0, 0, 0, 0)
        }
        
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        // Title Bar with green background
        val titleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setPadding(dpToPx(20), dpToPx(14), dpToPx(20), dpToPx(14))
            gravity = Gravity.CENTER_VERTICAL
        }
        
        val tvTitle = TextView(this).apply {
            text = "📊 Campaign Status"
            setTextColor(Color.WHITE)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        
        val btnClose = Button(this).apply {
            text = "✕"
            textSize = 16f
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(40),
                dpToPx(40)
            )
            setPadding(0, 0, 0, 0)
        }
        
        titleBar.addView(tvTitle)
        titleBar.addView(btnClose)
        
        // Content Area
        val contentArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20))
            gravity = Gravity.CENTER
        }
        
        val tvStatus = TextView(this).apply {
            text = "Sent: 0 / 0\nRemaining: 0"
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(16))
            setLineSpacing(dpToPx(4).toFloat(), 1f)
        }
        
        // Divider
        val divider = View(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(1)
            )
            setBackgroundColor(Color.parseColor("#40FFFFFF"))
        }
        
        // Button Container
        val buttonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(20), 0, 0)
        }
        
        // Single toggle button
        val btnToggle = Button(this).apply {
            text = "■ Stop"
            textSize = 14f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F44336"))
                cornerRadius = dpToPx(24).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(48)
            )
        }
        
        buttonContainer.addView(btnToggle)
        
        contentArea.addView(tvStatus)
        contentArea.addView(divider)
        contentArea.addView(buttonContainer)
        
        container.addView(titleBar)
        container.addView(contentArea)
        
        cardView.addView(container)
        
        // Store tags
        tvStatus.tag = "tvStatus"
        btnToggle.tag = "btnToggle"
        btnClose.tag = "btnClose"
        
        return cardView
    }

    private fun setupViews() {
        val cardView = overlayView as? CardView
        val container = cardView?.getChildAt(0) as? LinearLayout
        
        val titleBar = container?.getChildAt(0) as? LinearLayout
        val btnClose = titleBar?.findViewWithTag<Button>("btnClose")
        
        val contentArea = container?.getChildAt(1) as? LinearLayout
        val buttonContainer = contentArea?.getChildAt(2) as? LinearLayout
        val btnToggle = buttonContainer?.findViewWithTag<Button>("btnToggle")

        btnToggle?.setOnClickListener {
            isRunning = !isRunning
            
            if (isRunning) {
                // Campaign is running, show Stop button
                btnToggle.text = "■ Stop"
                btnToggle.background = GradientDrawable().apply {
                    setColor(Color.parseColor("#F44336"))
                    cornerRadius = (24 * resources.displayMetrics.density).toInt().toFloat()
                }
                
                // Resume campaign - reset stop flag
                com.message.bulksend.bulksend.CampaignState.shouldStop = false
                Log.d("OverlayService", "Campaign resumed - CampaignState.shouldStop = false")
                
                val intent = Intent(ACTION_CONTROL)
                intent.putExtra(EXTRA_ACTION, "start")
                sendBroadcast(intent)
            } else {
                // Campaign is stopped, show Start button
                btnToggle.text = "▶ Start"
                btnToggle.background = GradientDrawable().apply {
                    setColor(Color.parseColor("#4CAF50"))
                    cornerRadius = (24 * resources.displayMetrics.density).toInt().toFloat()
                }
                
                // STOP campaign - set global flag directly
                com.message.bulksend.bulksend.CampaignState.shouldStop = true
                Log.d("OverlayService", "Campaign STOPPED - CampaignState.shouldStop = true")
                
                val intent = Intent(ACTION_CONTROL)
                intent.putExtra(EXTRA_ACTION, "stop")
                sendBroadcast(intent)
            }
        }

        btnClose?.setOnClickListener {
            Log.d("OverlayService", "Close button clicked")
            
            // 1. Set stop flag directly
            com.message.bulksend.bulksend.CampaignState.shouldStop = true
            Log.d("OverlayService", "CampaignState.shouldStop = true (close button)")
            
            // 2. Deactivate accessibility service
            try {
                com.message.bulksend.bulksend.WhatsAppAutoSendService.deactivateService()
                Log.d("OverlayService", "Accessibility service deactivated")
            } catch (e: Exception) {
                Log.e("OverlayService", "Error deactivating service", e)
            }
            
            // 3. Send broadcast to stop campaign
            val stopIntent = Intent(ACTION_STOP_CAMPAIGN)
            sendBroadcast(stopIntent)
            
            // 4. Close overlay
            stopSelf()
        }
        
        // Initialize as running (Stop button shown)
        isRunning = true
    }

    fun updateProgress(sent: Int, total: Int) {
        sentCount = sent
        totalCount = total
        
        val cardView = overlayView as? CardView
        val container = cardView?.getChildAt(0) as? LinearLayout
        val tvStatus = container?.findViewWithTag<TextView>("tvStatus")
        
        tvStatus?.text = "Sent: $sent / $total\nRemaining: ${total - sent}"
        Log.d("OverlayService", "Progress updated: $sent/$total")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val sent = it.getIntExtra(EXTRA_SENT, 0)
            val total = it.getIntExtra(EXTRA_TOTAL, 0)
            updateProgress(sent, total)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let { windowManager?.removeView(it) }
    }

    companion object {
        const val ACTION_CONTROL = "com.message.bulksend.OVERLAY_CONTROL"
        const val ACTION_STOP_CAMPAIGN = "com.message.bulksend.STOP_CAMPAIGN"
        const val EXTRA_ACTION = "action"
        const val EXTRA_SENT = "sent"
        const val EXTRA_TOTAL = "total"
    }
}
