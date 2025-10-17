package com.github.pplong.sftp

import java.io.File
import java.io.FileInputStream

/**
 * JVM-specific implementation of UploadCallbackFactory
 * Works with file system paths (Desktop and Android with file paths)
 */
class JvmFileUploadCallbackFactory : UploadCallbackFactory {
    override fun create(
        localUri: String,
        fileName: String,
        remotePath: String,
        onProgressUpdate: (Float) -> Unit,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit
    ): UploadCallback {
        return JvmFileUploadCallback(
            localPath = localUri,
            fileName = fileName,
            onProgressUpdate = onProgressUpdate,
            onComplete = onComplete,
            onError = onError
        )
    }
}

/**
 * JVM-specific UploadCallback implementation using File I/O
 * Supports Desktop and legacy Android file access
 */
private class JvmFileUploadCallback(
    private val localPath: String,
    private val fileName: String,
    private val onProgressUpdate: (Float) -> Unit,
    private val onComplete: () -> Unit,
    private val onError: (Throwable) -> Unit
) : UploadCallback {

    override suspend fun openInputStream(): Pair<Any, Long>? {
        return try {
            val file = File(localPath)

            if (!file.exists()) {
                val error = IllegalArgumentException("File not found: $localPath")
                println(error.message)
                onError(error)
                return null
            }

            if (!file.isFile) {
                val error = IllegalArgumentException("Path is not a file: $localPath")
                println(error.message)
                onError(error)
                return null
            }

            val fileSize = file.length()
            val inputStream = FileInputStream(file)

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
