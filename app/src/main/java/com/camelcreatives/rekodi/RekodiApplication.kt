package com.camelcreatives.rekodi

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RekodiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(RekodiCrashHandler(this))
    }
}
