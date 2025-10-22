package com.github.pplong.sftp

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.*

/**
 * iOS-specific implementation of PlatformUploadCallbackFactory
 * Uses iOS FileManager for file I/O operations
 */
actual object PlatformUploadCallbackFactory {
    actual fun get(): UploadCallbackFactory {
        return IosFileUploadCallbackFactory()
    }
}

/**
 * iOS-specific implementation of UploadCallbackFactory
 * Works with iOS file system paths using FileManager
 */
class IosFileUploadCallbackFactory : UploadCallbackFactory {
    override fun create(
        localUri: String,
        fileName: String,
        remotePath: String,
        onProgressUpdate: (Float) -> Unit,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit
    ): UploadCallback {
        return IosFileUploadCallback(
            localPath = localUri,
            fileName = fileName,
            onProgressUpdate = onProgressUpdate,
            onComplete = onComplete,
            onError = onError
        )
    }
}

/**
 * iOS-specific UploadCallback implementation using FileManager
 * Supports iOS file system paths
 */
@OptIn(ExperimentalForeignApi::class)
private class IosFileUploadCallback(
    private val localPath: String,
    private val fileName: String,
    private val onProgressUpdate: (Float) -> Unit,
    private val onComplete: () -> Unit,
    private val onError: (Throwable) -> Unit
) : UploadCallback {

    override suspend fun openInputStream(): Pair<Any, Long>? {
        return try {
            println("[iOS Upload] Starting to open input stream for: $localPath")
            val fileManager = NSFileManager.defaultManager

            // Check if file exists
            if (!fileManager.fileExistsAtPath(localPath)) {
                val error = IllegalArgumentException("File not found: $localPath")
                println("[iOS Upload ERROR] ${error.message}")
                onError(error)
                return null
            }
            println("[iOS Upload] File exists: $localPath")

            // Get file attributes to determine file size
            val attributes = fileManager.attributesOfItemAtPath(
                path = localPath,
                error = null
            )

            if (attributes == null) {
                val error = IllegalStateException("Failed to get file attributes for: $localPath")
                println(error.message)
                onError(error)
                return null
            }

            // Get file size
            val fileSizeNumber = attributes[NSFileSize] as? NSNumber
            if (fileSizeNumber == null) {
                val error = IllegalStateException("Failed to get file size for: $localPath")
                println(error.message)
                onError(error)
                return null
            }

            val fileSize = fileSizeNumber.longValue
            println("[iOS Upload] File size: $fileSize bytes")

            if (fileSize <= 0) {
                val error = IllegalArgumentException("Invalid file size: $fileSize for file: $localPath")
                println("[iOS Upload ERROR] ${error.message}")
                onError(error)
                return null
            }

            // Create input stream
            val inputStream = NSInputStream.inputStreamWithFileAtPath(localPath)

            if (inputStream == null) {
                val error = IllegalStateException("Failed to create input stream for: $localPath")
                println("[iOS Upload ERROR] ${error.message}")
                onError(error)
                return null
            }

            // Open the stream
            inputStream.open()

            if (inputStream.streamStatus == NSStreamStatusError) {
                val error = IllegalStateException("Failed to open input stream: ${inputStream.streamError?.localizedDescription}")
                println("[iOS Upload ERROR] ${error.message}")
                onError(error)
                return null
            }

            println("[iOS Upload] Input stream opened successfully")
            // Return input stream and file size
            inputStream to fileSize
        } catch (e: Exception) {
            e.printStackTrace()
            onError(e)
            null
        }
    }

    override fun onProgress(bytesTransferred: Long, totalBytes: Long) {
        if (totalBytes > 0) {
            val progress = bytesTransferred.toFloat() / totalBytes.toFloat()
            println("[iOS Upload] Progress: $bytesTransferred / $totalBytes bytes (${(progress * 100).toInt()}%)")
            onProgressUpdate(progress)
        }
    }

    override fun onComplete() {
        println("[iOS Upload] Upload completed successfully!")
        onComplete.invoke()
    }

    override fun onError(error: Throwable) {
        println("[iOS Upload ERROR] Upload failed: ${error.message}")
        onError.invoke(error)
    }
}
