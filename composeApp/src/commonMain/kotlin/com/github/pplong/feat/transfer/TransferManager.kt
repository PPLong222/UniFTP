package com.github.pplong.feat.transfer

import com.github.pplong.feat.browse.FTPFileUiModel
import com.github.pplong.feat.transfer.model.TransferTaskEmbedded
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
        file: FTPFileUiModel,
        serverId: Long
    ): String

    /**
     * Enqueue/start an upload task
     */
    suspend fun enqueueUpload(
        fileName: String,
        localUri: String,
        remotePath: String,
        fileSize: Long,
        serverId: Long,
        lastModifiedTime: Long
    ): String

    /**
     * Cancel a transfer
     */
    suspend fun cancelTransfer(taskId: String)

    /**
     * Observe all transfers
     */
    fun observeTransfers(serverId: Long): Flow<List<TransferTaskEmbedded>>

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
