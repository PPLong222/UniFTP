package com.github.pplong.core.utils

import androidx.compose.runtime.Composable

/**
 * File information for upload
 */
data class FilePickerResult(
    val uri: String,        // File URI or path
    val name: String,       // File name
    val size: Long          // File size in bytes
)

/**
 * Platform-specific file picker interface for selecting files to upload.
 * Returns a lambda that launches the file picker when invoked.
 * Supports multiple file selection.
 */
@Composable
expect fun rememberFilePicker(
    onFilesSelected: (List<FilePickerResult>?) -> Unit
): () -> Unit
