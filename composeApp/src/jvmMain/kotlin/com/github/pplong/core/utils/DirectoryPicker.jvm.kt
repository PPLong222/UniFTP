package com.github.pplong.core.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame

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
