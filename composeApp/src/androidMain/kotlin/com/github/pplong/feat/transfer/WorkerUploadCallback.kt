package com.github.pplong.feat.transfer

import androidx.work.workDataOf
import com.github.pplong.feat.transfer.model.TransferTask
import com.github.pplong.sftp.AndroidUploadCallbackFactory
import com.github.pplong.sftp.UploadCallback
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Upload callback for TransferWorker
 * Extracted as separate class to avoid D8 bytecode verification issues
 */
class WorkerUploadCallback(
    private val task: TransferTask,
    private val callbackFactory: AndroidUploadCallbackFactory,
    private val transferTaskDao: com.github.pplong.feat.transfer.model.TransferTaskDao,
    private val notificationHelper: TransferNotificationHelper,
    private val onSetProgress: (Int) -> Unit
) : UploadCallback {

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

        // Update database with actual file size
        result?.let { (_, fileSize) ->
            if (fileSize > 0 && task.totalBytes == 0L) {
                CoroutineScope(Dispatchers.IO).launch {
                    transferTaskDao.updateTotalBytes(
                        taskId = task.id,
                        totalBytes = fileSize
                    )
                    println("[WorkerUploadCallback] Updated totalBytes for ${task.fileName}: $fileSize")
                }
            }
        }

        return result
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
        println("[TransferWorker] Upload completed: ${task.fileName}")
    }

    override fun onError(error: Throwable) {
        println("[TransferWorker] Upload error: ${error.message}")
    }
}
