package com.github.pplong.feat.browse

import com.github.pplong.feat.browse.ui.BrowseToolbarBarAction
import com.github.pplong.feat.browse.ui.BrowseToolbarStatus
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
        BrowseToolbarBarAction.CREATE_FOLDER -> BrowseUiIntent.Refresh
        BrowseToolbarBarAction.MOVE -> BrowseUiIntent.Refresh
        BrowseToolbarBarAction.SHARE -> BrowseUiIntent.Refresh
        BrowseToolbarBarAction.INFO -> BrowseUiIntent.Refresh
        BrowseToolbarBarAction.DELETE -> BrowseUiIntent.ShowDeleteDialog
    }
}

fun BrowseToolbarStatus.mapToUiIntent(): BrowseUiIntent {
    return when (this) {
        BrowseToolbarStatus.STANDARD -> BrowseUiIntent.Upload
        BrowseToolbarStatus.SELECTED -> BrowseUiIntent.Download
    }
}
