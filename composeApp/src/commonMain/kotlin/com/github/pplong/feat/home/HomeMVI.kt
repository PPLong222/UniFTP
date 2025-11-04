package com.github.pplong.feat.home

import com.github.pplong.core.arch.mvi.UiEffect
import com.github.pplong.core.arch.mvi.UiIntent
import com.github.pplong.core.arch.mvi.UiState
import com.github.pplong.core.def.CommonRequestStatus
import com.github.pplong.feat.home.ui.EditConfigureState
import com.github.pplong.feat.home.ui.EditFTPServerItem
import com.github.pplong.feat.home.ui.FTPServerItem

data class EditServerState(
    val server: EditFTPServerItem = EditFTPServerItem(),
    val configureState: EditConfigureState = EditConfigureState.CONNECTING,
    val status: CommonRequestStatus = CommonRequestStatus.INITIAL,
    val error: EditServerErrorState = EditServerErrorState()
) {
    val isSaveEnabled =
        error.noError && server.saveEnabled
}

data class EditServerErrorState(
    val nicknameState: EditServerNicknameState? = null,
) {
    val noError = nicknameState == null
}

enum class EditServerNicknameState {
    DUPLICATE
}

data class HomeUiState(
    val serverList: List<FTPServerItem> = emptyList(),
    val isEditDialogVisible: Boolean = false,
    val editServerState: EditServerState = EditServerState()
) : UiState

sealed class HomeUiIntent : UiIntent {
    // Function
    data class Connect(val server: FTPServerItem) : HomeUiIntent()

    // Dialog management
    data object DismissEditDialog : HomeUiIntent()
    data object ShowAddServerDialog : HomeUiIntent()

    // Edit server intents
    data object TestConnectivity : HomeUiIntent()
    data object SaveServer : HomeUiIntent()
    data object NextToConfigure : HomeUiIntent()
    data class OnChangeServerInfo(val editServer: EditFTPServerItem) : HomeUiIntent()

    sealed class AddServerUiIntent : HomeUiIntent() {
        data class CheckPublicKeyBox(val checked: Boolean) : AddServerUiIntent()
        data class CheckParaphraseBox(val checked: Boolean) : AddServerUiIntent()
        data class ParaphraseChanged(val paraphrase: String) : AddServerUiIntent()
        data class KeyFileSelected(val uri: String, val fileName: String) : AddServerUiIntent()
    }
}

sealed class HomeUiEffect : UiEffect {
    data object ServerSavedSuccessfully : HomeUiEffect()
    data class ShowError(val message: String) : HomeUiEffect()

    data class NavigateToBrowser(val server: FTPServerItem) : HomeUiEffect()
}