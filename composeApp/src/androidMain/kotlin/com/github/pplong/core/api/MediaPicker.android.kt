package com.github.pplong.core.api

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.github.pplong.core.def.LocalFile
import com.github.pplong.core.utils.getFileNameFromUri
import java.io.File
import java.io.FileOutputStream

/**
 * Android implementation of media picker using PickVisualMedia contract
 */
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

/**
 * Android implementation of file picker using OpenMultipleDocuments contract
 */
@Composable
actual fun rememberMultipleFilePicker(
    onFilesSelected: (List<FilePickerResult>?) -> Unit
): () -> Unit {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) {
            onFilesSelected(null)
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

        onFilesSelected(results.ifEmpty { null })
    }

    return { launcher.launch(arrayOf("*/*")) }
}

/**
 * Android implementation of directory picker using OpenDocumentTree contract
 */
@Composable
actual fun rememberDirectoryPicker(
    onDirectorySelected: (String?) -> Unit
): () -> Unit {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // Persist URI permissions to allow future access
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (e: SecurityException) {
                // Permissions might already be persisted or not available
                e.printStackTrace()
            }
        }
        onDirectorySelected(uri?.toString())
    }
    return { launcher.launch(null) }
}

class AndroidDownloadDirProvider(
    private val context: Context
) : DownloadDirProvider {

    override suspend fun getDefaultDownloadDir(): String {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir.absolutePath
    }
}

class AndroidKeyFileSaveProvider(
    private val context: Context
) : KeyFileSaveProvider {
    override suspend fun saveKeyFile(uri: String): String {
        val destFile = File(context.filesDir, "key_${System.currentTimeMillis()}")
        // TODO error here
        if (destFile.exists()) {
            destFile.delete()
        }
        context.contentResolver.openInputStream(uri.toUri())?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }

        return destFile.path
    }
}

@Composable
actual fun rememberSingleFilePicker(onFileSelected: (LocalFile?) -> Unit): () -> Unit {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            onFileSelected(null)
            return@rememberLauncherForActivityResult
        }
        val fileName = getFileNameFromUri(context, uri)
        onFileSelected(
            LocalFile(
                name = fileName,
                path = uri.toString()
            )
        )
    }
    return { launcher.launch(arrayOf("*/*")) }
}