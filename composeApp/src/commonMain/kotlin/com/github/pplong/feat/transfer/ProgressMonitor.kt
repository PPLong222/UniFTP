package com.github.pplong.feat.transfer

import com.github.pplong.feat.transfer.model.TransferDirection
import kotlinx.coroutines.flow.Flow

/**
 * Real-time progress update event
 */
data class ProgressUpdate(
    val remotePath: String,
    val fileName: String,
    val progress: Float,  // 0.0 - 1.0
    val state: ProgressState,
    val direction: TransferDirection,
    val speed: Long, // Byte/s
    val lastModified: Long,
    val size: Long,
    val taskId: String
)

/**
 * Progress state (platform-agnostic)
 */
enum class ProgressState {
    WAITING,      // Queued, not started yet
    RUNNING,      // Currently transferring
    SUCCEEDED,    // Completed successfully
    FAILED,       // Failed with error
    CANCELLED     // Cancelled by user
}

/**
 * Platform-specific progress monitor
 * Monitors real-time progress from platform background tasks
 */
expect class ProgressMonitor {
    /**
     * Observe progress updates for all active transfers
     * Returns a Flow that emits progress updates in real-time
     */
    fun observeAllProgress(): Flow<List<ProgressUpdate>>
}
