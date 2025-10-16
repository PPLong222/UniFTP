package com.github.pplong.feat.browse.ui

enum class BrowseToolbarStatus {
    STANDARD,
    SELECTED
}

sealed class BrowseFileLoadingStatus {
    data object None: BrowseFileLoadingStatus()
    data object Failed : BrowseFileLoadingStatus()
    data class Loading(val percent: Double) : BrowseFileLoadingStatus()
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
    INFO
}

val fileBrowseToolBarList = listOf(
    BrowseToolbarBarAction.DELETE,
    BrowseToolbarBarAction.MOVE,
    BrowseToolbarBarAction.SHARE,
)


val standardBrowseToolbarList = listOf(
    BrowseToolbarBarAction.REFRESH,
    BrowseToolbarBarAction.CREATE_FOLDER,
)