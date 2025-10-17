package com.github.pplong.sftp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.xfer.LocalFileFilter
import net.schmizz.sshj.xfer.LocalSourceFile
import java.io.InputStream
import java.io.OutputStream

/**
 * SSHJ-based implementation of ITransferFTPClient for file transfer operations
 * Supports both traditional file paths and Android content:// URIs through the DownloadCallback interface
 */
class SshjTransferSftpClient : SshjSftpBaseClient(), ITransferFTPClient {

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
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
            val sftp = ssh.newStatefulSFTPClient()

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
        ssh.newStatefulSFTPClient().use { sftp ->
            val inputStreamPair = callback.openInputStream()
            if (inputStreamPair == null) {
                val error = IllegalStateException("Failed to open input stream")
                println(error.message)
                callback.onError(error)
                return@withContext false
            }

            val (inputStreamAny, fileSize) = inputStreamPair
            val inputStream = inputStreamAny as? InputStream

            if (inputStream == null) {
                val error = IllegalArgumentException("Invalid input stream type")
                println(error.message)
                callback.onError(error)
                return@withContext false
            }
            inputStream.use {
                val localSourceFile = object : LocalSourceFile {
                    override fun getName(): String {
                        return remotePath.substringAfterLast('/')
                    }

                    override fun getLength(): Long {
                        return fileSize
                    }

                    override fun getInputStream(): InputStream {
                        // Wrap the input stream with progress tracking
                        return ProgressTrackingInputStream(inputStream, fileSize, callback)
                    }

                    override fun getPermissions(): Int {
                        return 644 // Default file permissions
                    }

                    override fun isFile(): Boolean = true

                    override fun isDirectory(): Boolean = false
                    override fun getChildren(filter: LocalFileFilter?): Iterable<LocalSourceFile?>? {
                        return null
                    }

                    override fun getLastAccessTime(): Long = System.currentTimeMillis() / 1000

                    override fun getLastModifiedTime(): Long = System.currentTimeMillis() / 1000

                    override fun providesAtimeMtime(): Boolean = true
                }
                sftp.put(localSourceFile, remotePath)
            }
            // Upload completed successfully
            callback.onComplete()
            return@withContext true
        }
    }


    /**
     * InputStream wrapper that tracks read progress and reports to callback
     */
    private class ProgressTrackingInputStream(
        private val inputStream: InputStream,
        private val totalSize: Long,
        private val callback: UploadCallback
    ) : InputStream() {
        private var bytesRead = 0L

        override fun read(): Int {
            val byte = inputStream.read()
            if (byte != -1) {
                bytesRead++
                callback.onProgress(bytesRead, totalSize)
            }
            return byte
        }

        override fun read(b: ByteArray): Int {
            val count = inputStream.read(b)
            if (count > 0) {
                bytesRead += count
                callback.onProgress(bytesRead, totalSize)
            }
            return count
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val count = inputStream.read(b, off, len)
            if (count > 0) {
                bytesRead += count
                callback.onProgress(bytesRead, totalSize)
            }
            return count
        }

        override fun close() {
            inputStream.close()
        }

        override fun available(): Int = inputStream.available()

        override fun skip(n: Long): Long = inputStream.skip(n)

        override fun mark(readlimit: Int) = inputStream.mark(readlimit)

        override fun reset() = inputStream.reset()

        override fun markSupported(): Boolean = inputStream.markSupported()
    }

    override suspend fun getFileSize(remotePath: String): Long = withContext(Dispatchers.IO) {
        return@withContext ssh.newStatefulSFTPClient().use { sftp ->
            sftp.stat(remotePath).size
        }
    }
}