package com.github.pplong.core.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.interop.LocalUIViewController
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.darwin.NSObject

@Composable
actual fun rememberDirectoryPicker(
    onDirectorySelected: (String?) -> Unit
): () -> Unit {
    val viewController = LocalUIViewController.current

    val delegate = remember {
        object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(
                controller: UIDocumentPickerViewController,
                didPickDocumentAtURL: NSURL
            ) {
                onDirectorySelected(didPickDocumentAtURL.path)
            }

            override fun documentPicker(
                controller: UIDocumentPickerViewController,
                didPickDocumentsAtURLs: List<*>
            ) {
                val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                onDirectorySelected(url?.path)
            }

            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                onDirectorySelected(null)
            }
        }
    }

    return {
        val picker = UIDocumentPickerViewController(
            documentTypes = listOf("public.folder"),
            inMode = UIDocumentPickerMode.UIDocumentPickerModeOpen
        )
        picker.delegate = delegate
        picker.allowsMultipleSelection = false
        viewController.presentViewController(picker, animated = true, completion = null)
    }
}
