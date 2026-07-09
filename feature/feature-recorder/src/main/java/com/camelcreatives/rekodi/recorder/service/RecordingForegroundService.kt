package com.camelcreatives.rekodi.recorder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.camelcreatives.rekodi.data.datastore.SettingsDataStore
import com.camelcreatives.rekodi.data.local.entity.RecordingEntity
import com.camelcreatives.rekodi.data.repository.RecordingRepository
import com.camelcreatives.rekodi.recorder.R
import com.camelcreatives.rekodi.recorder.model.RecordingState
import com.camelcreatives.rekodi.recorder.overlay.RecordingBubbleView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

private const val TAG = "RecordingService"

@AndroidEntryPoint
class RecordingForegroundService : Service() {

    @Inject lateinit var recordingRepository: RecordingRepository
    @Inject lateinit var windowManager: WindowManager
    @Inject lateinit var stateManager: RecordingStateManager
    @Inject lateinit var settingsDataStore: SettingsDataStore

    private var bubbleView: RecordingBubbleView? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private var mediaProjection: MediaProjection? = null
    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var outputFile: File? = null
    private var recordingId: Long = -1

    private val mainHandler = Handler(Looper.getMainLooper())

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
        Log.d(TAG, "Service onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")
        
        // Call startForeground immediately to avoid crash
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && intent?.action == ACTION_START) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        when (intent?.action) {
            ACTION_SHOW_BUBBLE -> {
                showBubble()
            }
            ACTION_START -> {
                showBubble()
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                
                if (resultCode != -1 && data != null) {
                    try {
                        // Get MediaProjection immediately while in foreground
                        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        mediaProjection = mgr.getMediaProjection(resultCode, data)
                        if (mediaProjection != null) {
                            startRecording()
                        } else {
                            Log.e(TAG, "MediaProjection was null")
                            stopRecording()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to initialize MediaProjection", e)
                        stopRecording()
                    }
                } else {
                    Log.e(TAG, "Invalid result code or data for ACTION_START")
                    stopRecording()
                }
            }
            ACTION_STOP -> stopRecording()
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_UPDATE_TAP_COUNT -> {
                val count = intent.getIntExtra(EXTRA_TAP_COUNT, 0)
                stateManager.setTapCount(count)
                bubbleView?.updateTapCount(count)
                updateNotification()
            }
            ACTION_CRASH_SAFEGUARD -> safeguardRecording()
        }
        return START_NOT_STICKY
    }

    private fun showBubble() {
        if (bubbleView == null) {
            bubbleView = RecordingBubbleView(this, windowManager)
            bubbleView?.show(stateManager.recordingState.value)
            bubbleView?.setOnCloseListener {
                if (stateManager.recordingState.value == RecordingState.IDLE) {
                    stopSelf()
                }
            }
        } else {
            bubbleView?.updateState(stateManager.recordingState.value)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
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

    private fun startRecording() {
        Log.d(TAG, "startRecording - countdown starting")
        
        stateManager.updateState(RecordingState.COUNTDOWN)
        mainHandler.post {
            bubbleView?.updateState(RecordingState.COUNTDOWN)
        }
        
        serviceScope.launch {
            for (i in 3 downTo 1) {
                if (stateManager.recordingState.value != RecordingState.COUNTDOWN) {
                    Log.d(TAG, "Countdown cancelled, state is ${stateManager.recordingState.value}")
                    return@launch
                }
                mainHandler.post {
                    bubbleView?.updateTimerText(i.toString())
                }
                delay(1000)
            }
            
            if (stateManager.recordingState.value != RecordingState.COUNTDOWN) return@launch
            
            mainHandler.post {
                performStart()
            }
        }
    }

    private fun performStart() {
        Log.d(TAG, "performStart - beginning actual capture")
        serviceScope.launch {
            try {
                val settings = settingsDataStore.settings.first()
                
                if (mediaProjection == null) {
                    Log.e(TAG, "MediaProjection is NULL in performStart")
                    mainHandler.post { stopRecording() }
                    return@launch
                }

                mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        Log.d(TAG, "MediaProjection stopped callback")
                        mainHandler.post { stopRecording() }
                    }
                }, mainHandler)

                val metrics = android.util.DisplayMetrics()
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealMetrics(metrics)

                // Force 720p if native is too high, for stability
                var screenWidth = metrics.widthPixels
                var screenHeight = metrics.heightPixels
                
                if (settings.videoResolution == "720p") {
                    val ratio = screenWidth.toFloat() / screenHeight.toFloat()
                    if (screenWidth > screenHeight) { // landscape
                        screenWidth = 1280
                        screenHeight = (1280 / ratio).toInt()
                    } else { // portrait
                        screenHeight = 1280
                        screenWidth = (1280 * ratio).toInt()
                    }
                }

                // Ensure dimensions are even
                screenWidth = screenWidth and 0xFFFFFFFE.toInt()
                screenHeight = screenHeight and 0xFFFFFFFE.toInt()
                val density = metrics.densityDpi

                Log.d(TAG, "Recording resolution: ${screenWidth}x${screenHeight} at $density dpi")

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "Rekodi_$timestamp.mp4"
                val dir = getExternalFilesDir(null)
                outputFile = File(dir, fileName)

                mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(this@RecordingForegroundService)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }.apply {
                    val hasAudioPerm = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    
                    if (hasAudioPerm && settings.audioSource != "Mute") {
                        setAudioSource(MediaRecorder.AudioSource.MIC)
                    }
                    setVideoSource(MediaRecorder.VideoSource.SURFACE)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    
                    if (hasAudioPerm && settings.audioSource != "Mute") {
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setAudioSamplingRate(settings.sampleRate)
                        setAudioEncodingBitRate(128_000)
                    }
                    
                    setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                    
                    val targetBitrate = when (settings.videoBitrate) {
                        "Low" -> 2_000_000
                        "Medium" -> 5_000_000
                        "High" -> 10_000_000
                        else -> 5_000_000
                    }
                    
                    setVideoEncodingBitRate(targetBitrate)
                    setVideoFrameRate(settings.videoFps)
                    setVideoSize(screenWidth, screenHeight)
                    setOutputFile(outputFile?.absolutePath)
                    
                    try {
                        prepare()
                        Log.d(TAG, "MediaRecorder prepared")
                    } catch (e: Exception) {
                        Log.e(TAG, "MediaRecorder prepare failed: ${e.message}", e)
                        throw e
                    }
                }

                val surface = mediaRecorder?.surface
                if (surface != null) {
                    virtualDisplay = mediaProjection?.createVirtualDisplay(
                        "RekodiDisplay",
                        screenWidth, screenHeight, density,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        surface, null, null
                    )
                    Log.d(TAG, "VirtualDisplay created successfully")
                } else {
                    throw IllegalStateException("MediaRecorder surface is null")
                }

                try {
                    mediaRecorder?.start()
                    Log.d(TAG, "MediaRecorder started successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "MediaRecorder start failed: ${e.message}", e)
                    throw e
                }

                mainHandler.post {
                    stateManager.updateState(RecordingState.RECORDING)
                    bubbleView?.updateState(RecordingState.RECORDING)
                    updateNotification()
                    startTimer()
                }

                val bitrateForEntity = when (settings.videoBitrate) {
                    "Low" -> 2_000_000
                    "Medium" -> 5_000_000
                    "High" -> 10_000_000
                    else -> 5_000_000
                }

                val entity = RecordingEntity(
                    filePath = outputFile?.absolutePath ?: "",
                    fileName = fileName,
                    mimeType = "video/mp4",
                    frameRate = settings.videoFps,
                    bitrate = bitrateForEntity,
                    resolution = "${screenWidth}x${screenHeight}"
                )
                recordingId = recordingRepository.insertRecording(entity)
                Log.d(TAG, "Recording entity inserted with ID: $recordingId")
            } catch (e: Exception) {
                Log.e(TAG, "CRITICAL: performStart failed", e)
                mainHandler.post { stopRecording() }
            }
        }
    }

    private fun stopRecording() {
        Log.d(TAG, "stopRecording - terminating session")
        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: RuntimeException) {
                    Log.w(TAG, "MediaRecorder stop failed (likely no data captured): ${e.message}")
                }
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during MediaRecorder cleanup: ${e.message}")
        }
        mediaRecorder = null
        virtualDisplay?.release()
        virtualDisplay = null
        
        try {
            mediaProjection?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaProjection: ${e.message}")
        }
        mediaProjection = null

        stateManager.updateState(RecordingState.IDLE)
        mainHandler.post {
            bubbleView?.updateState(RecordingState.IDLE)
        }
        
        val elapsed = _elapsedSeconds.value
        _elapsedSeconds.value = 0

        if (recordingId > 0) {
            serviceScope.launch {
                val entity = recordingRepository.getRecordingById(recordingId)
                if (entity != null) {
                    // If file is too small (failed recording), delete the entity
                    if ((outputFile?.length() ?: 0) < 1024) {
                        Log.w(TAG, "Recording file is too small, deleting entity")
                        recordingRepository.deleteRecordingById(recordingId)
                    } else {
                        recordingRepository.updateRecording(
                            entity.copy(
                                durationMs = elapsed * 1000,
                                fileSizeBytes = outputFile?.length() ?: 0,
                                tapCount = stateManager.tapCount.value
                            )
                        )
                    }
                }
                recordingId = -1
                stateManager.resetTapCount()
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        bubbleView?.hide()
        bubbleView = null
        stopSelf()
    }

    private fun pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try { 
                mediaRecorder?.pause() 
                stateManager.updateState(RecordingState.PAUSED)
                bubbleView?.updateState(RecordingState.PAUSED)
                updateNotification()
                Log.d(TAG, "Recording paused")
            } catch (e: Exception) {
                Log.e(TAG, "Error pausing MediaRecorder", e)
            }
        }
    }

    private fun resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try { 
                mediaRecorder?.resume() 
                stateManager.updateState(RecordingState.RECORDING)
                bubbleView?.updateState(RecordingState.RECORDING)
                updateNotification()
                Log.d(TAG, "Recording resumed")
            } catch (e: Exception) {
                Log.e(TAG, "Error resuming MediaRecorder", e)
            }
        }
    }

    private fun safeguardRecording() {
        Log.d(TAG, "safeguardRecording")
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {}
        mediaRecorder = null
        virtualDisplay?.release()
        virtualDisplay = null
        stateManager.updateState(RecordingState.IDLE)
    }

    private fun releaseResources() {
        Log.d(TAG, "releaseResources")
        try { mediaRecorder?.release() } catch (_: Exception) {}
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { mediaProjection?.stop() } catch (_: Exception) {}
        mediaRecorder = null
        virtualDisplay = null
        mediaProjection = null
    }

    private fun startTimer() {
        serviceScope.launch {
            while (stateManager.recordingState.value == RecordingState.RECORDING) {
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
                action = if (stateManager.recordingState.value == RecordingState.PAUSED) ACTION_RESUME else ACTION_PAUSE
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val elapsed = _elapsedSeconds.value
        val mins = elapsed / 60
        val secs = elapsed % 60
        val timeStr = String.format("%02d:%02d", mins, secs)

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(if (stateManager.recordingState.value == RecordingState.RECORDING) "Recording Screen" else "Rekodi is Ready")
            .setContentText(if (stateManager.recordingState.value == RecordingState.RECORDING) "$timeStr · ${stateManager.tapCount.value} taps" else "Tap bubble to start")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setSilent(true)

        if (stateManager.recordingState.value != RecordingState.IDLE) {
            builder.addAction(
                if (stateManager.recordingState.value == RecordingState.PAUSED)
                    android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (stateManager.recordingState.value == RecordingState.PAUSED) "Resume" else "Pause",
                pauseIntent
            )
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
        }

        return builder.build()
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }
}
