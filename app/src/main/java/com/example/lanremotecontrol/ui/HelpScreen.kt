package com.example.lanremotecontrol.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("User Manual") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Intro
            Text(
                text = "Welcome to OnDesk",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Secure, low-latency screen sharing and remote control over Local Network.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            // 1. Connection Basics
            ManualSection(
                title = "1. Before You Start",
                icon = Icons.Rounded.Wifi,
                content = "Both devices must be on the SAME Wi-Fi network.\n\n" +
                        "• If no Wi-Fi is available, turn on 'Personal Hotspot' on one phone and connect the other phone to it."
            )

            // 2. Hosting
            ManualSection(
                title = "2. How to Share Screen (Host)",
                icon = Icons.Rounded.WifiTethering,
                content = "1. Tap 'Share Screen (Host)' on the main menu.\n" +
                        "2. Grant 'Notification' permission if asked.\n" +
                        "3. If 'Remote Control' is disabled, tap Enable and turn ON 'LanRemoteControl' in Accessibility Settings.\n" +
                        "4. Wait for a client to connect.\n" +
                        "5. When prompted 'Start Recording?', click Start Now."
            )

            // 3. Client
            ManualSection(
                title = "3. How to Control (Client)",
                icon = Icons.Rounded.PhoneAndroid,
                content = "1. Tap 'Connect to Device (Client)'.\n" +
                        "2. Wait for the Host device to appear in the list.\n" +
                        "3. Tap the Host name to connect.\n" +
                        "4. Once connected, you will see the remote screen."
            )

            // 4. Gestures
            ManualSection(
                title = "4. Gestures & Controls",
                icon = Icons.Rounded.TouchApp,
                content = "• Tap: Click on screen.\n" +
                        "• Swipe/Scroll: Drag one finger.\n" +
                        "• Zoom: Pinch with two fingers.\n" +
                        "• Settings: Tap the Floating Gear icon to adjust scaling or Calibrate touch accuracy."
            )

            // 5. Troubleshooting
            ManualSection(
                title = "5. Troubleshooting",
                icon = Icons.Rounded.Build,
                content = "• Black Screen? Minimize the Client app and reopen it to resync the video.\n" +
                        "• Disconnects? On Host, enable 'Run in Background' switch to prevent battery optimization kills.\n" +
                        "• Touch Inaccurate? Open Settings > Calibrate Touch."
            )

            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "OnDesk v1.0 • Powered by VanTya",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ManualSection(title: String, icon: ImageVector, content: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp
            )
        }
    }
}