package com.example.lanremotecontrol.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.WindowManager
import android.util.DisplayMetrics
import com.example.lanremotecontrol.network.SocketManager
import com.example.lanremotecontrol.network.TouchData
import kotlin.math.abs

class RemoteControlService : AccessibilityService() {

    private val activePaths = mutableMapOf<Int, Path>()
    private val startCoordinates = mutableMapOf<Int, Pair<Float, Float>>()
    private var gestureStartTime: Long = 0

    override fun onServiceConnected() {
        super.onServiceConnected() // Fixed: Typo removed
        Log.d("RemoteService", "Accessibility Service CONNECTED")
        setupSocketListener()
    }

    /**
     * Called by HostViewModel every time a client connects to ensure the service
     * is listening to the current, active socket instance.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        setupSocketListener()
        return START_STICKY
    }

    private fun setupSocketListener() {
        Log.d("RemoteService", "Binding Touch Listener to SocketManager")
        SocketManager.onTouchReceived = { touch ->
            try {
                processTouch(touch)
            } catch (e: Exception) {
                Log.e("RemoteService", "Error processing touch: ${e.message}")
            }
        }
    }

    private fun processTouch(touch: TouchData) {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        
        val w = metrics.widthPixels.toFloat()
        val h = metrics.heightPixels.toFloat()

        when (touch.type) {
            "DOWN" -> {
                if (activePaths.isEmpty()) {
                    gestureStartTime = System.currentTimeMillis()
                }
                for (ptr in touch.pointers) {
                    if (!activePaths.containsKey(ptr.id)) {
                        val realX = (ptr.x * w).coerceIn(0f, w)
                        val realY = (ptr.y * h).coerceIn(0f, h)
                        val path = Path()
                        path.moveTo(realX, realY)
                        path.lineTo(realX + 1f, realY + 1f) // Ensure length > 0
                        activePaths[ptr.id] = path
                        startCoordinates[ptr.id] = Pair(realX, realY)
                    }
                }
            }
            "MOVE" -> {
                for (ptr in touch.pointers) {
                    val path = activePaths[ptr.id]
                    if (path != null) {
                        val realX = (ptr.x * w).coerceIn(0f, w)
                        val realY = (ptr.y * h).coerceIn(0f, h)
                        path.lineTo(realX, realY)
                    }
                }
            }
            "UP" -> {
                val duration = System.currentTimeMillis() - gestureStartTime
                val lastPointer = touch.pointers.lastOrNull()
                val lastX = lastPointer?.x?.times(w) ?: 0f
                val lastY = lastPointer?.y?.times(h) ?: 0f

                dispatchMultiTouchGesture(duration, lastX, lastY)

                activePaths.clear()
                startCoordinates.clear()
            }
        }
    }

    private fun dispatchMultiTouchGesture(duration: Long, lastX: Float, lastY: Float) {
        if (activePaths.isEmpty()) return

        // Single Tap Optimization
        if (activePaths.size == 1) {
            val start = startCoordinates.values.first()
            val dx = abs(lastX - start.first)
            val dy = abs(lastY - start.second)

            if (dx < 20 && dy < 20) {
                dispatchTap(start.first, start.second)
                return
            }
        }

        // It's a multi-touch or a swipe
        val builder = GestureDescription.Builder()
        val safeDuration = duration.coerceIn(50, 1000)

        for ((_, path) in activePaths) {
            val stroke = GestureDescription.StrokeDescription(path, 0, safeDuration)
            builder.addStroke(stroke)
        }

        try {
            dispatchGesture(builder.build(), null, null)
        } catch (e: Exception) {
            Log.e("RemoteService", "Gesture Dispatch Failed: ${e.message}")
        }
    }

    private fun dispatchTap(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)
        // Micro-movement to guarantee Android registers the stroke length > 0
        path.lineTo(x + 1f, y + 1f)
        val builder = GestureDescription.Builder()
        val stroke = GestureDescription.StrokeDescription(path, 0, 100) // 100ms for reliable tap
        builder.addStroke(stroke)
        dispatchGesture(builder.build(), null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}