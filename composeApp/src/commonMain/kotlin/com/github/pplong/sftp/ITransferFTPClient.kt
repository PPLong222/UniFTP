package com.github.pplong.sftp

/**
 * Callback interface for download operations
 */
interface DownloadCallback {
    /**
     * Called to get the OutputStream for writing downloaded data
     * This allows the caller to provide different output targets (File, ContentResolver URI, etc.)
     *
     * @param fileSize The total size of the file to be downloaded
     * @param resumeOffset The byte offset from where download will start (0 for new download)
     * @return Pair of (OutputStream, current file size) or null if failed
     *         - OutputStream: where to write the data
     *         - Long: current size of existing file (for resume support), 0 for new download
     */
    suspend fun openOutputStream(fileSize: Long, resumeOffset: Long): Pair<Any, Long>?

    /**
     * Called when download progress updates
     * @param bytesTransferred Number of bytes transferred so far
     * @param totalBytes Total file size in bytes
     */
    fun onProgress(bytesTransferred: Long, totalBytes: Long) {}

    /**
     * Called when download completes successfully
     */
    fun onComplete() {}

    /**
     * Called when download fails
     * @param error The error that occurred
     */
    fun onError(error: Throwable) {}
}

/**
 * Callback interface for upload operations
 */
interface UploadCallback {
    /**
     * Called to get the InputStream for reading upload data
     * This allows the caller to provide different input sources (File, ContentResolver URI, etc.)
     *
     * @return Pair of (InputStream, file size) or null if failed
     *         - InputStream: where to read the data from
     *         - Long: total file size in bytes
     */
    suspend fun openInputStream(): Pair<Any, Long>?

    /**
     * Called when upload progress updates
     * @param bytesTransferred Number of bytes transferred so far
     * @param totalBytes Total file size in bytes
     */
    fun onProgress(bytesTransferred: Long, totalBytes: Long) {}

    /**
     * Called when upload completes successfully
     */
    fun onComplete() {}

    /**
     * Called when upload fails
     * @param error The error that occurred
     */
    fun onError(error: Throwable) {}
}

/**
 * Interface for FTP/SFTP file transfer operations
 */
interface ITransferFTPClient : IBaseFTPClient {
    /**
     * Download a file from the remote server
     * Uses the DownloadCallback to handle output stream creation (supports content:// URIs on Android)
     *
     * @param remotePath The full path of the file on the remote server
     * @param callback Callback for handling output stream and progress
     * @return True if download successful, false otherwise
     */
    suspend fun downloadFile(
        remotePath: String,
        callback: DownloadCallback
    ): Boolean

    /**
     * Download a file from the remote server with resume support
     *
     * @param remotePath The full path of the file on the remote server
     * @param callback Callback for handling output stream and progress
     * @param resumeOffset The byte offset from where to resume the download (0 for new download)
     * @return True if download successful, false otherwise
     */
    suspend fun downloadFileWithResume(
        remotePath: String,
        callback: DownloadCallback,
        resumeOffset: Long = 0L
    ): Boolean

    /**
     * Upload a file to the remote server
     * Uses the UploadCallback to handle input stream creation (supports content:// URIs on Android)
     *
     * @param remotePath The full path where the file should be saved on the remote server
     * @param callback Callback for handling input stream and progress
     * @return True if upload successful, false otherwise
     */
    suspend fun uploadFile(
        remotePath: String,
        callback: UploadCallback
    ): Boolean

    /**
     * Get the size of a remote file in bytes
     * @param remotePath The full path of the file on the remote server
     * @return File size in bytes, or -1 if the file doesn't exist or error occurred
     */
    suspend fun getFileSize(remotePath: String): Long
}