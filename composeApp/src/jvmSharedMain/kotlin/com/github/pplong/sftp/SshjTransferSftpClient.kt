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
        private const val BUFFER_SIZE = 256 * 1024
    }

    override suspend fun downloadFile(
        remotePath: String,
        callback: DownloadCallback
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext downloadFileWithResume(remotePath, null, callback, 0L)
    }

    override suspend fun downloadFileWithResume(
        remotePath: String,
        localUri: String?,
        callback: DownloadCallback,
        resumeOffset: Long
    ): Boolean = withContext(Dispatchers.IO) {
        ssh.newStatefulSFTPClient().use { sftp ->
            val fileSize = getFileSize(remotePath)
            val isNew = localUri == null
            if (fileSize < 0) {
                val error = IllegalArgumentException("Failed to get file size for: $remotePath")
                println(error.message)
                callback.onError(error)
                return@withContext false
            }

            if (resumeOffset > fileSize) {
                val error = IllegalArgumentException(
                    "Resume offset ($resumeOffset) exceeds file size ($fileSize)"
                )
                println(error.message)
                callback.onError(error)
                return@withContext false
            }
            // Get output stream from callback
            val outputStreamInfo = if (isNew) {
                callback.openOutputStream(fileSize, resumeOffset)
            } else {
                callback.openOutputStream(localUri)
            }

            if (outputStreamInfo == null) {
                val error = IllegalStateException("Failed to open output stream")
                println(error.message)
                callback.onError(error)
                return@withContext false
            }

            val outputStream = outputStreamInfo.stream as? OutputStream
            if (outputStream == null) {
                val error = IllegalStateException("Failed to convert to output stream")
                println(error.message)
                callback.onError(error)
                return@withContext false
            }
            val existingFileSize = outputStreamInfo.fileSize

            outputStream.use {
                // Determine actual resume offset (use existing file size if available)
                if (localUri == null) {
                    callback.onOutputConfirmed(outputStreamInfo.uri)
                }
                val actualResumeOffset = existingFileSize
                // Validate resume offset
                if (actualResumeOffset > fileSize) {
                    val error = IllegalArgumentException(
                        "Existing file size ($actualResumeOffset) exceeds remote file size ($fileSize)"
                    )
                    println(error.message)
                    callback.onError(error)
                    return@withContext false
                }

                try {
                    sftp.open(remotePath).use { remoteFile ->
                        val inputStream = remoteFile.RemoteFileInputStream(actualResumeOffset)
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int
                        var totalBytesRead = actualResumeOffset

                        // Report initial progress if resuming
                        if (actualResumeOffset > 0L) {
                            callback.onProgress(totalBytesRead, fileSize)
                        }

                        inputStream.use {
                            // Read and write data
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead

                                // Report progress (will throw exception if cancelled)
                                callback.onProgress(totalBytesRead, fileSize)
                            }
                            outputStream.flush()
                            val success = totalBytesRead == fileSize
                            if (success) {
                                callback.onComplete()
                                return@withContext true
                            } else {
                                val error = IllegalStateException(
                                    "Download incomplete: $totalBytesRead / $fileSize bytes"
                                )
                                println(error.message)
                                callback.onError(error)
                                return@withContext false
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Re-throw the exception to be handled by the caller
                    // This allows TransferCancelledException to propagate properly
                    throw e
                }
            }
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
                try {
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
                            // rw-r--r-- in binary: 110 100 100
                            return 0b110_100_100 // 420 in decimal (octal 0644)
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
                } catch (e: Exception) {
                    // Re-throw the exception to be handled by the caller
                    // This allows TransferCancelledException to propagate properly
                    throw e
                }
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