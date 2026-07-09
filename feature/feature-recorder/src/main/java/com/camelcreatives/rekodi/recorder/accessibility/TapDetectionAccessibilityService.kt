package com.camelcreatives.rekodi.recorder.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.camelcreatives.rekodi.recorder.overlay.ZoomOverlayView
import com.camelcreatives.rekodi.recorder.service.RecordingForegroundService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TapDetectionAccessibilityService : AccessibilityService() {

    @Inject lateinit var zoomOverlay: ZoomOverlayView

    private var tapCount = 0
    private var isRecording = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 0
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !isRecording) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_TOUCH_INTERACTION_START -> {
                tapCount++
                updateTapCount()
                zoomOverlay.let {
                    if (event.source != null) {
                        val pos = IntArray(2)
                        event.source?.getLocationOnScreen(pos)
                    }
                }
            }
        }
    }

    override fun onInterrupt() {}

    fun setRecording(recording: Boolean) {
        isRecording = recording
        if (!recording) tapCount = 0
    }

    fun resetTapCount() { tapCount = 0 }

    private fun updateTapCount() {
        val intent = Intent(this, RecordingForegroundService::class.java).apply {
            action = RecordingForegroundService.ACTION_UPDATE_TAP_COUNT
            putExtra(RecordingForegroundService.EXTRA_TAP_COUNT, tapCount)
        }
        startService(intent)
    }
}
