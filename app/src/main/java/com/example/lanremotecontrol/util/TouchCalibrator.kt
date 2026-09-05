package com.example.lanremotecontrol.util

data class CalibrationData(
    val xOffset: Float = 0f,
    val yOffset: Float = 0f,
    val videoWidth: Float = 1f,
    val videoHeight: Float = 1f
)

object TouchCalibrator {

    private var viewWidth: Float = 1f
    private var viewHeight: Float = 1f

    fun setViewSize(w: Float, h: Float) {
        if (w > 0) viewWidth = w
        if (h > 0) viewHeight = h
    }

    /**
     * Mathematically maps raw screen touches into the 0.0 - 1.0 coordinate space expected by the host.
     * This factors in the visual scaleX/scaleY applied to the video layer, ensuring 100% precision.
     */
    fun mapCoordinate(rawX: Float, rawY: Float, scaleX: Float, scaleY: Float): Pair<Float, Float> {
        // The scaling originates from the center of the screen
        val centerX = viewWidth / 2f
        val centerY = viewHeight / 2f

        // Invert the scaling to find where the touch lands on the original unscaled surface
        val unscaledX = (rawX - centerX) / scaleX + centerX
        val unscaledY = (rawY - centerY) / scaleY + centerY

        // Normalize to 0.0 - 1.0
        val normalizedX = unscaledX / viewWidth
        val normalizedY = unscaledY / viewHeight

        // Clamp (Safety) so we don't send out-of-bounds coordinates
        val safeX = normalizedX.coerceIn(0f, 1f)
        val safeY = normalizedY.coerceIn(0f, 1f)

        return Pair(safeX, safeY)
    }
}