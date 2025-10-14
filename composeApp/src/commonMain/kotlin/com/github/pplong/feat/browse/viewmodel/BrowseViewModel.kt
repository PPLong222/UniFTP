package com.github.pplong.feat.browse.viewmodel

import androidx.lifecycle.viewModelScope
import com.github.pplong.core.arch.mvi.BaseViewModel
import com.github.pplong.core.arch.mvi.UiEffect
import com.github.pplong.core.def.CommonRequestStatus
import com.github.pplong.feat.browse.BrowseUiIntent
import com.github.pplong.feat.browse.BrowseUiState
import com.github.pplong.feat.browse.toFTPFileUiModel
import com.github.pplong.sftp.FTPGlobalSingleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class BrowseViewModel : BaseViewModel<BrowseUiState, BrowseUiIntent, UiEffect>() {
    private val manager = FTPGlobalSingleton.manager

    init {
        setState { copy(requestStatus = CommonRequestStatus.REQUESTING) }
        viewModelScope.launch(Dispatchers.IO) {
            if (!manager.connect()) {
                setState { copy(requestStatus = CommonRequestStatus.FAILED) }
                return@launch
            }
            val curPath = manager.pwd()
            val curFileList = manager.list(curPath).map { it.toFTPFileUiModel() }
            setState {
                copy(
                    path = curPath,
                    fileList = curFileList,
                    requestStatus = CommonRequestStatus.SUCCESS
                )
            }
        }
    }

    override fun initialState(): BrowseUiState {
        return BrowseUiState()
    }

    override suspend fun handleIntent(intent: BrowseUiIntent) {
        when (intent) {
            BrowseUiIntent.Refresh -> refresh()
        }
    }

    fun refresh() {
        setState { copy(requestStatus = CommonRequestStatus.REQUESTING) }
        viewModelScope.launch(Dispatchers.IO) {
            val curFileList = manager.list(uiState.value.path).map { it.toFTPFileUiModel() }
            setState {
                copy(
                    fileList = curFileList,
                    requestStatus = CommonRequestStatus.SUCCESS
                )
            }
        }
        setState { copy(requestStatus = CommonRequestStatus.SUCCESS) }
    }
}