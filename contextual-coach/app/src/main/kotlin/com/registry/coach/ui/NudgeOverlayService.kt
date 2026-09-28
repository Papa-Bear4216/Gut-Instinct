package com.registry.coach.ui

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.registry.coach.data.WorkflowStore
import com.registry.coach.sync.FirebaseWorkflowSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log

class NudgeOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var overlayView: android.view.View? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    companion object {
        var currentSuggestionId: String? = null
        var currentSuggestionTitle: String? = null
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showNudge()
        return START_NOT_STICKY
    }

    private fun showNudge() {
        if (overlayView != null) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val params = android.view.WindowManager.LayoutParams(
            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100
        }

        // Create layout programmatically
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20, 10, 20, 10)
            setBackgroundColor(Color.parseColor("#222222"))
        }

        val textView = TextView(this).apply {
            text = "Habit Detected: ${currentSuggestionTitle ?: "Unknown"}"
            setTextColor(Color.WHITE)
            setPadding(10, 0, 20, 0)
        }

        val btnYes = Button(this).apply {
            text = "Automate"
            setOnClickListener {
                handleTriage(true)
                dismiss()
            }
        }

        val btnNo = Button(this).apply {
            text = "Ignore"
            setOnClickListener {
                handleTriage(false)
                dismiss()
            }
        }

        layout.addView(textView)
        layout.addView(btnYes)
        layout.addView(btnNo)

        overlayView = layout
        windowManager.addView(overlayView, params)

        // Auto-dismiss after 10 seconds
        scope.launch {
            kotlinx.coroutines.delay(10000)
            dismiss()
        }
    }

    private fun handleTriage(accepted: Boolean) {
        val id = currentSuggestionId ?: return
        val store = WorkflowStore(applicationContext)
        val sync = FirebaseWorkflowSync(applicationContext)
        if (accepted) {
            val workflow = store.approve(id) ?: return
            scope.launch(Dispatchers.IO) {
                try {
                    sync.workflow(workflow)
                } catch (e: Exception) {
                    Log.e("NudgeOverlay", "Sync failed", e)
                }
            }
        } else {
            store.reject(id)
        }
    }

    private fun dismiss() {
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }

    override fun onDestroy() {
        dismiss()
        super.onDestroy()
    }
}