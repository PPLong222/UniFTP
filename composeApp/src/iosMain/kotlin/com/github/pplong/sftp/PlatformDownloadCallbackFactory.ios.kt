package com.github.pplong.sftp

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.*

/**
 * iOS-specific implementation of PlatformDownloadCallbackFactory
 * Uses iOS FileManager for file I/O operations
 */
actual object PlatformDownloadCallbackFactory {
    actual fun get(): DownloadCallbackFactory {
        return IosFileDownloadCallbackFactory()
    }
}

/**
 * iOS-specific implementation of DownloadCallbackFactory
 * Works with iOS file system paths using FileManager
 */
class IosFileDownloadCallbackFactory : DownloadCallbackFactory {
    override fun create(
        remotePath: String,
        fileName: String,
        downloadDir: String,
        onProgressUpdate: (Float) -> Unit,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit
    ): DownloadCallback {
        return IosFileDownloadCallback(
            fileName = fileName,
            downloadDir = downloadDir,
            onProgressUpdate = onProgressUpdate,
            onComplete = onComplete,
            onError = onError
        )
    }
}

/**
 * iOS-specific DownloadCallback implementation using FileManager
 * Supports iOS Documents directory and other accessible locations
 */
@OptIn(ExperimentalForeignApi::class)
private class IosFileDownloadCallback(
    private val fileName: String,
    private val downloadDir: String,
    private val onProgressUpdate: (Float) -> Unit,
    private val onComplete: () -> Unit,
    private val onError: (Throwable) -> Unit
) : DownloadCallback {

    override suspend fun openOutputStream(fileSize: Long, resumeOffset: Long): Pair<Any, Long>? {
        return try {
            val fileManager = NSFileManager.defaultManager

            // Ensure download directory exists
            var isDirectory = false
            val dirExists = fileManager.fileExistsAtPath(downloadDir, isDirectory = null)

            if (!dirExists) {
                val created = fileManager.createDirectoryAtPath(
                    path = downloadDir,
                    withIntermediateDirectories = true,
                    attributes = null,
                    error = null
                )
                if (!created) {
                    val error = IllegalStateException("Failed to create download directory: $downloadDir")
                    println(error.message)
                    onError(error)
                    return null
                }
            }

            // Generate unique file name if file already exists
            val uniqueFilePath = generateUniqueFileName(fileManager, downloadDir, fileName)

            // Create output stream
            val outputStream = NSOutputStream.outputStreamToFileAtPath(
                path = uniqueFilePath,
                append = false
            )

            if (outputStream == null) {
                val error = IllegalStateException("Failed to create output stream for: $uniqueFilePath")
                println(error.message)
                onError(error)
                return null
            }

            // Open the stream
            outputStream.open()

            if (outputStream.streamStatus == NSStreamStatusError) {
                val error = IllegalStateException("Failed to open output stream: ${outputStream.streamError?.localizedDescription}")
                println(error.message)
                onError(error)
                return null
            }

            // Return output stream and existing file size (0 for new files)
            outputStream to 0L
        } catch (e: Exception) {
            e.printStackTrace()
            onError(e)
            null
        }
    }

    /**
     * Generate unique file name by adding (1), (2), etc. if file exists
     * Example: file.txt -> file (1).txt -> file (2).txt
     */
    private fun generateUniqueFileName(
        fileManager: NSFileManager,
        dirPath: String,
        fileName: String
    ): String {
        // Build full path
        val separator = if (dirPath.endsWith("/")) "" else "/"
        var filePath = "$dirPath$separator$fileName"

        // Check if file exists
        if (!fileManager.fileExistsAtPath(filePath)) {
            return filePath
        }

        // Split file name and extension
        val lastDotIndex = fileName.lastIndexOf('.')
        val name = if (lastDotIndex > 0) fileName.substring(0, lastDotIndex) else fileName
        val extension = if (lastDotIndex > 0) fileName.substring(lastDotIndex) else ""

        // Try file (1), file (2), etc.
        var counter = 1
        while (fileManager.fileExistsAtPath(filePath)) {
            val newFileName = "$name ($counter)$extension"
            filePath = "$dirPath$separator$newFileName"
            counter++
        }

        return filePath
    }

    override fun onProgress(bytesTransferred: Long, totalBytes: Long) {
        if (totalBytes > 0) {
            val progress = bytesTransferred.toFloat() / totalBytes.toFloat()
            onProgressUpdate(progress)
        }
    }

    override fun onComplete() {
        onComplete.invoke()
    }

    override fun onError(error: Throwable) {
        onError.invoke(error)
    }
}
