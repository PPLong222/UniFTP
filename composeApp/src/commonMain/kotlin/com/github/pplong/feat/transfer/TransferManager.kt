package com.github.pplong.feat.transfer

import com.github.pplong.feat.transfer.model.TransferTask
import kotlinx.coroutines.flow.Flow

/**
 * Platform-agnostic transfer manager interface
 * Implemented differently on Android (WorkManager) and iOS (BackgroundTask)
 */
interface TransferManager {
    /**
     * Enqueue/start a download task
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
    ): String

    /**
     * Enqueue/start an upload task
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
    ): String

    /**
     * Cancel a transfer
     */
    suspend fun cancelTransfer(taskId: String)

    /**
     * Observe all transfers
     */
    fun observeTransfers(): Flow<List<TransferTask>>

    /**
     * Clear completed transfers
     */
    suspend fun clearCompleted()
}

/**
 * Expect platform-specific factory
 */
expect object TransferManagerFactory {
    fun create(): TransferManager
}
