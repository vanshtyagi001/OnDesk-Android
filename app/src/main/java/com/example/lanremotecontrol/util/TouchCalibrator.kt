package com.example.lanremotecontrol.util

data class CalibrationData(
    val xOffset: Float = 0f,
    val yOffset: Float = 0f,
    val videoWidth: Float = 1f,
    val videoHeight: Float = 1f
)

object TouchCalibrator {

    // Default: Assume video fills whole screen (1:1 mapping)
    private var data = CalibrationData()
    private var viewWidth: Float = 1f
    private var viewHeight: Float = 1f

    fun setViewSize(w: Float, h: Float) {
        viewWidth = w
        viewHeight = h
        // Reset default if not calibrated yet
        if (data.videoWidth == 1f) {
            data = CalibrationData(0f, 0f, w, h)
        }
    }

    fun updateCalibration(topLeftX: Float, topLeftY: Float, bottomRightX: Float, bottomRightY: Float) {
        val w = bottomRightX - topLeftX
        val h = bottomRightY - topLeftY

        // Save the active video area
        data = CalibrationData(
            xOffset = topLeftX,
            yOffset = topLeftY,
            videoWidth = w,
            videoHeight = h
        )
    }

    fun mapCoordinate(rawX: Float, rawY: Float): Pair<Float, Float> {
        // 1. Subtract the black bar offset
        val relativeX = rawX - data.xOffset
        val relativeY = rawY - data.yOffset

        // 2. Normalize based on ACTUAL video size, not screen size
        val normalizedX = relativeX / data.videoWidth
        val normalizedY = relativeY / data.videoHeight

        // 3. Clamp (Safety)
        // Ensure we don't send < 0.0 or > 1.0 if user touches black bars
        val safeX = normalizedX.coerceIn(0f, 1f)
        val safeY = normalizedY.coerceIn(0f, 1f)

        return Pair(safeX, safeY)
    }

    fun reset() {
        data = CalibrationData(0f, 0f, viewWidth, viewHeight)
    }
}