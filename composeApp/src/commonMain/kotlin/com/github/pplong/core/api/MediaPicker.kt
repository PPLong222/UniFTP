package com.github.pplong.core.api

import androidx.compose.runtime.Composable

/**
 * File information for upload
 */
data class FilePickerResult(
    val uri: String,        // File URI or path
    val name: String,       // File name
    val size: Long,         // File size in bytes
    val lastModified: Long = 0L  // Last modified time (optional, default 0)
)

/**
 * Platform-specific media picker interface for selecting photos and videos.
 * Returns a lambda that launches the media picker when invoked.
 * Supports multiple media selection.
 */
@Composable
expect fun rememberMediaPicker(
    onMediaSelected: (List<FilePickerResult>?) -> Unit
): () -> Unit

/**
 * Platform-specific file picker interface for selecting files to upload.
 * Returns a lambda that launches the file picker when invoked.
 * Supports multiple file selection.
 */
@Composable
expect fun rememberFilePicker(
    onFilesSelected: (List<FilePickerResult>?) -> Unit
): () -> Unit

/**
 * Platform-specific directory picker interface.
 * Returns a lambda that launches the directory picker when invoked.
 */
@Composable
expect fun rememberDirectoryPicker(
    onDirectorySelected: (String?) -> Unit
): () -> Unit

interface DownloadDirProvider {
    suspend fun getDefaultDownloadDir(): String
}