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
import com.example.lanremotecontrol.service.StealthModeService
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

    private val _isPinEnabled = MutableStateFlow(false)
    val isPinEnabled = _isPinEnabled.asStateFlow()

    private val _currentPin = MutableStateFlow("1234")
    val currentPin = _currentPin.asStateFlow()

    private val _isStealthModeEnabled = MutableStateFlow(false)
    val isStealthModeEnabled = _isStealthModeEnabled.asStateFlow()

    fun togglePinSecurity(enabled: Boolean) {
        _isPinEnabled.value = enabled
        if (enabled) generateNewPin()
    }

    fun generateNewPin() {
        val randomPin = (1000..9999).random().toString()
        _currentPin.value = randomPin
    }

    fun toggleStealthMode(enabled: Boolean) {
        _isStealthModeEnabled.value = enabled
        if (_isClientConnected.value) {
            val intent = Intent(context, StealthModeService::class.java)
            if (enabled) context.startService(intent) else context.stopService(intent)
        }
    }

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
            context.stopService(Intent(context, StealthModeService::class.java))
            waitForConnection()
        }

        waitForConnection()
    }

    fun stopHosting() {
        _isClientConnected.value = false
        _status.value = "Stopped"
        context.stopService(Intent(context, StealthModeService::class.java))
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
                _status.value = "Authenticating client..."
                val authSuccess = SocketManager.performHostHandshake(_isPinEnabled.value, _currentPin.value)
                
                if (authSuccess) {
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

                    if (_isStealthModeEnabled.value) {
                        context.startService(Intent(context, StealthModeService::class.java))
                    }

                    SocketManager.startListeningForPackets()
                } else {
                    _status.value = "Authentication Failed."
                    SocketManager.close()
                    kotlinx.coroutines.delay(2000)
                    waitForConnection()
                }
            } else {
                _status.value = "Server bind failed."
                SocketManager.close()
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
            context.stopService(Intent(context, StealthModeService::class.java))
        }
    }
}