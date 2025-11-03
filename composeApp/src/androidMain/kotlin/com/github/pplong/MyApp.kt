package com.github.pplong

import KoinInitializer
import android.app.Application
import com.github.pplong.sftp.PlatformUploadCallbackFactory

class MyApp: Application() {
    override fun onCreate() {
        super.onCreate()
        KoinInitializer(applicationContext).init()
        // Initialize platform-specific callback factories
        PlatformUploadCallbackFactory.init(this)
    }
}