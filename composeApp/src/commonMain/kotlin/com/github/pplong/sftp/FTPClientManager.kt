package com.github.pplong.sftp

import com.github.pplong.feat.browse.FTPFileUiModel
import com.github.pplong.sftp.def.FTPConfig
import com.github.pplong.sftp.def.FTPFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

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
        onError: (Throwable) -> Unit
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

    /**
     * Download files from the remote server
     * @param list List of files to download
     * @param downloadDir Download directory (platform-specific: file path or content:// URI)
     * @param callbackFactory Factory for creating platform-specific download callbacks
     * @param onProgress Progress callback for each file (file, progress 0.0-1.0)
     * @param onFileComplete Callback when each file completes
     * @param onFileError Callback when a file download fails
     */
    suspend fun download(
        list: List<FTPFileUiModel>,
        downloadDir: String,
        callbackFactory: DownloadCallbackFactory,
        onProgress: (FTPFileUiModel, Float) -> Unit,
        onFileComplete: ((FTPFileUiModel) -> Unit)? = null,
        onFileError: ((FTPFileUiModel, Throwable) -> Unit)? = null
    ) {
        list.forEach { file ->
            withContext(Dispatchers.IO) {

                // Skip directories
                if (file.isDirectory) {
                    return@withContext
                }

                val transferClient = SFTPClientFactory.createTransferClient()

                // Initialize transfer client
                if (!transferClient.initClient(config)) {
                    val error = IllegalStateException("Failed to initialize transfer client")
                    onFileError?.invoke(file, error)
                    return@withContext
                }

                try {
                    // Create callback for this file
                    val callback = callbackFactory.create(
                        remotePath = file.path,
                        fileName = file.name,
                        downloadDir = downloadDir,
                        onProgressUpdate = { progress ->
                            onProgress(file, progress)
                        },
                        onComplete = {
                            onFileComplete?.invoke(file)
                        },
                        onError = { error ->
                            onFileError?.invoke(file, error)
                        }
                    )

                    // Start download
                    val success = transferClient.downloadFile(file.path, callback)

                    if (!success) {
                        val error = IllegalStateException("Download failed for: ${file.name}")
                        onFileError?.invoke(file, error)
                    }
                } finally {
                    // Close transfer client after each file
                    transferClient.close()
                }
            }
        }
    }

    /**
     * Upload files to the remote server
     * @param files List of file information (local URI/path, fileName pairs)
     * @param remoteDir Remote directory path where files should be uploaded
     * @param callbackFactory Factory for creating platform-specific upload callbacks
     * @param onProgress Progress callback for each file (file info, progress 0.0-1.0)
     * @param onFileComplete Callback when each file completes
     * @param onFileError Callback when a file upload fails
     */
    suspend fun upload(
        files: List<Pair<String, String>>, // Pair of (localUri, fileName)
        remoteDir: String,
        callbackFactory: UploadCallbackFactory,
        onProgress: (Pair<String, String>, Float) -> Unit,
        onFileComplete: ((Pair<String, String>) -> Unit)? = null,
        onFileError: ((Pair<String, String>, Throwable) -> Unit)? = null
    ) {
        files.forEach { fileInfo ->
            withContext(Dispatchers.IO) {
                val (localUri, fileName) = fileInfo

                val transferClient = SFTPClientFactory.createTransferClient()

                // Initialize transfer client
                if (!transferClient.initClient(config)) {
                    val error = IllegalStateException("Failed to initialize transfer client")
                    onFileError?.invoke(fileInfo, error)
                    return@withContext
                }

                try {
                    // Build remote file path
                    val remotePath = if (remoteDir.endsWith("/")) {
                        "$remoteDir$fileName"
                    } else {
                        "$remoteDir/$fileName"
                    }

                    // Create callback for this file
                    val callback = callbackFactory.create(
                        localUri = localUri,
                        fileName = fileName,
                        remotePath = remotePath,
                        onProgressUpdate = { progress ->
                            onProgress(fileInfo, progress)
                        },
                        onComplete = {
                            onFileComplete?.invoke(fileInfo)
                        },
                        onError = { error ->
                            onFileError?.invoke(fileInfo, error)
                        }
                    )

                    // Start upload
                    val success = transferClient.uploadFile(remotePath, callback)

                    if (!success) {
                        val error = IllegalStateException("Upload failed for: $fileName")
                        onFileError?.invoke(fileInfo, error)
                    }
                } finally {
                    // Close transfer client after each file
                    transferClient.close()
                }
            }
        }
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