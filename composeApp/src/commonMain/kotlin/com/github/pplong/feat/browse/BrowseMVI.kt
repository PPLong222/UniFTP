package com.github.pplong.feat.browse

import com.github.pplong.core.arch.mvi.UiEffect
import com.github.pplong.core.arch.mvi.UiIntent
import com.github.pplong.core.arch.mvi.UiState
import com.github.pplong.core.def.CommonRequestStatus
import com.github.pplong.core.utils.FilePickerResult
import com.github.pplong.feat.browse.ui.BrowseToolbarStatus
import com.github.pplong.feat.home.ui.FTPServerItem

data class BrowseUiState(
    val path: String = "",
    val server: FTPServerItem = FTPServerItem(),
    val fileList: List<FTPFileSelectableUiModel> = emptyList(),
    val requestStatus: CommonRequestStatus = CommonRequestStatus.INITIAL,
    val toolbarStatus: BrowseToolbarStatus = BrowseToolbarStatus.STANDARD,
    val dialogState: BrowseDialogState = BrowseDialogState.None,
    // Map of file path to transfer task ID for tracking progress
    val transferTaskMap: Map<String, String> = emptyMap(),
    val searchState: SearchState = SearchState(),

    val transferringFile: List<FTPFileTransferringUiModel> = emptyList(),
    val transferredFile: List<FTPFileTransferringUiModel> = emptyList()
) : UiState

sealed class BrowseUiIntent : UiIntent {
    data object Refresh : BrowseUiIntent()
    data class Jump(val path: String) : BrowseUiIntent()
    data object Back : BrowseUiIntent()

    // Appbar
    data class ChangeBrowseMode(val appbarStatus: BrowseToolbarStatus): BrowseUiIntent()
    data class SelectFile(val file: FTPFileUiModel, val checked: Boolean) : BrowseUiIntent()
    data class StartSearch(val query: String, val local: Boolean = true) : BrowseUiIntent()

    // Toolbar
    data object Download : BrowseUiIntent()
    data object Upload : BrowseUiIntent()
    data object ShowDeleteDialog : BrowseUiIntent()
    data object Delete : BrowseUiIntent()

    // Upload with selected files
    data class UploadFiles(
        val files: List<FilePickerResult>, // Pair of (uri, fileName)
        val detectSameName: Boolean = false
    ) : BrowseUiIntent()

    // Dialog
    data object DismissDialog : BrowseUiIntent()
    data object ShowUploadFilesPicker : BrowseUiIntent()
    data object ShowCreateFolder : BrowseUiIntent()
    data class OnCreateFolderNameChanged(val folderName: String) : BrowseUiIntent()
    data object OnCreateFolderConfirmClicked : BrowseUiIntent()
    data object ShowUploadMediaPicker : BrowseUiIntent()
}

sealed class BrowseUiEffect : UiEffect {
    data object ShowUploadFilesPicker : BrowseUiEffect()
    data object ShowUploadMediaPicker : BrowseUiEffect()
}

sealed class BrowseDialogState {
    data object None : BrowseDialogState()
    data class ConfirmDelete(
        val fileList: List<FTPFileUiModel>,
        val deleteCount: Int
    ) : BrowseDialogState()

    data class FileNameDuplicate(
        val files: List<FilePickerResult>
    ) : BrowseDialogState()

    data class CreateFolder(
        val folderName: String,
        val state: BrowseCreateFolderStatus = BrowseCreateFolderStatus.NONE,
        val requestStatus: CommonRequestStatus = CommonRequestStatus.INITIAL
    ) : BrowseDialogState()
}

data class SearchState(
    val loadingStatus: CommonRequestStatus = CommonRequestStatus.INITIAL,
    val result: List<FTPFileUiModel> = emptyList()
)