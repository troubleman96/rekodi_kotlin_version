package com.camelcreatives.rekodi.editor.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.camelcreatives.rekodi.data.local.entity.RecordingEntity
import com.camelcreatives.rekodi.data.repository.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AudioEditorViewModel @Inject constructor(
    private val repository: RecordingRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val recordingId: String = checkNotNull(savedStateHandle["recordingId"])
    private val idLong = recordingId.toLongOrNull() ?: -1L

    val recording: StateFlow<RecordingEntity?> = repository.getRecordingByIdFlow(idLong)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
