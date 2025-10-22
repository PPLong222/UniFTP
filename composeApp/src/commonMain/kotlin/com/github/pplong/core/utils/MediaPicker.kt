package com.github.pplong.core.utils

import androidx.compose.runtime.Composable

/**
 * Platform-specific media picker interface for selecting photos and videos.
 * Returns a lambda that launches the media picker when invoked.
 * Supports multiple media selection.
 */
@Composable
expect fun rememberMediaPicker(
    onMediaSelected: (List<FilePickerResult>?) -> Unit
): () -> Unit
