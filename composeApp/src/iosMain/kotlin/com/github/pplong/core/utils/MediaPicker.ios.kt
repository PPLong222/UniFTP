package com.github.pplong.core.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.*
import platform.Foundation.*
import platform.PhotosUI.*
import platform.UIKit.*
import platform.darwin.NSObject
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.darwin.DISPATCH_TIME_FOREVER

/**
 * iOS implementation of media picker using PHPickerViewController
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberMediaPicker(
    onMediaSelected: (List<FilePickerResult>?) -> Unit
): () -> Unit {
    return remember {
        {
            // Get the current UIViewController
            val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController

            if (rootViewController == null) {
                println("[iOS MediaPicker] Failed to get root view controller")
                onMediaSelected(null)
                return@remember
            }

            // Find the topmost presented view controller
            var topController: UIViewController = rootViewController
            while (topController.presentedViewController != null) {
                topController = topController.presentedViewController ?: break
            }

            // Create PHPickerConfiguration for photos and videos
            val configuration = PHPickerConfiguration().apply {
                selectionLimit = 0 // 0 means unlimited selection
                val filter = PHPickerFilter.anyFilterMatchingSubfilters(
                    listOf(
                        PHPickerFilter.imagesFilter,
                        PHPickerFilter.videosFilter
                    )
                )
                setFilter(filter)
            }

            // Create delegate to handle media selection
            val delegate = MediaPickerDelegateImpl { results ->
                val mediaResults = results.mapNotNull { result ->
                    getMediaInfo(result)
                }
                onMediaSelected(if (mediaResults.isEmpty()) null else mediaResults)
            }

            // Create PHPickerViewController
            val picker = PHPickerViewController(configuration)
            picker.delegate = delegate

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
 * Delegate implementation for PHPickerViewController
 */
@OptIn(ExperimentalForeignApi::class)
private class MediaPickerDelegateImpl(
    private val onComplete: (List<PHPickerResult>) -> Unit
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    override fun picker(
        picker: PHPickerViewController,
        didFinishPicking: List<*>
    ) {
        // Dismiss the picker
        picker.dismissViewControllerAnimated(true, null)

        @Suppress("UNCHECKED_CAST")
        val results = didFinishPicking as? List<PHPickerResult> ?: emptyList()
        onComplete(results)
    }
}

/**
 * Helper function to get media information from PHPickerResult
 * This loads the file synchronously to a temporary location
 */
@OptIn(ExperimentalForeignApi::class)
private fun getMediaInfo(result: PHPickerResult): FilePickerResult? {
    return try {
        val itemProvider = result.itemProvider

        println("[iOS MediaPicker] Processing media item...")

        // Get the type identifiers
        val typeIdentifiers = itemProvider.registeredTypeIdentifiers() as? List<*> ?: return null
        if (typeIdentifiers.isEmpty()) {
            println("[iOS MediaPicker] No type identifiers found")
            return null
        }

        println("[iOS MediaPicker] Available type identifiers: $typeIdentifiers")

        // Prefer common image/video formats over Live Photo bundles
        val preferredTypes = listOf(
            "public.jpeg",
            "public.png",
            "public.heic",
            "public.mpeg-4",
            "public.movie",
            "com.apple.quicktime-movie"
        )

        var typeIdentifier: String? = null
        for (preferred in preferredTypes) {
            val found = typeIdentifiers.find { it as? String == preferred } as? String
            if (found != null) {
                typeIdentifier = found
                break
            }
        }

        // If no preferred type found, use the first available (but skip live photo bundle)
        if (typeIdentifier == null) {
            typeIdentifier = typeIdentifiers.firstOrNull {
                (it as? String)?.contains("live-photo-bundle") != true
            } as? String
        }

        // If still null, use the first one
        if (typeIdentifier == null) {
            typeIdentifier = typeIdentifiers.firstOrNull() as? String
        }

        if (typeIdentifier == null) {
            println("[iOS MediaPicker] No valid type identifier found")
            return null
        }

        println("[iOS MediaPicker] Selected type identifier: $typeIdentifier")

        // Get suggested name
        val suggestedName = itemProvider.suggestedName ?: "media_file"
        println("[iOS MediaPicker] Suggested name: $suggestedName")

        // Load file representation synchronously using semaphore
        var tempFilePath: String? = null
        var fileSize: Long = 0L
        val semaphore = dispatch_semaphore_create(0)

        itemProvider.loadFileRepresentationForTypeIdentifier(
            typeIdentifier = typeIdentifier,
            completionHandler = { url, error ->
                if (error != null) {
                    println("[iOS MediaPicker ERROR] Failed to load file: ${error.localizedDescription}")
                    dispatch_semaphore_signal(semaphore)
                    return@loadFileRepresentationForTypeIdentifier
                }

                if (url == null) {
                    println("[iOS MediaPicker ERROR] URL is null")
                    dispatch_semaphore_signal(semaphore)
                    return@loadFileRepresentationForTypeIdentifier
                }

                try {
                    // Get the source file path
                    val sourcePath = url.path ?: ""

                    // Create destination in temp directory
                    val fileManager = NSFileManager.defaultManager
                    val tempDir = NSTemporaryDirectory()
                    val uniqueFileName = "${NSUUID.UUID().UUIDString}_$suggestedName"
                    val destPath = tempDir + uniqueFileName

                    println("[iOS MediaPicker] Copying from: $sourcePath to: $destPath")

                    // Get source file size first
                    val sourceAttributes = fileManager.attributesOfItemAtPath(sourcePath, error = null)
                    val sourceSize = (sourceAttributes?.get(NSFileSize) as? NSNumber)?.longValue ?: 0L
                    println("[iOS MediaPicker] Source file size: $sourceSize bytes")

                    // Remove destination if exists
                    if (fileManager.fileExistsAtPath(destPath)) {
                        fileManager.removeItemAtPath(destPath, error = null)
                    }

                    // Copy file using NSData to ensure complete copy
                    val fileData = NSData.dataWithContentsOfFile(sourcePath)
                    if (fileData != null) {
                        val written = fileData.writeToFile(destPath, atomically = true)
                        if (written) {
                            tempFilePath = destPath
                            fileSize = fileData.length.toLong()
                            println("[iOS MediaPicker] File copied successfully to: $destPath, size: $fileSize bytes")
                        } else {
                            println("[iOS MediaPicker ERROR] Failed to write file to: $destPath")
                        }
                    } else {
                        println("[iOS MediaPicker ERROR] Failed to read source file: $sourcePath")
                    }
                } catch (e: Exception) {
                    println("[iOS MediaPicker ERROR] Exception: ${e.message}")
                    e.printStackTrace()
                }

                dispatch_semaphore_signal(semaphore)
            }
        )

        // Wait for completion (with timeout)
        dispatch_semaphore_wait(semaphore, DISPATCH_TIME_FOREVER)

        if (tempFilePath == null) {
            println("[iOS MediaPicker ERROR] Failed to get temp file path")
            return null
        }

        println("[iOS MediaPicker] Successfully loaded media: $suggestedName")
        FilePickerResult(
            uri = tempFilePath,
            name = suggestedName,
            size = fileSize
        )
    } catch (e: Exception) {
        println("[iOS MediaPicker ERROR] Exception in getMediaInfo: ${e.message}")
        e.printStackTrace()
        null
    }
}
