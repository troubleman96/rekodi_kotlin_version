package com.camelcreatives.rekodi.di

import com.camelcreatives.rekodi.common.DefaultRekodiDispatchers
import com.camelcreatives.rekodi.common.RekodiDispatchers
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDispatchers(): RekodiDispatchers = DefaultRekodiDispatchers()
}
