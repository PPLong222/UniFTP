package com.github.pplong.sftp

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.net.toUri
import java.io.InputStream

/**
 * Android-specific implementation of UploadCallbackFactory
 * Supports both content:// URIs (Scoped Storage) and legacy file paths
 */
class AndroidUploadCallbackFactory(
    private val context: Context
) : UploadCallbackFactory {
    override fun create(
        localUri: String,
        fileName: String,
        remotePath: String,
        onProgressUpdate: (Float) -> Unit,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit
    ): UploadCallback {
        return AndroidUploadCallback(
            context = context,
            localUri = localUri,
            fileName = fileName,
            onProgressUpdate = onProgressUpdate,
            onComplete = onComplete,
            onError = onError
        )
    }
}

/**
 * Android-specific UploadCallback implementation
 * Supports content:// URIs for Android 10+ Scoped Storage
 */
private class AndroidUploadCallback(
    private val context: Context,
    private val localUri: String,
    private val fileName: String,
    private val onProgressUpdate: (Float) -> Unit,
    private val onComplete: () -> Unit,
    private val onError: (Throwable) -> Unit
) : UploadCallback {

    override suspend fun openInputStream(): Pair<Any, Long>? {
        return try {
            val contentResolver = context.contentResolver

            // Check if localUri is a content:// URI
            if (localUri.startsWith("content://")) {
                openContentUriInputStream(contentResolver, localUri)
            } else {
                // Fallback to file path (for legacy support)
                openFileInputStream(localUri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onError(e)
            null
        }
    }

    /**
     * Open InputStream for content:// URI (Android 10+ Scoped Storage)
     */
    private fun openContentUriInputStream(
        contentResolver: ContentResolver,
        uriString: String
    ): Pair<InputStream, Long>? {
        try {
            val uri = uriString.toUri()

            // Get file size
            val fileSize = getFileSize(contentResolver, uri)
            if (fileSize <= 0) {
                println("Invalid file size: $fileSize")
                return null
            }

            // Open input stream
            val inputStream = contentResolver.openInputStream(uri)
                ?: return null

            return inputStream to fileSize
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Open InputStream for file path (legacy support)
     */
    private fun openFileInputStream(filePath: String): Pair<InputStream, Long>? {
        try {
            val file = java.io.File(filePath)

            if (!file.exists()) {
                println("File not found: $filePath")
                return null
            }

            val fileSize = file.length()
            val inputStream = java.io.FileInputStream(file)

            return inputStream to fileSize
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Get file size from content URI
     */
    private fun getFileSize(contentResolver: ContentResolver, uri: Uri): Long {
        return try {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndexOrThrow(OpenableColumns.SIZE)
                    cursor.getLong(sizeIndex)
                } else {
                    -1L
                }
            } ?: -1L
        } catch (e: Exception) {
            e.printStackTrace()
            -1L
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
