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
import com.github.pplong.feat.transfer.model.TransferTaskEmbedded
import com.github.pplong.sftp.AndroidDownloadCallback
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
        const val PROGRESS = "progress"
        const val SPEED = "speed"
        const val BYTES_TRANSFERRED = "bytes_transferred"
        const val PROGRESS_MAX = 100
    }

    override suspend fun doWork(): Result {
        return performTransfer()
    }

    private suspend fun performTransfer(): Result {
        val taskId = inputData.getString(KEY_TASK_ID)
            ?: return Result.failure()

        println("[TransferWorker] Starting transfer for task: $taskId")

        // Check if stopped before starting
        if (isStopped) {
            println("[TransferWorker] Worker stopped before starting task: $taskId")
            return Result.failure(workDataOf("error" to "Transfer cancelled"))
        }

        // Get transfer task from database
        val task = transferTaskDao.getTasksEmbeddedById(taskId)
            ?: return Result.failure(workDataOf("error" to "Task not found"))

        // Set as foreground service with notification
        setForeground(createForegroundInfo(task.task))

        // Update task status to IN_PROGRESS
        transferTaskDao.updateStatus(
            taskId = taskId,
            status = TransferStatus.IN_PROGRESS,
        )

        // Execute transfer based on direction
        return try {
            // Check if stopped before transfer
            if (isStopped) {
                println("[TransferWorker] Worker stopped, cancelling task: $taskId")
//                transferTaskDao.updateStatus(taskId = taskId, status = TransferStatus.PAUSED)
                return Result.failure(workDataOf("error" to "Transfer cancelled"))
            }

            val transferResult = when (task.task.direction) {
                TransferDirection.DOWNLOAD -> executeDownload(task)
                TransferDirection.UPLOAD -> executeUpload(task)
            }
            println("[TransferWorker] Transfer completed for task: $taskId")
            transferResult
        } catch (e: TransferCancelledException) {
            println("[TransferWorker] Transfer cancelled: ${e.message}")
            // Update task status to CANCELLED (not FAILED)
//            transferTaskDao.updateStatus(
//                taskId = taskId,
//                status = TransferStatus.CANCELLED,
//            )
            Result.failure(workDataOf("error" to "Transfer cancelled"))
        } catch (e: Exception) {
            println("[TransferWorker] Transfer failed: ${e.message}")
            e.printStackTrace()

            // Update task with error


            // Show error notification
            notificationHelper.showErrorNotification(task.task, e.message)

            Result.retry() // Retry on failure
        }
    }

    /**
     * Execute download task
     */
    private suspend fun executeDownload(taskEmbedded: TransferTaskEmbedded): Result {
        println("[TransferWorker] Executing download: ${taskEmbedded.task.fileName}")
        val task = taskEmbedded.task
        val server = taskEmbedded.server
        // Create FTP client manager
        val config = FTPConfig(
            host = server.host,
            port = server.port,
            username = server.user,
            password = server.password
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
            val transferClient = SFTPClientFactory.createTransferClient()

            if (!transferClient.initClient(config)) {
                transferClient.close()
                throw IllegalStateException("Failed to initialize transfer client")
            }

            val callback = AndroidDownloadCallback(
                context = appContext,
                fileName = task.fileName,
                downloadDir = taskEmbedded.server.downloadDir!!,
                onProgressUpdate = { bytesTransferred, speed ->
                    val progress = bytesTransferred * 1.0f / taskEmbedded.task.size
                    println(progress)
                    setProgressAsync(
                        workDataOf(
                            PROGRESS to progress,
                            SPEED to speed,
                            BYTES_TRANSFERRED to bytesTransferred
                        )
                    )
                },
                onComplete = {},
                onError = {},
                onOutputConfirmed = { uri ->
                    transferTaskDao.updateTransferUriByTaskId(taskEmbedded.task.id, uri)
                },
                isCancelled = { isStopped }
            )

            val result = try {
                // Start download with resume support
                val success = if (task.bytesTransferred > 0) {
                    transferClient.downloadFileWithResume(
                        remotePath = task.remotePath,
                        callback = callback,
                        localUri = task.localUri,
                        resumeOffset = task.bytesTransferred
                    )
                } else {
                    // TODO complete downloadwithreumse
                    transferClient.downloadFile(
                        remotePath = taskEmbedded.task.remotePath,
                        callback = callback
                    )
                }

                if (success) {
                    // Update task status to COMPLETED
                    transferTaskDao.updateStatus(
                        taskId = taskEmbedded.task.id,
                        status = TransferStatus.COMPLETED,
                    )

                    // Show completion notification
                    notificationHelper.showCompletionNotification(taskEmbedded.task)

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
    private suspend fun executeUpload(taskEmbedded: TransferTaskEmbedded): Result {

        val server = taskEmbedded.server
        val task = taskEmbedded.task
        println("[TransferWorker] Executing upload: ${task.fileName}")

        // Create FTP client manager
        val config = FTPConfig(
            host = server.host,
            port = server.port,
            username = server.user,
            password = server.password
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
                    onSetProgress = { bytesTransferred, speed ->
                        val progress = bytesTransferred * 1.0f / taskEmbedded.task.size
                        setProgressAsync(
                            workDataOf(
                                PROGRESS to progress,
                                SPEED to speed,
                                BYTES_TRANSFERRED to bytesTransferred
                            )
                        )
                    },
                    isCancelled = { isStopped }
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

/**
 * Exception thrown when a transfer is cancelled
 */
class TransferCancelledException(message: String = "Transfer cancelled") : Exception(message)
