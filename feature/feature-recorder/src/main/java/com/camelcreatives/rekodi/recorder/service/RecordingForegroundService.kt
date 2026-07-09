package com.camelcreatives.rekodi.recorder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.camelcreatives.rekodi.data.local.entity.RecordingEntity
import com.camelcreatives.rekodi.data.repository.RecordingRepository
import com.camelcreatives.rekodi.recorder.R
import com.camelcreatives.rekodi.recorder.model.RecordingState
import com.camelcreatives.rekodi.recorder.overlay.RecordingBubbleView
import com.camelcreatives.rekodi.recorder.overlay.ZoomOverlayView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class RecordingForegroundService : Service() {

    @Inject lateinit var recordingRepository: RecordingRepository
    @Inject lateinit var windowManager: WindowManager

    private var bubbleView: RecordingBubbleView? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()
    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()
    private val _tapCount = MutableStateFlow(0)
    val tapCount: StateFlow<Int> = _tapCount.asStateFlow()

    private var mediaProjection: MediaProjection? = null
    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var outputFile: File? = null
    private var recordingId: Long = -1

    companion object {
        const val ACTION_START = "com.camelcreatives.rekodi.action.START"
        const val ACTION_STOP = "com.camelcreatives.rekodi.action.STOP"
        const val ACTION_PAUSE = "com.camelcreatives.rekodi.action.PAUSE"
        const val ACTION_RESUME = "com.camelcreatives.rekodi.action.RESUME"
        const val ACTION_SHOW_BUBBLE = "com.camelcreatives.rekodi.action.SHOW_BUBBLE"
        const val ACTION_UPDATE_TAP_COUNT = "com.camelcreatives.rekodi.action.UPDATE_TAP_COUNT"
        const val ACTION_CRASH_SAFEGUARD = "com.camelcreatives.rekodi.action.CRASH_SAFEGUARD"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_TAP_COUNT = "extra_tap_count"

        private const val NOTIFICATION_CHANNEL_ID = "rekodi_recording"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_BUBBLE -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                showBubble()
            }
            ACTION_START -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                } else {
                    startForeground(NOTIFICATION_ID, buildNotification())
                }
                
                showBubble() // Ensure bubble is showing and updated
                bubbleView?.updateState(RecordingState.RECORDING)

                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                if (resultCode != -1 && data != null) {
                    try {
                        startRecording(resultCode, data)
                    } catch (e: Exception) {
                        android.util.Log.e("RecordingService", "Failed to start recording", e)
                        stopRecording()
                    }
                } else {
                    stopRecording()
                }
            }
            ACTION_STOP -> stopRecording()
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_UPDATE_TAP_COUNT -> {
                val count = intent.getIntExtra(EXTRA_TAP_COUNT, 0)
                _tapCount.value = count
                bubbleView?.updateTapCount(count)
                updateNotification()
            }
            ACTION_CRASH_SAFEGUARD -> safeguardRecording()
        }
        return START_REDELIVER_INTENT
    }

    private fun showBubble() {
        if (bubbleView == null) {
            bubbleView = RecordingBubbleView(this, windowManager)
            bubbleView?.show(_recordingState.value)
            bubbleView?.setStateCallback { state ->
                // Handle state changes from bubble if needed
            }
        } else {
            bubbleView?.updateState(_recordingState.value)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        releaseResources()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Rekodi Recording",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notification for active screen recording"
            setShowBadge(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun startRecording(resultCode: Int, data: Intent) {
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mgr.getMediaProjection(resultCode, data)

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        
        // Ensure even dimensions
        val screenWidth = metrics.widthPixels and 0xFFFFFFFE.toInt()
        val screenHeight = metrics.heightPixels and 0xFFFFFFFE.toInt()
        val density = metrics.densityDpi

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "Rekodi_$timestamp.mp4"
        val dir = getExternalFilesDir(null)
        outputFile = File(dir, fileName)

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoEncodingBitRate(8_000_000)
            setVideoFrameRate(30)
            setVideoSize(screenWidth, screenHeight)
            setOutputFile(outputFile?.absolutePath)
            prepare()
        }

        val surface = mediaRecorder?.surface
        if (surface != null) {
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "RekodiDisplay",
                screenWidth, screenHeight, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface, null, null
            )
        }

        mediaRecorder?.start()
        _recordingState.value = RecordingState.RECORDING
        updateNotification()
        startTimer()

        serviceScope.launch {
            val entity = RecordingEntity(
                filePath = outputFile?.absolutePath ?: "",
                fileName = fileName,
                mimeType = "video/mp4",
                frameRate = 30,
                bitrate = 8_000_000,
                resolution = "${screenWidth}x${screenHeight}"
            )
            recordingId = recordingRepository.insertRecording(entity)
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            android.util.Log.e("RecordingService", "Error stopping MediaRecorder", e)
        }
        mediaRecorder = null
        virtualDisplay?.release()
        virtualDisplay = null
        mediaProjection?.stop()
        mediaProjection = null

        _recordingState.value = RecordingState.IDLE
        bubbleView?.updateState(RecordingState.IDLE)
        val elapsed = _elapsedSeconds.value
        _elapsedSeconds.value = 0

        if (recordingId > 0) {
            serviceScope.launch {
                val entity = recordingRepository.getRecordingById(recordingId)
                if (entity != null) {
                    recordingRepository.updateRecording(
                        entity.copy(
                            durationMs = elapsed * 1000,
                            fileSizeBytes = outputFile?.length() ?: 0,
                            tapCount = _tapCount.value
                        )
                    )
                }
                recordingId = -1
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        // Only stop self if not showing bubble, or maybe we want to keep service alive for bubble?
        // Let's keep it simple for now and stop everything.
        bubbleView?.hide()
        bubbleView = null
        stopSelf()
    }

    private fun pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try { mediaRecorder?.pause() } catch (e: Exception) {
                android.util.Log.e("RecordingService", "Error pausing MediaRecorder", e)
            }
        }
        _recordingState.value = RecordingState.PAUSED
        bubbleView?.updateState(RecordingState.PAUSED)
        updateNotification()
    }

    private fun resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try { mediaRecorder?.resume() } catch (e: Exception) {
                android.util.Log.e("RecordingService", "Error resuming MediaRecorder", e)
            }
        }
        _recordingState.value = RecordingState.RECORDING
        bubbleView?.updateState(RecordingState.RECORDING)
        updateNotification()
    }

    private fun safeguardRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {}
        mediaRecorder = null
        virtualDisplay?.release()
        virtualDisplay = null
        _recordingState.value = RecordingState.IDLE
    }

    private fun releaseResources() {
        try { mediaRecorder?.release() } catch (_: Exception) {}
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { mediaProjection?.stop() } catch (_: Exception) {}
        mediaRecorder = null
        virtualDisplay = null
        mediaProjection = null
    }

    private fun startTimer() {
        serviceScope.launch {
            while (_recordingState.value == RecordingState.RECORDING) {
                delay(1000)
                _elapsedSeconds.value = _elapsedSeconds.value + 1
                
                val elapsed = _elapsedSeconds.value
                val mins = elapsed / 60
                val secs = elapsed % 60
                val timeStr = String.format("%02d:%02d", mins, secs)
                
                launch(Dispatchers.Main) {
                    bubbleView?.updateTimerText(timeStr)
                }
                updateNotification()
            }
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0, Intent(this, RecordingForegroundService::class.java).apply {
                action = ACTION_STOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = PendingIntent.getService(
            this, 1, Intent(this, RecordingForegroundService::class.java).apply {
                action = if (_recordingState.value == RecordingState.PAUSED) ACTION_RESUME else ACTION_PAUSE
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val elapsed = _elapsedSeconds.value
        val mins = elapsed / 60
        val secs = elapsed % 60
        val timeStr = String.format("%02d:%02d", mins, secs)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Recording")
            .setContentText("$timeStr · ${_tapCount.value} taps")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setSilent(true)
            .addAction(
                if (_recordingState.value == RecordingState.PAUSED)
                    android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (_recordingState.value == RecordingState.PAUSED) "Resume" else "Pause",
                pauseIntent
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }
}
