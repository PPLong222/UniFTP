package com.github.pplong.core.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

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
                                size = file.length()
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
