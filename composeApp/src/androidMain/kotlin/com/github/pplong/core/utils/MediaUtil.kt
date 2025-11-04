package com.github.pplong.core.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var name: String? = null
    val cursor = context.contentResolver.query(
        uri,
        null,
        null,
        null,
        null
    )
    cursor?.use {
        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && it.moveToFirst()) {
            name = it.getString(nameIndex)
        }
    }
    // If not found, use last path
    if (name.isNullOrEmpty()) {
        name = uri.lastPathSegment
    }
    return name
}