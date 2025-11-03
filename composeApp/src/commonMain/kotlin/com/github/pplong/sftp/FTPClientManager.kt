package com.github.pplong.sftp

import com.github.pplong.feat.browse.FTPFileUiModel
import com.github.pplong.sftp.def.FTPConfig
import com.github.pplong.sftp.def.FTPFile

/**
 * Factory interface for creating DownloadCallback instances
 * This allows platform-specific implementations (Android content:// URIs vs Desktop file paths)
 */
interface DownloadCallbackFactory {
    /**
     * Create a DownloadCallback for a specific file
     * @param remotePath The remote file path
     * @param fileName The file name to save as
     * @param downloadDir The download directory (can be a path or URI string)
     * @param onProgressUpdate Callback for progress updates (normalized 0.0-1.0)
     * @param onComplete Callback when download completes
     * @param onError Callback when download fails
     */
    fun create(
        remotePath: String,
        fileName: String,
        downloadDir: String,
        onProgressUpdate: (Float) -> Unit,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit,
        onOutputConfirmed: (String) -> Unit
    ): DownloadCallback
}

/**
 * Factory interface for creating UploadCallback instances
 * This allows platform-specific implementations (Android content:// URIs vs Desktop file paths)
 */
interface UploadCallbackFactory {
    /**
     * Create an UploadCallback for a specific file
     * @param localUri The local file URI or path
     * @param fileName The file name
     * @param remotePath The destination path on remote server
     * @param onProgressUpdate Callback for progress updates (normalized 0.0-1.0)
     * @param onComplete Callback when upload completes
     * @param onError Callback when upload fails
     */
    fun create(
        localUri: String,
        fileName: String,
        remotePath: String,
        onProgressUpdate: (Float) -> Unit,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit
    ): UploadCallback
}

class FTPClientManager(
    private val config: FTPConfig
) {
    lateinit var coreFTPClient: ICoreFTPClient

    suspend fun connect(): Boolean {
        coreFTPClient = SFTPClientFactory.create()
        return coreFTPClient.initClient(config)
    }

    suspend fun pwd(): String = coreFTPClient.pwd()

    suspend fun list(path: String): List<FTPFile> {
        return coreFTPClient.list(path)
    }

    suspend fun close() {
        coreFTPClient.close()
    }

    suspend fun delete(
        deleteFilesPaths: List<FTPFileUiModel>,
        onProgress: (removedCount: Int) -> Unit
    ) {
        coreFTPClient.delete(deleteFilesPaths, onProgress)
    }

    suspend fun mkdir(
        path: String
    ) {
        coreFTPClient.mkdir(path)
    }

    suspend fun find(
        query: String
    ): List<FTPFile> {
        return coreFTPClient.find(query)
    }
}