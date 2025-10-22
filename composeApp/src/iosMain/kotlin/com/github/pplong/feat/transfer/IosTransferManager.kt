package com.github.pplong.feat.transfer

import com.github.pplong.feat.transfer.model.TransferDirection
import com.github.pplong.feat.transfer.model.TransferStatus
import com.github.pplong.feat.transfer.model.TransferTask
import com.github.pplong.sftp.DownloadCallback
import com.github.pplong.sftp.FTPClientManager
import com.github.pplong.sftp.PlatformDownloadCallbackFactory
import com.github.pplong.sftp.PlatformUploadCallbackFactory
import com.github.pplong.sftp.SFTPClientFactory
import com.github.pplong.sftp.UploadCallback
import com.github.pplong.sftp.def.FTPConfig
import AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import platform.Foundation.NSDate
import platform.Foundation.NSUUID
import platform.Foundation.timeIntervalSince1970

/**
 * iOS-specific transfer manager
 * Uses background task to extend transfer time when app goes to background
 * Implements checkpoint-based resume for interrupted transfers
 */
class IosTransferManager : KoinComponent {

    private val database: AppDatabase by inject()
    private val transferTaskDao = database.getTransferTaskDao()
    private val backgroundTaskManager = IosBackgroundTaskManager()

    /**
     * Start a download task
     */
    suspend fun startDownload(
        fileName: String,
        remotePath: String,
        downloadDir: String,
        serverHost: String,
        serverPort: Int,
        serverUsername: String,
        serverPassword: String,
        fileSize: Long
    ): String {
        val taskId = NSUUID().UUIDString()
        val currentTime = (NSDate().timeIntervalSince1970 * 1000).toLong()

        // Create transfer task
        val task = TransferTask(
            id = taskId,
            direction = TransferDirection.DOWNLOAD,
            status = TransferStatus.PENDING,
            fileName = fileName,
            localUri = "",
            remotePath = remotePath,
            transferredBytes = 0L,
            totalBytes = fileSize,
            serverHost = serverHost,
            serverPort = serverPort,
            serverUsername = serverUsername,
            serverPassword = serverPassword,
            downloadDir = downloadDir,
            createdAt = currentTime,
            updatedAt = currentTime
        )

        // Save to database
        transferTaskDao.insert(task)

        // Execute transfer immediately
        executeTransfer(task)

        return taskId
    }

    /**
     * Start an upload task
     */
    suspend fun startUpload(
        fileName: String,
        localUri: String,
        remotePath: String,
        serverHost: String,
        serverPort: Int,
        serverUsername: String,
        serverPassword: String,
        fileSize: Long
    ): String {
        val taskId = NSUUID().UUIDString()
        val currentTime = (NSDate().timeIntervalSince1970 * 1000).toLong()

        // Create transfer task
        val task = TransferTask(
            id = taskId,
            direction = TransferDirection.UPLOAD,
            status = TransferStatus.PENDING,
            fileName = fileName,
            localUri = localUri,
            remotePath = remotePath,
            transferredBytes = 0L,
            totalBytes = fileSize,
            serverHost = serverHost,
            serverPort = serverPort,
            serverUsername = serverUsername,
            serverPassword = serverPassword,
            createdAt = currentTime,
            updatedAt = currentTime
        )

        // Save to database
        transferTaskDao.insert(task)

        // Execute transfer immediately
        executeTransfer(task)

        return taskId
    }

    /**
     * Resume a paused transfer
     */
    suspend fun resumeTransfer(taskId: String) {
        val task = transferTaskDao.getById(taskId) ?: return

        if (!task.canResume) {
            println("[iOS Transfer] Task cannot be resumed: ${task.status}")
            return
        }

        println("[iOS Transfer] Resuming transfer: ${task.fileName}")

        // Update status to IN_PROGRESS
        transferTaskDao.updateStatus(
            taskId = taskId,
            status = TransferStatus.IN_PROGRESS,
            updatedAt = (NSDate().timeIntervalSince1970 * 1000).toLong()
        )

        // Execute transfer
        executeTransfer(task)
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
     * Execute transfer (download or upload)
     */
    private suspend fun executeTransfer(task: TransferTask) = withContext(Dispatchers.IO) {
        println("[iOS Transfer] Executing transfer: ${task.fileName} (${task.direction})")

        // Begin background task
        backgroundTaskManager.beginBackgroundTask(task)

        try {
            // Update status to IN_PROGRESS
            transferTaskDao.updateStatus(
                taskId = task.id,
                status = TransferStatus.IN_PROGRESS,
                updatedAt = (NSDate().timeIntervalSince1970 * 1000).toLong()
            )

            // Create FTP config
            val config = FTPConfig(
                host = task.serverHost,
                port = task.serverPort,
                username = task.serverUsername,
                password = task.serverPassword
            )

            val manager = FTPClientManager(config)

            // Connect to server
            if (!manager.connect()) {
                throw IllegalStateException("Failed to connect to FTP server")
            }

            try {
                when (task.direction) {
                    TransferDirection.DOWNLOAD -> executeDownload(task, config)
                    TransferDirection.UPLOAD -> executeUpload(task, config)
                }

                // Transfer completed successfully
                transferTaskDao.updateStatus(
                    taskId = task.id,
                    status = TransferStatus.COMPLETED,
                    updatedAt = (NSDate().timeIntervalSince1970 * 1000).toLong()
                )

                println("[iOS Transfer] Transfer completed: ${task.fileName}")
            } finally {
                manager.close()
            }
        } catch (e: Exception) {
            println("[iOS Transfer] Transfer failed: ${e.message}")
            e.printStackTrace()

            // Update error status
            transferTaskDao.updateError(
                taskId = task.id,
                status = TransferStatus.FAILED,
                errorMessage = e.message ?: "Unknown error",
                updatedAt = (NSDate().timeIntervalSince1970 * 1000).toLong()
            )
        } finally {
            // End background task
            backgroundTaskManager.endBackgroundTask()
        }
    }

    /**
     * Execute download with checkpoint resume support
     */
    private suspend fun executeDownload(task: TransferTask, config: FTPConfig) {
        val callbackFactory = PlatformDownloadCallbackFactory.get()
        val transferClient = SFTPClientFactory.createTransferClient()

        if (!transferClient.initClient(config)) {
            throw IllegalStateException("Failed to initialize transfer client")
        }

        try {
            val callback = object : DownloadCallback {
                override suspend fun openOutputStream(
                    fileSize: Long,
                    resumeOffset: Long
                ): Pair<Any, Long>? {
                    val innerCallback = callbackFactory.create(
                        remotePath = task.remotePath,
                        fileName = task.fileName,
                        downloadDir = task.downloadDir ?: "",
                        onProgressUpdate = {},
                        onComplete = {},
                        onError = {}
                    )
                    return innerCallback.openOutputStream(fileSize, resumeOffset)
                }

                override fun onProgress(bytesTransferred: Long, totalBytes: Long) {
                    // Update progress in database
                    kotlinx.coroutines.runBlocking {
                        transferTaskDao.updateProgress(
                            taskId = task.id,
                            transferredBytes = bytesTransferred,
                            updatedAt = (NSDate().timeIntervalSince1970 * 1000).toLong()
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
            val success = if (task.transferredBytes > 0) {
                println("[iOS Transfer] Resuming from ${task.transferredBytes} bytes")
                transferClient.downloadFileWithResume(
                    remotePath = task.remotePath,
                    callback = callback,
                    resumeOffset = task.transferredBytes
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
        } finally {
            transferClient.close()
        }
    }

    /**
     * Execute upload
     */
    private suspend fun executeUpload(task: TransferTask, config: FTPConfig) {
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
                    // Update progress in database
                    kotlinx.coroutines.runBlocking {
                        transferTaskDao.updateProgress(
                            taskId = task.id,
                            transferredBytes = bytesTransferred,
                            updatedAt = (NSDate().timeIntervalSince1970 * 1000).toLong()
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
        } finally {
            transferClient.close()
        }
    }

    /**
     * Cancel a transfer
     */
    suspend fun cancelTransfer(taskId: String) {
        transferTaskDao.updateStatus(
            taskId = taskId,
            status = TransferStatus.CANCELLED,
            updatedAt = (NSDate().timeIntervalSince1970 * 1000).toLong()
        )
    }

    /**
     * Observe all transfers
     */
    fun observeTransfers(): Flow<List<TransferTask>> {
        return transferTaskDao.observeAll()
    }

    /**
     * Get paused tasks
     */
    suspend fun getPausedTasks(): List<TransferTask> {
        return transferTaskDao.getPausedTasks()
    }

    /**
     * Clear completed transfers
     */
    suspend fun clearCompleted() {
        transferTaskDao.deleteCompleted()
    }
}
