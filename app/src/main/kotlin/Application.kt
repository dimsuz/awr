package com.advaitaworld.app

import android.app.Application
import timber.log.Timber

public class AnApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }
}
