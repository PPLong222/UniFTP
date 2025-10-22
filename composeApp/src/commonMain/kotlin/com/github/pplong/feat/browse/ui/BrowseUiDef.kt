package com.github.pplong.feat.browse.ui

enum class BrowseToolbarStatus {
    STANDARD,
    SELECTED
}

sealed class BrowseFileLoadingStatus {
    data object None : BrowseFileLoadingStatus()
    data object UnChecked : BrowseFileLoadingStatus()
    data object Checked : BrowseFileLoadingStatus()
    data object Waiting : BrowseFileLoadingStatus()
    data class Failed(val message: String? = null) : BrowseFileLoadingStatus()
    data class Loading(val percent: Float) : BrowseFileLoadingStatus()
    data object Success : BrowseFileLoadingStatus()
}

enum class BrowseToolbarBarAction {
    REFRESH,
    SEARCH,
    CREATE_FOLDER,

    // FIle
    DELETE,
    MOVE,
    SHARE,
    INFO,

    DOWNLOAD,
    UPLOAD_FILE,
    UPLOAD_MEDIA
}

val fileBrowseToolBarList = listOf(
    BrowseToolbarBarAction.DELETE
)


val standardBrowseToolbarList = listOf(
    BrowseToolbarBarAction.REFRESH,
    BrowseToolbarBarAction.CREATE_FOLDER,
    BrowseToolbarBarAction.UPLOAD_MEDIA
)