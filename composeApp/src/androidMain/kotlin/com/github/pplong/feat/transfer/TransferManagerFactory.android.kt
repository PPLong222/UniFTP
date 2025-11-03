package com.github.pplong.feat.transfer

import com.github.pplong.feat.browse.FTPFileUiModel
import com.github.pplong.feat.transfer.model.TransferTaskEmbedded
import kotlinx.coroutines.flow.Flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Android implementation of TransferManager using WorkManager
 */
private class AndroidTransferManagerImpl : TransferManager, KoinComponent {
    private val androidManager: AndroidTransferManager by inject()

    override suspend fun enqueueDownload(
        file: FTPFileUiModel,
        serverId: Long
    ): String {
        return androidManager.enqueueDownload(
            file, serverId
        )
    }

    override suspend fun enqueueUpload(
        fileName: String,
        localUri: String,
        remotePath: String,
        fileSize: Long,
        serverId: Long,
        lastModifiedTime: Long
    ): String {
        return androidManager.enqueueUpload(
            fileName = fileName,
            localUri = localUri,
            remotePath = remotePath,
            fileSize = fileSize,
            serverId = serverId,
            lastModified = lastModifiedTime
        )
    }

    override suspend fun pausedTransfer(taskId: String) {
        return androidManager.pausedTransfer(taskId)
    }

    override suspend fun cancelTransfer(taskId: String) {
        androidManager.cancelTransfer(taskId)
    }

    override fun observeTransfers(serverId: Long): Flow<List<TransferTaskEmbedded>> {
        return androidManager.observeTransfers(serverId)
    }

    override suspend fun clearCompleted() {
        androidManager.clearCompleted()
    }

    override suspend fun resumeTransfer(taskId: String) {
        androidManager.resumeTransfer(taskId)
    }
}

actual object TransferManagerFactory {
    actual fun create(): TransferManager {
        return AndroidTransferManagerImpl()
    }
}
