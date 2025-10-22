package com.github.pplong.feat.transfer

import com.github.pplong.feat.transfer.model.TransferTask
import kotlinx.coroutines.flow.Flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Android implementation of TransferManager using WorkManager
 */
private class AndroidTransferManagerImpl : TransferManager, KoinComponent {
    private val androidManager: AndroidTransferManager by inject()

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
        return androidManager.enqueueDownload(
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
        return androidManager.enqueueUpload(
            fileName, localUri, remotePath,
            serverHost, serverPort, serverUsername, serverPassword, fileSize
        )
    }

    override suspend fun cancelTransfer(taskId: String) {
        androidManager.cancelTransfer(taskId)
    }

    override fun observeTransfers(): Flow<List<TransferTask>> {
        return androidManager.observeTransfers()
    }

    override suspend fun clearCompleted() {
        androidManager.clearCompleted()
    }
}

actual object TransferManagerFactory {
    actual fun create(): TransferManager {
        return AndroidTransferManagerImpl()
    }
}
