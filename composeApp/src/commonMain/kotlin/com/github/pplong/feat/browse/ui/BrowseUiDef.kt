package com.github.pplong.feat.browse.ui

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import uniftp.composeapp.generated.resources.Res
import uniftp.composeapp.generated.resources.create_folder_title
import uniftp.composeapp.generated.resources.delete
import uniftp.composeapp.generated.resources.download
import uniftp.composeapp.generated.resources.ic_arrow_downward
import uniftp.composeapp.generated.resources.ic_create_new_folder
import uniftp.composeapp.generated.resources.ic_delete
import uniftp.composeapp.generated.resources.ic_file
import uniftp.composeapp.generated.resources.ic_image_upload
import uniftp.composeapp.generated.resources.ic_refresh
import uniftp.composeapp.generated.resources.nickname
import uniftp.composeapp.generated.resources.search_all_places
import uniftp.composeapp.generated.resources.search_current_folder
import uniftp.composeapp.generated.resources.upload
import uniftp.composeapp.generated.resources.upload_files
import uniftp.composeapp.generated.resources.upload_media

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

enum class BrowseToolbarBarAction(
    val titleRes: StringResource,
    val iconRes: DrawableResource
) {
    REFRESH(Res.string.nickname, Res.drawable.ic_refresh),
    SEARCH(Res.string.nickname, Res.drawable.ic_refresh),
    CREATE_FOLDER(Res.string.create_folder_title, Res.drawable.ic_create_new_folder),
    // FIle
    DELETE(Res.string.delete, Res.drawable.ic_delete),
    MOVE(Res.string.nickname, Res.drawable.ic_refresh),
    SHARE(Res.string.nickname, Res.drawable.ic_refresh),
    INFO(Res.string.nickname, Res.drawable.ic_refresh),

    DOWNLOAD(Res.string.download, Res.drawable.ic_arrow_downward),
    UPLOAD_FILE(Res.string.upload_files, Res.drawable.ic_file),
    UPLOAD_MEDIA(Res.string.upload_media, Res.drawable.ic_image_upload)
}

enum class BrowseSearchBarDirectorSelection(
    val titleRes: StringResource,
) {
    CURRENT_DIR(Res.string.search_current_folder),
    ALL_PLACES(Res.string.search_all_places)
}

enum class BrowseTransferType(
    val titleRes: StringResource,
) {
    DOWNLOAD(Res.string.download),
    UPLOAD(Res.string.upload)
}

// Deprecated
val fileSelectionFabsMenuList = listOf(
    BrowseToolbarBarAction.DELETE,
    BrowseToolbarBarAction.DOWNLOAD
)

val standardFabsMenuList = listOf(
    BrowseToolbarBarAction.CREATE_FOLDER,
    BrowseToolbarBarAction.UPLOAD_FILE,
    BrowseToolbarBarAction.UPLOAD_MEDIA,
)


val fileBrowseToolBarList = listOf(
    BrowseToolbarBarAction.DELETE,
)

val standardBrowseToolbarList = listOf(
    BrowseToolbarBarAction.CREATE_FOLDER,
    BrowseToolbarBarAction.UPLOAD_MEDIA,
)