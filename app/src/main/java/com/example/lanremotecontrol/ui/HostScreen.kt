package com.example.lanremotecontrol.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName // Fixed: Added Import
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri // Fixed: Added Import
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils // Fixed: Added Import
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SettingsAccessibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.rounded.SignalWifi4Bar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lanremotecontrol.service.RemoteControlService
import com.example.lanremotecontrol.service.ScreenCaptureService
import com.example.lanremotecontrol.viewmodel.HostViewModel

@Composable
fun HostScreen(viewModel: HostViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val ipAddress by viewModel.serverIp.collectAsState()
    val isClientConnected by viewModel.isClientConnected.collectAsState()
    val isPinEnabled by viewModel.isPinEnabled.collectAsState()
    val currentPin by viewModel.currentPin.collectAsState()
    val isStealthModeEnabled by viewModel.isStealthModeEnabled.collectAsState()

    // --- State ---
    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }
    var isBatteryIgnored by remember { mutableStateOf(false) }

    // --- Logic ---
    fun tryToggleStealthMode(enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            showOverlayPermissionDialog = true
        } else {
            viewModel.toggleStealthMode(enabled)
        }
    }
    fun checkPermissions() {
        isAccessibilityEnabled = isAccessibilityServiceEnabled(context, RemoteControlService::class.java)
        if (isAccessibilityEnabled) showAccessibilityDialog = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(PowerManager::class.java)
            isBatteryIgnored = pm.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            isBatteryIgnored = true
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) checkPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { }

    val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    val screenCaptureLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ScreenCaptureService.permissionResultCode = result.resultCode
            ScreenCaptureService.permissionResultData = result.data
            val intent = Intent(context, ScreenCaptureService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        viewModel.startHosting()
        checkPermissions()
    }

    LaunchedEffect(isClientConnected) {
        if (isClientConnected && !ScreenCaptureService.isServiceRunning) {
            if (isAccessibilityServiceEnabled(context, RemoteControlService::class.java)) {
                screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
            } else {
                showAccessibilityDialog = true
            }
        }
    }

    // --- UI Layout ---
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding() // ✅ ADD THIS
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = "Host Mode",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(32.dp))

            // 1. Status Dashboard
            val isSessionActive = isClientConnected || ScreenCaptureService.isServiceRunning
            StatusIndicator(isActive = isSessionActive)

            Spacer(modifier = Modifier.height(32.dp))

            // 2. IP Address Card
            InfoCard(
                label = "Device IP Address",
                value = ipAddress,
                icon = Icons.Rounded.SignalWifi4Bar
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Configuration / Warnings
            AnimatedContent(
                targetState = isSessionActive,
                transitionSpec = {
                    (fadeIn() + slideInVertically(initialOffsetY = { 50 })).togetherWith(
                        fadeOut() + slideOutVertically(targetOffsetY = { -50 })
                    )
                },
                label = "HostStateTransition",
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) { active ->
                if (!active) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (!isAccessibilityEnabled) {
                            WarningCard(
                                title = "Remote Control Disabled",
                                text = "Accessibility permission is required.",
                                buttonText = "Enable",
                                onClick = { showAccessibilityDialog = true }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Battery Setting
                        BatterySettingCard(
                            isIgnored = isBatteryIgnored,
                            onToggle = {
                                @SuppressLint("BatteryLife")
                                if (!isBatteryIgnored && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    try {
                                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                        intent.data = Uri.parse("package:${context.packageName}")
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                    }
                                } else {
                                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Security Setting
                        SecurityCard(
                            isPinEnabled = isPinEnabled,
                            currentPin = currentPin,
                            onToggle = { viewModel.togglePinSecurity(it) }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Stealth Mode Setting
                        StealthModeCard(
                            isStealthModeEnabled = isStealthModeEnabled,
                            onToggle = { tryToggleStealthMode(it) }
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Button(
                            onClick = {
                                val intent = Intent(context, ScreenCaptureService::class.java)
                                context.stopService(intent)
                                viewModel.stopHosting()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Stop Sharing Session", fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }

    // Dialog
    if (showAccessibilityDialog) {
        AlertDialog(
            onDismissRequest = { },
            icon = { Icon(Icons.Default.SettingsAccessibility, contentDescription = null) },
            title = { Text("Permission Required") },
            text = { Text("To allow remote control, please enable 'OnDesk' (LanRemoteControl) in Accessibility Settings.") },
            confirmButton = {
                TextButton(onClick = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showOverlayPermissionDialog) {
        AlertDialog(
            onDismissRequest = { },
            icon = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
            title = { Text("Overlay Permission Required") },
            text = { Text("Stealth Mode requires the 'Display over other apps' permission to securely dim the screen.") },
            confirmButton = {
                TextButton(onClick = {
                    showOverlayPermissionDialog = false
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    context.startActivity(intent)
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayPermissionDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// --- Components ---

@Composable
fun StatusIndicator(isActive: Boolean) {
    val color by animateColorAsState(
        if (isActive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary, label = "color"
    )
    val text = if (isActive) "Session Active" else "Waiting for Connection..."
    val subText = if (isActive) "Sharing screen & control" else "Ready to pair"

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(color.copy(alpha = 0.2f), CircleShape)
                .border(2.dp, color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.WifiTethering,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(subText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun InfoCard(label: String, value: String, icon: ImageVector) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun WarningCard(title: String, text: String, buttonText: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f))
            }
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onErrorContainer, contentColor = MaterialTheme.colorScheme.errorContainer),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text(buttonText)
            }
        }
    }
}

@Composable
fun BatterySettingCard(isIgnored: Boolean, onToggle: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Bolt, contentDescription = null, tint = if (isIgnored) Color(0xFF4CAF50) else Color.Gray)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Run in Background", style = MaterialTheme.typography.titleSmall)
                Text(if (isIgnored) "Optimized for stability" else "Enable to prevent disconnects", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Switch(checked = isIgnored, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
fun SecurityCard(isPinEnabled: Boolean, currentPin: String, onToggle: (Boolean) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = if (isPinEnabled) Color(0xFF4CAF50) else Color.Gray)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("PIN Authentication", style = MaterialTheme.typography.titleSmall)
                    Text(if (isPinEnabled) "Client must enter PIN" else "Anyone can connect", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                Switch(checked = isPinEnabled, onCheckedChange = { onToggle(it) })
            }
            if (isPinEnabled) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(currentPin, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, letterSpacing = 8.sp)
                }
            }
        }
    }
}

@Composable
fun StealthModeCard(isStealthModeEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = if (isStealthModeEnabled) Color(0xFF4CAF50) else Color.Gray)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Stealth Mode", style = MaterialTheme.typography.titleSmall)
                Text(if (isStealthModeEnabled) "Screen dims during connection" else "Screen stays bright", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Switch(checked = isStealthModeEnabled, onCheckedChange = { onToggle(it) })
        }
    }
}

fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
    val componentName = ComponentName(context, serviceClass)
    val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    val colonSplitter = TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServices)
    while (colonSplitter.hasNext()) {
        val componentNameString = colonSplitter.next()
        val enabledComponent = ComponentName.unflattenFromString(componentNameString)
        if (enabledComponent != null && enabledComponent == componentName) return true
    }
    return false
}