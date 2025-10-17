package com.github.pplong.feat.browse

import com.github.pplong.core.arch.mvi.UiIntent
import com.github.pplong.core.arch.mvi.UiState
import com.github.pplong.core.def.CommonRequestStatus
import com.github.pplong.feat.browse.ui.BrowseToolbarStatus
import com.github.pplong.feat.home.ui.FTPServerItem

data class BrowseUiState(
    val path: String = "",
    val server: FTPServerItem = FTPServerItem(),
    val fileList: List<FTPFileSelectableUiModel> = emptyList(),
    val requestStatus: CommonRequestStatus = CommonRequestStatus.INITIAL,
    val toolbarStatus: BrowseToolbarStatus = BrowseToolbarStatus.STANDARD,
    val dialogState: BrowseDialogState = BrowseDialogState.None
) : UiState

sealed class BrowseUiIntent : UiIntent {
    data object Refresh : BrowseUiIntent()
    data class Jump(val path: String) : BrowseUiIntent()
    data object Back : BrowseUiIntent()

    // Appbar
    data class ChangeBrowseMode(val appbarStatus: BrowseToolbarStatus): BrowseUiIntent()
    data class SelectFile(val file: FTPFileUiModel, val checked: Boolean) : BrowseUiIntent()

    // Toolbar
    data object Download : BrowseUiIntent()
    data object Upload : BrowseUiIntent()
    data object ShowDeleteDialog : BrowseUiIntent()
    data object Delete : BrowseUiIntent()

    // Upload with selected files
    data class UploadFiles(
        val files: List<Pair<String, String>> // Pair of (uri, fileName)
    ) : BrowseUiIntent()

    // Dialog
    data object DismissDialog : BrowseUiIntent()
}

sealed class BrowseDialogState {
    data object None : BrowseDialogState()
    data class ConfirmDelete(
        val fileList: List<FTPFileUiModel>,
        val deleteCount: Int
    ) : BrowseDialogState()
}