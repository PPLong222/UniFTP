package com.github.pplong.sftp

import android.app.Application

/**
 * Android implementation of PlatformDownloadCallbackFactory
 * Uses application context to create Android-specific download callbacks
 */
actual object PlatformDownloadCallbackFactory {
    private lateinit var application: Application

    /**
     * Initialize with application context
     * This should be called from Application.onCreate()
     */
    fun init(app: Application) {
        application = app
    }

    actual fun get(): DownloadCallbackFactory {
        if (!::application.isInitialized) {
            throw IllegalStateException(
                "PlatformDownloadCallbackFactory not initialized. " +
                "Call PlatformDownloadCallbackFactory.init(application) in Application.onCreate()"
            )
        }
        return AndroidDownloadCallbackFactory(application)
    }
}
