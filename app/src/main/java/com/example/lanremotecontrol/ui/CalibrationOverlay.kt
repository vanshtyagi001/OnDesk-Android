package com.example.lanremotecontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lanremotecontrol.util.TouchCalibrator

@Composable
fun CalibrationOverlay(
    onCalibrationComplete: () -> Unit,
    onCancel: () -> Unit
) {
    // Steps: 0 = Intro, 1 = Tap Top-Left, 2 = Tap Bottom-Right
    var step by remember { mutableIntStateOf(0) }
    var topLeft by remember { mutableStateOf(Pair(0f, 0f)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Catch all taps
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (step == 1) {
                        topLeft = Pair(offset.x, offset.y)
                        step = 2
                    } else if (step == 2) {
                        // Finish
                        TouchCalibrator.updateCalibration(
                            topLeftX = topLeft.first,
                            topLeftY = topLeft.second,
                            bottomRightX = offset.x,
                            bottomRightY = offset.y
                        )
                        onCalibrationComplete()
                    }
                }
            }
            .background(Color.Black.copy(alpha = 0.5f)) // Dim background
    ) {

        // 1. Cancel Button (Top Right)
        IconButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
        }

        // 2. TARGET ICONS (Must be direct children of Box)
        if (step == 1) {
            TargetIcon(Alignment.TopStart)
        } else if (step == 2) {
            TargetIcon(Alignment.BottomEnd)
        }

        // 3. INSTRUCTIONS (Centered)
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (step) {
                0 -> {
                    Text(
                        "Touch Calibration",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "If touches are inaccurate near edges,\nwe need to calibrate screen borders.",
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { step = 1 }) {
                        Text("Start Calibration")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = {
                        TouchCalibrator.reset()
                        onCalibrationComplete()
                    }) {
                        Text("Reset to Default", color = Color.White)
                    }
                }
                1 -> {
                    // Instruction Text Only
                    Text(
                        "Tap the exact TOP-LEFT corner\nof the visible video.",
                        color = Color.Green,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.background(Color.Black.copy(0.7f)).padding(8.dp)
                    )
                }
                2 -> {
                    // Instruction Text Only
                    Text(
                        "Tap the exact BOTTOM-RIGHT corner\nof the visible video.",
                        color = Color.Green,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.background(Color.Black.copy(0.7f)).padding(8.dp)
                    )
                }
            }
        }
    }
}

// Extension on BoxScope allows using Modifier.align()
@Composable
fun BoxScope.TargetIcon(align: Alignment) {
    Box(
        modifier = Modifier
            .align(align)
            .padding(20.dp) // Guide the eye
            .size(40.dp)
            .background(Color.Red.copy(alpha = 0.3f), CircleShape)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(4.dp)
                .background(Color.Red, CircleShape)
        )
    }
}