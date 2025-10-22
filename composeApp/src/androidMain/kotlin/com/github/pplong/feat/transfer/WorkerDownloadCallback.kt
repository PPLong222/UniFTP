package com.github.pplong.feat.transfer

import androidx.work.workDataOf
import com.github.pplong.feat.transfer.model.TransferTask
import com.github.pplong.sftp.AndroidDownloadCallbackFactory
import com.github.pplong.sftp.DownloadCallback
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Download callback for TransferWorker
 * Extracted as separate class to avoid D8 bytecode verification issues
 */
class WorkerDownloadCallback(
    private val task: TransferTask,
    private val callbackFactory: AndroidDownloadCallbackFactory,
    private val transferTaskDao: com.github.pplong.feat.transfer.model.TransferTaskDao,
    private val notificationHelper: TransferNotificationHelper,
    private val onSetProgress: (Int) -> Unit
) : DownloadCallback {

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
        // Update notification
        val progress = if (totalBytes > 0) {
            (bytesTransferred * 100 / totalBytes).toInt()
        } else 0

        notificationHelper.updateProgressNotification(
            task,
            progress,
            TransferWorker.PROGRESS_MAX
        )

        // Update WorkManager progress - UI observes this directly via ProgressMonitor
        onSetProgress(progress)

        // Note: No database update needed for progress
        // Database is only updated when status changes (completed/failed)
    }

    override fun onComplete() {
        println("[TransferWorker] Download completed: ${task.fileName}")
    }

    override fun onError(error: Throwable) {
        println("[TransferWorker] Download error: ${error.message}")
    }
}
