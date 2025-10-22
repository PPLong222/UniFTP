package com.github.pplong.feat.transfer

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.github.pplong.feat.transfer.model.TransferTaskDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlinx.coroutines.ExperimentalCoroutinesApi

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
                            .map { workInfo ->
                                println("[ProgressMonitor] WorkInfo for $taskId: state=${workInfo?.state}, progress=${workInfo?.progress?.getInt(TransferWorker.PROGRESS_KEY, -1)}")
                                workInfo?.let { convertToProgressUpdate(taskId, it) }
                            }
                    }

                    // Combine all flows
                    combine(progressFlows) { updates ->
                        val filtered = updates.filterNotNull()
                        println("[ProgressMonitor] Emitting ${filtered.size} progress updates")
                        filtered
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
        val task = transferTaskDao.getById(taskId) ?: return null

        val state = when (workInfo.state) {
            WorkInfo.State.ENQUEUED -> ProgressState.WAITING
            WorkInfo.State.RUNNING -> ProgressState.RUNNING
            WorkInfo.State.SUCCEEDED -> ProgressState.SUCCEEDED
            WorkInfo.State.FAILED -> ProgressState.FAILED
            WorkInfo.State.CANCELLED -> ProgressState.CANCELLED
            else -> return null
        }

        val progress = if (workInfo.state == WorkInfo.State.RUNNING) {
            workInfo.progress.getInt(TransferWorker.PROGRESS_KEY, 0) / 100f
        } else {
            0f
        }

        return ProgressUpdate(
            remotePath = task.remotePath,
            fileName = task.fileName,
            progress = progress,
            state = state
        )
    }
}
