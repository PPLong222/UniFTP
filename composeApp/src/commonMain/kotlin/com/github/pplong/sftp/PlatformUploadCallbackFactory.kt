package com.github.pplong.sftp

/**
 * Platform-specific factory for creating UploadCallbackFactory instances
 * Each platform (Android, Desktop, iOS) provides its own implementation
 */
expect object PlatformUploadCallbackFactory {
    /**
     * Get the platform-specific UploadCallbackFactory instance
     * @return Platform-specific implementation of UploadCallbackFactory
     */
    fun get(): UploadCallbackFactory
}
