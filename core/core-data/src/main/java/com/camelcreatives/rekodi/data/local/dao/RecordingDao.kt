package com.camelcreatives.rekodi.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.camelcreatives.rekodi.data.local.entity.RecordingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    fun getAllRecordings(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getRecordingById(id: Long): RecordingEntity?

    @Query("SELECT * FROM recordings WHERE id = :id")
    fun getRecordingByIdFlow(id: Long): Flow<RecordingEntity?>

    @Query("SELECT * FROM recordings WHERE mimeType LIKE 'video%' ORDER BY createdAt DESC")
    fun getVideoRecordings(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE mimeType LIKE 'audio%' ORDER BY createdAt DESC")
    fun getAudioRecordings(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE isFavorite = 1 ORDER BY createdAt DESC")
    fun getFavoriteRecordings(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE fileName LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchRecordings(query: String): Flow<List<RecordingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: RecordingEntity): Long

    @Update
    suspend fun updateRecording(recording: RecordingEntity)

    @Delete
    suspend fun deleteRecording(recording: RecordingEntity)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteRecordingById(id: Long)
}
