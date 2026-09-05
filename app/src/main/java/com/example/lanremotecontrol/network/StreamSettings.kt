package com.example.lanremotecontrol.network

// The configuration payload
data class StreamConfig(
    val width: Int,
    val height: Int,
    val bitrate: Int, // in bits per second
    val fps: Int
)

// The wrapper packet sent over the network
data class ControlPacket(
    val type: String,               // "TOUCH" or "CONFIG" or "AUTH_REQ" or "AUTH_RESP" or "AUTH_OK" or "AUTH_FAIL" or "PING" or "PONG"
    val touchData: TouchData? = null,
    val config: StreamConfig? = null,
    val pin: String? = null,
    val timestamp: Long? = null
)