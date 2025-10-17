package com.github.pplong.sftp

/**
 * Desktop JVM implementation of PlatformDownloadCallbackFactory
 * Uses file system paths for downloads
 */
actual object PlatformDownloadCallbackFactory {
    actual fun get(): DownloadCallbackFactory {
        return JvmFileDownloadCallbackFactory()
    }
}
