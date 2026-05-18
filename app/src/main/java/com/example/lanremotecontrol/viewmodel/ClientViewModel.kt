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
                _status.value = "Connected!"
                _isConnected.value = true
                videoDecoder.onDisconnect = {
                    viewModelScope.launch {
                        _isConnected.value = false
                        _status.value = "Disconnected by Host"
                        SocketManager.close()
                    }
                }
                videoDecoder.start()
            } else {
                _status.value = "Connection Failed"
                _isConnected.value = false
            }
        }
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