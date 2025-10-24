package com.github.pplong.feat.transfer

import com.github.pplong.feat.transfer.model.TransferTaskDao
import com.github.pplong.feat.transfer.model.TransferTaskEmbedded
import com.github.pplong.sftp.AndroidDownloadCallbackFactory
import com.github.pplong.sftp.DownloadCallback

/**
 * Download callback for TransferWorker
 * Extracted as separate class to avoid D8 bytecode verification issues
 */
class WorkerDownloadCallback(
    private val taskEmbedded: TransferTaskEmbedded,
    private val callbackFactory: AndroidDownloadCallbackFactory,
    private val transferTaskDao: TransferTaskDao,
    private val notificationHelper: TransferNotificationHelper,
    private val onSetProgress: (Int, Long) -> Unit
) : DownloadCallback {
    private var lastTimeStamp = 0L
    private var lastBytesTransferred: Long = 0
    private var lastSpeed = 0L
    override suspend fun openOutputStream(
        fileSize: Long,
        resumeOffset: Long
    ): Pair<Any, Long>? {

        val innerCallback = callbackFactory.create(
            remotePath = taskEmbedded.task.remotePath,
            fileName = taskEmbedded.task.fileName,
            downloadDir = taskEmbedded.server.downloadDir ?: "",
            onProgressUpdate = {},
            onComplete = {},
            onError = {}
        )
        return innerCallback.openOutputStream(fileSize, resumeOffset)
    }

    override fun onProgress(bytesTransferred: Long, totalBytes: Long) {
        // Update notification

        val progress = if (totalBytes > 0) {
            (bytesTransferred * 100 / totalBytes).toInt()
        } else 0

        notificationHelper.updateProgressNotification(
            taskEmbedded.task,
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
        onSetProgress(progress, lastSpeed)


        // Note: No database update needed for progress
        // Database is only updated when status changes (completed/failed)
    }

    override fun onComplete() {
        println("[TransferWorker] Download completed: ${taskEmbedded.task.fileName}")
    }

    override fun onError(error: Throwable) {
        println("[TransferWorker] Download error: ${error.message}")
    }
}
