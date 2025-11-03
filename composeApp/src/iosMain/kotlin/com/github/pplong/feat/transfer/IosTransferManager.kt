package com.github.pplong.feat.transfer

import AppDatabase
import com.github.pplong.feat.browse.FTPFileUiModel
import com.github.pplong.feat.transfer.model.TransferDirection
import com.github.pplong.feat.transfer.model.TransferStatus
import com.github.pplong.feat.transfer.model.TransferTask
import com.github.pplong.feat.transfer.model.TransferTaskEmbedded
import com.github.pplong.sftp.DownloadCallback
import com.github.pplong.sftp.FTPClientManager
import com.github.pplong.sftp.PlatformUploadCallbackFactory
import com.github.pplong.sftp.SFTPClientFactory
import com.github.pplong.sftp.UploadCallback
import com.github.pplong.sftp.def.FTPConfig
import documentDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import platform.Foundation.NSFileManager
import platform.Foundation.NSUUID

/**
 * iOS-specific transfer manager
 * Uses background task to extend transfer time when app goes to background
 * Implements checkpoint-based resume for interrupted transfers
 */
class IosTransferManager : KoinComponent {

    private val database: AppDatabase by inject()
    private val transferTaskDao = database.getTransferTaskDao()
    private val progressMonitor: ProgressMonitor by inject()
    private val backgroundTaskManager = IosBackgroundTaskManager()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Track active transfer tasks for cancellation support
    private val activeTransfers = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    /**
     * Enqueue a download task
     */
    suspend fun enqueueDownload(
        file: FTPFileUiModel,
        serverId: Long
    ): String {
        val taskId = NSUUID().UUIDString()

        // Create transfer task
        val task = TransferTask(
            id = taskId,
            direction = TransferDirection.DOWNLOAD,
            status = TransferStatus.PENDING,
            fileName = file.name,
            localUri = "", // Not used for downloads
            remotePath = file.path,
            size = file.size,
            serverId = serverId,
            fileLastModified = file.modifiedTime,
            bytesTransferred = 0L
        )

        // Save to database
        transferTaskDao.insert(task)

        // Execute transfer in background
        scope.launch {
            executeTransferEmbedded(taskId)
        }

        return taskId
    }

    /**
     * Enqueue an upload task
     */
    suspend fun enqueueUpload(
        fileName: String,
        localUri: String,
        remotePath: String,
        fileSize: Long,
        serverId: Long,
        lastModified: Long
    ): String {
        val taskId = NSUUID().UUIDString()

        // Create transfer task
        val task = TransferTask(
            id = taskId,
            direction = TransferDirection.UPLOAD,
            status = TransferStatus.PENDING,
            fileName = fileName,
            localUri = localUri,
            remotePath = remotePath,
            size = fileSize,
            serverId = serverId,
            fileLastModified = lastModified,
            bytesTransferred = 0L
        )

        // Save to database
        transferTaskDao.insert(task)

        // Execute transfer in background
        scope.launch {
            executeTransferEmbedded(taskId)
        }

        return taskId
    }

    /**
     * Pause a transfer
     */
    suspend fun pausedTransfer(taskId: String) {
        println("[iOS Transfer] Pausing transfer: $taskId")

        // Mark task as cancelled in active transfers map
        activeTransfers.value = activeTransfers.value + (taskId to true)

        // Update status to PAUSED
        transferTaskDao.updateStatus(
            taskId = taskId,
            status = TransferStatus.PAUSED
        )
    }

    /**
     * Resume a paused transfer
     */
    suspend fun resumeTransfer(taskId: String) {
        val taskEmbedded = transferTaskDao.getTasksEmbeddedById(taskId) ?: return

        if (taskEmbedded.task.status != TransferStatus.PAUSED) {
            println("[iOS Transfer] Task cannot be resumed: ${taskEmbedded.task.status}")
            return
        }

        println("[iOS Transfer] Resuming transfer: ${taskEmbedded.task.fileName}")

        // Reset cancellation flag
        activeTransfers.value = activeTransfers.value - taskId

        // Update status to IN_PROGRESS
        transferTaskDao.updateStatus(
            taskId = taskId,
            status = TransferStatus.IN_PROGRESS
        )

        // Execute transfer
        scope.launch {
            executeTransferEmbedded(taskId)
        }
    }

    /**
     * Resume all paused transfers
     */
    suspend fun resumeAllPausedTransfers() {
        val pausedTasks = transferTaskDao.getPausedTasks()

        println("[iOS Transfer] Resuming ${pausedTasks.size} paused transfers")

        pausedTasks.forEach { task ->
            resumeTransfer(task.id)
        }
    }

    /**
     * Execute transfer using taskId (gets TaskEmbedded from database)
     */
    private suspend fun executeTransferEmbedded(taskId: String) = withContext(Dispatchers.IO) {
        val taskEmbedded = transferTaskDao.getTasksEmbeddedById(taskId)
        if (taskEmbedded == null) {
            println("[iOS Transfer] Task not found: $taskId")
            return@withContext
        }

        executeTransfer(taskEmbedded)
    }

    /**
     * Execute transfer (download or upload)
     */
    private suspend fun executeTransfer(taskEmbedded: TransferTaskEmbedded) = withContext(Dispatchers.IO) {
        val task = taskEmbedded.task
        val server = taskEmbedded.server

        println("[iOS Transfer] Executing transfer: ${task.fileName} (${task.direction})")

        // Begin background task
        backgroundTaskManager.beginBackgroundTask(task)

        // Mark as active
        activeTransfers.value = activeTransfers.value + (task.id to false)

        try {
            // Update status to IN_PROGRESS
            transferTaskDao.updateStatus(
                taskId = task.id,
                status = TransferStatus.IN_PROGRESS
            )

            // Create FTP config
            val config = FTPConfig(
                host = server.host,
                port = server.port,
                username = server.user,
                password = server.password
            )

            val manager = FTPClientManager(config)

            // Connect to server
            if (!manager.connect()) {
                throw IllegalStateException("Failed to connect to FTP server")
            }

            try {
                when (task.direction) {
                    TransferDirection.DOWNLOAD -> executeDownload(taskEmbedded, config)
                    TransferDirection.UPLOAD -> executeUpload(taskEmbedded, config)
                }

                // Check if task was cancelled/paused during transfer
                if (activeTransfers.value[task.id] == true) {
                    println("[iOS Transfer] Transfer was paused: ${task.fileName}")
                    return@withContext
                }

                // Transfer completed successfully
                transferTaskDao.updateStatus(
                    taskId = task.id,
                    status = TransferStatus.COMPLETED
                )

                // Clean up speed tracking
                progressMonitor.cleanupSpeedTracking(task.id)

                println("[iOS Transfer] Transfer completed: ${task.fileName}")
            } finally {
                manager.close()
            }
        } catch (e: Exception) {
            println("[iOS Transfer] Transfer failed: ${e.message}")
            e.printStackTrace()

            // Update error status - only if not paused
            if (activeTransfers.value[task.id] != true) {
                transferTaskDao.updateStatus(
                    taskId = task.id,
                    status = TransferStatus.FAILED
                )
            }

            // Clean up speed tracking
            progressMonitor.cleanupSpeedTracking(task.id)
        } finally {
            // Remove from active transfers
            activeTransfers.value = activeTransfers.value - task.id

            // End background task
            backgroundTaskManager.endBackgroundTask()
        }
    }

    /**
     * Execute download with checkpoint resume support
     */
    private suspend fun executeDownload(taskEmbedded: TransferTaskEmbedded, config: FTPConfig) {
        val task = taskEmbedded.task
        val server = taskEmbedded.server

        val callbackFactory = PlatformDownloadCallbackFactory.get()
        val transferClient = SFTPClientFactory.createTransferClient()

        if (!transferClient.initClient(config)) {
            throw IllegalStateException("Failed to initialize transfer client")
        }

        try {
            // Get writable download directory with fallback
            val downloadDir = getWritableDownloadDir(server.downloadDir)
            println("[iOS Transfer] Using download directory: $downloadDir")

            val callback = object : DownloadCallback {
                override suspend fun openOutputStream(
                    fileSize: Long,
                    resumeOffset: Long
                ): Pair<Any, Long>? {
                    val innerCallback = callbackFactory.create(
                        remotePath = task.remotePath,
                        fileName = task.fileName,
                        downloadDir = downloadDir,
                        onProgressUpdate = {},
                        onComplete = {},
                        onError = {}
                    )
                    return innerCallback.openOutputStream(fileSize, resumeOffset)
                }

                override fun onProgress(bytesTransferred: Long, totalBytes: Long) {
                    // Check if cancelled
                    if (activeTransfers.value[task.id] == true) {
                        throw TransferCancelledException("Transfer paused by user")
                    }

                    // Update progress in database
                    kotlinx.coroutines.runBlocking {
                        transferTaskDao.updateBytesTransferred(
                            taskId = task.id,
                            bytesTransferred = bytesTransferred
                        )
                    }

                    // Log progress periodically
                    if (bytesTransferred % (1024 * 1024) == 0L) { // Every 1MB
                        val progress = (bytesTransferred * 100 / totalBytes).toInt()
                        println("[iOS Transfer] Progress: ${task.fileName} - $progress%")

                        // Check remaining background time
                        val remainingTime = backgroundTaskManager.getRemainingBackgroundTime()
                        println("[iOS Transfer] Remaining background time: $remainingTime seconds")
                    }
                }
            }

            // Use resume if task has existing progress
            val success = if (task.bytesTransferred > 0) {
                println("[iOS Transfer] Resuming from ${task.bytesTransferred} bytes")
                transferClient.downloadFileWithResume(
                    remotePath = task.remotePath,
                    callback = callback,
                    resumeOffset = task.bytesTransferred
                )
            } else {
                transferClient.downloadFile(
                    remotePath = task.remotePath,
                    callback = callback
                )
            }

            if (!success) {
                throw IllegalStateException("Download failed")
            }
        } catch (e: TransferCancelledException) {
            // Re-throw cancellation exception
            throw e
        } finally {
            transferClient.close()
        }
    }

    /**
     * Execute upload
     */
    private suspend fun executeUpload(taskEmbedded: TransferTaskEmbedded, config: FTPConfig) {
        val task = taskEmbedded.task

        val callbackFactory = PlatformUploadCallbackFactory.get()
        val transferClient = SFTPClientFactory.createTransferClient()

        if (!transferClient.initClient(config)) {
            throw IllegalStateException("Failed to initialize transfer client")
        }

        try {
            val callback = object : UploadCallback {
                override suspend fun openInputStream(): Pair<Any, Long>? {
                    val innerCallback = callbackFactory.create(
                        localUri = task.localUri,
                        fileName = task.fileName,
                        remotePath = task.remotePath,
                        onProgressUpdate = {},
                        onComplete = {},
                        onError = {}
                    )
                    return innerCallback.openInputStream()
                }

                override fun onProgress(bytesTransferred: Long, totalBytes: Long) {
                    // Check if cancelled
                    if (activeTransfers.value[task.id] == true) {
                        throw TransferCancelledException("Transfer paused by user")
                    }

                    // Update progress in database
                    kotlinx.coroutines.runBlocking {
                        transferTaskDao.updateBytesTransferred(
                            taskId = task.id,
                            bytesTransferred = bytesTransferred
                        )
                    }

                    // Log progress periodically
                    if (bytesTransferred % (1024 * 1024) == 0L) { // Every 1MB
                        val progress = (bytesTransferred * 100 / totalBytes).toInt()
                        println("[iOS Transfer] Progress: ${task.fileName} - $progress%")

                        // Check remaining background time
                        val remainingTime = backgroundTaskManager.getRemainingBackgroundTime()
                        println("[iOS Transfer] Remaining background time: $remainingTime seconds")
                    }
                }
            }

            val success = transferClient.uploadFile(
                remotePath = task.remotePath,
                callback = callback
            )

            if (!success) {
                throw IllegalStateException("Upload failed")
            }
        } catch (e: TransferCancelledException) {
            // Re-throw cancellation exception
            throw e
        } finally {
            transferClient.close()
        }
    }

    /**
     * Cancel a transfer
     */
    suspend fun cancelTransfer(taskId: String) {
        println("[iOS Transfer] Cancelling transfer: $taskId")

        // Mark task as cancelled in active transfers map
        activeTransfers.value = activeTransfers.value + (taskId to true)

        // Update status to CANCELLED
        transferTaskDao.updateStatus(
            taskId = taskId,
            status = TransferStatus.CANCELLED
        )

        // Clean up speed tracking
        progressMonitor.cleanupSpeedTracking(taskId)
    }

    /**
     * Observe transfers for a specific server
     */
    fun observeTransfers(serverId: Long): Flow<List<TransferTaskEmbedded>> {
        return transferTaskDao.getTasksEmbeddedByServerId(serverId)
    }

    /**
     * Clear completed transfers
     */
    suspend fun clearCompleted() {
        transferTaskDao.deleteCompleted()
    }

    /**
     * Get writable download directory with fallback to Documents directory
     * @param configuredDir The configured download directory (may be null or not writable)
     * @return A writable directory path
     */
    private fun getWritableDownloadDir(configuredDir: String?): String {
        val fileManager = NSFileManager.defaultManager

        // If no directory configured, use Documents directory
        if (configuredDir.isNullOrEmpty()) {
            println("[iOS Transfer] No download directory configured, using Documents directory")
            return documentDirectory()
        }

        // Check if configured directory exists and is writable
        val exists = fileManager.fileExistsAtPath(configuredDir)
        if (!exists) {
            println("[iOS Transfer] Configured directory does not exist: $configuredDir, using Documents directory")
            return documentDirectory()
        }

        val isWritable = fileManager.isWritableFileAtPath(configuredDir)
        if (!isWritable) {
            println("[iOS Transfer] Configured directory is not writable: $configuredDir, using Documents directory")
            println("[iOS Transfer] This may be a File Provider Storage or system directory without write permissions")
            return documentDirectory()
        }

        // Directory is valid and writable
        println("[iOS Transfer] Using configured directory: $configuredDir")
        return configuredDir
    }
}

/**
 * Exception thrown when a transfer is cancelled
 */
class TransferCancelledException(message: String = "Transfer cancelled") : Exception(message)
