package com.example.lanremotecontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.lanremotecontrol.ui.ClientScreen
import com.example.lanremotecontrol.ui.HelpScreen
import com.example.lanremotecontrol.ui.HomeScreen
import com.example.lanremotecontrol.ui.HostScreen
import com.example.lanremotecontrol.ui.VideoPlayerScreen
import com.example.lanremotecontrol.ui.theme.LanRemoteControlTheme
import com.example.lanremotecontrol.viewmodel.ClientViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LanRemoteControlTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    // We create the ClientViewModel here so it is shared between
    // ClientScreen (Scanning) and VideoPlayerScreen (Streaming).
    // This prevents the connection from dropping when navigating.
    val sharedClientViewModel: ClientViewModel = viewModel()

    NavHost(navController = navController, startDestination = "home") {

        // 1. Home Screen
        composable("home") {
            HomeScreen(
                onNavigateToHost = { navController.navigate("host") },
                onNavigateToClient = { navController.navigate("client") },
                onNavigateToHelp = { navController.navigate("help") }
            )
        }

        // 2. Host Mode
        composable("host") {
            HostScreen()
        }

        // 3. Client Mode (Scanning)
        composable("client") {
            ClientScreen(
                viewModel = sharedClientViewModel,
                onConnected = {
                    // When connected, move to the video player
                    navController.navigate("player")
                }
            )
        }

        // 4. Video Player (Streaming & Control)
        composable("player") {
            VideoPlayerScreen(
                viewModel = sharedClientViewModel,
                onNavigateBack = {
                    // Pop back to Home, removing the player/client states from stack
                    navController.popBackStack("home", inclusive = false)
                }
            )
        }

        // 5. User Manual / Help
        composable("help") {
            HelpScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}