package com.github.pplong.feat.browse

import com.github.pplong.feat.browse.ui.BrowseFileLoadingStatus

data class FTPFileUiModel(
    val name: String,
    val path: String,
    val parentPath: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val modifiedTime: Long = 0L,
    val permissions: String? = null,
    val owner: String? = null,
    val group: String? = null
)

data class FTPFileSelectableUiModel(
    val file: FTPFileUiModel,
    val status: BrowseFileLoadingStatus = BrowseFileLoadingStatus.None
)

enum class BrowseCreateFolderStatus {
    NONE,
    DUPLICATE
}