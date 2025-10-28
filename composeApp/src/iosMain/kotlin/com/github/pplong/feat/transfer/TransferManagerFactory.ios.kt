package com.github.pplong.feat.transfer

import com.github.pplong.feat.browse.FTPFileUiModel
import com.github.pplong.feat.transfer.model.TransferTaskEmbedded
import kotlinx.coroutines.flow.Flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * iOS implementation of TransferManager using BackgroundTask
 */
private class IosTransferManagerImpl : TransferManager, KoinComponent {
    private val iosManager: IosTransferManager by inject()

    override suspend fun enqueueDownload(
        file: FTPFileUiModel,
        serverId: Long
    ): String {
        return iosManager.enqueueDownload(
            file = file,
            serverId = serverId
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
        return iosManager.enqueueUpload(
            fileName = fileName,
            localUri = localUri,
            remotePath = remotePath,
            fileSize = fileSize,
            serverId = serverId,
            lastModified = lastModifiedTime
        )
    }

    override suspend fun pausedTransfer(taskId: String) {
        iosManager.pausedTransfer(taskId)
    }

    override suspend fun cancelTransfer(taskId: String) {
        iosManager.cancelTransfer(taskId)
    }

    override fun observeTransfers(serverId: Long): Flow<List<TransferTaskEmbedded>> {
        return iosManager.observeTransfers(serverId)
    }

    override suspend fun clearCompleted() {
        iosManager.clearCompleted()
    }
}

actual object TransferManagerFactory {
    actual fun create(): TransferManager {
        return IosTransferManagerImpl()
    }
}
