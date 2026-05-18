package com.example.lanremotecontrol.network

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

object SocketManager {
    private const val PORT = 8888
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var outputStream: DataOutputStream? = null
    private var inputStream: DataInputStream? = null

    private val gson = Gson()
    private val networkScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Use a nullable channel so we can recreate it fresh every session
    private var packetSendChannel: Channel<ControlPacket>? = null
    private var senderJob: Job? = null

    var onTouchReceived: ((TouchData) -> Unit)? = null
    var onConfigReceived: ((StreamConfig) -> Unit)? = null
    var onClientDisconnected: (() -> Unit)? = null

    // --- SHARED RESET LOGIC ---
    private fun prepareNewSession() {
        // Cancel any old background sender loops
        senderJob?.cancel()
        packetSendChannel?.close()

        // Create a fresh queue for this session
        val newChannel = Channel<ControlPacket>(Channel.UNLIMITED)
        packetSendChannel = newChannel

        senderJob = networkScope.launch {
            try {
                for (packet in newChannel) {
                    sendPacketInternal(packet)
                }
            } catch (e: Exception) {
                Log.e("SocketManager", "Sender Loop Error: ${e.message}")
            }
        }
    }

    // --- HOST SIDE ---
    suspend fun startServer(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                close() // Ensure previous session is dead
                delay(100) // Give OS time to release port

                serverSocket = ServerSocket()
                serverSocket?.reuseAddress = true
                serverSocket?.bind(InetSocketAddress(PORT))

                Log.d("SocketManager", "HOST: Waiting on $PORT")
                clientSocket = serverSocket?.accept()

                // Configure for immediate response
                clientSocket?.tcpNoDelay = true
                clientSocket?.keepAlive = true

                outputStream = DataOutputStream(clientSocket?.getOutputStream())
                inputStream = DataInputStream(clientSocket?.getInputStream())

                prepareNewSession() // Start fresh sender loop
                true
            } catch (e: Exception) {
                Log.e("SocketManager", "Server Error", e)
                false
            }
        }
    }

    fun startListeningForPackets() {
        networkScope.launch {
            try {
                while (clientSocket != null && clientSocket!!.isConnected && !clientSocket!!.isClosed) {
                    val json = inputStream?.readUTF()
                    if (json != null) {
                        val packet = gson.fromJson(json, ControlPacket::class.java)
                        when (packet.type) {
                            "TOUCH" -> onTouchReceived?.invoke(packet.touchData!!)
                            "CONFIG" -> onConfigReceived?.invoke(packet.config!!)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SocketManager", "Read Loop Stopped: ${e.message}")
            } finally {
                onClientDisconnected?.invoke()
                close()
            }
        }
    }

    // --- CLIENT SIDE ---
    suspend fun connectToHost(ip: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                close()
                clientSocket = Socket()
                clientSocket?.reuseAddress = true
                clientSocket?.connect(InetSocketAddress(ip, PORT), 5000)

                clientSocket?.tcpNoDelay = true
                outputStream = DataOutputStream(clientSocket?.getOutputStream())
                inputStream = DataInputStream(clientSocket?.getInputStream())

                prepareNewSession()
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    fun sendTouch(touch: TouchData) {
        packetSendChannel?.trySend(ControlPacket(type = "TOUCH", touchData = touch))
    }

    private fun sendPacketInternal(packet: ControlPacket) {
        try {
            if (clientSocket?.isConnected == true && outputStream != null) {
                val json = gson.toJson(packet)
                outputStream?.writeUTF(json)
                outputStream?.flush()
            }
        } catch (e: Exception) { }
    }

    fun sendVideoData(data: ByteArray, length: Int, flags: Int) {
        try {
            synchronized(this) {
                outputStream?.writeInt(length)
                outputStream?.writeInt(flags)
                outputStream?.write(data, 0, length)
            }
        } catch (e: Exception) { }
    }

    fun getSocket(): Socket? = clientSocket

    fun close() {
        try {
            // Nullify callbacks to prevent ghost events
            // We keep onClientDisconnected so the HostViewModel can restart
            onTouchReceived = null
            onConfigReceived = null

            senderJob?.cancel()
            packetSendChannel?.close()

            outputStream?.close()
            inputStream?.close()
            clientSocket?.close()
            serverSocket?.close()
        } catch (e: Exception) { }

        outputStream = null
        inputStream = null
        clientSocket = null
        serverSocket = null
        senderJob = null
        packetSendChannel = null
    }
}