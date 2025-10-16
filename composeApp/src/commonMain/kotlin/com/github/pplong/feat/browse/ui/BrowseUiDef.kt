package com.github.pplong.feat.browse.ui

enum class BrowseToolbarStatus {
    STANDARD,
    SELECTED
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