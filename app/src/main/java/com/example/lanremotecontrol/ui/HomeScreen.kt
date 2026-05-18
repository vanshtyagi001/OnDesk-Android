package com.example.lanremotecontrol.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Monitor
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    onNavigateToHost: () -> Unit,
    onNavigateToClient: () -> Unit,
    onNavigateToHelp: () -> Unit // NEW CALLBACK
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(), // ✅ ADD THIS
        color = MaterialTheme.colorScheme.background
    ) {
        // Box allows placing the Help icon at TopEnd
        Box(modifier = Modifier.fillMaxSize()) {

            // --- HELP BUTTON ---
            IconButton(
                onClick = onNavigateToHelp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                    contentDescription = "User Manual",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            // --- MAIN CONTENT ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // --- HEADER ---
                Spacer(modifier = Modifier.height(40.dp))

                Icon(
                    imageVector = Icons.Rounded.Monitor,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "OnDesk",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "Seamless LAN Remote Control",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // --- CENTER CONTENT ---
                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "Select Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Start).padding(bottom = 16.dp)
                )

                // HOST CARD
                MenuOptionCard(
                    title = "Share Screen (Host)",
                    subtitle = "Allow other devices to view and control this phone.",
                    icon = Icons.Rounded.WifiTethering,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = onNavigateToHost
                )

                Spacer(modifier = Modifier.height(16.dp))

                // CLIENT CARD
                MenuOptionCard(
                    title = "Connect to Device (Client)",
                    subtitle = "View and control a remote screen on this network.",
                    icon = Icons.Rounded.PhoneAndroid,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = onNavigateToClient
                )

                Spacer(modifier = Modifier.weight(1f))

                // --- FOOTER ---
                Text(
                    text = "Powered by VanTya",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "v1.0.0 • Secure LAN Connection",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun MenuOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = contentColor.copy(alpha = 0.1f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.8f),
                    lineHeight = 16.sp
                )
            }
        }
    }
}