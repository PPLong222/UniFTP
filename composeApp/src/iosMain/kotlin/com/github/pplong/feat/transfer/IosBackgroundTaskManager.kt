package com.github.pplong.feat.transfer

import com.github.pplong.feat.transfer.model.TransferStatus
import com.github.pplong.feat.transfer.model.TransferTask
import AppDatabase
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid

/**
 * iOS Background Task Manager
 * Manages background time allocation for transfers using UIApplication.beginBackgroundTask
 */
class IosBackgroundTaskManager : KoinComponent {

    private val database: AppDatabase by inject()
    private val transferTaskDao = database.getTransferTaskDao()

    private var backgroundTaskId: UIBackgroundTaskIdentifier = UIBackgroundTaskInvalid
    private var currentTaskId: String? = null

    /**
     * Begin background task for a transfer
     * iOS gives approximately 30 seconds to save state when app goes to background
     */
    fun beginBackgroundTask(task: TransferTask) {
        println("[iOS BG Task] Beginning background task for: ${task.fileName}")

        currentTaskId = task.id

        backgroundTaskId = UIApplication.sharedApplication.beginBackgroundTaskWithName(
            "Transfer-${task.id}"
        ) {
            // Expiration handler - called when time is about to expire
            println("[iOS BG Task] Background time expiring, saving checkpoint...")
            saveCheckpointAndPause(task.id)
            endBackgroundTask()
        }

        if (backgroundTaskId == UIBackgroundTaskInvalid) {
            println("[iOS BG Task] WARNING: Failed to start background task!")
        } else {
            println("[iOS BG Task] Background task started with ID: $backgroundTaskId")
        }
    }

    /**
     * Save checkpoint and pause transfer
     */
    private fun saveCheckpointAndPause(taskId: String) {
        println("[iOS BG Task] Saving checkpoint for task: $taskId")

        runBlocking {
            // Update task status to PAUSED
            transferTaskDao.updateStatus(
                taskId = taskId,
                status = TransferStatus.PAUSED
            )
        }

        println("[iOS BG Task] Checkpoint saved, transfer paused")
    }

    /**
     * End background task
     */
    fun endBackgroundTask() {
        if (backgroundTaskId != UIBackgroundTaskInvalid) {
            println("[iOS BG Task] Ending background task: $backgroundTaskId")
            UIApplication.sharedApplication.endBackgroundTask(backgroundTaskId)
            backgroundTaskId = UIBackgroundTaskInvalid
            currentTaskId = null
        }
    }

    /**
     * Check if background task is active
     */
    fun isBackgroundTaskActive(): Boolean {
        return backgroundTaskId != UIBackgroundTaskInvalid
    }

    /**
     * Get remaining background time (if available)
     */
    fun getRemainingBackgroundTime(): Double {
        return UIApplication.sharedApplication.backgroundTimeRemaining
    }
}
