package com.camelcreatives.rekodi

import android.app.Application
import android.content.Intent
import com.camelcreatives.rekodi.recorder.service.RecordingForegroundService
import com.camelcreatives.rekodi.recorder.service.RecordingState

class RekodiCrashHandler(private val app: Application) : Thread.UncaughtExceptionHandler {
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val intent = Intent(app, RecordingForegroundService::class.java).apply {
            action = RecordingForegroundService.ACTION_CRASH_SAFEGUARD
        }
        try {
            app.startForegroundService(intent)
        } catch (_: Exception) {}
        defaultHandler?.uncaughtException(thread, throwable)
    }
}
