package com.github.pplong.sftp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

/**
 * SSHJ-based implementation of ITransferFTPClient for file transfer operations
 * Supports both traditional file paths and Android content:// URIs through the DownloadCallback interface
 */
class SshjTransferSftpClient : SshjSftpBaseClient(), ITransferFTPClient {

    companion object {
        private const val BUFFER_SIZE = 32 * 1024 // 32KB buffer for efficient file transfer
    }

    override suspend fun downloadFile(
        remotePath: String,
        callback: DownloadCallback
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext downloadFileWithResume(remotePath, callback, 0L)
    }

    override suspend fun downloadFileWithResume(
        remotePath: String,
        callback: DownloadCallback,
        resumeOffset: Long
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Ensure SFTP client is initialized
            sftp = ssh.newStatefulSFTPClient()

            // Get file size
            val fileSize = getFileSize(remotePath)
            if (fileSize < 0) {
                val error = IllegalArgumentException("Failed to get file size for: $remotePath")
                println(error.message)
                callback.onError(error)
                return@withContext false
            }

            // Check if resume offset is valid
            if (resumeOffset > fileSize) {
                val error = IllegalArgumentException(
                    "Resume offset ($resumeOffset) exceeds file size ($fileSize)"
                )
                println(error.message)
                callback.onError(error)
                return@withContext false
            }

            // Get output stream from callback
            val outputStreamPair = callback.openOutputStream(fileSize, resumeOffset)
            if (outputStreamPair == null) {
                val error = IllegalStateException("Failed to open output stream")
                println(error.message)
                callback.onError(error)
                return@withContext false
            }

            val (outputStreamAny, existingFileSize) = outputStreamPair
            val outputStream = outputStreamAny as? OutputStream
            if (outputStream == null) {
                val error = IllegalArgumentException("Invalid output stream type")
                println(error.message)
                callback.onError(error)
                return@withContext false
            }

            // Determine actual resume offset (use existing file size if available)
            val actualResumeOffset = if (existingFileSize > 0) existingFileSize else resumeOffset

            // Validate resume offset
            if (actualResumeOffset > fileSize) {
                val error = IllegalArgumentException(
                    "Existing file size ($actualResumeOffset) exceeds remote file size ($fileSize)"
                )
                println(error.message)
                callback.onError(error)
                outputStream.close()
                return@withContext false
            }

            // Open remote file for reading
            val remoteFile = sftp.open(remotePath)

            try {
                // Create input stream starting from resume offset
                val inputStream = remoteFile.RemoteFileInputStream(actualResumeOffset)

                try {
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    var totalBytesRead = actualResumeOffset

                    // Report initial progress if resuming
                    if (actualResumeOffset > 0L) {
                        callback.onProgress(totalBytesRead, fileSize)
                    }

                    // Read and write data
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        // Report progress
                        callback.onProgress(totalBytesRead, fileSize)
                    }

                    outputStream.flush()

                    // Verify download completed successfully
                    val success = totalBytesRead == fileSize
                    if (success) {
                        callback.onComplete()
                    } else {
                        val error = IllegalStateException(
                            "Download incomplete: $totalBytesRead / $fileSize bytes"
                        )
                        println(error.message)
                        callback.onError(error)
                    }

                    return@withContext success
                } finally {
                    try {
                        inputStream.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    try {
                        outputStream.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } finally {
                try {
                    remoteFile.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            callback.onError(e)
            return@withContext false
        }
    }

    override suspend fun uploadFile(
        remotePath: String,
        callback: UploadCallback
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Ensure SFTP client is initialized
            sftp = ssh.newStatefulSFTPClient()

            // Get input stream from callback
            val inputStreamPair = callback.openInputStream()
            if (inputStreamPair == null) {
                val error = IllegalStateException("Failed to open input stream")
                println(error.message)
                callback.onError(error)
                return@withContext false
            }

            val (inputStreamAny, fileSize) = inputStreamPair
            val inputStream = inputStreamAny as? java.io.InputStream
            if (inputStream == null) {
                val error = IllegalArgumentException("Invalid input stream type")
                println(error.message)
                callback.onError(error)
                return@withContext false
            }

            // Open remote file for writing
            val remoteFile = sftp.open(
                remotePath, setOf(
                    net.schmizz.sshj.sftp.OpenMode.WRITE,
                    net.schmizz.sshj.sftp.OpenMode.CREAT,
                    net.schmizz.sshj.sftp.OpenMode.TRUNC
                )
            )

            try {
                // Create output stream for remote file
                val outputStream = remoteFile.RemoteFileOutputStream()

                try {
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    // Read from local file and write to remote
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        // Report progress
                        callback.onProgress(totalBytesRead, fileSize)
                    }

                    outputStream.flush()

                    // Verify upload completed successfully
                    val success = totalBytesRead == fileSize
                    if (success) {

                        callback.onComplete()
                    } else {
                        val error = IllegalStateException(
                            "Upload incomplete: $totalBytesRead / $fileSize bytes"
                        )
                        println(error.message)
                        callback.onError(error)
                    }

                    return@withContext success
                } finally {
                    try {
                        inputStream.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    try {
                        outputStream.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } finally {
                try {
                    remoteFile.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            callback.onError(e)
            return@withContext false
        }
    }

    override suspend fun getFileSize(remotePath: String): Long = withContext(Dispatchers.IO) {
        try {
            // Ensure SFTP client is initialized
            sftp = ssh.newStatefulSFTPClient()
            // Get file attributes
            val attrs = sftp.stat(remotePath)
            return@withContext attrs.size
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext -1L
        }
    }
}