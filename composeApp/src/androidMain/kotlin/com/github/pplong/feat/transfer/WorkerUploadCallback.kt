package com.github.pplong.feat.transfer

import com.github.pplong.feat.transfer.model.TransferTask
import com.github.pplong.sftp.AndroidUploadCallbackFactory
import com.github.pplong.sftp.UploadCallback

/**
 * Upload callback for TransferWorker
 * Extracted as separate class to avoid D8 bytecode verification issues
 */

// TODO Removed
class WorkerUploadCallback(
    private val task: TransferTask,
    private val callbackFactory: AndroidUploadCallbackFactory,
    private val transferTaskDao: com.github.pplong.feat.transfer.model.TransferTaskDao,
    private val notificationHelper: TransferNotificationHelper,
    private val onSetProgress: (Long, Long) -> Unit,
    private val isCancelled: () -> Boolean
) : UploadCallback {
    private var lastTimeStamp = 0L
    private var lastBytesTransferred: Long = 0
    private var lastSpeed = 0L
    override suspend fun openInputStream(): Pair<Any, Long>? {
        val innerCallback = callbackFactory.create(
            localUri = task.localUri,
            fileName = task.fileName,
            remotePath = task.remotePath,
            onProgressUpdate = {},
            onComplete = {},
            onError = {}
        )
        val result = innerCallback.openInputStream()

        return result
    }

    override fun onProgress(bytesTransferred: Long, totalBytes: Long) {
        // Check if transfer is cancelled
        if (isCancelled()) {
            println("[WorkerUploadCallback] Transfer cancelled, throwing exception")
            throw TransferCancelledException("Upload cancelled by user")
        }

        // Update notification
        val progress = if (totalBytes > 0) {
            (bytesTransferred * 100 / totalBytes).toInt()
        } else 0

        notificationHelper.updateProgressNotification(
            task,
            progress,
            TransferWorker.PROGRESS_MAX
        )
        val currentTimeStamp = System.currentTimeMillis()
        if (currentTimeStamp - lastTimeStamp > 3000L) {
            lastSpeed =
                (bytesTransferred - lastBytesTransferred) * 1000 / (currentTimeStamp - lastTimeStamp)
            lastBytesTransferred = bytesTransferred
            lastTimeStamp = currentTimeStamp
        }
        // Update WorkManager progress - UI observes this directly via ProgressMonitor
        onSetProgress(bytesTransferred, lastSpeed)
    }

    override fun onComplete() {
        println("[TransferWorker] Upload completed: ${task.fileName}")
    }

    override fun onError(error: Throwable) {
        println("[TransferWorker] Upload error: ${error.message}")
    }
}
