package com.github.pplong.feat.transfer

import AppDatabase
import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import com.github.pplong.feat.browse.FTPFileUiModel
import com.github.pplong.feat.transfer.TransferWorker.Companion.BYTES_TRANSFERRED
import com.github.pplong.feat.transfer.model.TransferDirection
import com.github.pplong.feat.transfer.model.TransferStatus
import com.github.pplong.feat.transfer.model.TransferTask
import com.github.pplong.feat.transfer.model.TransferTaskEmbedded
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

/**
 * Transfer progress event for real-time UI updates
 */
data class TransferProgressEvent(
    val taskId: String,
    val remotePath: String,
    val progress: Int,  // 0-100
    val status: WorkInfo.State
)

/**
 * Android-specific transfer manager using WorkManager
 * Handles background file transfers with foreground service
 */
class AndroidTransferManager(
    private val context: Context
) : KoinComponent {

    private val database: AppDatabase by inject()
    private val transferTaskDao = database.getTransferTaskDao()
    private val workManager = WorkManager.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Map to track taskId -> workId (exposed for ProgressMonitor)
    private val taskWorkMap = mutableMapOf<String, UUID>()

    // Flow to notify when taskWorkMap changes
    private val _taskWorkMapFlow = MutableSharedFlow<Map<String, UUID>>(replay = 1)
    val taskWorkMapFlow: SharedFlow<Map<String, UUID>> = _taskWorkMapFlow

    init {
        // Emit initial empty map
        scope.launch {
            _taskWorkMapFlow.emit(emptyMap())
            println("[AndroidTransferManager] Initialized with empty taskWorkMap")
        }
    }

    /**
     * Get the current taskId -> workId mapping
     * Used by ProgressMonitor to observe WorkManager progress
     */
    fun getTaskWorkMap(): Map<String, UUID> = taskWorkMap.toMap()

    /**
     * Enqueue a download task
     */
    suspend fun enqueueDownload(
        file: FTPFileUiModel,
        serverId: Long
    ): String {
        val taskId = UUID.randomUUID().toString()
        val currentTime = System.currentTimeMillis()

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

        // Enqueue work
        enqueueWork(task)

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
        val taskId = UUID.randomUUID().toString()
        val currentTime = System.currentTimeMillis()

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

        // Enqueue work
        enqueueWork(task)

        return taskId
    }

    /**
     * Enqueue WorkManager work for a transfer task
     */
    private fun enqueueWork(task: TransferTask) {
        val workRequest = OneTimeWorkRequestBuilder<TransferWorker>()
            .setInputData(workDataOf(TransferWorker.KEY_TASK_ID to task.id))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        // Save workId mapping for progress tracking
        taskWorkMap[task.id] = workRequest.id

        // Notify observers about the new mapping
        scope.launch {
            val mapCopy = taskWorkMap.toMap()
            println("[AndroidTransferManager] Emitting taskWorkMap update: size=${mapCopy.size}, taskId=${task.id}")
            _taskWorkMapFlow.emit(mapCopy)
            println("[AndroidTransferManager] TaskWorkMap emitted successfully")
        }

        // Use unique work name to prevent duplicates
        workManager.enqueueUniqueWork(
            "transfer_${task.id}",
            ExistingWorkPolicy.KEEP, // Keep existing work if already enqueued
            workRequest
        )

        println("[AndroidTransferManager] Enqueued work: taskId=${task.id}, workId=${workRequest.id}")
    }


    /**
     * Cancel a transfer task
     */
    suspend fun cancelTransfer(taskId: String) {
        // Cancel WorkManager work
        workManager.cancelUniqueWork("transfer_$taskId")

        // Update task status
        transferTaskDao.updateStatus(
            taskId = taskId,
            status = TransferStatus.CANCELLED,
        )
    }

    /**
     * Retry a failed transfer
     */
    suspend fun retryTransfer(taskId: String) {
//        val task = transferTaskDao.getTasksEmbeddedById(taskId) ?: return
//
//        // Reset task status
//        transferTaskDao.updateStatus(
//            taskId = taskId,
//            status = TransferStatus.PENDING,
//        )
//
//        // Re-enqueue work
//        enqueueWork(task)
    }

    /**
     * Get all transfer tasks as Flow
     */
    fun observeTransfers(serverId: Long): Flow<List<TransferTaskEmbedded>> {
        return transferTaskDao.getTasksEmbeddedByServerId(serverId)
    }

    /**
     * Get active transfer tasks
     */
    suspend fun getActiveTasks(): List<TransferTask> {
        return transferTaskDao.getActiveTasks()
    }


    /**
     * Clear completed transfers
     */
    suspend fun clearCompleted() {
        transferTaskDao.deleteCompleted()
    }

    /**
     * Clear failed transfers
     */
    suspend fun clearFailed() {
        transferTaskDao.deleteFailed()
    }

    suspend fun pausedTransfer(taskId: String) {

        val bytesTransferred =
            workManager.getWorkInfosForUniqueWorkFlow("transfer_$taskId").firstOrNull()
                ?.firstOrNull()?.progress?.getLong(BYTES_TRANSFERRED, 0L) ?: 0L

        workManager.cancelUniqueWork("transfer_$taskId").await()

        transferTaskDao.pausedStatus(
            taskId = taskId,
            status = TransferStatus.PAUSED,
            bytesTransferred = bytesTransferred,
        )
    }
}
