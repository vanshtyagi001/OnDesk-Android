package com.example.lanremotecontrol.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

@Composable
fun StreamSettingsDialog(
    currentFullscreenState: Boolean,
    currentScaleX: Float,
    currentScaleY: Float,
    isAspectLocked: Boolean,
    showBorder: Boolean,
    showLatency: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    onScaleChange: (Float, Float) -> Unit,
    onAspectLockChange: (Boolean) -> Unit,
    onBorderChange: (Boolean) -> Unit,
    onLatencyChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var isFullscreen by remember { mutableStateOf(currentFullscreenState) }
    var scaleX by remember { mutableStateOf(currentScaleX) }
    var scaleY by remember { mutableStateOf(currentScaleY) }
    var locked by remember { mutableStateOf(isAspectLocked) }
    var border by remember { mutableStateOf(showBorder) }
    var latency by remember { mutableStateOf(showLatency) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Visual Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Immersive Mode")
                    Switch(
                        checked = isFullscreen,
                        onCheckedChange = {
                            isFullscreen = it
                            onFullscreenChange(it)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show Video Border", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = border,
                        onCheckedChange = {
                            border = it
                            onBorderChange(it)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show Network Latency", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = latency,
                        onCheckedChange = {
                            latency = it
                            onLatencyChange(it)
                        }
                    )
                }

                // FIX: Use HorizontalDivider
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))

                Text(
                    "Video Scaling",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Lock Aspect Ratio", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = locked,
                        onCheckedChange = {
                            locked = it
                            onAspectLockChange(it)
                            if (it) {
                                // Sync scales to the average when locked
                                val avg = (scaleX + scaleY) / 2f
                                scaleX = avg
                                scaleY = avg
                                onScaleChange(avg, avg)
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (locked) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Overall Scale", style = MaterialTheme.typography.bodySmall)
                        Text("${String.format(Locale.US, "%.2f", scaleX)}x", style = MaterialTheme.typography.bodySmall)
                    }
                    Slider(
                        value = scaleX,
                        onValueChange = {
                            scaleX = it
                            scaleY = it
                            onScaleChange(it, it)
                        },
                        valueRange = 0.5f..2.5f,
                        modifier = Modifier.height(20.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Width Stretch", style = MaterialTheme.typography.bodySmall)
                        Text("${String.format(Locale.US, "%.2f", scaleX)}x", style = MaterialTheme.typography.bodySmall)
                    }
                    Slider(
                        value = scaleX,
                        onValueChange = {
                            scaleX = it
                            onScaleChange(scaleX, scaleY)
                        },
                        valueRange = 0.5f..2.5f,
                        modifier = Modifier.height(20.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Height Stretch", style = MaterialTheme.typography.bodySmall)
                        Text("${String.format(Locale.US, "%.2f", scaleY)}x", style = MaterialTheme.typography.bodySmall)
                    }
                    Slider(
                        value = scaleY,
                        onValueChange = {
                            scaleY = it
                            onScaleChange(scaleX, scaleY)
                        },
                        valueRange = 0.5f..2.5f,
                        modifier = Modifier.height(20.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                OutlinedButton(
                    onClick = {
                        scaleX = 1f
                        scaleY = 1f
                        onScaleChange(1f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset Scaling")
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = onDismiss) {
                    Text("Close Panel")
                }
            }
        }
    }
}