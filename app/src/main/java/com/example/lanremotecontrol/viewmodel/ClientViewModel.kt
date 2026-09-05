package com.example.lanremotecontrol.viewmodel

import android.app.Application
import android.os.Build // <-- Import needed for the fix
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lanremotecontrol.network.NsdHelper
import com.example.lanremotecontrol.network.SocketManager
import com.example.lanremotecontrol.network.StreamConfig
import com.example.lanremotecontrol.network.VideoDecoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.widget.Toast
import android.util.Log

data class DiscoveredHost(val name: String, val ip: String, val port: Int)

class ClientViewModel(application: Application) : AndroidViewModel(application) {

    private val nsdHelper = NsdHelper(application)
    private val videoDecoder = VideoDecoder()

    private var currentConfig = StreamConfig(720, 1280, 2000000, 30)
    private val _hosts = MutableStateFlow<List<DiscoveredHost>>(emptyList())
    val hosts = _hosts.asStateFlow()
    private val _status = MutableStateFlow("Idle")
    val status = _status.asStateFlow()
    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()
    
    private val _isVideoReceiving = MutableStateFlow(false)
    val isVideoReceiving = _isVideoReceiving.asStateFlow()

    private val _latencyMs = MutableStateFlow(0L)
    val latencyMs = _latencyMs.asStateFlow()

    var isHostSelectionDialogVisible = MutableStateFlow(false)
    private val _isPinRequested = MutableStateFlow(false)
    val isPinRequested = _isPinRequested.asStateFlow()

    fun startScanning() {
        _status.value = "Scanning for hosts..."
        nsdHelper.onServiceFound = { serviceInfo ->

            // --- FIX APPLIED HERE ---
            // Use the modern 'hostAddresses' on new Android versions
            // and the deprecated 'host' as a fallback.
            @Suppress("DEPRECATION")
            val hostIp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                serviceInfo.hostAddresses.firstOrNull()?.hostAddress
            } else {
                serviceInfo.host?.hostAddress
            }

            val hostPort = serviceInfo.port
            val hostName = serviceInfo.serviceName

            if (hostIp != null) {
                val newHost = DiscoveredHost(hostName, hostIp, hostPort)
                viewModelScope.launch {
                    val currentList = _hosts.value.toMutableList()
                    if (currentList.none { it.ip == hostIp }) {
                        currentList.add(newHost)
                        _hosts.value = currentList
                    }
                }
            }
        }
        nsdHelper.discoverServices()
    }

    fun connectToHost(ip: String) {
        viewModelScope.launch {
            _status.value = "Connecting to $ip..."
            val success = SocketManager.connectToHost(ip)
            if (success) {
                _status.value = "Authenticating..."
                val authStatus = SocketManager.performClientHandshake()
                if (authStatus == "AUTH_REQ") {
                    _status.value = "PIN Required"
                    _isPinRequested.value = true
                } else if (authStatus == "AUTH_OK") {
                    onAuthSuccess()
                } else {
                    _status.value = "Authentication Failed"
                    _isConnected.value = false
                    SocketManager.close()
                }
            } else {
                _status.value = "Connection Failed"
                _isConnected.value = false
            }
        }
    }

    fun submitPin(pin: String) {
        viewModelScope.launch {
            _status.value = "Verifying PIN..."
            val result = SocketManager.sendClientAuthResponse(pin)
            if (result == "AUTH_OK") {
                _isPinRequested.value = false
                onAuthSuccess()
            } else {
                _status.value = "Invalid PIN"
                _isPinRequested.value = false
                SocketManager.close()
            }
        }
    }

    fun cancelPin() {
        _isPinRequested.value = false
        _status.value = "Connection Cancelled"
        SocketManager.close()
    }

    private fun onAuthSuccess() {
        _status.value = "Connected!"
        _isConnected.value = true
        _isVideoReceiving.value = false
        
        videoDecoder.onFirstFrameReceived = {
            viewModelScope.launch {
                _isVideoReceiving.value = true
            }
        }
        
        videoDecoder.onDisconnect = {
            viewModelScope.launch {
                _isConnected.value = false
                _isVideoReceiving.value = false
                _status.value = "Disconnected by Host"
                SocketManager.close()
            }
        }
        
        SocketManager.onClientDisconnected = {
            viewModelScope.launch {
                if (_isConnected.value) {
                    Toast.makeText(getApplication(), "Host ended the session", Toast.LENGTH_SHORT).show()
                }
                _isConnected.value = false
                _isVideoReceiving.value = false
                _latencyMs.value = 0L
                _status.value = "Disconnected by Host"
                SocketManager.close()
            }
        }
        
        SocketManager.onLatencyUpdated = { rtt ->
            _latencyMs.value = rtt
        }
        
        SocketManager.startClientDisconnectListener()
        
        // Start Ping Loop
        viewModelScope.launch {
            while (_isConnected.value) {
                SocketManager.sendPing()
                kotlinx.coroutines.delay(1000)
            }
        }
        
        // Start ABR Loop (Adaptive Bitrate)
        viewModelScope.launch {
            var currentBitrate = 2000000 // 2 Mbps start
            var perfectCycles = 0
            
            while (_isConnected.value) {
                kotlinx.coroutines.delay(3000)
                val dropRate = videoDecoder.getAndResetDropRate()
                var bitrateChanged = false
                
                if (dropRate > 0.05f) { // More than 5% loss
                    perfectCycles = 0
                    currentBitrate = (currentBitrate * 0.7f).toInt().coerceAtLeast(500000)
                    bitrateChanged = true
                    Log.d("ABR", "Network struggling. Downgrading bitrate to $currentBitrate (Drop Rate: ${dropRate*100}%)")
                } else if (dropRate == 0f) {
                    perfectCycles++
                    if (perfectCycles >= 2 && currentBitrate < 4000000) {
                        currentBitrate = (currentBitrate * 1.2f).toInt().coerceAtMost(4000000)
                        bitrateChanged = true
                        Log.d("ABR", "Network stable. Upgrading bitrate to $currentBitrate")
                        perfectCycles = 0
                    }
                } else {
                    perfectCycles = 0
                }
                
                if (bitrateChanged) {
                    val config = StreamConfig(720, 1280, currentBitrate, 30)
                    SocketManager.sendConfig(config)
                }
            }
        }
        
        videoDecoder.start()
    }

    fun updateStreamConfig(newConfig: StreamConfig) {
        if (currentConfig == newConfig) return
        currentConfig = newConfig
        viewModelScope.launch {
            videoDecoder.updateConfig(newConfig.width, newConfig.height)
        }
    }

    fun attachSurface(surface: Surface) {
        videoDecoder.setSurface(surface)
    }

    fun detachSurface() {
        videoDecoder.setSurface(null)
    }

    fun disconnect() {
        viewModelScope.launch {
            SocketManager.close()
            videoDecoder.stop()
            _isConnected.value = false
            _isVideoReceiving.value = false
            _status.value = "Disconnected"
        }
    }

    override fun onCleared() {
        super.onCleared()
        nsdHelper.tearDown()
        videoDecoder.stop()
        SocketManager.close()
    }
}