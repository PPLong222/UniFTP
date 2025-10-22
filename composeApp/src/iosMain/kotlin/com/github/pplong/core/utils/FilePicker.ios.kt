package com.github.pplong.core.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.*
import platform.Foundation.*
import platform.UIKit.*
import platform.UniformTypeIdentifiers.UTType
import platform.darwin.NSObject

/**
 * iOS implementation of file picker using UIDocumentPickerViewController
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberFilePicker(
    onFilesSelected: (List<FilePickerResult>?) -> Unit
): () -> Unit {
    return remember {
        {
            // Get the current UIViewController
            val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController

            if (rootViewController == null) {
                println("Failed to get root view controller")
                onFilesSelected(null)
                return@remember
            }

            // Find the topmost presented view controller
            var topController: UIViewController = rootViewController
            while (topController.presentedViewController != null) {
                topController = topController.presentedViewController ?: break
            }

            // Create delegate to handle file selection
            val delegate = FilePickerDelegateImpl { urls ->
                val results = urls.mapNotNull { url ->
                    getFileInfo(url)
                }
                onFilesSelected(if (results.isEmpty()) null else results)
            }

            // Create document picker for all file types
            val picker = UIDocumentPickerViewController(
                documentTypes = listOf("public.item"),
                inMode = UIDocumentPickerMode.UIDocumentPickerModeImport
            )

            picker.delegate = delegate
            picker.allowsMultipleSelection = true

            // Present the picker
            topController.presentViewController(
                picker,
                animated = true,
                completion = null
            )
        }
    }
}

/**
 * Delegate implementation for UIDocumentPickerViewController
 */
@OptIn(ExperimentalForeignApi::class)
private class FilePickerDelegateImpl(
    private val onComplete: (List<NSURL>) -> Unit
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>
    ) {
        @Suppress("UNCHECKED_CAST")
        val urls = didPickDocumentsAtURLs as? List<NSURL> ?: emptyList()
        onComplete(urls)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onComplete(emptyList())
    }
}

/**
 * Helper function to get file information from NSURL
 */
@OptIn(ExperimentalForeignApi::class)
private fun getFileInfo(url: NSURL): FilePickerResult? {
    return try {
        // Start accessing security-scoped resource
        val canAccess = url.startAccessingSecurityScopedResource()

        return try {
            val fileManager = NSFileManager.defaultManager
            val path = url.path ?: return null

            // Get file attributes
            val attributes = fileManager.attributesOfItemAtPath(path, error = null)
                ?: return null

            // Get file name
            val fileName = url.lastPathComponent ?: "unknown"

            // Get file size
            val fileSize = (attributes[NSFileSize] as? NSNumber)?.longValue ?: 0L

            FilePickerResult(
                uri = path,
                name = fileName,
                size = fileSize
            )
        } finally {
            // Stop accessing security-scoped resource
            if (canAccess) {
                url.stopAccessingSecurityScopedResource()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
