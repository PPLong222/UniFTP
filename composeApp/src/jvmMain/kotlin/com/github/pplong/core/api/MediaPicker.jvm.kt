package com.github.pplong.core.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * JVM implementation of media picker
 * Note: JVM/Desktop doesn't have a native media-only picker,
 * so we use the standard file picker with common media extensions
 */
@Composable
actual fun rememberMediaPicker(
    onMediaSelected: (List<FilePickerResult>?) -> Unit
): () -> Unit {
    val scope = rememberCoroutineScope()

    return {
        scope.launch {
            val results = withContext(Dispatchers.IO) {
                try {
                    val dialog = FileDialog(null as Frame?, "Select Media Files", FileDialog.LOAD)
                    dialog.isMultipleMode = true
                    // Set file filter for common media types
                    dialog.file = "*.jpg;*.jpeg;*.png;*.gif;*.bmp;*.mp4;*.mov;*.avi;*.mkv"
                    dialog.isVisible = true

                    val files = dialog.files
                    if (files.isNullOrEmpty()) {
                        null
                    } else {
                        files.map { file ->
                            FilePickerResult(
                                uri = file.absolutePath,
                                name = file.name,
                                size = file.length(),
                                lastModified = file.lastModified()
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
            onMediaSelected(results)
        }
    }
}

/**
 * JVM implementation of file picker using FileDialog
 */
@Composable
actual fun rememberFilePicker(
    onFilesSelected: (List<FilePickerResult>?) -> Unit
): () -> Unit {
    val scope = rememberCoroutineScope()

    return {
        scope.launch {
            val results = withContext(Dispatchers.IO) {
                try {
                    val dialog = FileDialog(null as Frame?, "Select Files", FileDialog.LOAD)
                    dialog.isMultipleMode = true
                    dialog.isVisible = true

                    val files = dialog.files
                    if (files.isNullOrEmpty()) {
                        null
                    } else {
                        files.map { file ->
                            FilePickerResult(
                                uri = file.absolutePath,
                                name = file.name,
                                size = file.length(),
                                lastModified = file.lastModified()
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
            onFilesSelected(results)
        }
    }
}

/**
 * JVM implementation of directory picker using FileDialog
 */
@Composable
actual fun rememberDirectoryPicker(
    onDirectorySelected: (String?) -> Unit
): () -> Unit {
    val scope = rememberCoroutineScope()

    return {
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                // Using FileDialog for better cross-platform support (macOS, Windows, Linux)
                val dialog = FileDialog(null as Frame?, "Select Directory", FileDialog.LOAD)

                // Set directory selection mode
                System.setProperty("apple.awt.fileDialogForDirectories", "true")

                dialog.isVisible = true

                // Reset property after use
                System.setProperty("apple.awt.fileDialogForDirectories", "false")

                val directory = dialog.directory
                val file = dialog.file

                if (directory != null && file != null) {
                    "$directory$file"
                } else {
                    null
                }
            }
            onDirectorySelected(path)
        }
    }
}
