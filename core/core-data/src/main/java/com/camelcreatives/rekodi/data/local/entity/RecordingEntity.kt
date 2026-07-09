package com.camelcreatives.rekodi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filePath: String,
    val fileName: String,
    val mimeType: String,
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
)
