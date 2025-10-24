package com.github.pplong.core.utils

import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberMediaPicker(
    onMediaSelected: (List<FilePickerResult>?) -> Unit
): () -> Unit {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) {
            onMediaSelected(null)
            return@rememberLauncherForActivityResult
        }

        val results = uris.mapNotNull { uri ->
            try {
                // Query file information
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

                        val name = if (nameIndex >= 0) {
                            cursor.getString(nameIndex)
                        } else {
                            uri.lastPathSegment ?: "unknown"
                        }

                        val size = if (sizeIndex >= 0) {
                            cursor.getLong(sizeIndex)
                        } else {
                            0L
                        }
                        val dateModifiedIndex =
                            cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                        val lastModified = if (dateModifiedIndex >= 0) {
                            cursor.getLong(dateModifiedIndex)
                        } else {
                            0L
                        }
                        FilePickerResult(
                            uri = uri.toString(),
                            name = name,
                            size = size,
                            lastModified = lastModified
                        )
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        onMediaSelected(results.ifEmpty { null })
    }

    return {
        launcher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
        )
    }
}
