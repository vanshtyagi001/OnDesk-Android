package com.example.lanremotecontrol.network

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.net.DatagramPacket
import java.nio.ByteBuffer

class VideoDecoder {

    private var mediaCodec: MediaCodec? = null
    private var isRunning = false
    @Volatile private var currentSurface: Surface? = null
    private val lock = Object()

    // Default start resolution
    private var currentWidth = 720
    private var currentHeight = 1280

    private var nextConfig: StreamConfig? = null

    // NEW: Callback to tell ViewModel connection died
    var onDisconnect: (() -> Unit)? = null
    var onFirstFrameReceived: (() -> Unit)? = null
    private var hasFiredFirstFrame = false

    // ABR stats
    private var framesExpected = 0
    private var framesReceived = 0

    fun getAndResetDropRate(): Float {
        if (framesExpected == 0) return 0f
        val expected = framesExpected
        val received = framesReceived
        framesExpected = 0
        framesReceived = 0
        return (expected - received) / expected.toFloat()
    }

    fun setSurface(surface: Surface?) {
        currentSurface = surface
        if (surface == null) {
            // Optional: Stop codec logic
        }
    }

    fun updateConfig(width: Int, height: Int) {
        nextConfig = StreamConfig(width, height, 0, 0)
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        hasFiredFirstFrame = false

        Thread {
            val udpSocket = SocketManager.getUdpSocket()
            if (udpSocket == null) {
                onDisconnect?.invoke()
                return@Thread
            }

            try {
                Log.d("VideoDecoder", "UDP Loop Started")

                var currentFrameId: Long = -1
                var expectedChunks = -1
                val receivedChunks = mutableMapOf<Int, ByteArray>()
                var currentFlags = 0

                val buffer = ByteArray(65535)

                while (isRunning && !udpSocket.isClosed) {
                    val packet = DatagramPacket(buffer, buffer.size)

                    try {
                        udpSocket.receive(packet)
                    } catch (e: Exception) {
                        break // Socket closed
                    }

                    val byteBuffer = ByteBuffer.wrap(packet.data, 0, packet.length)
                    if (packet.length < 16) continue // Invalid packet

                    val frameId = byteBuffer.long
                    val flags = byteBuffer.int
                    val totalChunks = byteBuffer.short.toInt()
                    val chunkIndex = byteBuffer.short.toInt()

                    if (frameId != currentFrameId) {
                        if (currentFrameId != -1L) {
                            framesExpected++
                        }
                        // New frame started, drop old incomplete frame if any
                        currentFrameId = frameId
                        expectedChunks = totalChunks
                        currentFlags = flags
                        receivedChunks.clear()
                    }

                    val payloadSize = byteBuffer.remaining()
                    val payload = ByteArray(payloadSize)
                    byteBuffer.get(payload)

                    receivedChunks[chunkIndex] = payload

                    if (receivedChunks.size == expectedChunks) {
                        framesReceived++
                        // Frame complete!
                        var totalSize = 0
                        for (i in 0 until expectedChunks) {
                            totalSize += receivedChunks[i]?.size ?: 0
                        }

                        val completeFrame = ByteArray(totalSize)
                        var offset = 0
                        for (i in 0 until expectedChunks) {
                            val chunk = receivedChunks[i]!!
                            System.arraycopy(chunk, 0, completeFrame, offset, chunk.size)
                            offset += chunk.size
                        }

                        receivedChunks.clear()

                        // Check for resolution updates
                        if (nextConfig != null) {
                            synchronized(lock) {
                                stopCodecInternal()
                                nextConfig?.let {
                                    currentWidth = it.width
                                    currentHeight = it.height
                                    nextConfig = null
                                }
                            }
                        }

                        // Decode Frame
                        val surface = currentSurface
                        if (surface != null && surface.isValid) {
                            synchronized(lock) {
                                if (mediaCodec == null) {
                                    try {
                                        val format = MediaFormat.createVideoFormat("video/avc", currentWidth, currentHeight)
                                        format.setInteger(MediaFormat.KEY_PUSH_BLANK_BUFFERS_ON_STOP, 1)
                                        mediaCodec = MediaCodec.createDecoderByType("video/avc")
                                        mediaCodec?.configure(format, surface, null, 0)
                                        mediaCodec?.start()
                                    } catch (e: Exception) {
                                        Log.e("VideoDecoder", "Init Failed", e)
                                    }
                                }

                                val codec = mediaCodec
                                if (codec != null) {
                                    try {
                                        val inputIndex = codec.dequeueInputBuffer(10000)
                                        if (inputIndex >= 0) {
                                            val inputBuffer = codec.getInputBuffer(inputIndex)
                                            inputBuffer?.clear()
                                            inputBuffer?.put(completeFrame)
                                            codec.queueInputBuffer(inputIndex, 0, completeFrame.size, 0, currentFlags)
                                        }

                                        val bufferInfo = MediaCodec.BufferInfo()
                                        var outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                                        while (outputIndex >= 0) {
                                            if (!hasFiredFirstFrame) {
                                                hasFiredFirstFrame = true
                                                onFirstFrameReceived?.invoke()
                                            }
                                            codec.releaseOutputBuffer(outputIndex, true)
                                            outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                                        }
                                    } catch (e: Exception) {
                                        stopCodecInternal()
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("VideoDecoder", "UDP Stream Ended / Error", e)
                // Trigger Disconnect on Main Thread (via ViewModel)
                onDisconnect?.invoke()
            } finally {
                stop()
            }
        }.start()
    }

    fun stop() {
        isRunning = false
        synchronized(lock) {
            stopCodecInternal()
        }
    }

    private fun stopCodecInternal() {
        try {
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (e: Exception) { }
        mediaCodec = null
    }
}