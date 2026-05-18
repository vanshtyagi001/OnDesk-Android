package com.example.lanremotecontrol.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lanremotecontrol.network.NetworkUtils
import com.example.lanremotecontrol.network.NsdHelper
import com.example.lanremotecontrol.network.SocketManager
import com.example.lanremotecontrol.service.RemoteControlService
import com.example.lanremotecontrol.service.ScreenCaptureService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HostViewModel(application: Application) : AndroidViewModel(application) {

    private val nsdHelper = NsdHelper(application)
    private val context = application.applicationContext

    private val _serverIp = MutableStateFlow("Fetching IP...")
    val serverIp = _serverIp.asStateFlow()

    private val _status = MutableStateFlow("Idle")
    val status = _status.asStateFlow()

    private val _isClientConnected = MutableStateFlow(false)
    val isClientConnected = _isClientConnected.asStateFlow()

    fun startHosting() {
        val ip = NetworkUtils.getLocalIpAddress()
        _serverIp.value = ip
        nsdHelper.registerService(8888)

        // If app was reopened and service is already sharing
        if (ScreenCaptureService.isServiceRunning) {
            _status.value = "Session Active"
            _isClientConnected.value = true
        }

        // Setup the restart loop for when client leaves
        SocketManager.onClientDisconnected = {
            _isClientConnected.value = false
            _status.value = "Disconnected. Waiting for new client..."
            waitForConnection()
        }

        waitForConnection()
    }

    fun stopHosting() {
        _isClientConnected.value = false
        _status.value = "Stopped"
        SocketManager.close()
        nsdHelper.tearDown()
    }

    private fun waitForConnection() {
        viewModelScope.launch {
            _isClientConnected.value = false
            _status.value = "Waiting on port 8888..."

            // startServer() now handles reuseAddress and port release internally
            val success = SocketManager.startServer()

            if (success) {
                _status.value = "Client Connected!"
                _isClientConnected.value = true

                // CRITICAL FIX: Trigger RemoteControlService to re-bind its listener
                // startService() on an already running Accessibility Service triggers onStartCommand
                try {
                    val intent = Intent(context, RemoteControlService::class.java)
                    context.startService(intent)
                } catch (e: Exception) {
                    // On some Android 14+ versions, background start might be restricted
                    // but since the service is already running, this usually works.
                }

                SocketManager.startListeningForPackets()
            } else {
                _status.value = "Server bind failed. Retrying..."
                kotlinx.coroutines.delay(2000)
                waitForConnection()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // If we aren't actively sharing in the background, clean everything up
        if (!ScreenCaptureService.isServiceRunning) {
            nsdHelper.tearDown()
            SocketManager.close()
        }
    }
}