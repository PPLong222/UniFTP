import androidx.lifecycle.viewModelScope
import com.github.pplong.core.arch.mvi.BaseViewModel
import com.github.pplong.core.arch.mvi.UiEffect
import com.github.pplong.core.def.CommonRequestStatus
import com.github.pplong.core.utils.appendFilePath
import com.github.pplong.feat.browse.BrowseCreateFolderStatus
import com.github.pplong.feat.browse.BrowseDialogState
import com.github.pplong.feat.browse.BrowseUiEffect
import com.github.pplong.feat.browse.BrowseUiIntent
import com.github.pplong.feat.browse.BrowseUiState
import com.github.pplong.feat.browse.FTPFileSelectableUiModel
import com.github.pplong.feat.browse.FTPFileUiModel
import com.github.pplong.feat.browse.toFTPFileUiModel
import com.github.pplong.feat.browse.ui.BrowseFileLoadingStatus
import com.github.pplong.feat.browse.ui.BrowseToolbarStatus
import com.github.pplong.feat.home.ui.FTPServerItem
import com.github.pplong.sftp.FTPGlobalSingleton
import com.github.pplong.sftp.PlatformDownloadCallbackFactory
import com.github.pplong.sftp.PlatformUploadCallbackFactory
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
            val curFileList =
                manager.list(curPath).map { FTPFileSelectableUiModel(file = it.toFTPFileUiModel()) }
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
            is BrowseUiIntent.SelectFile -> selectFile(intent.file, intent.checked)
            BrowseUiIntent.Download -> download()
            BrowseUiIntent.Upload -> upload()
            is BrowseUiIntent.UploadFiles -> uploadFiles(intent.files, intent.detectSameName)
            BrowseUiIntent.Delete -> delete()
            BrowseUiIntent.DismissDialog -> dismissDialog()
            BrowseUiIntent.ShowDeleteDialog -> showDismissDialog()
            BrowseUiIntent.ShowUploadFilesPicker -> showUploadPicker()
            BrowseUiIntent.OnCreateFolderConfirmClicked -> onCreateFolderConfirmedClicked()
            is BrowseUiIntent.OnCreateFolderNameChanged -> onFolderNameChanged(intent.folderName)
            BrowseUiIntent.ShowCreateFolder -> showCreateFolderDialog()
            BrowseUiIntent.ShowUploadMediaPicker -> showUploadMediaPicker()
        }
    }

    fun refresh() {
        jump(uiState.value.path)
    }

    private fun jump(path: String) {
        setState { copy(requestStatus = CommonRequestStatus.REQUESTING) }
        viewModelScope.launch(Dispatchers.IO) {
            val curFileList =
                manager.list(path).map { FTPFileSelectableUiModel(file = it.toFTPFileUiModel()) }
            setState {
                copy(
                    path = path,
                    fileList = curFileList,
                    requestStatus = CommonRequestStatus.SUCCESS,
                    dialogState = BrowseDialogState.None,
                    toolbarStatus = BrowseToolbarStatus.STANDARD
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
            copy(
                toolbarStatus = if (appbarStatus == BrowseToolbarStatus.STANDARD) BrowseToolbarStatus.SELECTED else BrowseToolbarStatus.STANDARD,
                fileList = fileList.map { it.copy(status = if (appbarStatus == BrowseToolbarStatus.STANDARD) BrowseFileLoadingStatus.UnChecked else BrowseFileLoadingStatus.None) })
        }
    }

    private fun selectFile(file: FTPFileUiModel, checked: Boolean) {
        setState {
            copy(
                fileList = fileList.map { fileModel ->
                    if (fileModel.file == file) {
                        fileModel.copy(status = if (checked) BrowseFileLoadingStatus.Checked else BrowseFileLoadingStatus.UnChecked)
                    } else {
                        fileModel
                    }
                }
            )
        }
    }

    private fun download() {
        val downloadFileList =
            uiState.value.fileList.filter { it.status == BrowseFileLoadingStatus.Checked }
                .map { it.file }
        setState {
            copy(
                toolbarStatus = BrowseToolbarStatus.STANDARD,
                fileList = fileList.map {
                    if (it.status == BrowseFileLoadingStatus.Checked) {
                        it.copy(
                            status = BrowseFileLoadingStatus.Waiting
                        )
                    } else {
                        it.copy(
                            status = BrowseFileLoadingStatus.None
                        )
                    }
                })
        }

        // Validate download directory
        val downloadDir = ftpServer.downloadDir
        if (downloadDir.isNullOrEmpty()) {
            println("Download directory not configured")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            // Get platform-specific download callback factory
            val callbackFactory = PlatformDownloadCallbackFactory.get()

            manager.download(
                list = downloadFileList,
                downloadDir = downloadDir,
                callbackFactory = callbackFactory,
                onProgress = { file, progress ->
                    setState {
                        copy(fileList = fileList.map {
                            if (it.file == file) {
                                it.copy(status = BrowseFileLoadingStatus.Loading(progress))
                            } else {
                                it
                            }
                        })
                    }
                },
                onFileComplete = { file ->
                    println("Download completed: ${file.name}")
                    setState {
                        copy(fileList = fileList.map {
                            if (it.file == file) {
                                it.copy(status = BrowseFileLoadingStatus.Success)
                            } else {
                                it
                            }
                        })
                    }
                },
                onFileError = { file, error ->
                    println("Download failed for ${file.name}: ${error.message}")
                    error.printStackTrace()
                    setState {
                        copy(fileList = fileList.map {
                            if (it.file == file) {
                                it.copy(
                                    status = BrowseFileLoadingStatus.Failed(
                                        error.message ?: "Unknown error"
                                    ),
                                )
                            } else {
                                it
                            }
                        })
                    }
                }
            )
        }
    }

    private fun upload() {
        // This method is called when user clicks upload button
        // The actual file selection and upload is handled in UI layer
        // UI will call UploadFiles intent after user selects files
    }

    private fun uploadFiles(
        files: List<Pair<String, String>>,
        hasDetectedSameName: Boolean = false
    ) {
        println("Uploading files: $files")
        if (files.isEmpty()) {
            println("No files selected for upload")
            return
        }

        val currentFileList = uiState.value.fileList.map { it.file.name }
        // if has same file name, toast a dialog
        if (!hasDetectedSameName) {
            if (files.map { it.second }.any { it in currentFileList }) {
                setState { copy(dialogState = BrowseDialogState.FileNameDuplicate(files)) }
                return
            }
        }

        // Get current remote directory
        val remoteDir = uiState.value.path

        viewModelScope.launch(Dispatchers.IO) {
            // Get platform-specific upload callback factory
            val callbackFactory = PlatformUploadCallbackFactory.get()

            // Track upload progress for UI
            val uploadingFiles = files.associate { it to 0f }.toMutableMap()

            manager.upload(
                files = files,
                remoteDir = remoteDir,
                callbackFactory = callbackFactory,
                onProgress = { fileInfo, progress ->
                    uploadingFiles[fileInfo] = progress
                    println("Uploading ${fileInfo.second}: ${(progress * 100).toInt()}%")
                },
                onFileComplete = { fileInfo ->
                    println("Upload completed: ${fileInfo.second}")
                    // Refresh file list to show newly uploaded file
                    refresh()
                },
                onFileError = { fileInfo, error ->
                    println("Upload failed for ${fileInfo.second}: ${error.message}")
                    error.printStackTrace()
                }
            )
        }
    }

    private fun delete() {
        val deleteFilesPaths =
            uiState.value.fileList.filter { it.status == BrowseFileLoadingStatus.Checked }
                .map { it.file }
        viewModelScope.launch(Dispatchers.IO) {
            manager.delete(deleteFilesPaths) { removedCount ->
                setState {
                    copy(
                        dialogState = BrowseDialogState.ConfirmDelete(
                            deleteFilesPaths,
                            removedCount
                        )
                    )
                }
                if (removedCount == deleteFilesPaths.size) {
                    refresh()
                }
            }
        }

    }

    private fun showDismissDialog() {
        val deleteFiles =
            uiState.value.fileList.filter { it.status == BrowseFileLoadingStatus.Checked }
                .map { it.file }
        setState { copy(dialogState = BrowseDialogState.ConfirmDelete(deleteFiles, 0)) }
    }

    private fun dismissDialog() {
        setState { copy(dialogState = BrowseDialogState.None) }
    }

    private fun showUploadPicker() {
        sendEffect { BrowseUiEffect.ShowUploadFilesPicker }
    }

    private fun showCreateFolderDialog() {
        setState { copy(dialogState = BrowseDialogState.CreateFolder("")) }
    }

    private fun onCreateFolderConfirmedClicked() {
        val dialogState = uiState.value.dialogState as? BrowseDialogState.CreateFolder ?: return
        setState { copy(dialogState = dialogState.copy(requestStatus = CommonRequestStatus.REQUESTING)) }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                manager.mkdir(uiState.value.path.appendFilePath(dialogState.folderName))
            }.onSuccess {
                dismissDialog()
                refresh()
            }.onFailure {
                setState { copy(dialogState = dialogState.copy(requestStatus = CommonRequestStatus.FAILED)) }
            }
        }
    }

    private fun onFolderNameChanged(folderName: String) {
        // Duplicate detection
        if (uiState.value.fileList.find { it.file.isDirectory && it.file.name == folderName } != null) {
            setState {
                copy(
                    dialogState = BrowseDialogState.CreateFolder(
                        folderName,
                        BrowseCreateFolderStatus.DUPLICATE
                    )
                )
            }
            return
        }

        setState {
            copy(dialogState = BrowseDialogState.CreateFolder(folderName))
        }
    }

    private fun showUploadMediaPicker() {
        sendEffect { BrowseUiEffect.ShowUploadMediaPicker }
    }
}
