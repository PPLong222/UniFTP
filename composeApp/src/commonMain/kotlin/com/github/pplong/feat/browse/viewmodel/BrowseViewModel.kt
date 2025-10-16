import androidx.lifecycle.viewModelScope
import com.github.pplong.core.arch.mvi.BaseViewModel
import com.github.pplong.core.arch.mvi.UiEffect
import com.github.pplong.core.def.CommonRequestStatus
import com.github.pplong.feat.browse.BrowseUiIntent
import com.github.pplong.feat.browse.BrowseUiState
import com.github.pplong.feat.browse.toFTPFileUiModel
import com.github.pplong.feat.browse.ui.BrowseToolbarStatus
import com.github.pplong.feat.home.ui.FTPServerItem
import com.github.pplong.sftp.FTPGlobalSingleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class BrowseViewModel(
    private val ftpServer: FTPServerItem
) : BaseViewModel<BrowseUiState, BrowseUiIntent, UiEffect>() {
    private val manager = FTPGlobalSingleton.manager

    init {
        // TODO: know why we have to pass server here rather than initialState which will lead to crash issue
        setState { copy(requestStatus = CommonRequestStatus.REQUESTING, server = ftpServer) }
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
            is BrowseUiIntent.Jump -> jump(intent.path)
            BrowseUiIntent.Back -> back()
            is BrowseUiIntent.ChangeBrowseMode -> changeBrowseMode(intent.appbarStatus)
        }
    }

    fun refresh() {
        jump(uiState.value.path)
    }

    private fun jump(path: String) {
        setState { copy(requestStatus = CommonRequestStatus.REQUESTING) }
        viewModelScope.launch(Dispatchers.IO) {
            val curFileList = manager.list(path).map { it.toFTPFileUiModel() }
            setState {
                copy(
                    path = path,
                    fileList = curFileList,
                    requestStatus = CommonRequestStatus.SUCCESS
                )
            }
        }
    }

    private fun back() {
        if (uiState.value.path.isEmpty() || uiState.value.path == "/") {
            return
        }

        val newPath = uiState.value.path.substringBeforeLast("/")
        jump(newPath)
    }

    private fun changeBrowseMode(appbarStatus: BrowseToolbarStatus) {
        setState {
            copy(toolbarStatus = if (appbarStatus == BrowseToolbarStatus.STANDARD) BrowseToolbarStatus.SELECTED else BrowseToolbarStatus.STANDARD)
        }
    }
}
