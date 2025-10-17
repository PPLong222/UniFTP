package com.github.pplong.sftp

import android.app.Application

/**
 * Android implementation of PlatformUploadCallbackFactory
 * Uses application context to create Android-specific upload callbacks
 */
actual object PlatformUploadCallbackFactory {
    private lateinit var application: Application

    /**
     * Initialize with application context
     * This should be called from Application.onCreate()
     */
    fun init(app: Application) {
        application = app
    }

    actual fun get(): UploadCallbackFactory {
        if (!::application.isInitialized) {
            throw IllegalStateException(
                "PlatformUploadCallbackFactory not initialized. " +
                "Call PlatformUploadCallbackFactory.init(application) in Application.onCreate()"
            )
        }
        return AndroidUploadCallbackFactory(application)
    }
}
