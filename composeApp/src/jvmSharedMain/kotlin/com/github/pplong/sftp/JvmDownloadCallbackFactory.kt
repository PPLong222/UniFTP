package com.github.pplong.sftp

import java.io.File
import java.io.FileOutputStream

/**
 * JVM-specific implementation of DownloadCallbackFactory
 * Works with file system paths (Desktop and Android with file paths)
 */
class JvmFileDownloadCallbackFactory : DownloadCallbackFactory {
    override fun create(
        remotePath: String,
        fileName: String,
        downloadDir: String,
        onProgressUpdate: (Float) -> Unit,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit
    ): DownloadCallback {
        return JvmFileDownloadCallback(
            fileName = fileName,
            downloadDir = downloadDir,
            onProgressUpdate = onProgressUpdate,
            onComplete = onComplete,
            onError = onError
        )
    }
}

/**
 * JVM-specific DownloadCallback implementation using File I/O
 * Supports Desktop and legacy Android file access
 */
private class JvmFileDownloadCallback(
    private val fileName: String,
    private val downloadDir: String,
    private val onProgressUpdate: (Float) -> Unit,
    private val onComplete: () -> Unit,
    private val onError: (Throwable) -> Unit
) : DownloadCallback {

    override suspend fun openOutputStream(fileSize: Long, resumeOffset: Long): Pair<Any, Long>? {
        return try {
            val dir = File(downloadDir)
            dir.mkdirs()

            // Generate unique file name if file already exists
            // This prevents overwriting existing files
            val uniqueFile = generateUniqueFileName(dir, fileName)

            // Create output stream (always new file, no append)
            val outputStream = FileOutputStream(uniqueFile, false)

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
    private fun generateUniqueFileName(dir: File, fileName: String): File {
        var file = File(dir, fileName)

        if (!file.exists()) {
            return file
        }

        // Split file name and extension
        val lastDotIndex = fileName.lastIndexOf('.')
        val name = if (lastDotIndex > 0) fileName.substring(0, lastDotIndex) else fileName
        val extension = if (lastDotIndex > 0) fileName.substring(lastDotIndex) else ""

        // Try file (1), file (2), etc.
        var counter = 1
        while (file.exists()) {
            val newFileName = "$name ($counter)$extension"
            file = File(dir, newFileName)
            counter++
        }

        return file
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
