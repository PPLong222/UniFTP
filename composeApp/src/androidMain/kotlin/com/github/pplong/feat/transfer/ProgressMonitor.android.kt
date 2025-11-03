package com.github.pplong.feat.transfer

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.github.pplong.feat.transfer.model.TransferTaskDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Android implementation using WorkManager
 */
actual class ProgressMonitor(
    private val context: Context,
    private val androidTransferManager: AndroidTransferManager
) : KoinComponent {

    private val workManager = WorkManager.getInstance(context)
    private val transferTaskDao: TransferTaskDao by inject()

    /**
     * Observe all active WorkManager tasks and emit progress updates
     * This is a hot Flow that reacts to new tasks being enqueued
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    actual fun observeAllProgress(): Flow<List<ProgressUpdate>> {
        println("[ProgressMonitor] Starting to observe all progress")

        return androidTransferManager.taskWorkMapFlow
            .flatMapLatest { taskWorkMap ->
                println("[ProgressMonitor] TaskWorkMap updated, size: ${taskWorkMap.size}")
                taskWorkMap.forEach { (taskId, workId) ->
                    println("[ProgressMonitor] Monitoring taskId=$taskId, workId=$workId")
                }

                if (taskWorkMap.isEmpty()) {
                    println("[ProgressMonitor] No tasks to monitor")
                    flowOf(emptyList())
                } else {
                    // Create a Flow for each task's progress
                    val progressFlows = taskWorkMap.map { (taskId, workId) ->
                        workManager.getWorkInfoByIdFlow(workId)
                            .filterNotNull()
                            .map { workInfo ->
                                convertToProgressUpdate(
                                    taskId,
                                    workInfo
                                )
                            }
                            .filterNotNull()
                    }
                    combine(progressFlows) {
                        it.toList()
                    }
                }
            }
    }

    /**
     * Convert WorkInfo to ProgressUpdate
     */
    private suspend fun convertToProgressUpdate(
        taskId: String,
        workInfo: WorkInfo
    ): ProgressUpdate? {
        // Get task details from database
        // TODO performance
        val task = transferTaskDao.getTasksEmbeddedById(taskId)?.task ?: return null

        val state = when (workInfo.state) {
            WorkInfo.State.ENQUEUED -> ProgressState.WAITING
            WorkInfo.State.RUNNING -> ProgressState.RUNNING
            WorkInfo.State.SUCCEEDED -> ProgressState.SUCCEEDED
            WorkInfo.State.FAILED -> ProgressState.FAILED
            WorkInfo.State.CANCELLED -> ProgressState.CANCELLED
            else -> return null
        }

        val progress = if (workInfo.state == WorkInfo.State.RUNNING) {
            workInfo.progress.getFloat(TransferWorker.PROGRESS, 0F)
        } else {
            0f
        }

        val speed = if (workInfo.state == WorkInfo.State.RUNNING) {
            workInfo.progress.getLong(TransferWorker.SPEED, 0)
        } else {
            0L
        }

        return ProgressUpdate(
            remotePath = task.remotePath,
            fileName = task.fileName,
            progress = progress,
            state = state,
            direction = task.direction,
            speed = speed,
            lastModified = task.fileLastModified,
            size = task.size,
            taskId = task.id
        )
    }
}
