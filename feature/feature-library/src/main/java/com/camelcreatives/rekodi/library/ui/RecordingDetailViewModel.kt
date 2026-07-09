package com.camelcreatives.rekodi.library.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.camelcreatives.rekodi.data.local.entity.RecordingEntity
import com.camelcreatives.rekodi.data.repository.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class RecordingDetailViewModel @Inject constructor(
    private val repository: RecordingRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val recordingId: Long = checkNotNull(savedStateHandle["recordingId"])

    val recording: StateFlow<RecordingEntity?> = repository.getRecordingByIdFlow(recordingId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun deleteRecording() {
        viewModelScope.launch {
            recording.value?.let {
                repository.deleteRecording(it)
                // The actual file deletion should also happen here
                val file = File(it.filePath)
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            recording.value?.let {
                repository.updateRecording(it.copy(isFavorite = !it.isFavorite))
            }
        }
    }

    fun updateNotes(notes: String) {
        viewModelScope.launch {
            recording.value?.let {
                repository.updateRecording(it.copy(notes = notes))
            }
        }
    }

    fun shareRecording(context: Context) {
        val r = recording.value ?: return
        val file = File(r.filePath)
        if (!file.exists()) return

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = r.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Recording"))
    }
}
