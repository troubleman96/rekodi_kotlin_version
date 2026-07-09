package com.camelcreatives.rekodi.recorder.service

import com.camelcreatives.rekodi.recorder.model.RecordingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingStateManager @Inject constructor() {
    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState = _recordingState.asStateFlow()

    private val _tapCount = MutableStateFlow(0)
    val tapCount = _tapCount.asStateFlow()

    fun updateState(state: RecordingState) {
        _recordingState.value = state
    }

    fun setTapCount(count: Int) {
        _tapCount.value = count
    }

    fun incrementTapCount() {
        _tapCount.value += 1
    }

    fun resetTapCount() {
        _tapCount.value = 0
    }
}
