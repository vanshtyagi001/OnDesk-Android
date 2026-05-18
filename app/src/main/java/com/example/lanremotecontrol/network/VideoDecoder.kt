package com.example.lanremotecontrol.network

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.io.DataInputStream

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

        Thread {
            val socket = SocketManager.getSocket()
            if (socket == null) {
                onDisconnect?.invoke()
                return@Thread
            }

            try {
                val inputStream = DataInputStream(socket.getInputStream())
                Log.d("VideoDecoder", "Loop Started")

                while (isRunning && socket.isConnected) {

                    // 1. READ FRAME SIZE
                    val frameSize = try {
                        inputStream.readInt()
                    } catch (e: Exception) {
                        // Socket closed or error -> Disconnect
                        throw e
                    }

                    // 2. CHECK FOR RESTART MARKER (-1)
                    if (frameSize == -1) {
                        synchronized(lock) {
                            stopCodecInternal()
                            nextConfig?.let {
                                currentWidth = it.width
                                currentHeight = it.height
                                nextConfig = null
                            }
                        }
                        continue
                    }

                    // 3. READ FLAGS
                    val flags = try {
                        inputStream.readInt()
                    } catch (e: Exception) { throw e }

                    if (frameSize < 0 || frameSize > 2000000) continue

                    // 4. READ DATA
                    val frameData = ByteArray(frameSize)
                    inputStream.readFully(frameData)

                    // 5. DECODE
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
                                        val buffer = codec.getInputBuffer(inputIndex)
                                        buffer?.clear()
                                        buffer?.put(frameData)
                                        codec.queueInputBuffer(inputIndex, 0, frameSize, 0, flags)
                                    }

                                    val bufferInfo = MediaCodec.BufferInfo()
                                    var outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                                    while (outputIndex >= 0) {
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
            } catch (e: Exception) {
                Log.e("VideoDecoder", "Stream Ended / Error: ${e.message}")
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