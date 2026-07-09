package com.camelcreatives.rekodi.recorder.di

import android.content.Context
import android.view.WindowManager
import com.camelcreatives.rekodi.recorder.overlay.ZoomOverlayView
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RecorderModule {

    @Provides
    @Singleton
    fun provideWindowManager(@ApplicationContext context: Context): WindowManager {
        return context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    @Provides
    @Singleton
    fun provideZoomOverlayView(
        @ApplicationContext context: Context,
        windowManager: WindowManager
    ): ZoomOverlayView {
        return ZoomOverlayView(context, windowManager)
    }
}
