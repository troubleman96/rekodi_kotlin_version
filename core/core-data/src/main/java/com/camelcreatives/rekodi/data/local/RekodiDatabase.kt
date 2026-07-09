package com.camelcreatives.rekodi.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.camelcreatives.rekodi.data.local.dao.RecordingDao
import com.camelcreatives.rekodi.data.local.entity.RecordingEntity

@Database(entities = [RecordingEntity::class], version = 1, exportSchema = false)
abstract class RekodiDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
}
