package com.github.pplong.feat.home.viewmodel

import androidx.lifecycle.viewModelScope
import com.github.pplong.core.api.DownloadDirProvider
import com.github.pplong.core.arch.mvi.BaseViewModel
import com.github.pplong.core.def.CommonRequestStatus
import com.github.pplong.feat.home.EditServerNicknameState
import com.github.pplong.feat.home.EditServerState
import com.github.pplong.feat.home.HomeUiEffect
import com.github.pplong.feat.home.HomeUiIntent
import com.github.pplong.feat.home.HomeUiState
import com.github.pplong.feat.home.model.FTPServerDao
import com.github.pplong.feat.home.ui.EditConfigureState
import com.github.pplong.feat.home.ui.EditFTPServerItem
import com.github.pplong.feat.home.ui.FTPServerItem
import com.github.pplong.feat.home.ui.toFTPServer
import com.github.pplong.feat.home.ui.toFTPServerItem
import com.github.pplong.sftp.FTPClientManager
import com.github.pplong.sftp.FTPGlobalSingleton
import com.github.pplong.sftp.def.FTPConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(
    private val ftpServerDao: FTPServerDao,
    private val downloadDirProvider: DownloadDirProvider
) : BaseViewModel<HomeUiState, HomeUiIntent, HomeUiEffect>() {

    private var defaultDownloadDir: String = ""

    init {
        viewModelScope.launch {
            loadServerList()
        }
        loadDefaultDownloadDir()
    }

    override fun initialState(): HomeUiState {
        return HomeUiState()
    }


    override suspend fun handleIntent(intent: HomeUiIntent) {
        when (intent) {
            HomeUiIntent.DismissEditDialog -> dismissEditDialog()
            HomeUiIntent.ShowAddServerDialog -> showAddServerDialog()
            HomeUiIntent.TestConnectivity -> testConnectivity()
            HomeUiIntent.NextToConfigure -> nextToConfigure()
            is HomeUiIntent.OnChangeServerInfo -> onChangeServerInfo(intent.editServer)
            HomeUiIntent.SaveServer -> saveServer()
            is HomeUiIntent.Connect -> connect(intent.server)
        }
    }

    private fun loadDefaultDownloadDir() {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = downloadDirProvider.getDefaultDownloadDir()
            withContext(Dispatchers.Main) {
                defaultDownloadDir = dir
            }
        }
    }

    // Dialog management
    private fun dismissEditDialog() {
        setState {
            copy(
                isEditDialogVisible = false,
                editServerState = EditServerState(
                    server = EditFTPServerItem(
                        defaultDownloadDir = defaultDownloadDir,
                    )
                )
            )
        }
    }

    private fun showAddServerDialog() {
        setState {
            copy(
                isEditDialogVisible = true,
                editServerState = EditServerState(
                    server = EditFTPServerItem(
                        defaultDownloadDir = defaultDownloadDir,
                    )
                )
            )
        }
    }

    // Edit server logic
    private fun onChangeServerInfo(editServer: EditFTPServerItem) {
        setState {
            copy(
                editServerState = editServerState.copy(
                    server = editServer,
                    status = CommonRequestStatus.INITIAL
                )
            )
        }

        validServerInfo(editServer)
    }

    private fun validServerInfo(editServer: EditFTPServerItem) {
        val serverList = uiState.value.serverList
        if (serverList.any { it.nickname == editServer.nickname }) {
            setState {
                copy(
                    editServerState = editServerState.copy(
                        error = editServerState.error.copy(
                            nicknameState = EditServerNicknameState.DUPLICATE
                        )
                    )
                )
            }
        } else {
            setState {
                copy(
                    editServerState = editServerState.copy(
                        error = editServerState.error.copy(
                            nicknameState = null
                        )
                    )
                )
            }
        }
    }

    private fun nextToConfigure() {
        setState {
            copy(editServerState = editServerState.copy(configureState = EditConfigureState.CONFIGURING))
        }
    }

    private fun testConnectivity() {
        setState {
            copy(editServerState = editServerState.copy(status = CommonRequestStatus.REQUESTING))
        }

        viewModelScope.launch(Dispatchers.IO) {
            val tempManager = FTPClientManager(
                FTPConfig(
                    host = uiState.value.editServerState.server.host,
                    username = uiState.value.editServerState.server.user,
                    port = uiState.value.editServerState.server.port,
                    password = uiState.value.editServerState.server.password
                )
            )

            if (tempManager.connect()) {
                tempManager.close()
                
                setState {
                    copy(editServerState = editServerState.copy(status = CommonRequestStatus.SUCCESS))
                }
            } else {
                setState {
                    copy(editServerState = editServerState.copy(status = CommonRequestStatus.FAILED))
                }
            }
        }
    }

    private fun saveServer() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    ftpServerDao.insert(uiState.value.editServerState.server.toFTPServer())
                }
            }.onSuccess {
                loadServerList()
                dismissEditDialog()
                sendEffect { HomeUiEffect.ServerSavedSuccessfully }
            }.onFailure { error ->
                sendEffect { HomeUiEffect.ShowError(error.message ?: "Failed to save server") }
            }
        }
    }

    private suspend fun loadServerList() {
        withContext(Dispatchers.IO) {
            val servers = ftpServerDao.getAll()
            setState { copy(serverList = servers.map { it.toFTPServerItem() }) }
        }
    }

    private fun connect(server: FTPServerItem) {
        val tempManager = FTPClientManager(
            FTPConfig(
                host = server.host,
                username = server.user,
                port = server.port,
                password = server.password
            )
        )
        FTPGlobalSingleton.manager = tempManager
        sendEffect { HomeUiEffect.NavigateToBrowser(server) }
    }
}
