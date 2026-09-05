package com.example.lanremotecontrol.network

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer

object SocketManager {
    private const val PORT = 8888
    private const val UDP_PORT = 8889
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var udpSocket: DatagramSocket? = null
    private var outputStream: DataOutputStream? = null
    private var inputStream: DataInputStream? = null

    private val gson = Gson()
    private val networkScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // UDP fragmentation counter
    private var frameCounter: Long = 0

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
                
                udpSocket = DatagramSocket()

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

    suspend fun performHostHandshake(isPinEnabled: Boolean, correctPin: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!isPinEnabled) {
                    val okPacket = gson.toJson(ControlPacket("AUTH_OK"))
                    outputStream?.writeUTF(okPacket)
                    outputStream?.flush()
                    return@withContext true
                }

                // Request Auth
                val reqPacket = gson.toJson(ControlPacket("AUTH_REQ"))
                outputStream?.writeUTF(reqPacket)
                outputStream?.flush()

                // Wait for response
                clientSocket?.soTimeout = 15000 // 15s to enter PIN
                val json = inputStream?.readUTF()
                clientSocket?.soTimeout = 0 // Reset

                if (json != null) {
                    val packet = gson.fromJson(json, ControlPacket::class.java)
                    if (packet.type == "AUTH_RESP" && packet.pin == correctPin) {
                        val ok = gson.toJson(ControlPacket("AUTH_OK"))
                        outputStream?.writeUTF(ok)
                        outputStream?.flush()
                        return@withContext true
                    }
                }

                val fail = gson.toJson(ControlPacket("AUTH_FAIL"))
                outputStream?.writeUTF(fail)
                outputStream?.flush()
                false
            } catch (e: Exception) {
                Log.e("SocketManager", "Host Handshake Error", e)
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
                            "PING" -> {
                                val pong = gson.toJson(ControlPacket("PONG", timestamp = packet.timestamp))
                                outputStream?.writeUTF(pong)
                                outputStream?.flush()
                            }
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
                
                udpSocket = DatagramSocket(UDP_PORT)
                udpSocket?.reuseAddress = true

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

    suspend fun performClientHandshake(): String {
        return withContext(Dispatchers.IO) {
            try {
                clientSocket?.soTimeout = 5000
                val json = inputStream?.readUTF()
                clientSocket?.soTimeout = 0

                if (json != null) {
                    val packet = gson.fromJson(json, ControlPacket::class.java)
                    return@withContext packet.type // "AUTH_OK" or "AUTH_REQ"
                }
                "AUTH_FAIL"
            } catch (e: Exception) {
                "AUTH_FAIL"
            }
        }
    }

    suspend fun sendClientAuthResponse(pin: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val resp = gson.toJson(ControlPacket("AUTH_RESP", pin = pin))
                outputStream?.writeUTF(resp)
                outputStream?.flush()

                clientSocket?.soTimeout = 5000
                val json = inputStream?.readUTF()
                clientSocket?.soTimeout = 0

                if (json != null) {
                    val packet = gson.fromJson(json, ControlPacket::class.java)
                    return@withContext packet.type // "AUTH_OK" or "AUTH_FAIL"
                }
                "AUTH_FAIL"
            } catch (e: Exception) {
                "AUTH_FAIL"
            }
        }
    }

    fun sendTouch(touch: TouchData) {
        packetSendChannel?.trySend(ControlPacket(type = "TOUCH", touchData = touch))
    }

    fun sendConfig(config: StreamConfig) {
        packetSendChannel?.trySend(ControlPacket(type = "CONFIG", config = config))
    }

    fun sendPing() {
        packetSendChannel?.trySend(ControlPacket(type = "PING", timestamp = System.currentTimeMillis()))
    }
    
    var onLatencyUpdated: ((Long) -> Unit)? = null

    fun startClientDisconnectListener() {
        networkScope.launch {
            try {
                while (clientSocket != null && clientSocket!!.isConnected && !clientSocket!!.isClosed) {
                    val json = inputStream?.readUTF()
                    if (json != null) {
                        val packet = gson.fromJson(json, ControlPacket::class.java)
                        if (packet.type == "PONG") {
                            val rtt = System.currentTimeMillis() - (packet.timestamp ?: 0L)
                            onLatencyUpdated?.invoke(rtt)
                        }
                    }
                }
            } catch (e: Exception) {
            } finally {
                onClientDisconnected?.invoke()
            }
        }
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
        val socket = udpSocket ?: return
        val targetIp = clientSocket?.inetAddress ?: return

        try {
            val frameId = frameCounter++
            val maxChunkSize = 60000
            val totalChunks = Math.ceil(length.toDouble() / maxChunkSize).toInt()

            for (i in 0 until totalChunks) {
                val start = i * maxChunkSize
                val chunkLength = Math.min(maxChunkSize, length - start)
                val buffer = ByteBuffer.allocate(16 + chunkLength)
                
                buffer.putLong(frameId)
                buffer.putInt(flags)
                buffer.putShort(totalChunks.toShort())
                buffer.putShort(i.toShort())
                buffer.put(data, start, chunkLength)

                val packet = DatagramPacket(buffer.array(), buffer.capacity(), targetIp, UDP_PORT)
                socket.send(packet)
            }
        } catch (e: Exception) {
            Log.e("SocketManager", "UDP Send Error", e)
        }
    }

    fun getSocket(): Socket? = clientSocket
    fun getUdpSocket(): DatagramSocket? = udpSocket

    fun close() {
        try {
            // We NO LONGER nullify onTouchReceived/onConfigReceived here.
            // AccessibilityServices cannot reliably be re-pinged via startService() in the background on Android 12+.
            // Leaving the listener hooked up allows it to persist across new connections.

            senderJob?.cancel()
            packetSendChannel?.close()

            outputStream?.close()
            inputStream?.close()
            clientSocket?.close()
            serverSocket?.close()
            udpSocket?.close()
        } catch (e: Exception) { }

        outputStream = null
        inputStream = null
        clientSocket = null
        serverSocket = null
        udpSocket = null
        senderJob = null
        packetSendChannel = null
    }
}