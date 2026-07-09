package com.camelcreatives.rekodi.recorder.model

import android.net.Uri

data class Recording(
    val id: Long = 0,
    val filePath: String = "",
    val fileName: String = "",
    val mimeType: String = "video/mp4",
    val durationMs: Long = 0,
    val fileSizeBytes: Long = 0,
    val resolution: String = "",
    val frameRate: Int = 30,
    val bitrate: Int = 0,
    val tapCount: Int = 0,
    val isFavorite: Boolean = false,
    val tags: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    val uri: Uri get() = Uri.parse(filePath)
    val durationSeconds: Long get() = durationMs / 1000
}

enum class RecordingState {
    IDLE,
    COUNTDOWN,
    RECORDING,
    PAUSED,
    STOPPING
}

data class RecorderConfig(
    val resolution: String = "Auto",
    val fps: Int = 30,
    val bitrate: String = "Medium",
    val orientationLock: String = "Auto",
    val audioSource: String = "Mic+Internal",
    val sampleRate: Int = 44100,
    val audioChannels: String = "Stereo",
    val countdownEnabled: Boolean = true
)
