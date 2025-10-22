package com.github.pplong.feat.transfer

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.github.pplong.feat.transfer.model.TransferDirection
import com.github.pplong.feat.transfer.model.TransferStatus
import com.github.pplong.feat.transfer.model.TransferTask
import com.github.pplong.feat.transfer.model.TransferTaskDao
import com.github.pplong.sftp.AndroidDownloadCallbackFactory
import com.github.pplong.sftp.AndroidUploadCallbackFactory
import com.github.pplong.sftp.FTPClientManager
import com.github.pplong.sftp.SFTPClientFactory
import com.github.pplong.sftp.def.FTPConfig

/**
 * WorkManager Worker for background file transfers
 * Handles both upload and download operations with progress tracking
 *
 * Note: Constructor parameters order is required by Koin WorkManager integration:
 * 1. Context - automatically provided by WorkManager
 * 2. WorkerParameters - automatically provided by WorkManager
 * 3. AppDatabase - injected by Koin using get()
 */
class TransferWorker(
    private val appContext: Context,
    private val workerParams: WorkerParameters,
    private val transferTaskDao: TransferTaskDao
) : CoroutineWorker(appContext, workerParams) {

    private val notificationHelper = TransferNotificationHelper(appContext)

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val PROGRESS_KEY = "progress"
        const val PROGRESS_MAX = 100
    }

    override suspend fun doWork(): Result {
        return performTransfer()
    }

    private suspend fun performTransfer(): Result {
        val taskId = inputData.getString(KEY_TASK_ID)
            ?: return Result.failure()

        println("[TransferWorker] Starting transfer for task: $taskId")

        // Get transfer task from database
        val task = transferTaskDao.getById(taskId)
            ?: return Result.failure(workDataOf("error" to "Task not found"))

        // Set as foreground service with notification
        setForeground(createForegroundInfo(task))

        // Update task status to IN_PROGRESS
        transferTaskDao.updateStatus(
            taskId = taskId,
            status = TransferStatus.IN_PROGRESS,
            updatedAt = System.currentTimeMillis()
        )

        // Execute transfer based on direction
        return try {
            val transferResult = when (task.direction) {
                TransferDirection.DOWNLOAD -> executeDownload(task)
                TransferDirection.UPLOAD -> executeUpload(task)
            }
            println("[TransferWorker] Transfer completed for task: $taskId")
            transferResult
        } catch (e: Exception) {
            println("[TransferWorker] Transfer failed: ${e.message}")
            e.printStackTrace()

            // Update task with error
            transferTaskDao.updateError(
                taskId = taskId,
                status = TransferStatus.FAILED,
                errorMessage = e.message ?: "Unknown error",
                updatedAt = System.currentTimeMillis()
            )

            // Show error notification
            notificationHelper.showErrorNotification(task, e.message)

            Result.retry() // Retry on failure
        }
    }

    /**
     * Execute download task
     */
    private suspend fun executeDownload(task: TransferTask): Result {
        println("[TransferWorker] Executing download: ${task.fileName}")

        // Create FTP client manager
        val config = FTPConfig(
            host = task.serverHost,
            port = task.serverPort,
            username = task.serverUsername,
            password = task.serverPassword
        )

        // TODO: try to use manager already exist
        val manager = FTPClientManager(config)

        // Connect to server
        if (!manager.connect()) {
            manager.close()
            throw IllegalStateException("Failed to connect to FTP server")
        }

        return try {
            // Create download callback
            val callbackFactory = AndroidDownloadCallbackFactory(appContext)
            val transferClient = SFTPClientFactory.createTransferClient()

            if (!transferClient.initClient(config)) {
                transferClient.close()
                throw IllegalStateException("Failed to initialize transfer client")
            }

            val result = try {
                val callback = WorkerDownloadCallback(
                    task = task,
                    callbackFactory = callbackFactory,
                    transferTaskDao = transferTaskDao,
                    notificationHelper = notificationHelper,
                    onSetProgress = { progress ->
                        setProgressAsync(workDataOf(PROGRESS_KEY to progress))
                    }
                )

                // Start download with resume support
                val success = if (task.transferredBytes > 0) {
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

                if (success) {
                    // Update task status to COMPLETED
                    transferTaskDao.updateStatus(
                        taskId = task.id,
                        status = TransferStatus.COMPLETED,
                        updatedAt = System.currentTimeMillis()
                    )

                    // Show completion notification
                    notificationHelper.showCompletionNotification(task)

                    Result.success()
                } else {
                    throw IllegalStateException("Download failed")
                }
            } finally {
                transferClient.close()
            }
            result
        } finally {
            manager.close()
        }
    }

    /**
     * Execute upload task
     */
    private suspend fun executeUpload(task: TransferTask): Result {
        println("[TransferWorker] Executing upload: ${task.fileName}")

        // Create FTP client manager
        val config = FTPConfig(
            host = task.serverHost,
            port = task.serverPort,
            username = task.serverUsername,
            password = task.serverPassword
        )

        val manager = FTPClientManager(config)

        // Connect to server
        if (!manager.connect()) {
            manager.close()
            throw IllegalStateException("Failed to connect to FTP server")
        }

        return try {
            // Create upload callback
            val callbackFactory = AndroidUploadCallbackFactory(appContext)
            val transferClient = SFTPClientFactory.createTransferClient()

            if (!transferClient.initClient(config)) {
                transferClient.close()
                throw IllegalStateException("Failed to initialize transfer client")
            }

            val result = try {
                val callback = WorkerUploadCallback(
                    task = task,
                    callbackFactory = callbackFactory,
                    transferTaskDao = transferTaskDao,
                    notificationHelper = notificationHelper,
                    onSetProgress = { progress ->
                        setProgressAsync(workDataOf(PROGRESS_KEY to progress))
                    }
                )

                // Start upload
                val success = transferClient.uploadFile(
                    remotePath = task.remotePath,
                    callback = callback
                )

                if (success) {
                    // Update task status to COMPLETED
                    transferTaskDao.updateStatus(
                        taskId = task.id,
                        status = TransferStatus.COMPLETED,
                        updatedAt = System.currentTimeMillis()
                    )

                    // Show completion notification
                    notificationHelper.showCompletionNotification(task)

                    Result.success()
                } else {
                    throw IllegalStateException("Upload failed")
                }
            } finally {
                transferClient.close()
            }
            result
        } finally {
            manager.close()
        }
    }

    /**
     * Create foreground info for notification
     */
    private fun createForegroundInfo(task: TransferTask): ForegroundInfo {
        val notification = notificationHelper.createProgressNotification(task, 0, PROGRESS_MAX)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                task.id.hashCode(),
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(task.id.hashCode(), notification)
        }
    }
}
