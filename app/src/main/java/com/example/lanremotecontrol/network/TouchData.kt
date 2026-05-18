package com.example.lanremotecontrol.network

data class TouchPointer(
    val id: Int,      // Unique ID for the finger (0, 1, etc.)
    val x: Float,     // 0.0 to 1.0
    val y: Float      // 0.0 to 1.0
)

data class TouchData(
    val type: String, // "DOWN", "MOVE", "UP"
    val pointers: List<TouchPointer> // List of all active fingers
)