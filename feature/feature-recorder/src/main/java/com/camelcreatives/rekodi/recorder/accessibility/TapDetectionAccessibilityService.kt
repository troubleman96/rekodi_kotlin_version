package com.camelcreatives.rekodi.recorder.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.camelcreatives.rekodi.recorder.model.RecordingState
import com.camelcreatives.rekodi.recorder.overlay.ZoomOverlayView
import com.camelcreatives.rekodi.recorder.service.RecordingForegroundService
import com.camelcreatives.rekodi.recorder.service.RecordingStateManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TapDetectionAccessibilityService : AccessibilityService() {

    @Inject lateinit var zoomOverlay: ZoomOverlayView
    @Inject lateinit var stateManager: RecordingStateManager

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
        if (event == null || stateManager.recordingState.value != RecordingState.RECORDING) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
                stateManager.incrementTapCount()
                updateTapCount()
                zoomOverlay.let {
                    event.source?.let { source ->
                        val rect = android.graphics.Rect()
                        source.getBoundsInScreen(rect)
                        it.simulateTap(rect.centerX().toFloat(), rect.centerY().toFloat())
                    }
                }
            }
        }
    }

    override fun onInterrupt() {}

    private fun updateTapCount() {
        val intent = Intent(this, RecordingForegroundService::class.java).apply {
            action = RecordingForegroundService.ACTION_UPDATE_TAP_COUNT
            putExtra(RecordingForegroundService.EXTRA_TAP_COUNT, stateManager.tapCount.value)
        }
        startService(intent)
    }
}
