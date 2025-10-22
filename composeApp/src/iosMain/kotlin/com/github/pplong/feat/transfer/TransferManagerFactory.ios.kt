package com.github.pplong.feat.transfer

import com.github.pplong.feat.transfer.model.TransferTask
import kotlinx.coroutines.flow.Flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * iOS implementation of TransferManager using BackgroundTask
 */
private class IosTransferManagerImpl : TransferManager, KoinComponent {
    private val iosManager: IosTransferManager by inject()

    override suspend fun enqueueDownload(
        fileName: String,
        remotePath: String,
        downloadDir: String,
        serverHost: String,
        serverPort: Int,
        serverUsername: String,
        serverPassword: String,
        fileSize: Long
    ): String {
        return iosManager.startDownload(
            fileName, remotePath, downloadDir,
            serverHost, serverPort, serverUsername, serverPassword, fileSize
        )
    }

    override suspend fun enqueueUpload(
        fileName: String,
        localUri: String,
        remotePath: String,
        serverHost: String,
        serverPort: Int,
        serverUsername: String,
        serverPassword: String,
        fileSize: Long
    ): String {
        return iosManager.startUpload(
            fileName, localUri, remotePath,
            serverHost, serverPort, serverUsername, serverPassword, fileSize
        )
    }

    override suspend fun cancelTransfer(taskId: String) {
        iosManager.cancelTransfer(taskId)
    }

    override fun observeTransfers(): Flow<List<TransferTask>> {
        return iosManager.observeTransfers()
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
