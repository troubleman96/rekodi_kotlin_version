package com.camelcreatives.rekodi.data.repository

import com.camelcreatives.rekodi.data.local.dao.RecordingDao
import com.camelcreatives.rekodi.data.local.entity.RecordingEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingRepository @Inject constructor(
    private val recordingDao: RecordingDao
) {
    fun getAllRecordings(): Flow<List<RecordingEntity>> = recordingDao.getAllRecordings()
    fun getVideoRecordings(): Flow<List<RecordingEntity>> = recordingDao.getVideoRecordings()
    fun getAudioRecordings(): Flow<List<RecordingEntity>> = recordingDao.getAudioRecordings()
    fun getFavoriteRecordings(): Flow<List<RecordingEntity>> = recordingDao.getFavoriteRecordings()
    fun searchRecordings(query: String): Flow<List<RecordingEntity>> = recordingDao.searchRecordings(query)
    fun getRecordingByIdFlow(id: Long): Flow<RecordingEntity?> = recordingDao.getRecordingByIdFlow(id)
    suspend fun getRecordingById(id: Long): RecordingEntity? = recordingDao.getRecordingById(id)
    suspend fun insertRecording(recording: RecordingEntity): Long = recordingDao.insertRecording(recording)
    suspend fun updateRecording(recording: RecordingEntity) = recordingDao.updateRecording(recording)
    suspend fun deleteRecording(recording: RecordingEntity) = recordingDao.deleteRecording(recording)
    suspend fun deleteRecordingById(id: Long) = recordingDao.deleteRecordingById(id)
}
