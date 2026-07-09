package com.camelcreatives.rekodi.data.di

import android.content.Context
import androidx.room.Room
import com.camelcreatives.rekodi.data.local.RekodiDatabase
import com.camelcreatives.rekodi.data.local.dao.RecordingDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RekodiDatabase {
        return Room.databaseBuilder(
            context,
            RekodiDatabase::class.java,
            "rekodi_database"
        ).build()
    }

    @Provides
    @Singleton
    fun provideRecordingDao(database: RekodiDatabase): RecordingDao {
        return database.recordingDao()
    }
}
