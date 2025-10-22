package com.github.pplong.feat.browse

import com.github.pplong.feat.browse.ui.BrowseToolbarBarAction
import com.github.pplong.sftp.def.FTPFile

fun FTPFile.toFTPFileUiModel(): FTPFileUiModel = FTPFileUiModel(
    name = name,
    path = path,
    parentPath = parentPath,
    isDirectory = isDirectory,
    size = size,
    modifiedTime = modifiedTime,
    permissions = permissions,
    owner = owner,
    group = group
)

fun BrowseToolbarBarAction.mapToUiIntent(): BrowseUiIntent {
    return when (this) {
        BrowseToolbarBarAction.REFRESH -> BrowseUiIntent.Refresh
        BrowseToolbarBarAction.SEARCH -> BrowseUiIntent.Refresh
        BrowseToolbarBarAction.CREATE_FOLDER -> BrowseUiIntent.ShowCreateFolder
        BrowseToolbarBarAction.MOVE -> BrowseUiIntent.Refresh
        BrowseToolbarBarAction.SHARE -> BrowseUiIntent.Refresh
        BrowseToolbarBarAction.INFO -> BrowseUiIntent.Refresh
        BrowseToolbarBarAction.DELETE -> BrowseUiIntent.ShowDeleteDialog
        BrowseToolbarBarAction.UPLOAD_FILE -> BrowseUiIntent.ShowUploadFilesPicker
        BrowseToolbarBarAction.UPLOAD_MEDIA -> BrowseUiIntent.ShowUploadMediaPicker
        BrowseToolbarBarAction.DOWNLOAD -> BrowseUiIntent.Download
    }
}
