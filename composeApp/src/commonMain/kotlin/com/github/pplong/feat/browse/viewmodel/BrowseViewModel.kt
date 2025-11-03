import androidx.lifecycle.viewModelScope
import com.github.pplong.core.api.FilePickerResult
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
import com.github.pplong.feat.browse.FTPFileTransferringUiModel
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
import com.github.pplong.feat.transfer.model.TransferStatus
import com.github.pplong.sftp.FTPGlobalSingleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
    private val filePathMap = mutableMapOf<String, FTPFileTransferringUiModel>()

    private val transferringList = combine(
        transferManager.observeTransfers(ftpServer.id),
        progressMonitor.observeAllProgress()
    ) { transfers, progressUpdates ->
        println("transfers: $transfers")
        println("progressUpdates $progressUpdates")
        progressUpdates.filter { it.state == ProgressState.RUNNING }.map { update ->
            FTPFileTransferringUiModel(
                file = FTPFileUiModel(
                    name = update.fileName,
                    path = update.remotePath,
                    parentPath = update.remotePath.substringBeforeLast("/", ""),
                    isDirectory = false,
                    size = update.size,
                    modifiedTime = update.lastModified,
                    permissions = "",
                    owner = "",
                    group = ""
                ),
                status = BrowseFileLoadingStatus.Loading(
                    percent = update.progress,
                    speed = update.speed
                ),
                type = when (update.direction) {
                    TransferDirection.DOWNLOAD -> BrowseTransferType.DOWNLOAD
                    TransferDirection.UPLOAD -> BrowseTransferType.UPLOAD
                },
                taskId = update.taskId
            )
        } + transfers.filter { it.task.status == TransferStatus.PAUSED || it.task.status == TransferStatus.PENDING || it.task.status == TransferStatus.FAILED }
            .map {
                FTPFileTransferringUiModel(
                    file = FTPFileUiModel(
                        name = it.task.fileName,
                        path = it.task.remotePath,
                        parentPath = "",
                        isDirectory = false,
                        size = it.task.size,
                        modifiedTime = it.task.fileLastModified,
                        permissions = "",
                        owner = "",
                        group = ""
                    ),
                    type = if (it.task.direction == TransferDirection.DOWNLOAD) BrowseTransferType.DOWNLOAD else BrowseTransferType.UPLOAD,
                    status = when (it.task.status) {
                        TransferStatus.PENDING -> BrowseFileLoadingStatus.Waiting
                        TransferStatus.PAUSED -> BrowseFileLoadingStatus.Paused(it.task.bytesTransferred * 1.0f / it.task.size)
                        else -> BrowseFileLoadingStatus.None
                    },
                    taskId = it.task.id
                )
            }
    }

    private val transferredList =
        transferManager.observeTransfers(ftpServer.id).map { list ->
            list.filter { it.task.status == TransferStatus.COMPLETED }.map {
                FTPFileTransferringUiModel(
                    file = FTPFileUiModel(
                        name = it.task.fileName,
                        path = it.task.remotePath,
                        parentPath = "",
                        isDirectory = false,
                        size = it.task.size,
                        modifiedTime = it.task.fileLastModified,
                        permissions = "",
                        owner = "",
                        group = ""
                    ),
                    type = if (it.task.direction == TransferDirection.DOWNLOAD) BrowseTransferType.DOWNLOAD else BrowseTransferType.UPLOAD,
                    status = BrowseFileLoadingStatus.Success,
                    taskId = it.task.id
                )
            }
        }


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
            transferringList.collectLatest { list ->

                filePathMap.clear()
                filePathMap.putAll(list.associateBy { it.file.path })
                setState {
                    copy(
                        transferringFile = list,
                        fileList = fileList.map { fileModel ->
                            val browseFile = filePathMap[fileModel.file.path].takeIf {
                                fileModel.file.size == it?.file?.size && fileModel.file.modifiedTime == it.file.modifiedTime && it.type == BrowseTransferType.DOWNLOAD
                            }
                            if (browseFile != null) {
                                fileModel.copy(status = browseFile.status)
                            } else {
                                fileModel
                            }
                        }
                    )
                }
            }
        }

        viewModelScope.launch {
            transferredList.collectLatest {
                setState {
                    copy(
                        transferredFile = it,
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
            is BrowseUiIntent.OnLoadingTaskClicked -> onLoadingTaskClicked(intent.taskId)
        }
    }

    fun refresh() {
        jump(uiState.value.path)
    }

    private fun jump(path: String) {
        setState { copy(requestStatus = CommonRequestStatus.REQUESTING) }
        viewModelScope.launch(Dispatchers.IO) {
            val curFileList =
                manager.list(path)

            val updatedFileList =
                curFileList.map { FTPFileSelectableUiModel(file = it.toFTPFileUiModel()) }

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
                        file = file,
                        serverId = ftpServer.id
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
        files: List<FilePickerResult>,
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
            if (files.map { it.name }.any { it in currentFileList }) {
                setState { copy(dialogState = BrowseDialogState.FileNameDuplicate(files)) }
                return
            }
        }

        // Get current remote directory
        val remoteDir = uiState.value.path

        // Enqueue uploads using background transfer manager
        viewModelScope.launch(Dispatchers.IO) {
            val taskMap = mutableMapOf<String, String>()

            files.forEach { file ->
                try {
                    // Build remote file path
                    val remotePath = if (remoteDir.endsWith("/")) {
                        "$remoteDir${file.name}"
                    } else {
                        "$remoteDir/${file.name}"
                    }

                    val taskId = transferManager.enqueueUpload(
                        fileName = file.name,
                        localUri = file.uri,
                        remotePath = remotePath,
                        fileSize = file.size,
                        serverId = ftpServer.id,
                        lastModifiedTime = file.lastModified
                    )

                    // Save task ID mapping using remote path
                    taskMap[remotePath] = taskId
                    println("Upload enqueued: ${file.name}, taskId: $taskId")
                } catch (e: Exception) {
                    println("Failed to enqueue upload: ${file.name}, error: ${e.message}")
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
            delay(2000)
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

    private fun onLoadingTaskClicked(taskId: String) {
        val fileItem = uiState.value.transferringFile.find { it.taskId == taskId }
        if (fileItem == null) {
            return
        }
        if (fileItem.status is BrowseFileLoadingStatus.Loading) {
            viewModelScope.launch {
                println("Pause ${taskId}")
                transferManager.pausedTransfer(taskId)
            }
        } else if (fileItem.status is BrowseFileLoadingStatus.Paused) {
            viewModelScope.launch {
                println("Pause ${taskId}")
                transferManager.resumeTransfer(taskId)
            }
        }
    }
}
