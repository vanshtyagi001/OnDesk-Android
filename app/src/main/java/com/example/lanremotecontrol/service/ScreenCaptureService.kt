package com.example.lanremotecontrol.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.lanremotecontrol.MainActivity
import com.example.lanremotecontrol.network.SocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScreenCaptureService : Service() {

    companion object {
        var permissionResultCode: Int = 0
        var permissionResultData: Intent? = null
        var isServiceRunning = false
    }

    private var mediaProjection: MediaProjection? = null
    private var mediaCodec: MediaCodec? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var isStreaming = false

    private val width = 720
    private val height = 1280
    private val bitrate = 2000000
    private val fps = 30

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_ACTION") {
            stopSelf()
            return START_NOT_STICKY
        }

        isServiceRunning = true
        startForegroundServiceNotification()

        val resultCode = permissionResultCode
        val data = permissionResultData

        if (resultCode != 0 && data != null) {
            setupMediaProjection(resultCode, data)
            startStreaming()
        } else {
            stopSelf()
        }
        return START_STICKY
    }

    private fun setupMediaProjection(code: Int, data: Intent) {
        val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(code, data)
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                stopSelf()
            }
        }, null)
    }

    private fun startStreaming() {
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            format.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            mediaCodec?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = mediaCodec?.createInputSurface()
            mediaCodec?.start()

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture", width, height, 320,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface, null, null
            )
            isStreaming = true
            startEncodingLoop()
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private fun startEncodingLoop() {
        CoroutineScope(Dispatchers.IO).launch {
            val bufferInfo = MediaCodec.BufferInfo()
            while (isStreaming) {
                try {
                    val outputBufferId = mediaCodec?.dequeueOutputBuffer(bufferInfo, 10000) ?: -1
                    if (outputBufferId >= 0) {
                        val outputBuffer = mediaCodec?.getOutputBuffer(outputBufferId)
                        val outData = ByteArray(bufferInfo.size)
                        outputBuffer?.get(outData)
                        SocketManager.sendVideoData(outData, bufferInfo.size, bufferInfo.flags)
                        mediaCodec?.releaseOutputBuffer(outputBufferId, false)
                    }
                } catch (e: Exception) { break }
            }
        }
    }

    private fun startForegroundServiceNotification() {
        val channelId = "ScreenCaptureChannel"
        val channel = NotificationChannel(channelId, "Screen Sharing", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingOpenIntent = PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply { action = "STOP_ACTION" }
        val pendingStopIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Screen Sharing Active")
            .setContentText("Tap to open app, or Stop to end session.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingOpenIntent)
            .addAction(Notification.Action.Builder(null, "STOP", pendingStopIntent).build())
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onDestroy() {
        isServiceRunning = false
        isStreaming = false
        try {
            virtualDisplay?.release()
            mediaCodec?.stop()
            mediaCodec?.release()
            mediaProjection?.stop()
            SocketManager.close()

            // FIX: Use modern stopForeground
            stopForeground(STOP_FOREGROUND_REMOVE)

        } catch (e: Exception) { }
        super.onDestroy()
    }
}