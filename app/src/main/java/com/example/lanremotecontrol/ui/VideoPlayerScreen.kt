package com.example.lanremotecontrol.ui

import android.app.Activity
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lanremotecontrol.network.SocketManager
import com.example.lanremotecontrol.network.TouchData
import com.example.lanremotecontrol.network.TouchPointer
import com.example.lanremotecontrol.util.DraggableSettingsFab
import com.example.lanremotecontrol.util.TouchCalibrator
import com.example.lanremotecontrol.viewmodel.ClientViewModel

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VideoPlayerScreen(
    viewModel: ClientViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val window = (context as? Activity)?.window

    // Observe Connection State for Auto-Exit
    val isConnected by viewModel.isConnected.collectAsState()

    // UI States
    var showSettings by remember { mutableStateOf(false) }
    var showCalibration by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }

    // Scaling States (Manual Zoom/Stretch)
    var scaleX by remember { mutableFloatStateOf(1f) }
    var scaleY by remember { mutableFloatStateOf(1f) }

    // Touch States
    var viewWidth by remember { mutableFloatStateOf(1f) }
    var viewHeight by remember { mutableFloatStateOf(1f) }
    var lastMoveTime by remember { mutableLongStateOf(0L) }
    val throttleMs = 15L

    // 1. Auto-Exit Logic (If Host disconnects)
    LaunchedEffect(isConnected) {
        if (!isConnected) {
            // Restore System Bars if needed
            window?.let {
                WindowCompat.getInsetsController(it, it.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
            onNavigateBack()
        }
    }

    // 2. Fullscreen Logic
    LaunchedEffect(isFullscreen) {
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (isFullscreen) controller.hide(WindowInsetsCompat.Type.systemBars())
            else controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // 3. Back Button Logic
    BackHandler {
        if (showCalibration) {
            showCalibration = false
        } else if (isFullscreen) {
            isFullscreen = false
        } else {
            viewModel.disconnect()
            onNavigateBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onGloballyPositioned { coordinates ->
                viewWidth = coordinates.size.width.toFloat()
                viewHeight = coordinates.size.height.toFloat()
                // Update Calibrator with exact screen size
                TouchCalibrator.setViewSize(viewWidth, viewHeight)
            }
    ) {
        // A. Video Surface (Apply Scaling Here)
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(h: SurfaceHolder) { viewModel.attachSurface(h.surface) }
                        override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, he: Int) {}
                        override fun surfaceDestroyed(h: SurfaceHolder) { viewModel.detachSurface() }
                    })
                }
            },
            modifier = Modifier
                .fillMaxSize()
                // Visual Stretch logic
                .graphicsLayer(
                    scaleX = scaleX,
                    scaleY = scaleY
                )
        )

        // B. Touch Layer (Full Screen - Not Scaled, mapped via Calibrator)
        if (!showCalibration) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInteropFilter { event ->
                        val pointers = ArrayList<TouchPointer>()
                        for (i in 0 until event.pointerCount) {

                            // Use Calibrator to map raw touch to video coordinates
                            val (calX, calY) = TouchCalibrator.mapCoordinate(event.getX(i), event.getY(i))

                            pointers.add(TouchPointer(event.getPointerId(i), calX, calY))
                        }

                        val action = when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> "DOWN"
                            MotionEvent.ACTION_MOVE -> "MOVE"
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> "UP"
                            else -> "UNKNOWN"
                        }

                        if (action == "MOVE") {
                            val now = System.currentTimeMillis()
                            if (now - lastMoveTime >= throttleMs) {
                                SocketManager.sendTouch(TouchData("MOVE", pointers))
                                lastMoveTime = now
                            }
                        } else if (action != "UNKNOWN") {
                            SocketManager.sendTouch(TouchData(action, pointers))
                        }
                        true
                    }
            )
        }

        // C. Draggable Settings FAB (Visible unless calibrating)
        //XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
        /*
        if (!showCalibration) {
            Box(modifier = Modifier.fillMaxSize()) {
                DraggableSettingsFab(onClick = { showSettings = true })
            }
        }*/
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding() // ✅ SAFE HERE
        ) {
            DraggableSettingsFab(onClick = { showSettings = true })
        }


        //XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX


        // D. Calibration Overlay (On Top)
        if (showCalibration) {
            CalibrationOverlay(
                onCalibrationComplete = { showCalibration = false },
                onCancel = { showCalibration = false }
            )
        }
    }

    // E. Settings Dialog
    if (showSettings) {
        StreamSettingsDialog(
            currentFullscreenState = isFullscreen,
            currentScaleX = scaleX,
            currentScaleY = scaleY,
            onFullscreenChange = { isFullscreen = it },
            onScaleChange = { x, y ->
                scaleX = x
                scaleY = y
            },
            onOpenCalibration = { showCalibration = true },
            onDismiss = { showSettings = false }
        )
    }
}