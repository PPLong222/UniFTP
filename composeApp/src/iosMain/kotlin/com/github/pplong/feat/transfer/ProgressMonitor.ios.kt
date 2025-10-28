package com.github.pplong.feat.transfer

import AppDatabase
import com.github.pplong.feat.transfer.model.TransferStatus
import com.github.pplong.feat.transfer.model.TransferTaskDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/**
 * iOS implementation of ProgressMonitor
 * Monitors transfer progress by observing database updates
 */
actual class ProgressMonitor : KoinComponent {

    private val database: AppDatabase by inject()
    private val transferTaskDao: TransferTaskDao = database.getTransferTaskDao()

    // Speed calculation state for each task
    private val speedTracking = mutableMapOf<String, SpeedTracker>()

    /**
     * Speed tracking data for a single task
     */
    private data class SpeedTracker(
        var lastBytesTransferred: Long = 0L,
        var lastTimestamp: Long = 0L,
        var lastSpeed: Long = 0L
    )

    /**
     * Observe all active transfer tasks' progress
     * Monitors database for transfer task updates
     */
    actual fun observeAllProgress(): Flow<List<ProgressUpdate>> {
        // Observe all tasks from database and convert to progress updates
        return transferTaskDao.observeActiveTasks()
            .map { tasks ->
                tasks.mapNotNull { taskEmbedded ->
                    val task = taskEmbedded.task

                    // Only include tasks that are actively transferring or pending
                    if (task.status != TransferStatus.IN_PROGRESS &&
                        task.status != TransferStatus.PENDING) {
                        return@mapNotNull null
                    }

                    // Calculate progress
                    val progress = if (task.size > 0) {
                        task.bytesTransferred.toFloat() / task.size.toFloat()
                    } else {
                        0f
                    }

                    // Calculate speed using speed tracker
                    val speed = calculateSpeed(task.id, task.bytesTransferred)

                    // Convert status to ProgressState
                    val state = when (task.status) {
                        TransferStatus.PENDING -> ProgressState.WAITING
                        TransferStatus.IN_PROGRESS -> ProgressState.RUNNING
                        TransferStatus.COMPLETED -> ProgressState.SUCCEEDED
                        TransferStatus.FAILED -> ProgressState.FAILED
                        TransferStatus.CANCELLED -> ProgressState.CANCELLED
                        TransferStatus.PAUSED -> ProgressState.WAITING // Treat paused as waiting
                    }

                    ProgressUpdate(
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
    }

    /**
     * Calculate transfer speed for a task
     * Updates speed every 3 seconds (similar to Android implementation)
     * @param taskId The task ID
     * @param bytesTransferred Current bytes transferred
     * @return Speed in bytes per second
     */
    private fun calculateSpeed(taskId: String, bytesTransferred: Long): Long {
        // Get or create speed tracker for this task
        val tracker = speedTracking.getOrPut(taskId) { SpeedTracker() }

        // Get current timestamp in milliseconds
        val currentTimestamp = (NSDate().timeIntervalSince1970 * 1000).toLong()

        // If this is the first update, initialize and return 0
        if (tracker.lastTimestamp == 0L) {
            tracker.lastBytesTransferred = bytesTransferred
            tracker.lastTimestamp = currentTimestamp
            tracker.lastSpeed = 0L
            return 0L
        }

        // Calculate time difference
        val timeDiff = currentTimestamp - tracker.lastTimestamp

        // Update speed every 3 seconds (similar to Android)
        if (timeDiff > 3000L) {
            val bytesDiff = bytesTransferred - tracker.lastBytesTransferred

            // Calculate speed: bytes per second
            tracker.lastSpeed = if (timeDiff > 0) {
                (bytesDiff * 1000) / timeDiff
            } else {
                0L
            }

            // Update tracking values
            tracker.lastBytesTransferred = bytesTransferred
            tracker.lastTimestamp = currentTimestamp

            println("[iOS ProgressMonitor] Task $taskId speed: ${tracker.lastSpeed} bytes/s")
        }

        // Return last calculated speed
        return tracker.lastSpeed
    }

    /**
     * Clean up speed tracking for completed tasks
     */
    fun cleanupSpeedTracking(taskId: String) {
        speedTracking.remove(taskId)
    }
}
