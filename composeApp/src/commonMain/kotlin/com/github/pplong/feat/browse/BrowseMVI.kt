package com.github.pplong.feat.browse

import com.github.pplong.core.arch.mvi.UiIntent
import com.github.pplong.core.arch.mvi.UiState
import com.github.pplong.core.def.CommonRequestStatus

data class BrowseUiState(
    val path: String = "",
    val fileList: List<FTPFileUiModel> = emptyList(),
    val requestStatus: CommonRequestStatus = CommonRequestStatus.INITIAL
) : UiState

sealed class BrowseUiIntent : UiIntent {
    data object Refresh : BrowseUiIntent()
}