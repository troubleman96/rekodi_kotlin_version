package com.camelcreatives.rekodi.common

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Context.getRecordingOutputFile(): File {
    val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    return File(dir, "Rekodi_$timestamp.mp4")
}

fun Context.getAudioOutputFile(): File {
    val dir = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    return File(dir, "Rekodi_Audio_$timestamp.m4a")
}

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%02d:%02d", m, s)
}

fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024
    val mb = kb / 1024
    val gb = mb / 1024
    return when {
        gb > 0 -> String.format("%.1f GB", bytes / (1024f * 1024f * 1024f))
        mb > 0 -> String.format("%.1f MB", bytes / (1024f * 1024f))
        kb > 0 -> String.format("%.1f KB", bytes / 1024f)
        else -> "$bytes B"
    }
}
