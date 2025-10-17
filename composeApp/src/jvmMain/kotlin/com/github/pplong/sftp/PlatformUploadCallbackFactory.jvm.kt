package com.github.pplong.sftp

/**
 * Desktop JVM implementation of PlatformUploadCallbackFactory
 * Uses file system paths for uploads
 */
actual object PlatformUploadCallbackFactory {
    actual fun get(): UploadCallbackFactory {
        return JvmFileUploadCallbackFactory()
    }
}
