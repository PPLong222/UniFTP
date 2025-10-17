package com.github.pplong.sftp

/**
 * Platform-specific factory for creating DownloadCallbackFactory instances
 * Each platform (Android, Desktop, iOS) provides its own implementation
 */
expect object PlatformDownloadCallbackFactory {
    /**
     * Get the platform-specific DownloadCallbackFactory instance
     * @return Platform-specific implementation of DownloadCallbackFactory
     */
    fun get(): DownloadCallbackFactory
}
