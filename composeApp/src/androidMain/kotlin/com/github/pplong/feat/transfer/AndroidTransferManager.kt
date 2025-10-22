package com.github.pplong.feat.transfer

import AppDatabase
import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.github.pplong.feat.transfer.model.TransferDirection
import com.github.pplong.feat.transfer.model.TransferStatus
import com.github.pplong.feat.transfer.model.TransferTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.map
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
        fileName: String,
        remotePath: String,
        downloadDir: String,
        serverHost: String,
        serverPort: Int,
        serverUsername: String,
        serverPassword: String,
        fileSize: Long
    ): String {
        val taskId = UUID.randomUUID().toString()
        val currentTime = System.currentTimeMillis()

        // Create transfer task
        val task = TransferTask(
            id = taskId,
            direction = TransferDirection.DOWNLOAD,
            status = TransferStatus.PENDING,
            fileName = fileName,
            localUri = "", // Not used for downloads
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
        serverHost: String,
        serverPort: Int,
        serverUsername: String,
        serverPassword: String,
        fileSize: Long
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
     * Observe WorkManager progress for a specific task
     */
    fun observeTaskProgress(taskId: String): Flow<TransferProgressEvent?> {
        val workId = taskWorkMap[taskId] ?: return kotlinx.coroutines.flow.flowOf(null)

        return workManager.getWorkInfoByIdFlow(workId).map { workInfo ->
            workInfo?.let {
                // Get task info from database for remotePath
                val task = transferTaskDao.getById(taskId)
                TransferProgressEvent(
                    taskId = taskId,
                    remotePath = task?.remotePath ?: "",
                    progress = it.progress.getInt(TransferWorker.PROGRESS_KEY, 0),
                    status = it.state
                )
            }
        }
    }

    /**
     * Observe all active transfer works' progress
     */
    fun observeAllProgress(): Flow<List<TransferProgressEvent>> {
        return workManager.getWorkInfosByTagFlow("transfer")
            .map { workInfos ->
                workInfos.mapNotNull { workInfo ->
                    val taskId = workInfo.progress.getString(TransferWorker.KEY_TASK_ID)
                        ?: workInfo.tags.find { it.startsWith("transfer_") }?.substring(9)
                        ?: return@mapNotNull null

                    val task = transferTaskDao.getById(taskId) ?: return@mapNotNull null

                    TransferProgressEvent(
                        taskId = taskId,
                        remotePath = task.remotePath,
                        progress = workInfo.progress.getInt(TransferWorker.PROGRESS_KEY, 0),
                        status = workInfo.state
                    )
                }
            }
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
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Retry a failed transfer
     */
    suspend fun retryTransfer(taskId: String) {
        val task = transferTaskDao.getById(taskId) ?: return

        // Reset task status
        transferTaskDao.updateStatus(
            taskId = taskId,
            status = TransferStatus.PENDING,
            updatedAt = System.currentTimeMillis()
        )

        // Re-enqueue work
        enqueueWork(task)
    }

    /**
     * Get all transfer tasks as Flow
     */
    fun observeTransfers(): Flow<List<TransferTask>> {
        return transferTaskDao.observeAll()
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
}
