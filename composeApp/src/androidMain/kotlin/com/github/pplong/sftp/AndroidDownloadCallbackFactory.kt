package com.github.pplong.sftp

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import java.io.OutputStream

/**
 * Android-specific implementation of DownloadCallbackFactory
 * Supports both content:// URIs (Scoped Storage) and legacy file paths
 */
class AndroidDownloadCallbackFactory(
    private val context: Context
) : DownloadCallbackFactory {
    override fun create(
        remotePath: String,
        fileName: String,
        downloadDir: String,
        onProgressUpdate: (Float) -> Unit,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit
    ): DownloadCallback {
        return AndroidDownloadCallback(
            context = context,
            fileName = fileName,
            downloadDir = downloadDir,
            onProgressUpdate = onProgressUpdate,
            onComplete = onComplete,
            onError = onError
        )
    }
}

/**
 * Android-specific DownloadCallback implementation
 * Supports content:// URIs for Android 10+ Scoped Storage
 */
private class AndroidDownloadCallback(
    private val context: Context,
    private val fileName: String,
    private val downloadDir: String,
    private val onProgressUpdate: (Float) -> Unit,
    private val onComplete: () -> Unit,
    private val onError: (Throwable) -> Unit
) : DownloadCallback {

    override suspend fun openOutputStream(fileSize: Long, resumeOffset: Long): Pair<Any, Long>? {
        return try {
            val contentResolver = context.contentResolver

            // Check if downloadDir is a content:// URI
            if (downloadDir.startsWith("content://")) {
                openContentUriOutputStream(contentResolver, downloadDir, fileName)
            } else {
                // Fallback to file path (for legacy support or Desktop JVM)
                openFileOutputStream(downloadDir, fileName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onError(e)
            null
        }
    }

    /**
     * Open OutputStream for content:// URI (Android 10+ Scoped Storage)
     *
     * Strategy: Always create a new file. If file exists, Android will auto-rename it.
     * For example: file.txt -> file (1).txt -> file (2).txt
     */
    private fun openContentUriOutputStream(
        contentResolver: ContentResolver,
        dirUriString: String,
        fileName: String
    ): Pair<OutputStream, Long>? {
        try {
            val dirUri = dirUriString.toUri()

            // Verify we have a tree URI (directory selection)
            if (!DocumentsContract.isTreeUri(dirUri)) {
                println("Invalid URI: not a tree URI (directory). Please select a directory using the directory picker.")
                return null
            }

            // Always create new file - Android will auto-rename if file exists
            // e.g., "file.txt" becomes "file (1).txt" if "file.txt" exists
            val newUri = createFile(contentResolver, dirUri, fileName)
                ?: return null

            val outputStream = contentResolver.openOutputStream(newUri, "w")
                ?: return null

            return outputStream to 0L

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Open OutputStream for file path (legacy support)
     *
     * Strategy: Auto-rename if file exists (similar to browser downloads)
     * For example: file.txt -> file (1).txt -> file (2).txt
     */
    private fun openFileOutputStream(
        dirPath: String,
        fileName: String
    ): Pair<OutputStream, Long>? {
        try {
            val dir = java.io.File(dirPath)
            dir.mkdirs()

            // Generate unique file name if file already exists
            val uniqueFile = generateUniqueFileName(dir, fileName)

            val outputStream = java.io.FileOutputStream(uniqueFile, false)
            return outputStream to 0L
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Generate unique file name by adding (1), (2), etc. if file exists
     */
    private fun generateUniqueFileName(dir: java.io.File, fileName: String): java.io.File {
        var file = java.io.File(dir, fileName)

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
            file = java.io.File(dir, newFileName)
            counter++
        }

        return file
    }

    /**
     * Find existing file in the directory
     */
    private fun findExistingFile(
        contentResolver: ContentResolver,
        dirUri: Uri,
        fileName: String
    ): Uri? {
        return try {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                dirUri,
                DocumentsContract.getTreeDocumentId(dirUri)
            )

            contentResolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameColumn)
                    if (name == fileName) {
                        val documentId = cursor.getString(idColumn)
                        return DocumentsContract.buildDocumentUriUsingTree(dirUri, documentId)
                    }
                }
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Create a new file in the directory
     */
    private fun createFile(
        contentResolver: ContentResolver,
        dirUri: Uri,
        fileName: String
    ): Uri? {
        return try {
            val treeDocumentId = DocumentsContract.getTreeDocumentId(dirUri)
            DocumentsContract.createDocument(
                contentResolver,
                DocumentsContract.buildDocumentUriUsingTree(dirUri, treeDocumentId),
                "application/octet-stream", // Generic MIME type
                fileName
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get file size from content URI
     */
    private fun getFileSize(contentResolver: ContentResolver, uri: Uri): Long {
        return try {
            contentResolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                    cursor.getLong(sizeIndex)
                } else {
                    0L
                }
            } ?: 0L
        } catch (e: Exception) {
            e.printStackTrace()
            0L
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
