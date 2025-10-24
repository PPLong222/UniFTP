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
import com.github.pplong.feat.browse.SearchState
import com.github.pplong.feat.browse.toFTPFileUiModel
import com.github.pplong.feat.browse.ui.BrowseFileLoadingStatus
import com.github.pplong.feat.browse.ui.BrowseToolbarStatus
import com.github.pplong.feat.browse.ui.BrowseTransferType
import com.github.pplong.feat.home.ui.FTPServerItem
import com.github.pplong.feat.transfer.ProgressMonitor
import com.github.pplong.feat.transfer.ProgressState
import com.github.pplong.feat.transfer.TransferManagerFactory
import com.github.pplong.feat.transfer.model.TransferDirection
import com.github.pplong.sftp.FTPGlobalSingleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BrowseViewModel(
    private val ftpServer: FTPServerItem
) : BaseViewModel<BrowseUiState, BrowseUiIntent, UiEffect>(), KoinComponent {
    private var searchJob: Job? = null
    private val manager = FTPGlobalSingleton.manager
    private val transferManager = TransferManagerFactory.create()
    private val progressMonitor: ProgressMonitor by inject()

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
        observeTransfers()
    }

    private fun observeTransfers() {
        // Observe database for task status changes (low frequency - only for completion/failure)
        viewModelScope.launch {
            transferManager.observeTransfers()
                .collectLatest { transfers ->
                    println("[BrowseViewModel] Database: ${transfers.size} transfers")

                    val transferMap = transfers.associateBy { it.remotePath }

                    setState {
                        copy(fileList = fileList.map { fileModel ->
                            val task = transferMap[fileModel.file.path]

                            when {
                                task == null -> fileModel
                                task.status == com.github.pplong.feat.transfer.model.TransferStatus.COMPLETED -> {
                                    fileModel.copy(status = BrowseFileLoadingStatus.Success)
                                }

                                task.status == com.github.pplong.feat.transfer.model.TransferStatus.FAILED -> {
                                    fileModel.copy(status = BrowseFileLoadingStatus.Failed(task.errorMessage))
                                }

                                task.status == com.github.pplong.feat.transfer.model.TransferStatus.CANCELLED -> {
                                    fileModel.copy(status = BrowseFileLoadingStatus.None)
                                }

                                else -> fileModel
                            }
                        })
                    }
                }
        }

        // Observe WorkManager for real-time progress (high frequency)
        observeRealtimeProgress()
    }

    private fun observeRealtimeProgress() {
        println("[BrowseViewModel] Starting real-time progress observation")
        viewModelScope.launch {
            progressMonitor.observeAllProgress()
                .collectLatest { progressUpdates ->
                    println("[BrowseViewModel] Received progress updates: ${progressUpdates.size} items")

                    if (progressUpdates.isEmpty()) {
                        println("[BrowseViewModel] Empty progress updates, skipping")
                        return@collectLatest
                    }

                    println("[BrowseViewModel] WorkManager: ${progressUpdates.size} progress updates")
                    progressUpdates.forEach { update ->
                        println("[BrowseViewModel]   - ${update.fileName}: ${(update.progress * 100).toInt()}% [${update.state}]")
                    }

                    val progressMap = progressUpdates.associateBy { it.remotePath }

                    // Build transferringFile list from active progress updates
                    val transferringFiles = progressUpdates
                        .filter { it.state == ProgressState.RUNNING || it.state == ProgressState.WAITING }
                        .map { update ->
                            com.github.pplong.feat.browse.FTPFileTransferringUiModel(
                                file = FTPFileUiModel(
                                    name = update.fileName,
                                    path = update.remotePath,
                                    parentPath = update.remotePath.substringBeforeLast("/", ""),
                                    isDirectory = false,
                                    size = 0L,  // We don't have size info in ProgressUpdate
                                    modifiedTime = 0L,
                                    permissions = "",
                                    owner = "",
                                    group = ""
                                ),
                                progress = update.progress,
                                type = when (update.direction) {
                                    TransferDirection.DOWNLOAD -> BrowseTransferType.DOWNLOAD

                                    TransferDirection.UPLOAD -> BrowseTransferType.UPLOAD
                                }
                            )
                        }

                    setState {
                        copy(
                            transferringFile = transferringFiles,
                            fileList = fileList.map { fileModel ->
                                val progress = progressMap[fileModel.file.path]

                                if (progress != null) {
                                    val newStatus = when (progress.state) {
                                        ProgressState.WAITING -> BrowseFileLoadingStatus.Waiting
                                        ProgressState.RUNNING -> BrowseFileLoadingStatus.Loading(
                                            progress.progress
                                        )

                                        ProgressState.SUCCEEDED -> BrowseFileLoadingStatus.Success
                                        ProgressState.FAILED -> BrowseFileLoadingStatus.Failed("Transfer failed")
                                        ProgressState.CANCELLED -> BrowseFileLoadingStatus.None
                                    }
                                    fileModel.copy(status = newStatus)
                                } else {
                                    fileModel
                                }
                            }
                        )
                    }
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
            is BrowseUiIntent.StartSearch -> startSearch(intent.query, intent.local)
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

            // Preserve loading status for files that are being transferred
            val oldStatusMap = uiState.value.fileList
                .filter {
                    it.status is BrowseFileLoadingStatus.Loading ||
                            it.status is BrowseFileLoadingStatus.Waiting
                }
                .associateBy { it.file.path }

            val updatedFileList = curFileList.map { fileModel ->
                // Restore loading status if this file was being transferred
                val oldStatus = oldStatusMap[fileModel.file.path]?.status
                if (oldStatus is BrowseFileLoadingStatus.Loading ||
                    oldStatus is BrowseFileLoadingStatus.Waiting
                ) {
                    fileModel.copy(status = oldStatus)
                } else {
                    fileModel
                }
            }

            setState {
                copy(
                    path = path,
                    fileList = updatedFileList,
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
                        it.copy(status = BrowseFileLoadingStatus.Waiting)
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

        // Enqueue downloads using background transfer manager
        viewModelScope.launch(Dispatchers.IO) {
            val taskMap = mutableMapOf<String, String>()

            downloadFileList.forEach { file ->
                // Skip directories
                if (file.isDirectory) {
                    return@forEach
                }

                try {
                    val taskId = transferManager.enqueueDownload(
                        fileName = file.name,
                        remotePath = file.path,
                        downloadDir = downloadDir,
                        serverHost = ftpServer.host,
                        serverPort = ftpServer.port,
                        serverUsername = ftpServer.user,
                        serverPassword = ftpServer.password,
                        fileSize = file.size
                    )

                    // Save task ID mapping
                    taskMap[file.path] = taskId
                    println("Download enqueued: ${file.name}, taskId: $taskId")
                } catch (e: Exception) {
                    println("Failed to enqueue download: ${file.name}, error: ${e.message}")
                    e.printStackTrace()
                }
            }

            // Update state with task mappings
            setState {
                copy(transferTaskMap = transferTaskMap + taskMap)
            }

            // Show toast or notification that downloads have been enqueued
            println("${downloadFileList.size} downloads enqueued in background")
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

        // Enqueue uploads using background transfer manager
        viewModelScope.launch(Dispatchers.IO) {
            val taskMap = mutableMapOf<String, String>()

            files.forEach { (localUri, fileName) ->
                try {
                    // Build remote file path
                    val remotePath = if (remoteDir.endsWith("/")) {
                        "$remoteDir$fileName"
                    } else {
                        "$remoteDir/$fileName"
                    }

                    // TODO: Get file size - for now use 0 as placeholder
                    // You may need to add a helper function to get file size from localUri
                    val fileSize = 0L

                    val taskId = transferManager.enqueueUpload(
                        fileName = fileName,
                        localUri = localUri,
                        remotePath = remotePath,
                        serverHost = ftpServer.host,
                        serverPort = ftpServer.port,
                        serverUsername = ftpServer.user,
                        serverPassword = ftpServer.password,
                        fileSize = fileSize
                    )

                    // Save task ID mapping using remote path
                    taskMap[remotePath] = taskId
                    println("Upload enqueued: $fileName, taskId: $taskId")
                } catch (e: Exception) {
                    println("Failed to enqueue upload: $fileName, error: ${e.message}")
                    e.printStackTrace()
                }
            }

            // Update state with task mappings
            setState {
                copy(transferTaskMap = transferTaskMap + taskMap)
            }

            // Show toast or notification that uploads have been enqueued
            println("${files.size} uploads enqueued in background")

            // Refresh file list after a delay to show newly uploaded files
            kotlinx.coroutines.delay(2000)
            refresh()
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

    private fun onSearchIconClicked() {
        setState { copy(searchState = SearchState()) }
    }

    private fun startSearch(query: String, local: Boolean) {
        if (local) {
            setState {
                copy(
                    searchState = searchState.copy(
                        result = fileList.map { it.file }
                            .filter { it.name.contains(query) },
                        loadingStatus = CommonRequestStatus.SUCCESS
                    )
                )
            }
            return
        }

        searchJob?.let {
            if (it.isActive) {
                it.cancel()
            }
        }

        setState { copy(searchState = searchState.copy(loadingStatus = CommonRequestStatus.REQUESTING)) }
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                manager.find(query)
            }.onSuccess { list ->
                setState {
                    copy(
                        searchState = searchState.copy(
                            loadingStatus = CommonRequestStatus.SUCCESS,
                            result = list.map { it.toFTPFileUiModel() })
                    )
                }
            }.onFailure {

            }
        }

    }


}
